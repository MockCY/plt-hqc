package com.qinglian.fitness.customcourse;

import com.qinglian.fitness.customcourse.CustomCourseDtos.CreateCustomCourseRequest;
import com.qinglian.fitness.customcourse.CustomCourseDtos.CustomCourseView;
import com.qinglian.fitness.mapper.CustomCourseMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class CustomCourseRepository {

    private final CustomCourseMapper customCourseMapper;

    public CustomCourseRepository(CustomCourseMapper customCourseMapper) {
        this.customCourseMapper = customCourseMapper;
    }

    public List<CustomCourseView> findAll(long userId) {
        return customCourseMapper.findAll(userId).stream().map(this::view).toList();
    }

    @Transactional
    public CustomCourseView create(long userId, CreateCustomCourseRequest request) {
        CustomCourseMapper.NewCustomCourse course = new CustomCourseMapper.NewCustomCourse(
            userId, request.title().trim(), request.durationMinutes(), blankToNull(request.summary())
        );
        customCourseMapper.create(course);
        int sortOrder = 10;
        for (Long exerciseId : request.exerciseIds().stream().distinct().toList()) {
            customCourseMapper.addExercise(course.getId(), exerciseId, sortOrder);
            sortOrder += 10;
        }
        return findById(course.getId(), userId);
    }

    public void delete(long id, long userId) {
        customCourseMapper.delete(id, userId);
    }

    private CustomCourseView findById(long id, long userId) {
        CustomCourseMapper.CustomCourseRow row = customCourseMapper.findById(id, userId);
        if (row == null) {
            throw new IllegalStateException("Custom course was not found after creation");
        }
        return view(row);
    }

    private CustomCourseView view(CustomCourseMapper.CustomCourseRow row) {
        List<Long> exerciseIds = customCourseMapper.findExerciseIds(row.id());
        return new CustomCourseView(
            row.id(), row.title(), row.durationMinutes(), row.summary(), exerciseIds, row.createdAt()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
