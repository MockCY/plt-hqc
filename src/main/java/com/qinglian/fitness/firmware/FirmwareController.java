package com.qinglian.fitness.firmware;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/firmware")
public class FirmwareController {
    private final FirmwareService service;
    private final tools.jackson.databind.ObjectMapper json;
    public FirmwareController(FirmwareService service, tools.jackson.databind.ObjectMapper json) { this.service = service; this.json = json; }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latest(@RequestParam String model, @RequestParam String currentVersion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.latest(model, currentVersion));
    }
    @GetMapping("/releases/{releaseId}")
    public ResponseEntity<byte[]> release(@PathVariable String releaseId) {
        byte[] body = json.writeValueAsBytes(service.release(releaseId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON).contentLength(body.length).body(body);
    }
    @GetMapping("/releases/{releaseId}/firmware.bin")
    public ResponseEntity<FileSystemResource> binary(@PathVariable String releaseId) throws IOException {
        var path = service.binary(releaseId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(Files.size(path))
                .body(new FileSystemResource(path));
    }
}
