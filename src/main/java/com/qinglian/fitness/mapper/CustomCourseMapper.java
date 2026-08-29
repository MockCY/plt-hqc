package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface CustomCourseMapper {

    List<CustomCourseRow> findAll(@Param("userId") long userId);

    CustomCourseRow findById(@Param("id") long id, @Param("userId") long userId);

    List<Long> findExerciseIds(@Param("courseId") long courseId);

    int create(NewCustomCourse course);

    int addExercise(
        @Param("courseId") long courseId,
        @Param("exerciseId") long exerciseId,
        @Param("sortOrder") int sortOrder
    );

    int delete(@Param("id") long id, @Param("userId") long userId);

    record CustomCourseRow(long id, String title, int durationMinutes, String summary, Instant createdAt) {
    }

    final class NewCustomCourse {
        private Long id;
        private final long userId;
        private final String title;
        private final int durationMinutes;
        private final String summary;

        public NewCustomCourse(long userId, String title, int durationMinutes, String summary) {
            this.userId = userId;
            this.title = title;
            this.durationMinutes = durationMinutes;
            this.summary = summary;
        }

        public long getId() {
            if (id == null) {
                throw new IllegalStateException("Custom course id was not generated");
            }
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public long getUserId() {
            return userId;
        }

        public String getTitle() {
            return title;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public String getSummary() {
            return summary;
        }
    }
}
