package com.qinglian.fitness.plan;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.plan.PlanDtos.PlanView;
import com.qinglian.fitness.plan.PlanDtos.PlanSelection;
import com.qinglian.fitness.plan.PlanDtos.PlanSummary;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<PlanView> current(HttpServletRequest request) {
        return repository.currentPlan(CurrentUser.id(request))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/catalog")
    public List<PlanSummary> catalog() {
        return repository.catalog();
    }

    @GetMapping("/detail/{id}")
    public PlanView detail(@PathVariable long id) {
        return repository.detail(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "训练计划不存在"));
    }

    @PutMapping("/{id}/select")
    public PlanSelection select(HttpServletRequest request, @PathVariable long id) {
        return repository.select(CurrentUser.id(request), id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "训练计划不存在"));
    }
}
