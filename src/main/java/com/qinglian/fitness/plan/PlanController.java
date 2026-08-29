package com.qinglian.fitness.plan;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.plan.PlanDtos.PlanView;
import com.qinglian.fitness.plan.PlanDtos.PlanSelection;
import com.qinglian.fitness.plan.PlanDtos.PlanSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanRepository repository;

    public PlanController(PlanRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/current")
    public PlanView current(HttpServletRequest request) {
        return repository.currentPlan(CurrentUser.id(request))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "当前没有可用训练计划"));
    }

    @GetMapping("/catalog")
    public List<PlanSummary> catalog() {
        return repository.catalog();
    }

    @PutMapping("/{id}/select")
    public PlanSelection select(HttpServletRequest request, @PathVariable long id) {
        return repository.select(CurrentUser.id(request), id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "训练计划不存在"));
    }
}
