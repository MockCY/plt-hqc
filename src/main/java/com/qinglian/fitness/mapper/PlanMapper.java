package com.qinglian.fitness.mapper;

import com.qinglian.fitness.plan.PlanDtos.PlanSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PlanMapper {

    Long selectedPlanId(@Param("userId") long userId);

    PlanHeader findPlanHeader(@Param("planId") Long planId);

    List<PlanItemRow> findPlanItems(
        @Param("userId") long userId,
        @Param("weekStart") LocalDate weekStart,
        @Param("weekEnd") LocalDate weekEnd,
        @Param("planId") long planId
    );

    List<PlanSummary> catalog();

    int updateSelection(@Param("userId") long userId, @Param("planId") long planId);

    int createSelection(@Param("userId") long userId, @Param("planId") long planId);

    record PlanHeader(long id, String title, int weekNumber, int sessionsPerWeek, String description) {
    }

    record PlanItemRow(
        long id,
        int dayOffset,
        long courseId,
        String courseTitle,
        int durationMinutes,
        String status
    ) {
    }
}
