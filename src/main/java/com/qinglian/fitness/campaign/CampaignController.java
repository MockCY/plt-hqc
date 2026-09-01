package com.qinglian.fitness.campaign;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.campaign.CampaignDtos.CampaignStatus;
import com.qinglian.fitness.campaign.CampaignDtos.CheckinResult;
import com.qinglian.fitness.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/campaigns")
public class CampaignController {

    private final CampaignRepository repository;

    public CampaignController(CampaignRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{code}")
    public CampaignStatus status(HttpServletRequest request, @PathVariable String code) {
        return repository.status(CurrentUser.id(request), normalize(code));
    }

    @PostMapping("/{code}/checkins")
    public CheckinResult checkin(HttpServletRequest request, @PathVariable String code) {
        return repository.checkin(CurrentUser.id(request), normalize(code));
    }

    private String normalize(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!normalized.matches("[A-Z0-9_]{2,40}")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "训练营不存在");
        }
        return normalized;
    }
}
