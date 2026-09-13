package com.qinglian.fitness.user;

import com.qinglian.fitness.auth.AuthDtos.UserView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileView(UserView user, UserSettings settings) {
    }

    public static final class UpdateProfileRequest {
        @Size(max = 40)
        private String nickname;

        @Size(max = 500)
        private String avatarUrl;

        @DecimalMin(value = "50", message = "身高需在 50 至 250 厘米之间")
        @DecimalMax(value = "250", message = "身高需在 50 至 250 厘米之间")
        @Digits(integer = 3, fraction = 1, message = "身高最多保留 1 位小数")
        private BigDecimal heightCm;

        @DecimalMin(value = "20", message = "体重需在 20 至 300 千克之间")
        @DecimalMax(value = "300", message = "体重需在 20 至 300 千克之间")
        @Digits(integer = 3, fraction = 1, message = "体重最多保留 1 位小数")
        private BigDecimal weightKg;

        private boolean heightCmProvided;
        private boolean weightKgProvided;

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }

        public String getAvatarUrl() { return avatarUrl; }
        public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

        public BigDecimal getHeightCm() { return heightCm; }

        @JsonSetter("heightCm")
        public void setHeightCm(BigDecimal heightCm) {
            this.heightCm = heightCm;
            this.heightCmProvided = true;
        }

        public BigDecimal getWeightKg() { return weightKg; }

        @JsonSetter("weightKg")
        public void setWeightKg(BigDecimal weightKg) {
            this.weightKg = weightKg;
            this.weightKgProvided = true;
        }

        // Track explicit null separately so older clients cannot erase measurements.
        @JsonIgnore
        public boolean isHeightCmProvided() { return heightCmProvided; }

        @JsonIgnore
        public boolean isWeightKgProvided() { return weightKgProvided; }
    }

    public record UpdateSettingsRequest(Boolean reminderEnabled, Boolean soundEnabled) {
    }

    public record UserSettings(boolean reminderEnabled, boolean soundEnabled) {
    }
}
