package com.qinglian.fitness.campaign;

import com.qinglian.fitness.campaign.CampaignDtos.CampaignStatus;
import com.qinglian.fitness.campaign.CampaignDtos.CheckinResult;
import com.qinglian.fitness.mapper.CampaignMapper;
import com.qinglian.fitness.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public class CampaignRepository {

    private final CampaignMapper campaignMapper;

    public CampaignRepository(CampaignMapper campaignMapper) {
        this.campaignMapper = campaignMapper;
    }

    public CampaignStatus status(long userId, String code) {
        LocalDate today = LocalDate.now();
        CampaignMapper.CampaignContentRow content = campaignMapper.findOpenCampaign(code, today);
        if (content == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CAMPAIGN_NOT_FOUND", "训练营不存在或暂未开放");
        }
        return new CampaignStatus(
            code,
            content.title(),
            content.rulesText().lines().filter(line -> !line.isBlank()).toList(),
            countForDate(userId, code, today) > 0,
            countAll(userId, code),
            today
        );
    }

    @Transactional
    public CheckinResult checkin(long userId, String code) {
        status(userId, code);
        LocalDate today = LocalDate.now();
        if (countForDate(userId, code, today) == 0) {
            campaignMapper.createCheckin(userId, code, today);
        }
        return new CheckinResult(true, countAll(userId, code), today);
    }

    private int countForDate(long userId, String code, LocalDate date) {
        return campaignMapper.countForDate(userId, code, date);
    }

    private int countAll(long userId, String code) {
        return campaignMapper.countAll(userId, code);
    }
}
