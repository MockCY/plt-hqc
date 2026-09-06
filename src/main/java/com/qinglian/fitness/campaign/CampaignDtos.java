package com.qinglian.fitness.campaign;

import java.time.LocalDate;
import java.util.List;

public final class CampaignDtos {

    private CampaignDtos() {
    }

    public record CampaignStatus(
        String code,
        String title,
        List<String> rules,
        boolean checkedInToday,
        int totalCheckins,
        LocalDate today
    ) {
    }

    public record CheckinResult(boolean checkedInToday, int totalCheckins, LocalDate checkinDate) {
    }

    public record CampaignSummary(String code, String title, List<String> rules, LocalDate startDate, LocalDate endDate) {
    }
}
