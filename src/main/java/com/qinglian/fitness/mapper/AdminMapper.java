package com.qinglian.fitness.mapper;

import com.qinglian.fitness.admin.AdminDtos.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AdminMapper {

    long countUsers();

    long countUsersSince(@Param("since") Instant since);

    long countWorkoutsSince(@Param("since") Instant since);

    long countCourses();

    long countPendingFeedback();

    long countDailyOnline(@Param("date") LocalDate date);

    List<TrendPoint> findWorkoutTrend(@Param("since") Instant since);

    List<TrendPoint> findOnlineTrend(@Param("since") LocalDate since);

    List<RecentContent> findRecentContent(@Param("limit") int limit);

    long countUsersFiltered(@Param("query") String query, @Param("online") Boolean online, @Param("cutoff") Instant cutoff);

    List<UserRow> findUsers(
        @Param("query") String query,
        @Param("online") Boolean online,
        @Param("cutoff") Instant cutoff,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    UserRow findUser(@Param("id") long id);

    long countUsersByPhoneExcept(@Param("phone") String phone, @Param("id") long id);

    int updateUser(
        @Param("id") long id,
        @Param("phone") String phone,
        @Param("status") String status
    );

    int revokeUserSessions(@Param("userId") long userId, @Param("now") Instant now);

    long countCoursesFiltered(@Param("query") String query, @Param("status") String status);

    List<CourseData> findCourses(
        @Param("query") String query,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    CourseData findCourse(@Param("id") long id);

    List<Long> findCourseExerciseIds(@Param("courseId") long courseId);

    List<CourseExerciseRequest> findCourseExerciseSettings(@Param("courseId") long courseId);

    int insertCourse(InsertCommand<CourseRequest> command);

    int updateCourse(@Param("id") long id, @Param("request") CourseRequest request);

    int deleteCourse(@Param("id") long id);

    int deleteCourseExercises(@Param("courseId") long courseId);

    int insertCourseExercise(
        @Param("courseId") long courseId,
        @Param("exerciseId") long exerciseId,
        @Param("sortOrder") int sortOrder,
        @Param("sets") List<com.qinglian.fitness.catalog.TrainingSet> sets
    );

    long countExercisesFiltered(@Param("query") String query, @Param("status") String status);

    List<ExerciseRow> findExercises(
        @Param("query") String query,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    ExerciseRow findExercise(@Param("id") long id);

    int insertExercise(InsertCommand<ExerciseRequest> command);

    int updateExercise(@Param("id") long id, @Param("request") ExerciseRequest request);

    int deleteExercise(@Param("id") long id);

    long countPlansFiltered(@Param("query") String query);

    List<PlanData> findPlans(
        @Param("query") String query,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    PlanData findPlan(@Param("id") long id);

    List<PlanItemRow> findPlanItems(@Param("planId") long planId);

    int insertPlan(InsertCommand<PlanRequest> command);

    int updatePlan(@Param("id") long id, @Param("request") PlanRequest request);

    int deletePlan(@Param("id") long id);

    int deletePlanItems(@Param("planId") long planId);

    int insertPlanItem(@Param("planId") long planId, @Param("item") PlanItemRequest item);

    long countCampaignsFiltered(@Param("query") String query);

    List<CampaignRow> findCampaigns(
        @Param("query") String query,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    CampaignRow findCampaign(@Param("id") long id);

    int insertCampaign(InsertCommand<CampaignRequest> command);

    int updateCampaign(@Param("id") long id, @Param("request") CampaignRequest request);

    int deleteCampaign(@Param("id") long id);

    long countWorkoutsFiltered(@Param("query") String query);

    List<WorkoutRow> findWorkouts(
        @Param("query") String query,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    long countFeedbackFiltered(@Param("status") String status);

    List<FeedbackRow> findFeedback(
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    FeedbackRow findFeedbackById(@Param("id") long id);

    int updateFeedbackStatus(@Param("id") long id, @Param("status") String status);

    List<DeviceModelRow> findDeviceModels();

    DeviceModelRow findDeviceModel(@Param("id") long id);

    DeviceModelRow findDeviceModelByName(@Param("name") String name);

    int insertDeviceModel(InsertCommand<DeviceModelRequest> command);

    int updateDeviceModel(@Param("id") long id, @Param("request") DeviceModelRequest request);

    int deleteDeviceModel(@Param("id") long id);

    long countDevicesFiltered(
        @Param("query") String query,
        @Param("serialNumber") String serialNumber,
        @Param("deviceQuery") String deviceQuery,
        @Param("boundUser") String boundUser,
        @Param("deviceModel") String deviceModel,
        @Param("brand") String brand,
        @Param("deviceSource") String deviceSource,
        @Param("bindingStatus") String bindingStatus,
        @Param("createdFrom") java.time.LocalDate createdFrom,
        @Param("createdTo") java.time.LocalDate createdTo
    );

    List<DeviceRow> findDevices(
        @Param("query") String query,
        @Param("serialNumber") String serialNumber,
        @Param("deviceQuery") String deviceQuery,
        @Param("boundUser") String boundUser,
        @Param("deviceModel") String deviceModel,
        @Param("brand") String brand,
        @Param("deviceSource") String deviceSource,
        @Param("bindingStatus") String bindingStatus,
        @Param("createdFrom") java.time.LocalDate createdFrom,
        @Param("createdTo") java.time.LocalDate createdTo,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    DeviceRow findDevice(@Param("id") long id);

    List<DeviceRow> findDevicesByIds(@Param("ids") List<Long> ids);

    long lockDeviceSerialSequence(@Param("id") long id);

    int updateDeviceSerialSequence(@Param("id") long id, @Param("sequence") long sequence);

    int insertDevice(InsertCommand<GeneratedDevice> command);

    int deleteDevice(@Param("id") long id);

    long countAudits();

    List<AuditRow> findAudits(@Param("limit") int limit, @Param("offset") int offset);

    record CourseData(
        long id, String title, String type, int durationMinutes, String level, String equipment,
        String summary, String coverImage, String videoUrl, String videoCoverImage,
        Integer videoDurationSeconds, long viewCount, String status, int sortOrder,
        Instant createdAt, Instant updatedAt, String introduction, String audience
    ) {
    }

    record PlanData(
        long id, String title, int weekNumber, int sessionsPerWeek, String description,
        String subtitle, String coverImage, String level, String trainingScene, Integer sessionMinutes,
        String benefitOne, String benefitTwo, String benefitThree,
        boolean active, int sortOrder, Instant createdAt, Instant updatedAt
    ) {
    }

    final class InsertCommand<T> {
        private final T request;
        private Long id;

        public InsertCommand(T request) {
            this.request = request;
        }

        public T getRequest() {
            return request;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }
}
