package com.qinglian.fitness.plan;

import java.time.LocalDate;
import java.util.List;

public final class PlanDtos {

    private PlanDtos() {
    }

    public record PlanView(
        long id,
        String title,
        int weekNumber,
        int sessionsPerWeek,
        String description,
        String subtitle,
        String coverImage,
        String detailImage,
        String level,
        String trainingScene,
        Integer sessionMinutes,
        String benefitOne,
        String benefitTwo,
        String benefitThree,
        List<PlanDayView> days
    ) {
    }

    public record PlanDayView(
        long id,
        int dayNumber,
        LocalDate trainingDate,
        String title,
        int durationMinutes,
        String status,
        List<PlanExerciseView> exercises
    ) {
    }

    public record PlanExerciseView(
        long id,
        long exerciseId,
        String exerciseName,
        int repetitions,
        int setCount,
        int sortOrder
    ) {
    }

    public record PlanSummary(
        long id,
        String title,
        int sessionsPerWeek,
        String description,
        String subtitle,
        String coverImage,
        String detailImage,
        String level,
        String trainingScene,
        Integer sessionMinutes,
        String benefitOne,
        String benefitTwo,
        String benefitThree,
        int dayCount
    ) {
    }

    public record PlanSelection(long planId, String title, boolean selected) {
    }

    public record PlanDayCompletion(long planId, int dayNumber, boolean completed) {
    }
}
