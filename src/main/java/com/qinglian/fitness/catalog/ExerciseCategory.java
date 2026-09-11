package com.qinglian.fitness.catalog;

import com.qinglian.fitness.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;

/** Training categories stored in the existing body_part column. */
public enum ExerciseCategory {
    CORE("核心训练", "核心"),
    GLUTES_LEGS("臀腿塑形", "臀腿", "下肢"),
    SHOULDERS_BACK("肩背体态", "肩背"),
    MOBILITY("拉伸协调", "全身", "拉伸");

    public static final String VALID_VALUES_PATTERN = "核心训练|臀腿塑形|肩背体态|拉伸协调";
    public static final String VALIDATION_MESSAGE = "动作类型只能选择核心训练、臀腿塑形、肩背体态或拉伸协调";

    private final String label;
    private final List<String> storedValues;

    ExerciseCategory(String label, String... aliases) {
        this.label = label;
        var values = new java.util.ArrayList<String>();
        values.add(label);
        values.addAll(List.of(aliases));
        this.storedValues = List.copyOf(values);
    }

    /** Keep unrecognized historical values visible until an administrator classifies them. */
    public static String normalizeStored(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return Arrays.stream(values())
            .filter(category -> category.storedValues.contains(trimmed))
            .map(category -> category.label)
            .findFirst().orElse(trimmed);
    }

    /** Include old database values so filters work before and after the data migration. */
    public static List<String> filterValues(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if ("全部".equals(trimmed) || "ALL".equals(trimmed)) return null;
        return Arrays.stream(values())
            .filter(category -> category.storedValues.contains(trimmed))
            .map(category -> category.storedValues)
            .findFirst().orElseThrow(ExerciseCategory::invalidCategory);
    }

    public static void validateWrite(String value) {
        if (Arrays.stream(values()).noneMatch(category -> category.label.equals(value))) {
            throw invalidCategory();
        }
    }

    private static ApiException invalidCategory() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXERCISE_CATEGORY", VALIDATION_MESSAGE);
    }
}
