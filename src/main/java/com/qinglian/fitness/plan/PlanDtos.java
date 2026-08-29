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
        List<PlanItemView> items
    ) {
    }

    public record PlanItemView(
        long id,
        int dayOffset,
        LocalDate trainingDate,
        long courseId,
        String courseTitle,
        int durationMinutes,
        String status
    ) {
    }

    public record PlanSummary(
        long id,
        String title,
        int sessionsPerWeek,
        String description,
        int courseCount
    ) {
    }

    public record PlanSelection(long planId, String title, boolean selected) {
    }
}
