package com.qinglian.fitness.workout;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public final class WorkoutDtos {

    private WorkoutDtos() {
    }

    public enum DetailType { EXERCISE, COURSE, PLAN }

    public enum ActivityType { EXERCISE, COURSE, CUSTOM_COURSE }

    public record ActivityRequest(
        @NotNull ActivityType activityType,
        @NotNull @Min(1) Long itemId,
        @NotNull Instant startedAt,
        @NotNull LocalDate trainingDate,
        @Min(1) @Max(86400) int activeSeconds
    ) {
    }

    public record DetailVisitRequest(@NotNull DetailType detailType, @NotNull @Min(1) Long itemId) {
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

    public record WorkoutStats(long completedCount, long totalMinutes, int consecutiveDays, long trainingDays) {
        @com.fasterxml.jackson.annotation.JsonProperty("watchMinutes")
        public long watchMinutes() { return totalMinutes; }
    }

    public record WatchView(String itemType, long itemId, String title, Instant viewedAt) {}

    public record WatchHistory(java.util.List<WatchView> items, Integer nextPage) {}
}
