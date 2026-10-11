package com.qinglian.fitness.firmware;

import com.qinglian.fitness.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class AdminFirmwareServiceTest {
    @TempDir Path root;
    private final JsonMapper json = JsonMapper.builder().build();
    private FirmwareService publicService() { return new FirmwareService(root.toString(), json); }
    private AdminFirmwareService service() { return new AdminFirmwareService(publicService(), json); }
    private MockMultipartFile image(String version) { return new MockMultipartFile("file", "application.ino.bin", "application/octet-stream", validImage("AVS-80A5", version)); }

    // A valid ESP image has segment headers, app_desc, XOR checksum and an appended SHA-256.
    static byte[] validImage(String model, String version) {
        byte[] bytes = new byte[592];
        bytes[0] = (byte) 0xe9; bytes[1] = 1; bytes[12] = 9; bytes[23] = 1;
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(24, 0x3c100020).putInt(28, 512).putInt(32, 0xabcd5432);
        byte[] identity = ("ARVELLO_OTA|" + model + "|" + version + "|\0").getBytes(StandardCharsets.UTF_8);
        System.arraycopy(identity, 0, bytes, 300, identity.length);
        refreshImageChecksums(bytes);
        return bytes;
    }
    static void refreshImageChecksums(byte[] bytes) {
        int checksum = 0xef;
        for (int offset = 32; offset < 544; offset++) checksum ^= bytes[offset] & 0xff;
        bytes[559] = (byte) checksum;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bytes, 560));
            System.arraycopy(digest, 0, bytes, 560, 32);
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void assertError(String code, Runnable action) {
        assertEquals(code, assertThrows(ApiException.class, action::run).code());
    }

    @Test void uploadsDraftWithoutAdvertisingItAndProvidesExactHash() throws Exception {
        var service = service();
        var release = service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", "训练优化");
        assertEquals("DRAFT", release.status());
        assertEquals("AVS-80A5-4_1_2", release.releaseId());
        assertEquals(592, release.size());
        assertEquals("训练优化", release.notes());
        assertNotNull(java.time.Instant.parse(release.createdAt()));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image("4.1.2").getBytes())), release.sha256());
        assertArrayEquals(image("4.1.2").getBytes(), Files.readAllBytes(service.binary(release.releaseId())));
        assertEquals(false, publicService().latest("AVS-80A5", "4.1.1").get("published"));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> publicService().release(release.releaseId())).status());
        assertEquals(1, service.list().size());
        assertFalse(Files.exists(root.resolve("releases/" + release.releaseId())));
    }

    @Test void publicationMovesDraftAndOnlyHigherVersionsBecomeCurrent() {
        var service = service();
        var first = service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", "");
        assertEquals("CURRENT", service.publish(first.releaseId()).status());
        assertEquals(true, publicService().latest("AVS-80A5", "4.1.1").get("available"));
        assertEquals(first.releaseId(), publicService().release(first.releaseId()).releaseId());
        assertFalse(Files.exists(root.resolve("drafts/" + first.releaseId())));
        var second = service.upload(image("4.1.3"), "AVS-80A5", "4.1.3", "");
        assertEquals("CURRENT", service.publish(second.releaseId()).status());
        assertEquals("HISTORY", service.list().stream().filter(r -> r.releaseId().equals(first.releaseId())).findFirst().orElseThrow().status());
        assertError("FIRMWARE_DOWNGRADE", () -> service.publish(first.releaseId()));
        assertEquals("CURRENT", service.publish(second.releaseId()).status()); // Idempotent retry.
    }

    @Test void withdrawalKeepsHistoricalDownloadAndCannotWithdrawAnotherCurrentVersion() throws Exception {
        var service = service();
        var first = service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", ""); service.publish(first.releaseId());
        var second = service.upload(image("4.1.3"), "AVS-80A5", "4.1.3", ""); service.publish(second.releaseId());
        service.withdraw(first.releaseId());
        assertEquals(second.releaseId(), Files.readString(root.resolve("latest/AVS-80A5.txt")).trim());
        assertEquals("HISTORY", service.withdraw(second.releaseId()).status());
        assertEquals(false, publicService().latest("AVS-80A5", "4.1.1").get("published"));
        assertArrayEquals(image("4.1.3").getBytes(), Files.readAllBytes(publicService().binary(second.releaseId())));
        var draft = service.upload(image("4.1.4"), "AVS-80A5", "4.1.4", "");
        assertError("FIRMWARE_NOT_PUBLISHED", () -> service.withdraw(draft.releaseId()));
    }

    @Test void neverOverwritesUploadedOrPublishedVersions() throws Exception {
        var service = service();
        var first = service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", "original");
        assertError("FIRMWARE_VERSION_EXISTS", () -> service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", "replacement"));
        service.publish(first.releaseId());
        assertError("FIRMWARE_VERSION_EXISTS", () -> service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", "replacement"));
        assertEquals("original", publicService().release(first.releaseId()).notes());
        try (var directories = Files.list(root.resolve("drafts"))) { assertEquals(0, directories.count()); }
    }

    @Test void rejectsNonApplicationCorruptTruncatedAndOversizedImagesRegardlessOfFilename() {
        var service = service();
        byte[] good = validImage("AVS-80A5", "4.1.2");
        byte[] wrongChip = good.clone(); wrongChip[12] = 0;
        byte[] bootloader = good.clone(); bootloader[32] = 0;
        byte[] wrongChecksum = good.clone(); wrongChecksum[559] ^= 1;
        byte[] wrongHash = good.clone(); wrongHash[591] ^= 1;
        byte[] overflow = good.clone(); ByteBuffer.wrap(overflow).order(ByteOrder.LITTLE_ENDIAN).putInt(28, Integer.MAX_VALUE);
        for (byte[] bytes : new byte[][]{new byte[256], new byte[24], Arrays.copyOf(good, 570), wrongChip,
                bootloader, wrongChecksum, wrongHash, overflow, Arrays.copyOf(good, good.length + 16), new byte[AdminFirmwareService.MAX_IMAGE_SIZE + 1]}) {
            assertError("INVALID_FIRMWARE_IMAGE", () -> service.upload(new MockMultipartFile("file", "firmware.ino.bin", null, bytes), "AVS-80A5", "4.1.2", ""));
        }
        assertTrue(service.list().isEmpty());
        assertFalse(Files.exists(root.resolve("latest/AVS-80A5.txt")));
    }

    @Test void validatesEmbeddedModelAndVersionAndRejectsPathParameters() {
        var service = service();
        assertError("FIRMWARE_IDENTITY_MISMATCH", () -> service.upload(image("4.1.1"), "AVS-80A5", "4.1.2", ""));
        assertError("FIRMWARE_IDENTITY_MISMATCH", () -> service.upload(image("4.1.2"), "OTHER", "4.1.2", ""));
        assertError("INVALID_FIRMWARE_MODEL", () -> service.upload(image("4.1.2"), "../secret", "4.1.2", ""));
        for (String version : new String[]{"4.1", "04.1.2", "9999999.1.2", "4.1.2/../secret"}) {
            assertError("INVALID_FIRMWARE_VERSION", () -> service.upload(image("4.1.2"), "AVS-80A5", version, ""));
        }
        assertError("INVALID_FIRMWARE_NOTES", () -> service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", "x".repeat(501)));
        assertError("INVALID_FIRMWARE_ID", () -> service.binary("../secret"));
        assertError("INVALID_FIRMWARE_ID", () -> service.publish("../secret"));
    }

    @Test void failedUploadOrPublicationNeverChangesExistingLatest() throws Exception {
        var service = service();
        var first = service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", ""); service.publish(first.releaseId());
        Path pointer = root.resolve("latest/AVS-80A5.txt"); String current = Files.readString(pointer);
        assertError("FIRMWARE_IDENTITY_MISMATCH", () -> service.upload(image("4.1.2"), "AVS-80A5", "4.1.3", ""));
        var draft = service.upload(image("4.1.3"), "AVS-80A5", "4.1.3", "");
        Path binary = service.binary(draft.releaseId()); byte[] bytes = Files.readAllBytes(binary); bytes[400] ^= 1; Files.write(binary, bytes);
        assertError("INVALID_FIRMWARE", () -> service.publish(draft.releaseId()));
        assertEquals(current, Files.readString(pointer));
        assertTrue(Files.exists(root.resolve("drafts/" + draft.releaseId())));
        assertFalse(Files.exists(root.resolve("releases/" + draft.releaseId())));
    }

    @Test void unavailableLatestStorageLeavesTheCompleteVersionInDrafts() throws Exception {
        var service = service();
        var draft = service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", "");
        Files.writeString(root.resolve("latest"), "blocked");
        assertError("INVALID_FIRMWARE_STORAGE", () -> service.publish(draft.releaseId()));
        assertTrue(Files.exists(root.resolve("drafts/" + draft.releaseId() + "/firmware.bin")));
        assertFalse(Files.exists(root.resolve("releases/" + draft.releaseId())));
        assertEquals("blocked", Files.readString(root.resolve("latest")));
    }

    @Test void supportsExistingCommandLinePublishedReleasesWithoutManagementMetadata() throws Exception {
        var service = service();
        var release = service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", ""); service.publish(release.releaseId());
        Files.delete(root.resolve("releases/" + release.releaseId() + "/upload-info.json"));
        var listed = service.list().getFirst();
        assertNotNull(java.time.Instant.parse(listed.createdAt()));
        assertEquals("CURRENT", listed.status());
    }

    @Test void concurrentUploadsOfTheSameVersionKeepExactlyOneCompleteCopy() throws Exception {
        var service = service();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var threads = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> task = () -> {
                start.await();
                try { service.upload(image("4.1.2"), "AVS-80A5", "4.1.2", ""); return "CREATED"; }
                catch (ApiException error) { return error.code(); }
            };
            var first = threads.submit(task); var second = threads.submit(task); start.countDown();
            assertEquals(java.util.Set.of("CREATED", "FIRMWARE_VERSION_EXISTS"), java.util.Set.of(first.get(), second.get()));
        }
        assertEquals(1, service.list().size());
        assertArrayEquals(image("4.1.2").getBytes(), Files.readAllBytes(service.binary("AVS-80A5-4_1_2")));
    }
}
