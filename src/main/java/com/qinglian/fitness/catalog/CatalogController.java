package com.qinglian.fitness.catalog;

import com.qinglian.fitness.catalog.CatalogDtos.CourseView;
import com.qinglian.fitness.catalog.CatalogDtos.CourseDetailView;
import com.qinglian.fitness.catalog.CatalogDtos.ExerciseView;
import com.qinglian.fitness.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogRepository repository;

    public CatalogController(CatalogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/courses")
    public List<CourseView> courses(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String query
    ) {
        return repository.findCourses(type, query);
    }

    @GetMapping("/exercises")
    public List<ExerciseView> exercises(
        @RequestParam(required = false) String bodyPart,
        @RequestParam(required = false) String query
    ) {
        return repository.findExercises(bodyPart, query);
    }

    @GetMapping("/courses/{id}")
    public CourseDetailView course(@PathVariable long id) {
        return repository.findCourse(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "课程不存在"));
    }
}
