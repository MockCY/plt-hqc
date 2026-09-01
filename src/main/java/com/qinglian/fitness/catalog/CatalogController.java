package com.qinglian.fitness.catalog;

import com.qinglian.fitness.catalog.CatalogDtos.CourseView;
import com.qinglian.fitness.catalog.CatalogDtos.CourseDetailView;
import com.qinglian.fitness.catalog.CatalogDtos.ExerciseView;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.auth.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogRepository repository;
    private final SessionService sessionService;

    public CatalogController(CatalogRepository repository, SessionService sessionService) {
        this.repository = repository;
        this.sessionService = sessionService;
    }

    @GetMapping("/courses")
    public List<CourseView> courses(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String query
    ) {
        return repository.findCourses(type, query);
    }

    @GetMapping("/exercises")
    public List<ExerciseView> exercises(
        @RequestParam(required = false) String bodyPart,
        @RequestParam(required = false) String query
    ) {
        return repository.findExercises(bodyPart, query);
    }

    @GetMapping("/courses/{id}")
    public CourseDetailView course(@PathVariable long id, HttpServletRequest request) {
        return repository.viewCourse(id, visitorKey(request))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在"));
    }

    private String visitorKey(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            var userId = sessionService.verify(authorization.substring(7).trim());
            if (userId.isPresent()) return "user:" + userId.getAsLong();
        }
        String visitorId = request.getHeader("X-Visitor-Id");
        if (visitorId != null && visitorId.matches("[A-Za-z0-9_-]{16,64}")) return "device:" + visitorId;
        return "request:" + sha256(request.getRemoteAddr() + "|" + String.valueOf(request.getHeader("User-Agent")));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
