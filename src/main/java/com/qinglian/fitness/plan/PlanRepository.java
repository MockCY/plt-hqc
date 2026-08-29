package com.qinglian.fitness.plan;

import com.qinglian.fitness.plan.PlanDtos.PlanItemView;
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
        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        List<PlanItemView> items = planMapper.findPlanItems(
            userId, weekStart, weekStart.plusDays(7), header.id()
        ).stream().map(item -> new PlanItemView(
            item.id(), item.dayOffset(), weekStart.plusDays(item.dayOffset()), item.courseId(),
            item.courseTitle(), item.durationMinutes(), item.status()
        )).toList();
        return Optional.of(new PlanView(
            header.id(), header.title(), header.weekNumber(), header.sessionsPerWeek(),
            header.description(), items
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

}
