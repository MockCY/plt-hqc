package com.qinglian.fitness.firmware;

import com.qinglian.fitness.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import static org.junit.jupiter.api.Assertions.*;

class FirmwareServiceTest {
    @TempDir Path root;
    private final JsonMapper json = JsonMapper.builder().build();
    private FirmwareService service() { return new FirmwareService(root.toString(), json); }

    private FirmwareService.Release publish(String version) throws Exception {
        String id = "AVS-80A5-" + version.replace('.', '_');
        byte[] binary = new byte[1024]; binary[0] = (byte) 0xe9; binary[12] = 9;
        byte[] identity = ("ARVELLO_OTA|AVS-80A5|" + version + "|\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(identity, 0, binary, 100, identity.length);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(binary));
        var release = new FirmwareService.Release(id, "AVS-80A5", "esp32s3", version, binary.length, hash, "升级说明");
        Path directory = root.resolve("releases").resolve(id); Files.createDirectories(directory);
        Files.write(directory.resolve("firmware.bin"), binary);
        Files.writeString(directory.resolve("manifest.json"), json.writeValueAsString(release));
        Files.createDirectories(root.resolve("latest"));
        Files.writeString(root.resolve("latest/AVS-80A5.txt"), id);
        return release;
    }

    @Test void noPublicationDoesNotAdvertiseAnUpdate() {
        assertEquals(false, service().latest("AVS-80A5", "4.1.0").get("available"));
        assertEquals(false, service().latest("AVS-80A5", "4.1.0").get("published"));
    }
    @Test void comparesVersionComponentsNumericallyAndNeverOffersDowngrades() throws Exception {
        publish("4.10.0");
        assertEquals(true, service().latest("AVS-80A5", "4.9.9").get("available"));
        assertEquals(false, service().latest("AVS-80A5", "4.10.0").get("available"));
        assertEquals(false, service().latest("AVS-80A5", "5.0.0").get("available"));
    }
    @Test void rejectsTraversalAndMalformedVersions() {
        assertThrows(ApiException.class, () -> service().release("../secrets"));
        assertThrows(ApiException.class, () -> service().latest("../../etc", "4.1.0"));
        assertThrows(ApiException.class, () -> service().latest("AVS-80A5", "4.1"));
        assertThrows(ApiException.class, () -> service().latest("AVS-80A5", "999999999.1.0"));
    }
    @Test void refusesCorruptedAndTruncatedBinariesBeforeAdvertisingOrServing() throws Exception {
        var release = publish("4.1.1");
        Path binary = root.resolve("releases").resolve(release.releaseId()).resolve("firmware.bin");
        byte[] bytes = Files.readAllBytes(binary); bytes[200] ^= 1; Files.write(binary, bytes);
        assertThrows(ApiException.class, () -> service().latest("AVS-80A5", "4.1.0"));
        assertThrows(ApiException.class, () -> service().binary(release.releaseId()));
        Files.write(binary, new byte[23]);
        assertThrows(ApiException.class, () -> service().release(release.releaseId()));
    }
    @Test void rejectsWrongModelPointer() throws Exception {
        publish("4.1.1");
        Files.writeString(root.resolve("latest/OTHER.txt"), "AVS-80A5-4_1_1");
        assertThrows(ApiException.class, () -> service().latest("OTHER", "4.1.0"));
    }
    @Test void endpointsProvideExactLengthAndDisableStaleManifestCaching() throws Exception {
        var release = publish("4.1.1");
        var controller = new FirmwareController(service(), json);
        var manifest = controller.release(release.releaseId());
        assertEquals(manifest.getBody().length, manifest.getHeaders().getContentLength());
        assertEquals("no-store", manifest.getHeaders().getCacheControl());
        assertEquals(release.releaseId(), json.readTree(manifest.getBody()).get("releaseId").asText());
        var binary = controller.binary(release.releaseId());
        assertEquals(release.size(), binary.getHeaders().getContentLength());
        assertEquals(release.size(), binary.getBody().contentLength());
    }
    @Test void httpRoutesServeManifestAndImageWithoutChunkedLengthAmbiguity() throws Exception {
        var release = publish("4.1.1");
        var mvc = MockMvcBuilders.standaloneSetup(new FirmwareController(service(), json)).build();
        var latest = mvc.perform(get("/api/v1/firmware/latest").param("model", "AVS-80A5").param("currentVersion", "4.1.0"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertTrue(json.readTree(latest.getResponse().getContentAsString()).get("available").asBoolean());
        var manifest = mvc.perform(get("/api/v1/firmware/releases/" + release.releaseId()))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(manifest.getContentAsByteArray().length, Long.parseLong(manifest.getHeader("Content-Length")));
        var response = mvc.perform(get("/api/v1/firmware/releases/" + release.releaseId() + "/firmware.bin"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Length", "1024")).andReturn().getResponse();
        assertArrayEquals(Files.readAllBytes(service().binary(release.releaseId())), response.getContentAsByteArray());
    }
}
