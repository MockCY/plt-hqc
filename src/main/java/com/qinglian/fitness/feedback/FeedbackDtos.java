package com.qinglian.fitness.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    public record CreateFeedbackRequest(
        @NotBlank @Size(max = 30) String category,
        @NotBlank @Size(max = 1000) String content,
        @Size(max = 100) String contact
    ) {
    }

    public record FeedbackView(
        long id,
        String category,
        String content,
        String contact,
        String status,
        Instant createdAt
    ) {
    }
}
