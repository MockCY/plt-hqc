package com.qinglian.fitness.user;

import com.qinglian.fitness.user.ProfileDtos.UpdateProfileRequest;
import com.qinglian.fitness.user.ProfileDtos.UpdateSettingsRequest;
import com.qinglian.fitness.user.ProfileDtos.UserSettings;
import com.qinglian.fitness.mapper.ProfileMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {

    private final ProfileMapper profileMapper;
    private final UserRepository userRepository;

    public ProfileRepository(ProfileMapper profileMapper, UserRepository userRepository) {
        this.profileMapper = profileMapper;
        this.userRepository = userRepository;
    }

    public UserSettings settings(long userId) {
        ensureSettings(userId);
        return profileMapper.findSettings(userId);
    }

    public User updateProfile(long userId, UpdateProfileRequest request) {
        profileMapper.updateProfile(
            userId, blankToNull(request.nickname()), blankToNull(request.avatarUrl())
        );
        return userRepository.findById(userId).orElseThrow();
    }

    public UserSettings updateSettings(long userId, UpdateSettingsRequest request) {
        ensureSettings(userId);
        profileMapper.updateSettings(userId, request.reminderEnabled(), request.soundEnabled());
        return settings(userId);
    }

    private void ensureSettings(long userId) {
        profileMapper.ensureSettings(userId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
