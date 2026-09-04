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
        String videoUrl,
        String videoCoverImage,
        Integer videoDurationSeconds,
        long viewCount,
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
        String safetyTip,
        String coverImage,
        String videoUrl,
        String videoCoverImage,
        Integer videoDurationSeconds,
        String backgroundMusicUrl
    ) {
    }

    public record CourseExerciseView(
        long id,
        String name,
        String target,
        String cue,
        String safetyTip,
        int durationSeconds,
        String coverImage,
        String videoUrl,
        String videoCoverImage,
        Integer videoDurationSeconds,
        String backgroundMusicUrl
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
        String videoUrl,
        String videoCoverImage,
        Integer videoDurationSeconds,
        long viewCount,
        List<CourseExerciseView> exercises
    ) {
    }
}
