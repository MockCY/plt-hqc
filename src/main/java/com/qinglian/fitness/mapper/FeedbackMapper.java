package com.qinglian.fitness.mapper;

import com.qinglian.fitness.feedback.FeedbackDtos.FeedbackView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedbackMapper {

    List<FeedbackView> findAll(@Param("userId") long userId);

    FeedbackView findById(@Param("id") long id, @Param("userId") long userId);

    int create(NewFeedback feedback);

    final class NewFeedback {
        private Long id;
        private final long userId;
        private final String category;
        private final String content;
        private final String contact;

        public NewFeedback(long userId, String category, String content, String contact) {
            this.userId = userId;
            this.category = category;
            this.content = content;
            this.contact = contact;
        }

        public long getId() {
            if (id == null) {
                throw new IllegalStateException("Feedback id was not generated");
            }
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getUserId() {
            return userId;
        }

        public String getCategory() {
            return category;
        }

        public String getContent() {
            return content;
        }

        public String getContact() {
            return contact;
        }
    }
}
