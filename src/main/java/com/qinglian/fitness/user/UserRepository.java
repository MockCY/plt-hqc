package com.qinglian.fitness.user;

import com.qinglian.fitness.mapper.UserMapper;
import com.qinglian.fitness.common.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class UserRepository {

    private final UserMapper userMapper;
    private final com.qinglian.fitness.sensor.SensorOwnershipService sensors;

    public UserRepository(UserMapper userMapper,com.qinglian.fitness.sensor.SensorOwnershipService sensors) {
        this.userMapper = userMapper; this.sensors=sensors;
    }

    public Optional<User> findByOpenId(String openId) {
        return Optional.ofNullable(userMapper.findByOpenId(openId));
    }

    public Optional<User> findById(long id) {
        return Optional.ofNullable(userMapper.findById(id));
    }

    public User create(String openId, String unionId) {
        UserInsert newUser = new UserInsert(openId, unionId);
        int insertedRows = userMapper.insert(newUser);

        if (insertedRows != 1 || newUser.getId() == null) {
            throw new IllegalStateException("创建用户失败");
        }

        User createdUser = userMapper.findById(newUser.getId());
        if (createdUser == null) {
            throw new IllegalStateException("用户创建成功但重新查询失败");
        }

        return createdUser;
    }

    public User findOrCreate(String openId, String unionId) {
        User existingUser = userMapper.findByOpenId(openId);
        if (existingUser != null) {
            return existingUser;
        }

        return create(openId, unionId);
    }

    public User bindPhone(long userId, String phone, String countryCode) {
        if (userMapper.countByPhoneExcept(phone, userId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_PHONE_ALREADY_BOUND", "该手机号已绑定其他账号");
        }

        int updatedRows;
        try {
            updatedRows = userMapper.bindPhone(userId, phone, countryCode);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_PHONE_ALREADY_BOUND", "该手机号已绑定其他账号");
        }
        if (updatedRows != 1) {
            throw new IllegalStateException("绑定手机号失败，用户不存在");
        }

        User updatedUser = userMapper.findById(userId);
        if (updatedUser == null) {
            throw new IllegalStateException("手机号绑定成功但重新查询用户失败");
        }

        return updatedUser;
    }

    @Transactional
    public void deleteById(long userId) {
        sensors.releaseUser(userId);
        userMapper.recordDeviceUnbinding(userId);
        userMapper.deleteCustomCourses(userId);
        userMapper.deleteFavorites(userId);
        userMapper.deletePlanSelections(userId);
        userMapper.deleteFeedback(userId);
        userMapper.deleteCampaignCheckins(userId);
        userMapper.deleteSettings(userId);
        userMapper.deleteWorkoutRecords(userId);
        userMapper.deleteSessions(userId);
        userMapper.deleteById(userId);
    }
}
