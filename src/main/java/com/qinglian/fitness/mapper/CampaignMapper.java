package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CampaignMapper {

    CampaignContentRow findOpenCampaign(@Param("code") String code, @Param("date") LocalDate date);

    List<CampaignCatalogRow> findOpenCampaigns(@Param("date") LocalDate date);

    int createCheckin(
        @Param("userId") long userId,
        @Param("code") String code,
        @Param("date") LocalDate date
    );

    int countForDate(
        @Param("userId") long userId,
        @Param("code") String code,
        @Param("date") LocalDate date
    );

    int countAll(@Param("userId") long userId, @Param("code") String code);

    record CampaignContentRow(String title, String rulesText) {
    }

    record CampaignCatalogRow(String code, String title, String rulesText, LocalDate startDate, LocalDate endDate) {
    }
}
