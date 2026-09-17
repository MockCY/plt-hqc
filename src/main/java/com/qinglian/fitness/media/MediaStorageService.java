package com.qinglian.fitness.media;

import com.qinglian.fitness.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class MediaStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediaStorageService.class);
    private static final String VIDEO_CONTENT_TYPE = "video/mp4";
    private static final int TRANSCODE_LOG_LIMIT = 4_000;

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
        "image", Set.of("image/jpeg", "image/png", "image/webp"),
        "video", Set.of("video/mp4", "video/webm", "video/quicktime"),
        "audio", Set.of("audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/wav", "audio/x-wav", "audio/ogg")
    );
    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
        Map.entry("image/jpeg", ".jpg"),
        Map.entry("image/png", ".png"),
        Map.entry("image/webp", ".webp"),
        Map.entry("video/mp4", ".mp4"),
        Map.entry("video/webm", ".webm"),
        Map.entry("video/quicktime", ".mov"),
        Map.entry("audio/mpeg", ".mp3"),
        Map.entry("audio/mp4", ".m4a"),
        Map.entry("audio/x-m4a", ".m4a"),
        Map.entry("audio/wav", ".wav"),
        Map.entry("audio/x-wav", ".wav"),
        Map.entry("audio/ogg", ".ogg")
    );

    private final Path root;
    private final boolean videoTranscodingEnabled;
    private final String ffmpegCommand;
    private final long videoTranscodingTimeoutSeconds;

    public MediaStorageService(
            @Value("${app.media.root}") String root,
            @Value("${app.media.video-transcoding.enabled:true}") boolean videoTranscodingEnabled,
            @Value("${app.media.video-transcoding.ffmpeg-command:ffmpeg}") String ffmpegCommand,
            @Value("${app.media.video-transcoding.timeout-seconds:900}") long videoTranscodingTimeoutSeconds) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.videoTranscodingEnabled = videoTranscodingEnabled;
        this.ffmpegCommand = ffmpegCommand;
        this.videoTranscodingTimeoutSeconds = Math.max(30, videoTranscodingTimeoutSeconds);
    }

    public StoredMedia store(MultipartFile file, String kind) {
        String normalizedKind = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        Set<String> allowed = ALLOWED_TYPES.get(normalizedKind);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_EMPTY", "请选择需要上传的文件");
        }
        if (allowed == null || !allowed.contains(contentType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_TYPE_INVALID", "不支持该媒体格式");
        }

        LocalDate today = LocalDate.now();
        String relativeDirectory = normalizedKind + "s/" + today.getYear() + "/" + String.format("%02d", today.getMonthValue());
        String mediaId = UUID.randomUUID().toString();
        String storedContentType = "video".equals(normalizedKind) && videoTranscodingEnabled ? VIDEO_CONTENT_TYPE : contentType;
        String filename = mediaId + EXTENSIONS.get(storedContentType);
        Path directory = root.resolve(relativeDirectory).normalize();
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_PATH_INVALID", "文件路径不正确");
        }

        try {
            Files.createDirectories(directory);
            if ("video".equals(normalizedKind) && videoTranscodingEnabled) transcodeVideo(file, contentType, directory, target, mediaId);
            else copy(file, target);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_SAVE_FAILED", "媒体文件保存失败");
        }

        String path = "/api/media/files/" + relativeDirectory + "/" + filename;
        try {
            return new StoredMedia(path, normalizedKind, storedContentType, Files.size(target), file.getOriginalFilename());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_SAVE_FAILED", "媒体文件保存失败");
        }
    }

    private void copy(MultipartFile file, Path target) throws IOException {
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void transcodeVideo(MultipartFile file, String contentType, Path directory, Path target, String mediaId)
            throws IOException {
        Path source = directory.resolve("." + mediaId + ".source" + EXTENSIONS.get(contentType));
        Path output = directory.resolve("." + mediaId + ".transcoding.mp4");
        Path log = directory.resolve("." + mediaId + ".ffmpeg.log");
        try {
            copy(file, source);
            List<String> command = List.of(
                ffmpegCommand,
                "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-fflags", "+genpts", "-i", source.toString(),
                "-map", "0:v:0", "-map", "0:a:0?",
                "-vf", "scale=w='min(1280,iw)':h='min(720,ih)':force_original_aspect_ratio=decrease:force_divisible_by=2,fps=30",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-maxrate", "1800k", "-bufsize", "3600k", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k", "-ar", "48000",
                "-af", "aresample=async=1:first_pts=0",
                "-fps_mode", "cfr", "-avoid_negative_ts", "make_zero",
                "-movflags", "+faststart", output.toString()
            );
            Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(log.toFile())
                .start();
            boolean completed;
            try {
                completed = process.waitFor(videoTranscodingTimeoutSeconds, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_TRANSCODE_INTERRUPTED", "视频处理被中断，请重新上传");
            }
            if (!completed) {
                process.destroyForcibly();
                throw new ApiException(HttpStatus.REQUEST_TIMEOUT, "VIDEO_TRANSCODE_TIMEOUT", "视频处理超时，请压缩后重新上传");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(output) || Files.size(output) == 0) {
                LOGGER.warn("Video transcoding failed: {}", readTranscodeLog(log));
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VIDEO_TRANSCODE_FAILED", "视频处理失败，请检查文件编码后重新上传");
            }
            moveAtomically(output, target);
        } finally {
            deleteQuietly(source, output, log);
        }
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String readTranscodeLog(Path log) {
        try {
            String detail = Files.readString(log, StandardCharsets.UTF_8).trim();
            if (detail.length() > TRANSCODE_LOG_LIMIT) detail = detail.substring(detail.length() - TRANSCODE_LOG_LIMIT);
            return detail.isBlank() ? "FFmpeg did not return an error message" : detail;
        } catch (IOException exception) {
            return "FFmpeg error log could not be read";
        }
    }

    private void deleteQuietly(Path... paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                LOGGER.warn("Could not remove temporary media file {}", path, exception);
            }
        }
    }

    public StoredFile load(String relativePath) {
        String normalizedRelativePath = relativePath == null ? "" : relativePath.replace('\\', '/');
        while (normalizedRelativePath.startsWith("/")) {
            normalizedRelativePath = normalizedRelativePath.substring(1);
        }
        Path target = root.resolve(normalizedRelativePath).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "媒体文件不存在");
        }
        try {
            String detectedType = Files.probeContentType(target);
            MediaType mediaType = detectedType == null
                ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(detectedType);
            return new StoredFile(new FileSystemResource(target), mediaType);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_READ_FAILED", "媒体文件读取失败");
        }
    }

    public record StoredMedia(String url, String kind, String contentType, long size, String originalName) {
    }

    public record StoredFile(Resource resource, MediaType mediaType) {
    }
}
