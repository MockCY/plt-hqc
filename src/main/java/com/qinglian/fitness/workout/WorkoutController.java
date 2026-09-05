package com.qinglian.fitness.workout;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.workout.WorkoutDtos.CreateWorkoutRequest;
import com.qinglian.fitness.workout.WorkoutDtos.WorkoutStats;
import com.qinglian.fitness.workout.WorkoutDtos.WorkoutView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workout-records")
public class WorkoutController {

    private final WorkoutRepository repository;

    public WorkoutController(WorkoutRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutView create(
        HttpServletRequest servletRequest,
        @Valid @RequestBody CreateWorkoutRequest request
    ) {
        if ((request.courseId() == null) == (request.customCourseId() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORKOUT_COURSE", "课程和自定义课程必须且只能选择一个");
        }
        return repository.create(CurrentUser.id(servletRequest), request);
    }

    @GetMapping
    public List<WorkoutView> recent(
        HttpServletRequest request,
        @RequestParam(defaultValue = "20") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return repository.findRecent(CurrentUser.id(request), safeLimit);
    }

    @GetMapping("/stats")
    public WorkoutStats stats(HttpServletRequest request) {
        return repository.stats(CurrentUser.id(request));
    }

    @PostMapping("/detail-visits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordDetailVisit(HttpServletRequest request,
        @Valid @RequestBody WorkoutDtos.DetailVisitRequest visit) {
        repository.recordDetailVisit(CurrentUser.id(request), visit);
    }
}
