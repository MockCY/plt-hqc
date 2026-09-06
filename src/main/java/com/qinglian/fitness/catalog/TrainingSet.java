package com.qinglian.fitness.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TrainingSet(
    @NotNull @Pattern(regexp = "双侧|左侧|右侧") String side,
    @Min(1) @Max(3600) Integer durationSeconds,
    @NotNull @Min(1) @Max(999) Integer repetitions,
    @Min(0) @Max(12) int springCount
) {
}
