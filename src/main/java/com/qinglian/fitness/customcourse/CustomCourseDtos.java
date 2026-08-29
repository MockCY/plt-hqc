package com.qinglian.fitness.customcourse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CustomCourseDtos {

    private CustomCourseDtos() {
    }

    public record CreateCustomCourseRequest(
        @NotBlank @Size(max = 80) String title,
        @Min(1) @Max(300) int durationMinutes,
        @Size(max = 300) String summary,
        @NotEmpty @Size(max = 20) List<Long> exerciseIds
    ) {
    }

    public record CustomCourseView(
        long id,
        String title,
        int durationMinutes,
        String summary,
        List<Long> exerciseIds,
        Instant createdAt
    ) {
    }
}
