package com.qinglian.fitness.workout;

import com.qinglian.fitness.workout.WorkoutDtos.CreateWorkoutRequest;
import com.qinglian.fitness.workout.WorkoutDtos.WorkoutStats;
import com.qinglian.fitness.workout.WorkoutDtos.WorkoutView;
import com.qinglian.fitness.mapper.WorkoutMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
public class WorkoutRepository {

    private final WorkoutMapper workoutMapper;

    public WorkoutRepository(WorkoutMapper workoutMapper) {
        this.workoutMapper = workoutMapper;
    }

    public WorkoutView create(long userId, CreateWorkoutRequest request) {
        if (request.customCourseId() != null) {
            return createCustom(userId, request);
        }
        Instant now = Instant.now();
        Instant startedAt = request.startedAt() == null ? now : request.startedAt();
        WorkoutMapper.NewWorkout workout = new WorkoutMapper.NewWorkout(
            userId, request.courseId(), null, request.durationMinutes(),
            request.completionPercent(), startedAt, now
        );
        workoutMapper.create(workout);
        return required(workoutMapper.findById(workout.getId(), userId));
    }

    private WorkoutView createCustom(long userId, CreateWorkoutRequest request) {
        if (workoutMapper.countOwnedCustomCourse(request.customCourseId(), userId) == 0) {
            throw new IllegalArgumentException("Custom course does not belong to user");
        }
        Instant now = Instant.now();
        Instant startedAt = request.startedAt() == null ? now : request.startedAt();
        WorkoutMapper.NewWorkout workout = new WorkoutMapper.NewWorkout(
            userId, null, request.customCourseId(), request.durationMinutes(),
            request.completionPercent(), startedAt, now
        );
        workoutMapper.createCustom(workout);
        return required(workoutMapper.findCustomById(workout.getId(), userId));
    }

    public List<WorkoutView> findRecent(long userId, int limit) {
        return workoutMapper.findRecent(userId, limit);
    }

    public WorkoutStats stats(long userId) {
        WorkoutMapper.StatsBase base = workoutMapper.stats(userId);
        List<LocalDate> dates = workoutMapper.completedDates(userId);
        return new WorkoutStats(base.completedCount(), base.totalMinutes(), consecutiveDays(dates));
    }

    public void recordDetailVisit(long userId, WorkoutDtos.DetailVisitRequest visit) {
        workoutMapper.recordDetailVisit(userId, visit.detailType().name(), visit.itemId());
    }

    private WorkoutView required(WorkoutView workout) {
        if (workout == null) {
            throw new IllegalStateException("Workout was not found after creation");
        }
        return workout;
    }

    private int consecutiveDays(List<LocalDate> dates) {
        Set<LocalDate> completedDates = new HashSet<>(dates);
        LocalDate cursor = LocalDate.now();
        if (!completedDates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int count = 0;
        while (completedDates.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }
}
