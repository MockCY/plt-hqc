package com.qinglian.fitness.admin;

import com.qinglian.fitness.admin.AdminDtos.*;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.mapper.AdminMapper;
import com.qinglian.fitness.mapper.AdminMapper.CourseData;
import com.qinglian.fitness.mapper.AdminMapper.InsertCommand;
import com.qinglian.fitness.mapper.AdminMapper.PlanData;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Repository
public class AdminRepository {

    private final AdminMapper mapper;

    public AdminRepository(AdminMapper mapper) {
        this.mapper = mapper;
    }

    public Dashboard dashboard() {
        Instant weekStart = LocalDate.now().minusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<LocalDate, Long> trend = new LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) {
            trend.put(LocalDate.now().minusDays(offset), 0L);
        }
        mapper.findWorkoutTrend(weekStart).forEach(point -> trend.put(point.date(), point.count()));

        return new Dashboard(
            mapper.countUsers(), mapper.countUsersSince(weekStart), mapper.countWorkoutsSince(weekStart),
            mapper.countCourses(), mapper.countPendingFeedback(),
            trend.entrySet().stream().map(item -> new TrendPoint(item.getKey(), item.getValue())).toList(),
            mapper.findRecentContent(6)
        );
    }

    public PageResult<UserRow> users(String query, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String filter = normalizeQuery(query);
        return new PageResult<>(mapper.findUsers(filter, paging.pageSize(), paging.offset()),
            mapper.countUsersFiltered(filter), paging.page(), paging.pageSize());
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
        replaceCourseExercises(id, request.exerciseIds());
        return course(id);
    }

    @Transactional
    public CourseRow updateCourse(long id, CourseRequest request) {
        course(id);
        validateContentStatus(request.status());
        mapper.updateCourse(id, request);
        replaceCourseExercises(id, request.exerciseIds());
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

    public PageResult<ExerciseRow> exercises(String query, String status, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String normalizedQuery = normalizeQuery(query);
        String normalizedStatus = normalizeFilter(status);
        return new PageResult<>(mapper.findExercises(normalizedQuery, normalizedStatus, paging.pageSize(), paging.offset()),
            mapper.countExercisesFiltered(normalizedQuery, normalizedStatus), paging.page(), paging.pageSize());
    }

    public ExerciseRow exercise(long id) {
        ExerciseRow row = mapper.findExercise(id);
        if (row == null) throw notFound("EXERCISE_NOT_FOUND", "动作不存在");
        return row;
    }

    @Transactional
    public ExerciseRow createExercise(ExerciseRequest request) {
        validateContentStatus(request.status());
        InsertCommand<ExerciseRequest> command = new InsertCommand<>(request);
        mapper.insertExercise(command);
        return exercise(generatedId(command));
    }

    public ExerciseRow updateExercise(long id, ExerciseRequest request) {
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

    public List<DeviceCategoryRow> deviceCategories() {
        return mapper.findDeviceCategories();
    }

    public DeviceCategoryRow deviceCategory(long id) {
        DeviceCategoryRow row = mapper.findDeviceCategory(id);
        if (row == null) throw notFound("DEVICE_CATEGORY_NOT_FOUND", "设备分类不存在");
        return row;
    }

    public DeviceCategoryRow createDeviceCategory(DeviceCategoryRequest request) {
        InsertCommand<DeviceCategoryRequest> command = new InsertCommand<>(request);
        try {
            mapper.insertDeviceCategory(command);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_CATEGORY_EXISTS", "设备分类名称已存在");
        }
        return deviceCategory(generatedId(command));
    }

    public DeviceCategoryRow updateDeviceCategory(long id, DeviceCategoryRequest request) {
        deviceCategory(id);
        try {
            mapper.updateDeviceCategory(id, request);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_CATEGORY_EXISTS", "设备分类名称已存在");
        }
        return deviceCategory(id);
    }

    public void deleteDeviceCategory(long id) {
        DeviceCategoryRow row = deviceCategory(id);
        if (row.deviceCount() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_CATEGORY_IN_USE", "该分类下还有设备，无法删除");
        }
        try {
            mapper.deleteDeviceCategory(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_CATEGORY_IN_USE", "该分类下还有设备，无法删除");
        }
    }

    public PageResult<DeviceRow> devices(String query, String category, int page, int pageSize) {
        Paging paging = paging(page, pageSize);
        String normalizedQuery = normalizeQuery(query);
        String normalizedCategory = normalizeCategory(category);
        return new PageResult<>(mapper.findDevices(normalizedQuery, normalizedCategory, paging.pageSize(), paging.offset()),
            mapper.countDevicesFiltered(normalizedQuery, normalizedCategory), paging.page(), paging.pageSize());
    }

    public DeviceRow device(long id) {
        DeviceRow row = mapper.findDevice(id);
        if (row == null) throw notFound("DEVICE_NOT_FOUND", "设备不存在");
        return row;
    }

    public DeviceRow createDevice(DeviceRequest request) {
        validateDeviceCategory(request.category());
        InsertCommand<DeviceRequest> command = new InsertCommand<>(request);
        mapper.insertDevice(command);
        return device(generatedId(command));
    }

    public DeviceRow updateDevice(long id, DeviceRequest request) {
        validateDeviceCategory(request.category());
        device(id);
        mapper.updateDevice(id, request);
        return device(id);
    }

    public void deleteDevice(long id) {
        DeviceRow item = device(id);
        if (item.boundUserCount() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_IN_USE", "该设备已被用户绑定，请停用而不是删除");
        }
        try {
            mapper.deleteDevice(id);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DEVICE_IN_USE", "该设备已被用户绑定，请停用而不是删除");
        }
    }

    private CourseRow toCourseRow(CourseData row) {
        return new CourseRow(row.id(), row.title(), row.type(), row.durationMinutes(), row.level(), row.equipment(),
            row.summary(), row.coverImage(), row.videoUrl(), row.videoCoverImage(), row.videoDurationSeconds(),
            row.viewCount(), row.status(), row.sortOrder(), mapper.findCourseExerciseIds(row.id()),
            row.createdAt(), row.updatedAt());
    }

    private PlanRow toPlanRow(PlanData row) {
        return new PlanRow(row.id(), row.title(), row.weekNumber(), row.sessionsPerWeek(), row.description(),
            row.active(), row.sortOrder(), mapper.findPlanItems(row.id()), row.createdAt(), row.updatedAt());
    }

    private void replaceCourseExercises(long courseId, List<Long> exerciseIds) {
        mapper.deleteCourseExercises(courseId);
        if (exerciseIds == null) return;
        int order = 10;
        for (Long exerciseId : exerciseIds.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            mapper.insertCourseExercise(courseId, exerciseId, order);
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

    private String normalizeCategory(String value) {
        return value == null || value.isBlank() || "ALL".equalsIgnoreCase(value) ? null : value.trim();
    }

    private void validateDeviceCategory(String category) {
        if (category == null || mapper.findDeviceCategoryByName(category.trim()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_CATEGORY_INVALID", "设备分类不正确");
        }
    }

    private ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    private record Paging(int page, int pageSize, int offset) {
    }
}
