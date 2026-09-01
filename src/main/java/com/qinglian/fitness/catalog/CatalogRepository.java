package com.qinglian.fitness.catalog;

import com.qinglian.fitness.catalog.CatalogDtos.CourseDetailView;
import com.qinglian.fitness.catalog.CatalogDtos.CourseExerciseView;
import com.qinglian.fitness.catalog.CatalogDtos.CourseView;
import com.qinglian.fitness.catalog.CatalogDtos.ExerciseView;
import com.qinglian.fitness.mapper.CatalogMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class CatalogRepository {

    private final CatalogMapper catalogMapper;

    public CatalogRepository(CatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    public List<CourseView> findCourses(String type, String query) {
        return catalogMapper.findCourses(normalizeFilter(type), normalizeQuery(query));
    }

    public List<ExerciseView> findExercises(String bodyPart, String query) {
        return catalogMapper.findExercises(normalizeFilter(bodyPart), normalizeQuery(query));
    }

    public Optional<CourseDetailView> findCourse(long id) {
        CatalogMapper.CourseDetailRow row = catalogMapper.findCourse(id);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new CourseDetailView(
            row.id(), row.title(), row.type(), row.durationMinutes(), row.level(),
            row.equipment(), row.summary(), row.coverImage(), row.videoUrl(), row.videoCoverImage(),
            row.videoDurationSeconds(), row.viewCount(), catalogMapper.findCourseExercises(row.id())
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

    private String normalizeQuery(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
