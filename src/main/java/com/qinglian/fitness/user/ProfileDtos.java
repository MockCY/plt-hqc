package com.qinglian.fitness.user;

import com.qinglian.fitness.auth.AuthDtos.UserView;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileView(UserView user, UserSettings settings) {
    }

    public record UpdateProfileRequest(
        @Size(max = 40) String nickname,
        @Size(max = 500) String avatarUrl
    ) {
    }

    public record UpdateSettingsRequest(Boolean reminderEnabled, Boolean soundEnabled) {
    }

    public record UserSettings(boolean reminderEnabled, boolean soundEnabled) {
    }
}
