package com.qinglian.fitness.customcourse;

import com.qinglian.fitness.customcourse.CustomCourseDtos.CreateCustomCourseRequest;
import com.qinglian.fitness.customcourse.CustomCourseDtos.CustomExerciseRequest;
import com.qinglian.fitness.customcourse.CustomCourseDtos.CustomExerciseView;
import com.qinglian.fitness.customcourse.CustomCourseDtos.CustomCourseView;
import com.qinglian.fitness.common.LiteralSearch;
import com.qinglian.fitness.mapper.CustomCourseMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 组织自定义训练的归属查询和保存，内容筛选由数据库完成。 */
@Repository
public class CustomCourseRepository {

    private final CustomCourseMapper customCourseMapper;

    public CustomCourseRepository(CustomCourseMapper customCourseMapper) {
        this.customCourseMapper = customCourseMapper;
    }

    /** 搜索当前用户自己的训练，关键词与公开内容复用同一长度和字面匹配规则。 */
    public List<CustomCourseView> findAll(long userId, String query) {
        return customCourseMapper.findAll(userId, LiteralSearch.containsPattern(query)).stream().map(this::view).toList();
    }

    @Transactional
    public CustomCourseView create(long userId, CreateCustomCourseRequest request) {
        CustomCourseMapper.NewCustomCourse course = new CustomCourseMapper.NewCustomCourse(
            userId,
            request.title().trim(),
            request.durationMinutes(),
            blankToNull(request.summary()),
            supported(request.goal(), List.of("核心强化", "体态改善", "塑形减脂", "放松舒缓"), "核心强化"),
            supported(request.level(), List.of("初级", "中级", "高级"), "初级"),
            request.warmupMinutes() == null ? 5 : request.warmupMinutes(),
            request.restSeconds() == null ? 30 : request.restSeconds()
        );
        customCourseMapper.create(course);
        Map<Long, CustomExerciseRequest> configs = request.exerciseConfigs() == null ? Map.of() : request.exerciseConfigs().stream()
            .collect(Collectors.toMap(CustomExerciseRequest::exerciseId, Function.identity(), (first, ignored) -> first));
        int sortOrder = 10;
        for (Long exerciseId : request.exerciseIds().stream().distinct().toList()) {
            CustomExerciseRequest config = configs.get(exerciseId);
            customCourseMapper.addExercise(
                course.getId(),
                exerciseId,
                sortOrder,
                config == null ? 3 : config.setCount(),
                config == null ? 10 : config.repetitions()
            );
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
        List<CustomExerciseView> exerciseConfigs = customCourseMapper.findExerciseConfigs(row.id()).stream()
            .map(item -> new CustomExerciseView(item.exerciseId(), item.setCount(), item.repetitions()))
            .toList();
        return new CustomCourseView(
            row.id(), row.title(), row.durationMinutes(), row.summary(), exerciseIds,
            row.goal(), row.level(), row.warmupMinutes(), row.restSeconds(), exerciseConfigs, row.createdAt()
        );
    }

    private String supported(String value, List<String> options, String fallback) {
        String normalized = blankToNull(value);
        return normalized != null && options.contains(normalized) ? normalized : fallback;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
