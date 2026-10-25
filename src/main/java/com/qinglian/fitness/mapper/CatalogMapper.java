package com.qinglian.fitness.mapper;

import com.qinglian.fitness.catalog.CatalogDtos.CourseExerciseView;
import com.qinglian.fitness.catalog.CatalogDtos.CourseView;
import com.qinglian.fitness.catalog.CatalogDtos.ExerciseView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 小程序公开内容的数据库访问入口，列表仅检索已发布内容。 */
@Mapper
public interface CatalogMapper {

    /** 按可空类型与已转义 LIKE 参数检索课程，响应继续沿用原有数组字段。 */
    List<CourseView> findCourses(@Param("type") String type, @Param("pattern") String pattern);

    /** 动作类型过滤与关键词取交集，关键词的类型匹配兼容历史存储别名。 */
    List<ExerciseView> findExercises(@Param("bodyParts") List<String> bodyParts, @Param("pattern") String pattern,
                                   @Param("categoryAliases") List<String> categoryAliases);

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
        long viewCount,
        String introduction,
        String audience,
        String trainingTags
    ) {
    }
}
