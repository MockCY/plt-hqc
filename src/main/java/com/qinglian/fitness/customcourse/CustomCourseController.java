package com.qinglian.fitness.customcourse;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.customcourse.CustomCourseDtos.CreateCustomCourseRequest;
import com.qinglian.fitness.customcourse.CustomCourseDtos.CustomCourseView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/custom-courses")
public class CustomCourseController {

    private final CustomCourseRepository repository;

    public CustomCourseController(CustomCourseRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CustomCourseView> all(HttpServletRequest request) {
        return repository.findAll(CurrentUser.id(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomCourseView create(HttpServletRequest request, @Valid @RequestBody CreateCustomCourseRequest body) {
        return repository.create(CurrentUser.id(request), body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable long id) {
        repository.delete(id, CurrentUser.id(request));
    }
}
