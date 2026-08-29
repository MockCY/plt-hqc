package com.qinglian.fitness.workout;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public record CreateWorkoutRequest(
        Long courseId,
        Long customCourseId,
        @Min(1) @Max(600) int durationMinutes,
        @Min(0) @Max(100) int completionPercent,
        Instant startedAt
    ) {
    }

    public record WorkoutView(
        long id,
        Long courseId,
        Long customCourseId,
        String courseTitle,
        int durationMinutes,
        int completionPercent,
        Instant startedAt,
        Instant completedAt
    ) {
    }

    public record WorkoutStats(long completedCount, long totalMinutes, int consecutiveDays) {
    }
}
