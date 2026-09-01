package com.qinglian.fitness.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record AdminProfile(String username) {
    }

    public record PageResult<T>(List<T> items, long total, int page, int pageSize) {
    }

    public record TrendPoint(LocalDate date, long count) {
    }

    public record RecentContent(long id, String title, String kind, String status, Instant updatedAt) {
    }

    public record Dashboard(
        long userCount,
        long weeklyNewUsers,
        long weeklyWorkoutCount,
        long courseCount,
        long pendingFeedbackCount,
        List<TrendPoint> workoutTrend,
        List<RecentContent> recentContent
    ) {
    }

    public record UserRow(
        long id, String nickname, String phone, String avatarUrl, String status,
        long workoutCount, long totalMinutes, Instant createdAt, Instant updatedAt
    ) {
    }

    public record CourseRow(
        long id, String title, String type, int durationMinutes, String level, String equipment,
        String summary, String coverImage, String videoUrl, String videoCoverImage,
        Integer videoDurationSeconds, long viewCount, String status, int sortOrder,
        List<Long> exerciseIds, Instant createdAt, Instant updatedAt
    ) {
    }

    public record CourseRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 30) String type,
        @Min(1) @Max(600) int durationMinutes,
        @NotBlank @Size(max = 30) String level,
        @NotBlank @Size(max = 80) String equipment,
        @NotBlank @Size(max = 300) String summary,
        @Size(max = 500) String coverImage,
        @Size(max = 500) String videoUrl,
        @Size(max = 500) String videoCoverImage,
        @Min(0) Integer videoDurationSeconds,
        @NotBlank String status,
        int sortOrder,
        List<Long> exerciseIds
    ) {
    }

    public record ExerciseRow(
        long id, String name, String bodyPart, String level, String equipment, int suggestedSets,
        String target, String cue, String safetyTip, String coverImage, String videoUrl,
        String videoCoverImage, Integer videoDurationSeconds, String status, int sortOrder,
        Instant createdAt, Instant updatedAt
    ) {
    }

    public record ExerciseRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Size(max = 30) String bodyPart,
        @NotBlank @Size(max = 30) String level,
        @NotBlank @Size(max = 80) String equipment,
        @Min(1) @Max(20) int suggestedSets,
        @NotBlank @Size(max = 40) String target,
        @NotBlank @Size(max = 500) String cue,
        @NotBlank @Size(max = 500) String safetyTip,
        @Size(max = 500) String coverImage,
        @Size(max = 500) String videoUrl,
        @Size(max = 500) String videoCoverImage,
        @Min(0) Integer videoDurationSeconds,
        @NotBlank String status,
        int sortOrder
    ) {
    }

    public record PlanItemRow(long id, long courseId, String courseTitle, int dayOffset, int sortOrder) {
    }

    public record PlanRow(
        long id, String title, int weekNumber, int sessionsPerWeek, String description,
        boolean active, int sortOrder, List<PlanItemRow> items, Instant createdAt, Instant updatedAt
    ) {
    }

    public record PlanItemRequest(@NotNull Long courseId, @Min(0) @Max(30) int dayOffset, int sortOrder) {
    }

    public record PlanRequest(
        @NotBlank @Size(max = 80) String title,
        @Min(1) @Max(52) int weekNumber,
        @Min(1) @Max(14) int sessionsPerWeek,
        @Size(max = 300) String description,
        boolean active,
        int sortOrder,
        List<@Valid PlanItemRequest> items
    ) {
    }

    public record CampaignRow(
        long id, String code, String title, String rulesText, LocalDate startDate,
        LocalDate endDate, String status, int sortOrder, long checkinCount,
        Instant createdAt, Instant updatedAt
    ) {
    }

    public record CampaignRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 1000) String rulesText,
        LocalDate startDate,
        LocalDate endDate,
        @NotBlank String status,
        int sortOrder
    ) {
    }

    public record WorkoutRow(
        long id, long userId, String userName, long courseId, String courseTitle,
        int durationMinutes, int completionPercent, Instant startedAt, Instant completedAt
    ) {
    }

    public record FeedbackRow(
        long id, long userId, String userName, String category, String content,
        String contact, String status, Instant createdAt
    ) {
    }

    public record DeviceModelRow(
        long id, String name, String snPrefix, long deviceCount,
        Instant createdAt, Instant updatedAt
    ) {
    }

    public record DeviceModelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 2, max = 12)
        @Pattern(regexp = "[A-Za-z0-9]+", message = "SN 前缀只能包含字母和数字") String snPrefix
    ) {
    }

    public record DeviceRow(
        long id, String serialNumber, String deviceModel, boolean bound,
        Instant createdAt, Instant updatedAt
    ) {
    }

    public record DeviceCreateRequest(
        @NotBlank @Size(max = 100) String deviceModel
    ) {
    }

    public record GeneratedDevice(
        String serialNumber, String qrToken, String deviceModel
    ) {
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record AuditRow(
        long id, String username, String action, String targetType, Long targetId,
        String summary, String ipAddress, Instant createdAt
    ) {
    }
}
