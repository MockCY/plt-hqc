package com.qinglian.fitness.customcourse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
        @NotEmpty @Size(max = 20) List<Long> exerciseIds,
        @Size(max = 20) String goal,
        @Size(max = 10) String level,
        @Min(0) @Max(30) Integer warmupMinutes,
        @Min(0) @Max(300) Integer restSeconds,
        @Size(max = 20) List<CustomExerciseRequest> exerciseConfigs
    ) {
    }

    public record CustomExerciseRequest(
        @NotNull Long exerciseId,
        @Min(1) @Max(20) int setCount,
        @Min(1) @Max(999) int repetitions
    ) {
    }

    public record CustomExerciseView(long exerciseId, int setCount, int repetitions) {
    }

    public record CustomCourseView(
        long id,
        String title,
        int durationMinutes,
        String summary,
        List<Long> exerciseIds,
        String goal,
        String level,
        int warmupMinutes,
        int restSeconds,
        List<CustomExerciseView> exerciseConfigs,
        Instant createdAt
    ) {
    }
}
