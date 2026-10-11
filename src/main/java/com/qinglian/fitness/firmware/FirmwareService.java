package com.qinglian.fitness.firmware;

import com.qinglian.fitness.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class FirmwareService {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,79}");
    private static final Pattern VERSION = Pattern.compile("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})");
    private final Path root;
    private final ObjectMapper json;

    public FirmwareService(@Value("${app.firmware.root:${user.dir}/data/firmware}") String root, ObjectMapper json) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.json = json;
    }

    public record Release(String releaseId, String model, String chip, String version,
                          long size, String sha256, String notes) {}

    public Map<String, Object> latest(String model, String currentVersion) {
        validateId(model);
        validateVersion(currentVersion);
        Path pointer = root.resolve("latest").resolve(model + ".txt");
        if (!Files.isRegularFile(pointer)) return Map.of("available", false, "published", false);
        try {
            Release release = release(Files.readString(pointer).trim());
            if (!release.model().equals(model)) throw invalid();
            return Map.of("available", compareVersions(release.version(), currentVersion) > 0,
                    "published", true, "release", release);
        } catch (IOException e) { throw unavailable(e); }
    }

    public Release release(String releaseId) {
        validateId(releaseId);
        Path directory = root.resolve("releases").resolve(releaseId);
        Path manifest = directory.resolve("manifest.json");
        if (!Files.isRegularFile(manifest)) throw new ApiException(HttpStatus.NOT_FOUND, "FIRMWARE_NOT_FOUND", "固件版本尚未发布");
        try {
            if (Files.size(manifest) > 4096) throw invalid();
            Release release = json.readValue(Files.readString(manifest), Release.class);
            if (!releaseId.equals(release.releaseId()) || release.model() == null || !ID.matcher(release.model()).matches()
                    || !"esp32s3".equals(release.chip()) || release.version() == null || !VERSION.matcher(release.version()).matches()
                    || release.size() < 24 || release.size() > 16 * 1024 * 1024
                    || release.sha256() == null || !release.sha256().matches("[0-9a-f]{64}")
                    || release.notes() == null || release.notes().length() > 500) throw invalid();
            Path binary = directory.resolve("firmware.bin");
            if (!Files.isRegularFile(binary) || Files.size(binary) != release.size()) throw invalid();
            // Published directories are immutable. Verify contents before advertising or serving a release.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(binary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(release.sha256())) throw invalid();
            return release;
        } catch (IOException | NoSuchAlgorithmException e) { throw unavailable(e); }
    }

    public Path binary(String releaseId) {
        release(releaseId);
        return root.resolve("releases").resolve(releaseId).resolve("firmware.bin");
    }

    static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int result = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (result != 0) return result;
        }
        return 0;
    }

    private static void validateId(String value) {
        if (value == null || !ID.matcher(value).matches()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FIRMWARE_ID", "固件型号或版本编号不正确");
    }
    private static void validateVersion(String value) {
        if (value == null || !VERSION.matcher(value).matches()) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FIRMWARE_VERSION", "固件版本格式不正确");
    }
    private static ApiException invalid() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "INVALID_FIRMWARE", "固件发布文件校验失败，请联系管理员"); }
    private static ApiException unavailable(Exception e) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "FIRMWARE_UNAVAILABLE", "固件服务暂时不可用", e); }
}
