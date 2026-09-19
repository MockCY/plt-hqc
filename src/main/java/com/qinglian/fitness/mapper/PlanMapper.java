package com.qinglian.fitness.mapper;

import com.qinglian.fitness.plan.PlanDtos.PlanSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PlanMapper {

    Long currentPlanId(@Param("userId") long userId);

    PlanHeader findPlanHeader(@Param("planId") Long planId);

    List<PlanDayRow> findPlanDays(
        @Param("userId") long userId,
        @Param("planId") long planId
    );

    List<PlanExerciseRow> findPlanDayExercises(@Param("planDayId") long planDayId);

    List<PlanSummary> catalog();

    int createSelection(@Param("userId") long userId, @Param("planId") long planId);

    int planDayExists(@Param("planId") long planId, @Param("dayNumber") int dayNumber);

    int completeDay(@Param("userId") long userId, @Param("planId") long planId, @Param("dayNumber") int dayNumber);

    record PlanHeader(
        long id, String title, int weekNumber, int sessionsPerWeek, int cycleDays, String description,
        String subtitle, String coverImage, String detailImage, String homeImage, String level, String trainingScene, Integer sessionMinutes,
        String benefitOne, String benefitTwo, String benefitThree
    ) {
    }

    record PlanDayRow(
        long id,
        int dayNumber,
        String title,
        int durationMinutes,
        int exerciseCount,
        String status
    ) {
    }

    record PlanExerciseRow(
        long id, long exerciseId, String exerciseName, int repetitions, int setCount, int sortOrder
    ) {
    }
}
