package com.qinglian.fitness.mapper;

import com.qinglian.fitness.user.ProfileDtos.UserSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProfileMapper {

    UserSettings findSettings(@Param("userId") long userId);

    int updateProfile(
        @Param("userId") long userId,
        @Param("nickname") String nickname,
        @Param("avatarUrl") String avatarUrl
    );

    int updateSettings(
        @Param("userId") long userId,
        @Param("reminderEnabled") Boolean reminderEnabled,
        @Param("soundEnabled") Boolean soundEnabled
    );

    int ensureSettings(@Param("userId") long userId);
}
