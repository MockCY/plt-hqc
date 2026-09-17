package com.qinglian.fitness.plan;

import com.qinglian.fitness.plan.PlanDtos.PlanDayCompletion;
import com.qinglian.fitness.plan.PlanDtos.PlanDayView;
import com.qinglian.fitness.plan.PlanDtos.PlanExerciseView;
import com.qinglian.fitness.plan.PlanDtos.PlanSelection;
import com.qinglian.fitness.plan.PlanDtos.PlanSummary;
import com.qinglian.fitness.plan.PlanDtos.PlanView;
import com.qinglian.fitness.mapper.PlanMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class PlanRepository {

    private final PlanMapper planMapper;

    public PlanRepository(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    public Optional<PlanView> currentPlan(long userId) {
        Long selectedPlanId = planMapper.selectedPlanId(userId);
        PlanMapper.PlanHeader header = planMapper.findPlanHeader(selectedPlanId);
        if (header == null) {
            return Optional.empty();
        }
        LocalDate startDate = LocalDate.now();
        List<PlanDayView> days = planDays(userId, header.id(), startDate);
        return Optional.of(new PlanView(
            header.id(), header.title(), header.weekNumber(), header.sessionsPerWeek(), header.cycleDays(),
            header.description(), header.subtitle(), header.coverImage(), header.detailImage(), header.homeImage(), header.level(), header.trainingScene(),
            header.sessionMinutes(), header.benefitOne(), header.benefitTwo(), header.benefitThree(), days
        ));
    }

    public Optional<PlanView> detail(long planId) {
        PlanMapper.PlanHeader header = planMapper.findPlanHeader(planId);
        if (header == null) return Optional.empty();
        List<PlanDayView> days = planDays(0L, header.id(), LocalDate.now());
        return Optional.of(new PlanView(
            header.id(), header.title(), header.weekNumber(), header.sessionsPerWeek(), header.cycleDays(), header.description(),
            header.subtitle(), header.coverImage(), header.detailImage(), header.homeImage(), header.level(), header.trainingScene(), header.sessionMinutes(),
            header.benefitOne(), header.benefitTwo(), header.benefitThree(), days
        ));
    }

    public List<PlanSummary> catalog() {
        return planMapper.catalog();
    }

    @Transactional
    public Optional<PlanSelection> select(long userId, long planId) {
        Optional<PlanSummary> plan = catalog().stream().filter(item -> item.id() == planId).findFirst();
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        int updated = planMapper.updateSelection(userId, planId);
        if (updated == 0) {
            planMapper.createSelection(userId, planId);
        }
        return Optional.of(new PlanSelection(planId, plan.get().title(), true));
    }

    @Transactional
    public Optional<PlanDayCompletion> completeDay(long userId, long planId, int dayNumber) {
        Long selectedPlanId = planMapper.selectedPlanId(userId);
        if (selectedPlanId == null || selectedPlanId != planId || planMapper.planDayExists(planId, dayNumber) == 0) {
            return Optional.empty();
        }
        planMapper.completeDay(userId, planId, dayNumber);
        return Optional.of(new PlanDayCompletion(planId, dayNumber, true));
    }

    private List<PlanDayView> planDays(long userId, long planId, LocalDate startDate) {
        return planMapper.findPlanDays(userId, planId).stream().map(day -> new PlanDayView(
            day.id(), day.dayNumber(), startDate.plusDays(day.dayNumber() - 1L), day.title(), day.durationMinutes(),
            day.status(), planMapper.findPlanDayExercises(day.id()).stream().map(item ->
                new PlanExerciseView(
                    item.id(), item.exerciseId(), item.exerciseName(), item.repetitions(), item.setCount(), item.sortOrder()
                )
            ).toList()
        )).toList();
    }

}
