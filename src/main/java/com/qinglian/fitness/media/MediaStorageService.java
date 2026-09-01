package com.qinglian.fitness.media;

import com.qinglian.fitness.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaStorageService {

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
        "image", Set.of("image/jpeg", "image/png", "image/webp"),
        "video", Set.of("video/mp4", "video/webm", "video/quicktime")
    );
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", ".jpg",
        "image/png", ".png",
        "image/webp", ".webp",
        "video/mp4", ".mp4",
        "video/webm", ".webm",
        "video/quicktime", ".mov"
    );

    private final Path root;

    public MediaStorageService(@Value("${app.media.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
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
        String filename = UUID.randomUUID() + EXTENSIONS.get(contentType);
        Path directory = root.resolve(relativeDirectory).normalize();
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_PATH_INVALID", "文件路径不正确");
        }

        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_SAVE_FAILED", "媒体文件保存失败");
        }

        String path = "/media/" + relativeDirectory + "/" + filename;
        return new StoredMedia(path, normalizedKind, contentType, file.getSize(), file.getOriginalFilename());
    }

    public record StoredMedia(String url, String kind, String contentType, long size, String originalName) {
    }
}
