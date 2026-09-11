package com.qinglian.fitness.admin;

import com.qinglian.fitness.admin.AdminDtos.*;
import com.qinglian.fitness.catalog.ExerciseCategory;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.mapper.AdminMapper;
import com.qinglian.fitness.mapper.AdminMapper.CourseData;
import com.qinglian.fitness.mapper.AdminMapper.InsertCommand;
import com.qinglian.fitness.mapper.AdminMapper.PlanData;
import com.qinglian.fitness.presence.PresenceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Repository
public class AdminRepository {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AdminMapper mapper;
    private final PresenceService presenceService;

    public AdminRepository(AdminMapper mapper, PresenceService presenceService) {
        this.mapper = mapper;
        this.presenceService = presenceService;
    }

    public Dashboard dashboard() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate firstTrendDate = today.minusDays(6);
        Instant weekStart = firstTrendDate.atStartOfDay(BUSINESS_ZONE).toInstant();
        Map<LocalDate, Long> workoutTrend = new LinkedHashMap<>();
        Map<LocalDate, Long> onlineTrend = new LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            workoutTrend.put(date, 0L);
            onlineTrend.put(date, 0L);
        }
        mapper.findWorkoutTrend(weekStart).forEach(point -> workoutTrend.put(point.date(), point.count()));
        mapper.findOnlineTrend(firstTrendDate).forEach(point -> onlineTrend.put(point.date(), point.count()));

        return new Dashboard(
            mapper.countUsers(), mapper.countUsersSince(weekStart), mapper.countWorkoutsSince(weekStart),
            mapper.countCourses(), mapper.countPendingFeedback(),
            mapper.countDailyOnline(today), presenceService.currentOnlineCount(),
            workoutTrend.entrySet().stream().map(item -> new TrendPoint(item.getKey(), item.getValue())).toList(),
            onlineTrend.entrySet().stream().map(item -> new TrendPoint(item.getKey(), item.getValue())).toList(),
            mapper.findRecentContent(6)
        );
    }

    public PageResult<UserRow> users(String query, String presence, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String filter = normalizeQuery(query);
        Boolean online = switch (presence == null ? "ALL" : presence) {
            case "ALL" -> null;
            case "ONLINE" -> true;
            case "OFFLINE" -> false;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRESENCE", "在线状态无效");
        };
        Instant cutoff = Instant.now().minusSeconds(PresenceService.TIMEOUT_SECONDS);
        List<UserRow> users = mapper.findUsers(filter, online, cutoff, paging.pageSize(), paging.offset());
        Map<Long, com.qinglian.fitness.presence.PresenceMapper.Summary> summaries = new LinkedHashMap<>();
        presenceService.summaries(users.stream().map(UserRow::id).toList()).forEach(item -> summaries.put(item.userId(), item));
        List<UserRow> rows = users.stream().map(row -> row.withPresence(summaries.getOrDefault(row.id(),
            new com.qinglian.fitness.presence.PresenceMapper.Summary(row.id(), false, null, null)))).toList();
        return new PageResult<>(rows, mapper.countUsersFiltered(filter, online, cutoff), paging.page(), paging.pageSize());
    }

    public PageResult<com.qinglian.fitness.presence.PresenceMapper.Visit> presenceHistory(long id, int page, int pageSize) {
        user(id);
        Paging paging = paging(page, pageSize);
        return new PageResult<>(presenceService.history(id, paging.pageSize(), paging.offset()),
            presenceService.historyCount(id), paging.page(), paging.pageSize());
    }

    public UserRow user(long id) {
        UserRow row = mapper.findUser(id);
        if (row == null) throw notFound("USER_NOT_FOUND", "用户不存在");
        return row;
    }

    @Transactional
    public UserRow updateUser(long id, UserUpdateRequest request) {
        user(id);
        String status = normalizeUserStatus(request.status());
        String phone = normalizePhone(request.phone());
        if (phone != null && mapper.countUsersByPhoneExcept(phone, id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_PHONE_ALREADY_BOUND", "该手机号已绑定其他账号");
        }
        try {
            if (mapper.updateUser(id, phone, status) == 0) {
                throw notFound("USER_NOT_FOUND", "用户不存在");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_PHONE_ALREADY_BOUND", "该手机号已绑定其他账号");
        }
        if (!"ACTIVE".equals(status)) {
            mapper.revokeUserSessions(id, Instant.now());
        }
        return user(id);
    }

    public PageResult<CourseRow> courses(String query, String status, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String normalizedQuery = normalizeQuery(query);
        String normalizedStatus = normalizeFilter(status);
        List<CourseRow> items = mapper.findCourses(normalizedQuery, normalizedStatus, paging.pageSize(), paging.offset())
            .stream().map(this::toCourseRow).toList();
        return new PageResult<>(items, mapper.countCoursesFiltered(normalizedQuery, normalizedStatus),
            paging.page(), paging.pageSize());
    }

    public CourseRow course(long id) {
        CourseData row = mapper.findCourse(id);
        if (row == null) throw notFound("COURSE_NOT_FOUND", "课程不存在");
        return toCourseRow(row);
    }

    @Transactional
    public CourseRow createCourse(CourseRequest request) {
        validateContentStatus(request.status());
        InsertCommand<CourseRequest> command = new InsertCommand<>(request);
        mapper.insertCourse(command);
        long id = generatedId(command);
        replaceCourseExercises(id, request.exerciseIds(), request.exercises(), "PUBLISHED".equals(request.status()));
        return course(id);
    }

    @Transactional
    public CourseRow updateCourse(long id, CourseRequest request) {
        course(id);
        validateContentStatus(request.status());
        mapper.updateCourse(id, request);
        replaceCourseExercises(id, request.exerciseIds(), request.exercises(), "PUBLISHED".equals(request.status()));
        return course(id);
    }

    public void deleteCourse(long id) {
        course(id);
        try {
            mapper.deleteCourse(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "COURSE_IN_USE", "该课程已被计划或训练记录使用，请先下架而不是删除");
        }
    }

    public PageResult<ExerciseRow> exercises(String query, String status, String bodyPart, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String normalizedQuery = normalizeQuery(query);
        String normalizedStatus = normalizeFilter(status);
        List<String> bodyParts = ExerciseCategory.filterValues(bodyPart);
        return new PageResult<>(mapper.findExercises(normalizedQuery, normalizedStatus, bodyParts, paging.pageSize(), paging.offset()),
            mapper.countExercisesFiltered(normalizedQuery, normalizedStatus, bodyParts), paging.page(), paging.pageSize());
    }

    public ExerciseRow exercise(long id) {
        ExerciseRow row = mapper.findExercise(id);
        if (row == null) throw notFound("EXERCISE_NOT_FOUND", "动作不存在");
        return row;
    }

    @Transactional
    public ExerciseRow createExercise(ExerciseRequest request) {
        ExerciseCategory.validateWrite(request.bodyPart());
        validateContentStatus(request.status());
        InsertCommand<ExerciseRequest> command = new InsertCommand<>(request);
        mapper.insertExercise(command);
        return exercise(generatedId(command));
    }

    public ExerciseRow updateExercise(long id, ExerciseRequest request) {
        ExerciseCategory.validateWrite(request.bodyPart());
        exercise(id);
        validateContentStatus(request.status());
        mapper.updateExercise(id, request);
        return exercise(id);
    }

    public void deleteExercise(long id) {
        exercise(id);
        try {
            mapper.deleteExercise(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "EXERCISE_IN_USE", "该动作已被课程使用，请先从课程中移除");
        }
    }

    public PageResult<PlanRow> plans(String query, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String filter = normalizeQuery(query);
        List<PlanRow> items = mapper.findPlans(filter, paging.pageSize(), paging.offset())
            .stream().map(this::toPlanRow).toList();
        return new PageResult<>(items, mapper.countPlansFiltered(filter), paging.page(), paging.pageSize());
    }

    public PlanRow plan(long id) {
        PlanData row = mapper.findPlan(id);
        if (row == null) throw notFound("PLAN_NOT_FOUND", "训练计划不存在");
        return toPlanRow(row);
    }

    @Transactional
    public PlanRow createPlan(PlanRequest request) {
        InsertCommand<PlanRequest> command = new InsertCommand<>(request);
        mapper.insertPlan(command);
        long id = generatedId(command);
        replacePlanItems(id, request.items());
        return plan(id);
    }

    @Transactional
    public PlanRow updatePlan(long id, PlanRequest request) {
        plan(id);
        mapper.updatePlan(id, request);
        replacePlanItems(id, request.items());
        return plan(id);
    }

    public void deletePlan(long id) {
        plan(id);
        try {
            mapper.deletePlan(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_IN_USE", "已有用户选择该计划，请停用而不是删除");
        }
    }

    public PageResult<CampaignRow> campaigns(String query, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String filter = normalizeQuery(query);
        return new PageResult<>(mapper.findCampaigns(filter, paging.pageSize(), paging.offset()),
            mapper.countCampaignsFiltered(filter), paging.page(), paging.pageSize());
    }

    public CampaignRow campaign(long id) {
        CampaignRow row = mapper.findCampaign(id);
        if (row == null) throw notFound("CAMPAIGN_NOT_FOUND", "训练营不存在");
        return row;
    }

    @Transactional
    public CampaignRow createCampaign(CampaignRequest request) {
        validateContentStatus(request.status());
        validateDates(request.startDate(), request.endDate());
        InsertCommand<CampaignRequest> command = new InsertCommand<>(request);
        mapper.insertCampaign(command);
        return campaign(generatedId(command));
    }

    public CampaignRow updateCampaign(long id, CampaignRequest request) {
        campaign(id);
        validateContentStatus(request.status());
        validateDates(request.startDate(), request.endDate());
        mapper.updateCampaign(id, request);
        return campaign(id);
    }

    public void deleteCampaign(long id) {
        CampaignRow item = campaign(id);
        if (item.checkinCount() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CAMPAIGN_IN_USE", "该训练营已有打卡记录，请下架而不是删除");
        }
        mapper.deleteCampaign(id);
    }

    public PageResult<WorkoutRow> workouts(String query, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String filter = normalizeQuery(query);
        return new PageResult<>(mapper.findWorkouts(filter, paging.pageSize(), paging.offset()),
            mapper.countWorkoutsFiltered(filter), paging.page(), paging.pageSize());
    }

    public PageResult<FeedbackRow> feedback(String status, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String normalizedStatus = normalizeFilter(status);
        return new PageResult<>(mapper.findFeedback(normalizedStatus, paging.pageSize(), paging.offset()),
            mapper.countFeedbackFiltered(normalizedStatus), paging.page(), paging.pageSize());
    }

    public FeedbackRow updateFeedbackStatus(long id, String status) {
        if (!List.of("SUBMITTED", "PROCESSING", "RESOLVED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS_INVALID", "反馈状态不正确");
        }
        if (mapper.updateFeedbackStatus(id, status) == 0) {
            throw notFound("FEEDBACK_NOT_FOUND", "反馈不存在");
        }
        return mapper.findFeedbackById(id);
    }

    public PageResult<AuditRow> audits(int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        return new PageResult<>(mapper.findAudits(paging.pageSize(), paging.offset()), mapper.countAudits(),
            paging.page(), paging.pageSize());
    }

    public List<DeviceModelRow> deviceModels() {
        return mapper.findDeviceModels();
    }

    public DeviceModelRow deviceModel(long id) {
        DeviceModelRow row = mapper.findDeviceModel(id);
        if (row == null) throw notFound("DEVICE_MODEL_NOT_FOUND", "设备型号不存在");
        return row;
    }

    public DeviceModelRow createDeviceModel(DeviceModelRequest request) {
        request = validateDeviceModel(request);
        InsertCommand<DeviceModelRequest> command = new InsertCommand<>(request);
        try {
            mapper.insertDeviceModel(command);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_MODEL_EXISTS", "设备型号或 SN 前缀已存在");
        }
        return deviceModel(generatedId(command));
    }

    public DeviceModelRow updateDeviceModel(long id, DeviceModelRequest request) {
        DeviceModelRow existing = deviceModel(id);
        request = validateDeviceModel(request);
        if (existing.deviceCount() > 0 && !existing.brand().equals(request.brand())) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_MODEL_IN_USE", "该型号已有设备，不能更换品牌，请新增型号");
        }
        try {
            mapper.updateDeviceModel(id, request);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_MODEL_EXISTS", "设备型号或 SN 前缀已存在");
        }
        return deviceModel(id);
    }

    public void deleteDeviceModel(long id) {
        DeviceModelRow row = deviceModel(id);
        if (row.deviceCount() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_MODEL_IN_USE", "该型号下还有设备，无法删除");
        }
        try {
            mapper.deleteDeviceModel(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_MODEL_IN_USE", "该型号下还有设备，无法删除");
        }
    }

    public PageResult<DeviceRow> devices(String query, String serialNumber, String deviceQuery, String boundUser,
                                         String deviceModel, String brand, String deviceSource, String bindingStatus,
                                         LocalDate createdFrom, LocalDate createdTo,
                                         int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String normalizedQuery = normalizeQuery(query);
        String normalizedSerialNumber = normalizeQuery(serialNumber);
        String normalizedDeviceQuery = normalizeQuery(deviceQuery);
        String normalizedBoundUser = normalizeQuery(boundUser);
        String normalizedModel = normalizeDeviceModel(deviceModel);
        String normalizedBrand = normalizeDeviceBrandFilter(brand);
        String normalizedSource = normalizeDeviceSourceFilter(deviceSource);
        String normalizedBindingStatus = normalizeBindingStatus(bindingStatus);
        validateDates(createdFrom, createdTo);
        return new PageResult<>(mapper.findDevices(normalizedQuery, normalizedSerialNumber, normalizedDeviceQuery,
                normalizedBoundUser, normalizedModel, normalizedBrand, normalizedSource, normalizedBindingStatus,
                createdFrom, createdTo, paging.pageSize(), paging.offset()),
            mapper.countDevicesFiltered(normalizedQuery, normalizedSerialNumber, normalizedDeviceQuery,
                normalizedBoundUser, normalizedModel, normalizedBrand, normalizedSource, normalizedBindingStatus,
                createdFrom, createdTo), paging.page(), paging.pageSize());
    }

    public DeviceRow device(long id) {
        DeviceRow row = mapper.findDevice(id);
        if (row == null) throw notFound("DEVICE_NOT_FOUND", "设备不存在");
        return row;
    }

    public List<DeviceRow> devicesByIds(List<Long> ids) {
        List<Long> distinctIds = new LinkedHashSet<>(ids).stream().toList();
        List<DeviceRow> rows = mapper.findDevicesByIds(distinctIds);
        Map<Long, DeviceRow> rowsById = rows.stream().collect(java.util.stream.Collectors.toMap(DeviceRow::id, row -> row));
        if (rowsById.size() != distinctIds.size()) {
            throw notFound("DEVICE_NOT_FOUND", "部分设备不存在或已被删除");
        }
        return distinctIds.stream().map(rowsById::get).toList();
    }

    @Transactional
    public DeviceRow createDevice(DeviceCreateRequest request) {
        DeviceModelRow model = requireDeviceModel(request.deviceModel(), request.brand());
        long sequence = mapper.lockDeviceSerialSequence(model.id()) + 1;
        if (sequence > DeviceSerialNumber.MAX_SEQUENCE) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_SN_EXHAUSTED", "该型号的设备流水号已用完");
        }
        GeneratedDevice generated = new GeneratedDevice(
            DeviceSerialNumber.format(model.snPrefix(), sequence), randomToken(), model.name(), normalizeBrand(request.brand()));
        InsertCommand<GeneratedDevice> command = new InsertCommand<>(generated);
        mapper.updateDeviceSerialSequence(model.id(), sequence);
        try {
            mapper.insertDevice(command);
            return device(generatedId(command));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_ID_GENERATION_FAILED", "无法生成唯一设备 SN，请检查已有数据");
        }
    }

    @Transactional
    public DeviceBatchCreateResult createDevices(DeviceBatchCreateRequest request) {
        DeviceModelRow model = requireDeviceModel(request.deviceModel(), request.brand());
        long currentSequence = mapper.lockDeviceSerialSequence(model.id());
        if (currentSequence > DeviceSerialNumber.MAX_SEQUENCE - request.quantity()) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_SN_EXHAUSTED", "该型号剩余的设备流水号不足");
        }

        long firstSequence = currentSequence + 1;
        long lastSequence = currentSequence + request.quantity();
        String brand = normalizeBrand(request.brand());
        mapper.updateDeviceSerialSequence(model.id(), lastSequence);
        try {
            for (long sequence = firstSequence; sequence <= lastSequence; sequence++) {
                GeneratedDevice generated = new GeneratedDevice(
                    DeviceSerialNumber.format(model.snPrefix(), sequence), randomToken(), model.name(), brand);
                mapper.insertDevice(new InsertCommand<>(generated));
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_ID_GENERATION_FAILED", "无法生成唯一设备 SN，请检查已有数据");
        }
        return new DeviceBatchCreateResult(
            request.quantity(),
            DeviceSerialNumber.format(model.snPrefix(), firstSequence),
            DeviceSerialNumber.format(model.snPrefix(), lastSequence)
        );
    }

    public void deleteDevice(long id) {
        DeviceRow item = device(id);
        if (item.bound()) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_IN_USE", "该设备已被用户绑定，无法删除");
        }
        try {
            mapper.deleteDevice(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_IN_USE", "该设备已被用户绑定，无法删除");
        }
    }

    private CourseRow toCourseRow(CourseData row) {
        return new CourseRow(row.id(), row.title(), row.type(), row.durationMinutes(), row.level(), row.equipment(),
            row.summary(), row.coverImage(), row.videoUrl(), row.videoCoverImage(), row.videoDurationSeconds(),
            row.viewCount(), row.status(), row.sortOrder(), mapper.findCourseExerciseIds(row.id()),
            row.createdAt(), row.updatedAt(), row.introduction(), row.audience(), mapper.findCourseExerciseSettings(row.id()), row.trainingTags());
    }

    private PlanRow toPlanRow(PlanData row) {
        return new PlanRow(row.id(), row.title(), row.weekNumber(), row.sessionsPerWeek(), row.description(),
            row.subtitle(), row.coverImage(), row.level(), row.trainingScene(), row.sessionMinutes(),
            row.benefitOne(), row.benefitTwo(), row.benefitThree(),
            row.active(), row.sortOrder(), mapper.findPlanItems(row.id()), row.createdAt(), row.updatedAt());
    }

    private void replaceCourseExercises(long courseId, List<Long> exerciseIds, List<CourseExerciseRequest> exercises, boolean publishing) {
        // Preserve per-set settings when an older client sends only exercise IDs.
        var existing = mapper.findCourseExerciseSettings(courseId);
        List<CourseExerciseRequest> items = exercises != null ? exercises :
            (exerciseIds == null ? List.of() : exerciseIds.stream().distinct().map(id -> existing.stream()
                .filter(item -> item.exerciseId() == id).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "COURSE_SETS_REQUIRED", "请在课程动作编排中配置训练组和次数")))
                .toList());
        var seen = new java.util.HashSet<Long>();
        if (publishing && items.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_SETS_REQUIRED", "发布课程前请配置训练动作和次数");
        for (var item : items) {
            if (!seen.add(item.exerciseId())) throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_EXERCISE_DUPLICATE", "课程中不能重复添加同一动作");
            var exercise = mapper.findExercise(item.exerciseId());
            if (exercise == null) throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_EXERCISE_NOT_FOUND", "所选动作不存在");
            if (item.sets() == null || item.sets().isEmpty() || item.sets().stream().anyMatch(set -> set == null || set.repetitions() == null || set.repetitions() < 1 || set.repetitions() > 999)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_REPETITIONS_REQUIRED", "请为每个训练组填写 1 至 999 次");
            }
            if (publishing && (!"PUBLISHED".equals(exercise.status()) || exercise.videoUrl() == null || exercise.videoUrl().isBlank())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COURSE_VIDEO_REQUIRED", "发布课程前请为所有动作配置示范视频并发布动作");
            }
        }
        mapper.deleteCourseExercises(courseId);
        int order = 10;
        for (var item : items) {
            mapper.insertCourseExercise(courseId, item.exerciseId(), order, item.sets(), item.recommendedPlays());
            order += 10;
        }
    }

    private void replacePlanItems(long planId, List<PlanItemRequest> items) {
        mapper.deletePlanItems(planId);
        if (items == null) return;
        for (PlanItemRequest item : items) mapper.insertPlanItem(planId, item);
    }

    private void validateContentStatus(String status) {
        if (!List.of("DRAFT", "PUBLISHED", "ARCHIVED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS_INVALID", "内容状态不正确");
        }
    }

    private String normalizeUserStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "INACTIVE").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_STATUS_INVALID", "用户状态不正确");
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String normalized = phone.trim().replaceAll("[\\s-]", "");
        if (!normalized.matches("\\d{6,20}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "USER_PHONE_INVALID", "手机号只能包含 6 至 20 位数字");
        }
        return normalized;
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_INVALID", "结束日期不能早于开始日期");
        }
    }

    private long generatedId(InsertCommand<?> command) {
        if (command.getId() == null) throw new IllegalStateException("Generated id is missing");
        return command.getId();
    }

    private Paging paging(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return new Paging(safePage, safeSize, (safePage - 1) * safeSize);
    }

    private String normalizeQuery(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)
            ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDeviceModel(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? null : value.trim();
    }

    private String normalizeDeviceBrandFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        if ("manhart".equalsIgnoreCase(value.trim())) return "Manhart";
        if ("ARVELLO".equalsIgnoreCase(value.trim())) return "ARVELLO";
        if ("UNBRANDED".equalsIgnoreCase(value.trim())) return "UNBRANDED";
        throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_BRAND_FILTER_INVALID", "设备品牌筛选条件不正确");
    }

    private String normalizeDeviceSourceFilter(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        if ("OWN".equalsIgnoreCase(value.trim())) return "OWN";
        if ("THIRD_PARTY".equalsIgnoreCase(value.trim())) return "THIRD_PARTY";
        throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_SOURCE_FILTER_INVALID", "设备来源筛选条件不正确");
    }

    private String normalizeBindingStatus(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        if ("BOUND".equalsIgnoreCase(value.trim())) return "BOUND";
        if ("UNBOUND".equalsIgnoreCase(value.trim())) return "UNBOUND";
        if ("RELEASED".equalsIgnoreCase(value.trim())) return "RELEASED";
        throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_BINDING_FILTER_INVALID", "设备绑定状态筛选条件不正确");
    }

    private DeviceModelRequest validateDeviceModel(DeviceModelRequest request) {
        String brand = normalizeBrand(request.brand());
        String prefix = request.snPrefix().trim().toUpperCase(java.util.Locale.ROOT);
        String expected = "Manhart".equals(brand) ? "MN" : "AV";
        if (!prefix.startsWith(expected)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_MODEL_PREFIX_INVALID", "该品牌的 SN 前缀必须以 " + expected + " 开头");
        }
        return new DeviceModelRequest(request.name().trim(), brand, prefix);
    }

    private DeviceModelRow requireDeviceModel(String deviceModel, String brand) {
        DeviceModelRow row = deviceModel == null ? null : mapper.findDeviceModelByName(deviceModel.trim());
        if (row == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_MODEL_INVALID", "设备型号不正确");
        }
        if (!row.brand().equals(normalizeBrand(brand))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_MODEL_BRAND_MISMATCH", "所选型号不属于该品牌");
        }
        validateDeviceModel(new DeviceModelRequest(row.name(), row.brand(), row.snPrefix()));
        return row;
    }

    private String normalizeBrand(String brand) {
        if (brand == null || brand.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_BRAND_REQUIRED", "请选择设备品牌");
        }
        if ("manhart".equalsIgnoreCase(brand.trim())) return "Manhart";
        if ("ARVELLO".equalsIgnoreCase(brand.trim())) return "ARVELLO";
        throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_BRAND_INVALID", "设备品牌只能选择 Manhart 或 ARVELLO");
    }

    private String randomToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private record Paging(int page, int pageSize, int offset) {
    }
}
