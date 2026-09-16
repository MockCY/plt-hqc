package com.qinglian.fitness.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.qinglian.fitness.catalog.TrainingSet;
import com.qinglian.fitness.catalog.ExerciseCategory;
import com.qinglian.fitness.catalog.SpringCounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
        long todayOnlineCount,
        long currentOnlineCount,
        List<TrendPoint> workoutTrend,
        List<TrendPoint> onlineTrend,
        List<RecentContent> recentContent
    ) {
    }

    public record UserRow(
        long id, String nickname, String phone, String avatarUrl, String status,
        long workoutCount, long totalMinutes, Instant createdAt, Instant updatedAt,
        com.qinglian.fitness.presence.PresenceMapper.Summary presence
    ) {
        public UserRow(long id, String nickname, String phone, String avatarUrl, String status,
                       long workoutCount, long totalMinutes, Instant createdAt, Instant updatedAt) {
            this(id, nickname, phone, avatarUrl, status, workoutCount, totalMinutes, createdAt, updatedAt, null);
        }

        public UserRow withPresence(com.qinglian.fitness.presence.PresenceMapper.Summary summary) {
            return new UserRow(id, nickname, phone, avatarUrl, status, workoutCount, totalMinutes, createdAt, updatedAt, summary);
        }
    }

    public record UserUpdateRequest(
        @Size(max = 32) String phone,
        @NotBlank String status
    ) {
    }

    public record CourseRow(
        long id, String title, String type, int durationMinutes, String level, String equipment,
        String summary, String coverImage, String videoUrl, String videoCoverImage,
        Integer videoDurationSeconds, long viewCount, String status, int sortOrder,
        List<Long> exerciseIds, Instant createdAt, Instant updatedAt,
        String introduction, String audience, List<CourseExerciseRequest> exercises, String trainingTags
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
        List<@NotNull Long> exerciseIds,
        @Size(max = 5000) String introduction,
        @Size(max = 2000) String audience,
        @Size(max = 100) List<@NotNull @Valid CourseExerciseRequest> exercises,
        @Size(max = 200) String trainingTags
    ) {
    }

    public record CourseExerciseRequest(
        @Min(1) long exerciseId,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid TrainingSet> sets,
        @Min(1) @Max(999) Integer recommendedPlays
    ) {
    }

    public record ExerciseRow(
        long id, String name, String bodyPart, String level, String equipment, int suggestedSets,
        String target, String cue, String safetyTip, String coverImage, String videoUrl,
        String videoCoverImage, Integer videoDurationSeconds, String backgroundMusicUrl,
        String focusImageUrl, String focusParts, List<Integer> springSets, SpringCounts springCounts, String keyPoints, String commonMistakes, String instructionAudioUrl,
        String status, int sortOrder,
        Instant createdAt, Instant updatedAt
    ) {
        public ExerciseRow {
            bodyPart = ExerciseCategory.normalizeStored(bodyPart);
        }
    }

    public record ExerciseRequest(
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Pattern(regexp = ExerciseCategory.VALID_VALUES_PATTERN,
            message = ExerciseCategory.VALIDATION_MESSAGE) String bodyPart,
        @NotBlank @Size(max = 30) String level,
        @NotBlank @Size(max = 80) String equipment,
        @Min(1) @Max(20) int suggestedSets,
        @Size(max = 40) String target,
        @NotBlank @Size(max = 500) String cue,
        @NotBlank @Size(max = 500) String safetyTip,
        @Size(max = 500) String coverImage,
        @Size(max = 500) String videoUrl,
        @Size(max = 500) String videoCoverImage,
        @Min(0) Integer videoDurationSeconds,
        @Size(max = 500) String backgroundMusicUrl,
        @Size(max = 500) String focusImageUrl,
        @Size(max = 100) String focusParts,
        @Size(max = 20) List<@Min(1) @Max(20) Integer> springSets,
        @Valid SpringCounts springCounts,
        @Size(max = 2000) String keyPoints,
        @Size(max = 2000) String commonMistakes,
        @Size(max = 500) String instructionAudioUrl,
        @NotBlank String status,
        int sortOrder
    ) {
        public ExerciseRequest {
            if (springCounts != null) springSets = List.of();
        }
    }

    public record PlanDayExerciseRow(
        long id, long exerciseId, String exerciseName, int repetitions, int setCount, int sortOrder
    ) {
    }

    public record PlanDayRow(long id, int dayNumber, String title, int sortOrder, List<PlanDayExerciseRow> exercises) {
    }

    public record PlanRow(
        long id, String title, int weekNumber, int sessionsPerWeek, String description,
        String subtitle, String coverImage, String detailImage, String level, String trainingScene, Integer sessionMinutes,
        String benefitOne, String benefitTwo, String benefitThree,
        boolean active, int sortOrder, List<PlanDayRow> days, Instant createdAt, Instant updatedAt
    ) {
    }

    public record PlanDayExerciseRequest(
        @NotNull Long exerciseId,
        @Min(1) @Max(999) int repetitions,
        @Min(1) @Max(20) int setCount,
        int sortOrder
    ) {
    }

    public record PlanDayRequest(
        @Min(1) @Max(365) int dayNumber,
        @NotBlank @Size(max = 80) String title,
        int sortOrder,
        List<@Valid PlanDayExerciseRequest> exercises
    ) {
    }

    public record PlanRequest(
        @NotBlank @Size(max = 80) String title,
        @Min(1) @Max(52) int weekNumber,
        @Min(1) @Max(365) int sessionsPerWeek,
        @Size(max = 300) String description,
        @Size(max = 160) String subtitle,
        @Size(max = 500) String coverImage,
        @Size(max = 500) String detailImage,
        @Size(max = 30) String level,
        @Size(max = 30) String trainingScene,
        @Min(1) @Max(600) Integer sessionMinutes,
        @Size(max = 80) String benefitOne,
        @Size(max = 80) String benefitTwo,
        @Size(max = 80) String benefitThree,
        boolean active,
        int sortOrder,
        List<@Valid PlanDayRequest> days
    ) {
    }

    public record CampaignRow(
        long id, String code, String title, String bannerImage, String posterImage, String buttonText, String rulesText, LocalDate startDate,
        LocalDate endDate, String status, int sortOrder, long checkinCount,
        Instant createdAt, Instant updatedAt
    ) {
    }

    public static final class CampaignRequest {
        @JsonProperty @Size(max = 40) private String code;
        @JsonProperty @NotBlank @Size(max = 80) private String title;
        @JsonProperty @Size(max = 1024) private String bannerImage;
        @JsonProperty @Size(max = 1024) private String posterImage;
        @JsonProperty @Size(max = 20) private String buttonText;
        @JsonProperty @NotBlank @Size(max = 1000) private String rulesText;
        @JsonProperty private LocalDate startDate;
        @JsonProperty private LocalDate endDate;
        @JsonProperty @NotBlank private String status;
        @JsonProperty private int sortOrder;
        @JsonIgnore private boolean bannerImageProvided;

        public CampaignRequest() {
        }

        public CampaignRequest(String code, String title, String posterImage, String buttonText, String rulesText,
                               LocalDate startDate, LocalDate endDate, String status, int sortOrder) {
            this.code = code;
            this.title = title;
            this.posterImage = posterImage;
            this.buttonText = buttonText;
            this.rulesText = rulesText;
            this.startDate = startDate;
            this.endDate = endDate;
            this.status = status;
            this.sortOrder = sortOrder;
        }

        public CampaignRequest(String code, String title, String bannerImage, String posterImage, String buttonText,
                               String rulesText, LocalDate startDate, LocalDate endDate, String status, int sortOrder) {
            this(code, title, posterImage, buttonText, rulesText, startDate, endDate, status, sortOrder);
            setBannerImage(bannerImage);
        }

        // Old admin clients omit this newly added property. Explicit null still means remove the banner.
        @JsonSetter("bannerImage")
        public void setBannerImage(String bannerImage) {
            this.bannerImage = bannerImage;
            this.bannerImageProvided = true;
        }

        public String code() { return code; }
        public String title() { return title; }
        public String bannerImage() { return bannerImage; }
        public String posterImage() { return posterImage; }
        public String buttonText() { return buttonText; }
        public String rulesText() { return rulesText; }
        public LocalDate startDate() { return startDate; }
        public LocalDate endDate() { return endDate; }
        public String status() { return status; }
        public int sortOrder() { return sortOrder; }
        public boolean bannerImageProvided() { return bannerImageProvided; }
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
        long id, String name, String brand, String snPrefix, String imageUrl, long deviceCount,
        Instant createdAt, Instant updatedAt
    ) {
    }

    public record DeviceModelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "(?i)(manhart|ARVELLO)") String brand,
        @NotBlank @Size(min = 2, max = 12)
        @Pattern(regexp = "[A-Za-z0-9]+", message = "SN 前缀只能包含字母和数字") String snPrefix,
        @Size(max = 500, message = "型号图片地址不能超过500个字符") String imageUrl
    ) {
    }

    public record DeviceRow(
        long id, String serialNumber, String deviceModel, String brand, String deviceName, String deviceSource,
        boolean bound, Long boundUserId, String boundUserName, String boundUserPhone,
        Instant createdAt, Instant updatedAt, Instant boundAt, Instant unboundAt
    ) {
    }

    public record DeviceCreateRequest(
        @NotBlank @Size(max = 100) String deviceModel,
        @NotBlank(message = "请选择设备品牌")
        @Pattern(regexp = "(?i)(manhart|ARVELLO)", message = "设备品牌只能选择 Manhart 或 ARVELLO") String brand
    ) {
    }

    public record DeviceBatchCreateRequest(
        @NotBlank @Size(max = 100) String deviceModel,
        @NotBlank(message = "请选择设备品牌")
        @Pattern(regexp = "(?i)(manhart|ARVELLO)", message = "设备品牌只能选择 Manhart 或 ARVELLO") String brand,
        @Min(value = 1, message = "批量新增数量不能少于 1")
        @Max(value = 100, message = "单次最多批量新增 100 台设备") int quantity
    ) {
    }

    public record DeviceBatchCreateResult(
        int count, String firstSerialNumber, String lastSerialNumber
    ) {
    }

    public record DeviceExportRequest(
        @NotEmpty(message = "请至少选择一台设备")
        @Size(max = 100, message = "单次最多导出 100 台设备") List<@NotNull Long> deviceIds
    ) {
    }

    public record GeneratedDevice(
        String serialNumber, String qrToken, String deviceModel, String brand
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
