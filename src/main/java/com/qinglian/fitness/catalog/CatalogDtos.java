package com.qinglian.fitness.catalog;

import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CourseView(
        long id,
        String title,
        String type,
        int durationMinutes,
        String level,
        String equipment,
        String summary,
        String coverImage,
        int exerciseCount
    ) {
    }

    public record ExerciseView(
        long id,
        String name,
        String bodyPart,
        String level,
        String equipment,
        int suggestedSets,
        String target,
        String cue,
        String safetyTip
    ) {
    }

    public record CourseExerciseView(
        long id,
        String name,
        String target,
        String cue,
        String safetyTip,
        int durationSeconds
    ) {
    }

    public record CourseDetailView(
        long id,
        String title,
        String type,
        int durationMinutes,
        String level,
        String equipment,
        String summary,
        String coverImage,
        List<CourseExerciseView> exercises
    ) {
    }
}
