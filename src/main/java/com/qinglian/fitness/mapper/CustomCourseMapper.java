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

    List<CustomCourseExerciseRow> findExerciseConfigs(@Param("courseId") long courseId);

    int create(NewCustomCourse course);

    int addExercise(
        @Param("courseId") long courseId,
        @Param("exerciseId") long exerciseId,
        @Param("sortOrder") int sortOrder,
        @Param("setCount") int setCount,
        @Param("repetitions") int repetitions
    );

    int delete(@Param("id") long id, @Param("userId") long userId);

    record CustomCourseRow(
        long id,
        String title,
        int durationMinutes,
        String summary,
        String goal,
        String level,
        int warmupMinutes,
        int restSeconds,
        Instant createdAt
    ) {
    }

    record CustomCourseExerciseRow(long exerciseId, int setCount, int repetitions) {
    }

    final class NewCustomCourse {
        private Long id;
        private final long userId;
        private final String title;
        private final int durationMinutes;
        private final String summary;
        private final String goal;
        private final String level;
        private final int warmupMinutes;
        private final int restSeconds;

        public NewCustomCourse(
            long userId,
            String title,
            int durationMinutes,
            String summary,
            String goal,
            String level,
            int warmupMinutes,
            int restSeconds
        ) {
            this.userId = userId;
            this.title = title;
            this.durationMinutes = durationMinutes;
            this.summary = summary;
            this.goal = goal;
            this.level = level;
            this.warmupMinutes = warmupMinutes;
            this.restSeconds = restSeconds;
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

        public String getGoal() {
            return goal;
        }

        public String getLevel() {
            return level;
        }

        public int getWarmupMinutes() {
            return warmupMinutes;
        }

        public int getRestSeconds() {
            return restSeconds;
        }
    }
}
