package com.qinglian.fitness.mapper;

import com.qinglian.fitness.user.ProfileDtos.UserSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;

@Mapper
public interface ProfileMapper {

    UserSettings findSettings(@Param("userId") long userId);

    int updateProfile(
        @Param("userId") long userId,
        @Param("nickname") String nickname,
        @Param("avatarUrl") String avatarUrl,
        @Param("heightCmProvided") boolean heightCmProvided,
        @Param("heightCm") BigDecimal heightCm,
        @Param("weightKgProvided") boolean weightKgProvided,
        @Param("weightKg") BigDecimal weightKg
    );

    int updateSettings(
        @Param("userId") long userId,
        @Param("reminderEnabled") Boolean reminderEnabled,
        @Param("soundEnabled") Boolean soundEnabled
    );

    int ensureSettings(@Param("userId") long userId);
}
