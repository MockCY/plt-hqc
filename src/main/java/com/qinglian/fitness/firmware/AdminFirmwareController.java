package com.qinglian.fitness.firmware;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/firmware")
public class AdminFirmwareController {
    private final AdminFirmwareService service;

    public AdminFirmwareController(AdminFirmwareService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<Map<String, List<AdminFirmwareService.ManagedRelease>>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("releases", service.list()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminFirmwareService.ManagedRelease> upload(@RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String model, @RequestParam(required = false) String version, @RequestParam(defaultValue = "") String notes) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.upload(file, model, version, notes));
    }

    @PostMapping("/{releaseId}/publish")
    public ResponseEntity<AdminFirmwareService.ManagedRelease> publish(@PathVariable String releaseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.publish(releaseId));
    }

    @PostMapping("/{releaseId}/withdraw")
    public ResponseEntity<AdminFirmwareService.ManagedRelease> withdraw(@PathVariable String releaseId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.withdraw(releaseId));
    }

    @GetMapping("/{releaseId}/firmware.bin")
    public ResponseEntity<FileSystemResource> binary(@PathVariable String releaseId) throws IOException {
        var path = service.binary(releaseId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", ContentDisposition.attachment().filename(releaseId + ".bin").build().toString())
                .contentLength(Files.size(path)).body(new FileSystemResource(path));
    }
}
