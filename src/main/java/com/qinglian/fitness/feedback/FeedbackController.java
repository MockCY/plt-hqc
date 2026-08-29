package com.qinglian.fitness.feedback;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.feedback.FeedbackDtos.CreateFeedbackRequest;
import com.qinglian.fitness.feedback.FeedbackDtos.FeedbackView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackRepository repository;

    public FeedbackController(FeedbackRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<FeedbackView> all(HttpServletRequest request) {
        return repository.findAll(CurrentUser.id(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackView create(HttpServletRequest request, @Valid @RequestBody CreateFeedbackRequest body) {
        return repository.create(CurrentUser.id(request), body);
    }
}
