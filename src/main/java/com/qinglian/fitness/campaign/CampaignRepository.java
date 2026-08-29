package com.qinglian.fitness.campaign;

import com.qinglian.fitness.campaign.CampaignDtos.CampaignStatus;
import com.qinglian.fitness.campaign.CampaignDtos.CheckinResult;
import com.qinglian.fitness.mapper.CampaignMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public class CampaignRepository {

    private static final List<String> RULES = List.of(
        "完成当日任意一节训练后即可打卡",
        "每天最多记录一次，连续打卡会保留在个人记录中",
        "训练过程中请量力而行，身体不适时立即停止"
    );

    private final CampaignMapper campaignMapper;

    public CampaignRepository(CampaignMapper campaignMapper) {
        this.campaignMapper = campaignMapper;
    }

    public CampaignStatus status(long userId, String code) {
        LocalDate today = LocalDate.now();
        return new CampaignStatus(
            code,
            "8月马甲线训练营",
            RULES,
            countForDate(userId, code, today) > 0,
            countAll(userId, code),
            today
        );
    }

    @Transactional
    public CheckinResult checkin(long userId, String code) {
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
