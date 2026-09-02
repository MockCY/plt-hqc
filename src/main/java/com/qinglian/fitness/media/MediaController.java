package com.qinglian.fitness.media;

import com.qinglian.fitness.media.MediaStorageService.StoredMedia;
import com.qinglian.fitness.media.MediaStorageService.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaStorageService storageService;

    public MediaController(MediaStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public StoredMedia upload(@RequestParam MultipartFile file, @RequestParam String kind) {
        return storageService.store(file, kind);
    }

    @GetMapping("/files/{*path}")
    public ResponseEntity<Resource> file(@PathVariable("path") String path) {
        StoredFile file = storageService.load(path);
        return ResponseEntity.ok()
            .contentType(file.mediaType())
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .body(file.resource());
    }
}
