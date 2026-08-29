package com.qinglian.fitness.feedback;

import com.qinglian.fitness.feedback.FeedbackDtos.CreateFeedbackRequest;
import com.qinglian.fitness.feedback.FeedbackDtos.FeedbackView;
import com.qinglian.fitness.mapper.FeedbackMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FeedbackRepository {

    private final FeedbackMapper feedbackMapper;

    public FeedbackRepository(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public List<FeedbackView> findAll(long userId) {
        return feedbackMapper.findAll(userId);
    }

    public FeedbackView create(long userId, CreateFeedbackRequest request) {
        FeedbackMapper.NewFeedback feedback = new FeedbackMapper.NewFeedback(
            userId, request.category().trim(), request.content().trim(), blankToNull(request.contact())
        );
        feedbackMapper.create(feedback);
        FeedbackView created = feedbackMapper.findById(feedback.getId(), userId);
        if (created == null) {
            throw new IllegalStateException("Feedback was not found after creation");
        }
        return created;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
