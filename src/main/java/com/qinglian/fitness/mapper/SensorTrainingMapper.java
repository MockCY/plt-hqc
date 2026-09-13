package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SensorTrainingMapper {

    TrainingTotals findTotals(
            @Param("userId") long userId,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);

    List<TrainingDateRange> findTrainingDateRanges(@Param("userId") long userId);

    CalorieProfile findCalorieProfile(@Param("userId") long userId);

    record CalorieProfile(BigDecimal weightKg, boolean weightDefaulted) {}

    record TrainingTotals(
            long completedCount,
            long trainingDurationMs,
            long repetitionCount,
            long todayTrainingDurationMs,
            BigDecimal totalEstimatedCalories,
            BigDecimal todayEstimatedCalories) {}

    record TrainingDateRange(LocalDate firstDay, LocalDate lastDay) {}
}
