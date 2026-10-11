package com.qinglian.fitness.firmware;

import com.qinglian.fitness.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AdminFirmwareService {
    static final int MAX_IMAGE_SIZE = 0x1e0000;
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,39}");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,79}");
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})");
    private final FirmwareService publicService;
    private final ObjectMapper json;
    private final Path root;

    public AdminFirmwareService(FirmwareService publicService, ObjectMapper json) {
        this.publicService = publicService;
        this.json = json;
        this.root = publicService.storageRoot();
    }

    public record ManagedRelease(String releaseId, String model, String chip, String version,
                                 long size, String sha256, String notes, String createdAt, String status) {}
    private record UploadInfo(String createdAt) {}
    private record StoredRelease(FirmwareService.Release release, Path directory, boolean draft) {}

    public synchronized List<ManagedRelease> list() {
        try {
            List<ManagedRelease> releases = new ArrayList<>();
            for (String location : List.of("drafts", "releases")) {
                Path area = area(location);
                if (!Files.exists(area, LinkOption.NOFOLLOW_LINKS)) continue;
                try (var directories = Files.list(area)) {
                    for (Path directory : directories.toList()) {
                        String id = directory.getFileName().toString();
                        if (!ID.matcher(id).matches()) continue; // Ignore incomplete temporary uploads.
                        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw storageInvalid();
                        releases.add(managed(load(id)));
                    }
                }
            }
            releases.sort(Comparator.comparing((ManagedRelease release) -> Instant.parse(release.createdAt())).reversed()
                    .thenComparing(ManagedRelease::releaseId));
            return releases;
        } catch (IOException e) { throw unavailable(e); }
    }

    public synchronized ManagedRelease upload(MultipartFile file, String model, String version, String notes) {
        validateModel(model);
        validateVersion(version);
        if (notes == null) notes = "";
        if (notes.length() > 500) throw badRequest("INVALID_FIRMWARE_NOTES", "更新说明不能超过 500 字");
        if (file == null || file.isEmpty()) throw badRequest("FIRMWARE_FILE_REQUIRED", "请选择应用固件 .ino.bin 文件");
        if (file.getSize() < 256 || file.getSize() > MAX_IMAGE_SIZE) throw wrongImage();
        String id = model + "-" + version.replace('.', '_');
        Path temporary = null;
        try {
            Path drafts = createArea("drafts");
            Path target = drafts.resolve(id);
            if (exists(target) || exists(area("releases").resolve(id))) throw duplicate();
            byte[] bytes;
            try (var input = file.getInputStream()) {
                bytes = input.readNBytes(MAX_IMAGE_SIZE + 1);
            }
            validateImage(bytes, model, version);
            var release = new FirmwareService.Release(id, model, "esp32s3", version, bytes.length,
                    HexFormat.of().formatHex(sha256(bytes)), notes);
            temporary = Files.createTempDirectory(drafts, ".tmp-");
            Files.write(temporary.resolve("firmware.bin"), bytes, StandardOpenOption.CREATE_NEW);
            Files.writeString(temporary.resolve("manifest.json"), json.writeValueAsString(release), StandardOpenOption.CREATE_NEW);
            Files.writeString(temporary.resolve("upload-info.json"), json.writeValueAsString(new UploadInfo(Instant.now().toString())),
                    StandardOpenOption.CREATE_NEW);
            // A complete directory becomes visible in one step; existing versions are never overwritten.
            if (exists(target) || exists(area("releases").resolve(id))) throw duplicate();
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            temporary = null;
            return managed(load(id));
        } catch (IOException e) { throw unavailable(e); }
        finally { removeTemporary(temporary); }
    }

    public synchronized ManagedRelease publish(String releaseId) {
        try {
            StoredRelease stored = load(releaseId);
            var release = stored.release();
            String current = currentId(release.model());
            if (releaseId.equals(current)) return managed(stored);
            if (current != null) {
                var previous = load(current);
                if (previous.draft() || !previous.release().model().equals(release.model())) throw storageInvalid();
                if (FirmwareService.compareVersions(release.version(), previous.release().version()) <= 0) {
                    throw new ApiException(HttpStatus.CONFLICT, "FIRMWARE_DOWNGRADE", "不能发布低于或等于当前版本的固件，请使用更高版本号");
                }
            }
            // Recheck the application image when publishing, in addition to the manifest hash.
            validateImage(Files.readAllBytes(stored.directory().resolve("firmware.bin")), release.model(), release.version());
            Path latest = createArea("latest");
            Path pointer = latest.resolve(release.model() + ".txt");
            rejectSymlink(pointer);
            Path pending = Files.createTempFile(latest, ".tmp-", ".txt");
            Path originalDraft = stored.draft() ? stored.directory() : null;
            Path published = null;
            try {
                Files.writeString(pending, releaseId + "\n");
                if (originalDraft != null) {
                    published = createArea("releases").resolve(releaseId);
                    if (exists(published)) throw duplicate();
                    Files.move(originalDraft, published, StandardCopyOption.ATOMIC_MOVE);
                    stored = new StoredRelease(release, published, false);
                }
                Files.move(pending, pointer, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // An unsuccessful pointer replacement must not silently turn a draft into a release.
                if (originalDraft != null && published != null && exists(published) && !exists(originalDraft)) {
                    try { Files.move(published, originalDraft, StandardCopyOption.ATOMIC_MOVE); }
                    catch (IOException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                }
                throw e;
            } finally { Files.deleteIfExists(pending); }
            return managed(stored);
        } catch (IOException e) { throw unavailable(e); }
    }

    public synchronized ManagedRelease withdraw(String releaseId) {
        try {
            StoredRelease stored = load(releaseId);
            if (stored.draft()) throw new ApiException(HttpStatus.CONFLICT, "FIRMWARE_NOT_PUBLISHED", "此版本尚未发布，无需停用");
            Path pointer = area("latest").resolve(stored.release().model() + ".txt");
            if (releaseId.equals(currentId(stored.release().model()))) Files.delete(pointer);
            // Downloads that have already started can still finish against the immutable release directory.
            return managed(stored);
        } catch (IOException e) { throw unavailable(e); }
    }

    public synchronized Path binary(String releaseId) {
        try { return load(releaseId).directory().resolve("firmware.bin"); }
        catch (IOException e) { throw unavailable(e); }
    }

    private StoredRelease load(String id) throws IOException {
        validateId(id);
        Path draft = area("drafts").resolve(id), published = area("releases").resolve(id);
        boolean draftExists = exists(draft), publishedExists = exists(published);
        if (draftExists && publishedExists) throw storageInvalid();
        if (!draftExists && !publishedExists) throw new ApiException(HttpStatus.NOT_FOUND, "FIRMWARE_NOT_FOUND", "未找到此固件版本");
        Path directory = draftExists ? draft : published;
        rejectSymlink(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw storageInvalid();
        for (String name : List.of("firmware.bin", "manifest.json", "upload-info.json")) rejectSymlink(directory.resolve(name));
        var release = publicService.readStoredRelease(id, directory);
        if (!MODEL.matcher(release.model()).matches() || !id.equals(release.model() + "-" + release.version().replace('.', '_'))) {
            throw storageInvalid();
        }
        return new StoredRelease(release, directory, draftExists);
    }

    private ManagedRelease managed(StoredRelease stored) throws IOException {
        var release = stored.release();
        Path information = stored.directory().resolve("upload-info.json");
        String createdAt;
        if (exists(information)) {
            if (!Files.isRegularFile(information, LinkOption.NOFOLLOW_LINKS) || Files.size(information) > 512) throw storageInvalid();
            createdAt = json.readValue(Files.readString(information), UploadInfo.class).createdAt();
            try { Instant.parse(createdAt); }
            catch (DateTimeParseException | NullPointerException e) { throw storageInvalid(); }
        } else {
            // Releases made by the existing command-line publisher remain manageable.
            createdAt = Files.getLastModifiedTime(stored.directory().resolve("manifest.json")).toInstant().toString();
        }
        String status = stored.draft() ? "DRAFT" : release.releaseId().equals(currentId(release.model())) ? "CURRENT" : "HISTORY";
        return new ManagedRelease(release.releaseId(), release.model(), release.chip(), release.version(), release.size(),
                release.sha256(), release.notes(), createdAt, status);
    }

    private String currentId(String model) throws IOException {
        Path pointer = area("latest").resolve(model + ".txt");
        rejectSymlink(pointer);
        if (!exists(pointer)) return null;
        if (!Files.isRegularFile(pointer, LinkOption.NOFOLLOW_LINKS) || Files.size(pointer) > 128) throw storageInvalid();
        String id = Files.readString(pointer).trim();
        if (!ID.matcher(id).matches()) throw storageInvalid();
        return id;
    }

    private Path area(String name) {
        rejectSymlink(root);
        Path path = root.resolve(name);
        rejectSymlink(path);
        if (exists(path) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw storageInvalid();
        return path;
    }
    private Path createArea(String name) throws IOException {
        Path path = area(name);
        Files.createDirectories(path);
        return path;
    }
    private static boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }
    private static void rejectSymlink(Path path) { if (Files.isSymbolicLink(path)) throw storageInvalid(); }

    static void validateImage(byte[] bytes, String model, String version) {
        if (bytes.length < 256 || bytes.length > MAX_IMAGE_SIZE || (bytes[0] & 0xff) != 0xe9
                || uint16(bytes, 12) != 9 || uint32(bytes, 32) != 0xabcd5432L) throw wrongImage();
        int segments = bytes[1] & 0xff;
        if (segments < 1 || segments > 16 || (bytes[23] != 0 && bytes[23] != 1)) throw wrongImage();
        byte[] identity = ("ARVELLO_OTA|" + model + "|" + version + "|\0").getBytes(StandardCharsets.UTF_8);
        int offset = 24, checksum = 0xef;
        boolean identityFound = false;
        for (int index = 0; index < segments; index++) {
            if (offset + 8 > bytes.length) throw wrongImage();
            long length = uint32(bytes, offset + 4);
            int start = offset + 8;
            if ((length & 3) != 0 || length > bytes.length - start || (index == 0 && length < 256)) throw wrongImage();
            int end = start + (int) length;
            for (int position = start; position < end; position++) checksum ^= bytes[position] & 0xff;
            if (contains(bytes, start, end, identity)) identityFound = true;
            offset = end;
        }
        int checksumOffset = ((offset + 16) / 16) * 16 - 1;
        int imageLength = checksumOffset + 1;
        int totalLength = imageLength + (bytes[23] == 1 ? 32 : 0);
        if (totalLength != bytes.length || (bytes[checksumOffset] & 0xff) != checksum) throw wrongImage();
        if (bytes[23] == 1 && !MessageDigest.isEqual(sha256(Arrays.copyOf(bytes, imageLength)),
                Arrays.copyOfRange(bytes, imageLength, totalLength))) throw wrongImage();
        if (!identityFound) throw badRequest("FIRMWARE_IDENTITY_MISMATCH", "固件内的型号或版本与填写内容不一致，请修改源码版本并重新编译");
    }

    private static boolean contains(byte[] bytes, int start, int end, byte[] expected) {
        outer: for (int offset = start; offset <= end - expected.length; offset++) {
            for (int index = 0; index < expected.length; index++) if (bytes[offset + index] != expected[index]) continue outer;
            return true;
        }
        return false;
    }
    private static int uint16(byte[] bytes, int offset) { return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8); }
    private static long uint32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8) | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }
    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
    private static void validateModel(String value) {
        if (value == null || !MODEL.matcher(value).matches()) throw badRequest("INVALID_FIRMWARE_MODEL", "固件型号不正确");
    }
    private static void validateVersion(String value) {
        if (value == null || !VERSION.matcher(value).matches()) throw badRequest("INVALID_FIRMWARE_VERSION", "版本号须为 x.y.z，例如 4.1.2");
    }
    private static void validateId(String value) {
        if (value == null || !ID.matcher(value).matches()) throw badRequest("INVALID_FIRMWARE_ID", "固件版本编号不正确");
    }
    private static ApiException badRequest(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private static ApiException wrongImage() {
        return badRequest("INVALID_FIRMWARE_IMAGE", "请选择完整的 ESP32-S3 应用固件 .ino.bin，大小须为 256–1,966,080 字节；不可上传 bootloader、partitions 或 merged 文件");
    }
    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "FIRMWARE_VERSION_EXISTS", "此型号和版本已经存在，请使用新的版本号");
    }
    private static ApiException storageInvalid() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INVALID_FIRMWARE_STORAGE", "固件存储文件校验失败，请联系管理员");
    }
    private static ApiException unavailable(Exception cause) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FIRMWARE_UNAVAILABLE", "固件存储暂时不可用，请检查服务器存储权限后重试", cause);
    }
    private static void removeTemporary(Path directory) {
        if (directory == null) return;
        try {
            for (String file : List.of("firmware.bin", "manifest.json", "upload-info.json")) Files.deleteIfExists(directory.resolve(file));
            Files.deleteIfExists(directory);
        } catch (IOException ignored) { /* Hidden temporary directories never become available to devices. */ }
    }
}
