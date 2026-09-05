package com.qinglian.fitness.mapper;

import com.qinglian.fitness.user.User;
import com.qinglian.fitness.user.UserInsert;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByOpenId(@Param("openId") String openId);

    User findById(@Param("id") long id);

    int insert(UserInsert user);

    long countByPhoneExcept(@Param("phone") String phone, @Param("userId") long userId);

    int bindPhone(
        @Param("userId") long userId,
        @Param("phone") String phone,
        @Param("countryCode") String countryCode
    );

    int deleteCustomCourses(@Param("userId") long userId);

    int deleteFavorites(@Param("userId") long userId);

    int deletePlanSelections(@Param("userId") long userId);

    int deleteFeedback(@Param("userId") long userId);

    int deleteCampaignCheckins(@Param("userId") long userId);

    int deleteSettings(@Param("userId") long userId);

    int deleteWorkoutRecords(@Param("userId") long userId);

    int deleteSessions(@Param("userId") long userId);

    int deleteById(@Param("userId") long userId);
    int recordDeviceUnbinding(@Param("userId") long userId);
}
