package com.qinglian.fitness.mapper;

import com.qinglian.fitness.catalog.CatalogDtos.CourseExerciseView;
import com.qinglian.fitness.catalog.CatalogDtos.CourseView;
import com.qinglian.fitness.catalog.CatalogDtos.ExerciseView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CatalogMapper {

    List<CourseView> findCourses(@Param("type") String type, @Param("query") String query);

    List<ExerciseView> findExercises(@Param("bodyPart") String bodyPart, @Param("query") String query);

    CourseDetailRow findCourse(@Param("id") long id);

    List<CourseExerciseView> findCourseExercises(@Param("courseId") long courseId);

    int recordCourseView(@Param("courseId") long courseId, @Param("visitorKey") String visitorKey);

    int incrementCourseViewCount(@Param("courseId") long courseId);

    record CourseDetailRow(
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
        long viewCount
    ) {
    }
}
