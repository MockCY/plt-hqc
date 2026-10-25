package com.qinglian.fitness.catalog;

import com.qinglian.fitness.catalog.CatalogDtos.CourseDetailView;
import com.qinglian.fitness.catalog.CatalogDtos.CourseView;
import com.qinglian.fitness.catalog.CatalogDtos.ExerciseView;
import com.qinglian.fitness.common.LiteralSearch;
import com.qinglian.fitness.mapper.CatalogMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 组织公开课程和动作的查询，关键词匹配由数据库完成，详情访问统计保留既有事务。 */
@Repository
public class CatalogRepository {

    private final CatalogMapper catalogMapper;

    public CatalogRepository(CatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    /** 查询已发布课程，复用字面包含匹配规则，不改变原列表字段与稳定顺序。 */
    public List<CourseView> findCourses(String type, String query) {
        return catalogMapper.findCourses(normalizeFilter(type), LiteralSearch.containsPattern(query));
    }

    /** 搜索已发布动作，并展开展示类型的历史别名，避免数据库迁移前漏搜。 */
    public List<ExerciseView> findExercises(String bodyPart, String query) {
        return catalogMapper.findExercises(ExerciseCategory.filterValues(bodyPart), LiteralSearch.containsPattern(query),
            ExerciseCategory.searchValues(query));
    }

    public Optional<CourseDetailView> findCourse(long id) {
        CatalogMapper.CourseDetailRow row = catalogMapper.findCourse(id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new CourseDetailView(
            row.id(), row.title(), row.type(), row.durationMinutes(), row.level(),
            row.equipment(), row.summary(), row.coverImage(), row.videoUrl(), row.videoCoverImage(),
            row.videoDurationSeconds(), row.viewCount(), catalogMapper.findCourseExercises(row.id()),
            row.introduction(), row.audience(), row.trainingTags()
        ));
    }

    @Transactional
    public Optional<CourseDetailView> viewCourse(long id, String visitorKey) {
        if (catalogMapper.findCourse(id) == null) return Optional.empty();
        if (catalogMapper.recordCourseView(id, visitorKey) == 1) catalogMapper.incrementCourseViewCount(id);
        return findCourse(id);
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() || "全部".equals(value) ? null : value;
    }
}
