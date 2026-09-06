package com.qinglian.fitness.mapper;

import com.qinglian.fitness.workout.WorkoutDtos.WorkoutView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WorkoutMapper {

    int create(NewWorkout workout);

    int createCustom(NewWorkout workout);

    int countOwnedCustomCourse(@Param("courseId") long courseId, @Param("userId") long userId);

    WorkoutView findById(@Param("id") long id, @Param("userId") long userId);

    WorkoutView findCustomById(@Param("id") long id, @Param("userId") long userId);

    List<WorkoutView> findRecent(@Param("userId") long userId, @Param("limit") int limit);

    StatsBase stats(@Param("userId") long userId);

    int recordActivity(@Param("userId") long userId,
                       @Param("activity") com.qinglian.fitness.workout.WorkoutDtos.ActivityRequest activity);

    int recordDetailVisit(@Param("userId") long userId, @Param("detailType") String detailType,
                         @Param("itemId") long itemId);

    List<LocalDate> completedDates(@Param("userId") long userId);

    record StatsBase(long completedCount, long totalMinutes) {
    }

    final class NewWorkout {
        private Long id;
        private final long userId;
        private final Long courseId;
        private final Long customCourseId;
        private final int durationMinutes;
        private final int completionPercent;
        private final Instant startedAt;
        private final Instant completedAt;

        public NewWorkout(
            long userId,
            Long courseId,
            Long customCourseId,
            int durationMinutes,
            int completionPercent,
            Instant startedAt,
            Instant completedAt
        ) {
            this.userId = userId;
            this.courseId = courseId;
            this.customCourseId = customCourseId;
            this.durationMinutes = durationMinutes;
            this.completionPercent = completionPercent;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
        }

        public long getId() {
            if (id == null) {
                throw new IllegalStateException("Workout id was not generated");
            }
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getUserId() {
            return userId;
        }

        public Long getCourseId() {
            return courseId;
        }

        public Long getCustomCourseId() {
            return customCourseId;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public int getCompletionPercent() {
            return completionPercent;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public Instant getCompletedAt() {
            return completedAt;
        }
    }
}
