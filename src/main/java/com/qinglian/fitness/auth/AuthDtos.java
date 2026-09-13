package com.qinglian.fitness.auth;

import com.qinglian.fitness.user.User;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String code) {
    }

    public record PhoneLoginRequest(
        @NotBlank String loginCode,
        @NotBlank String phoneCode
    ) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserView user) {
    }

    public record UserView(
        long id,
        String phone,
        boolean phoneBound,
        String nickname,
        String avatarUrl,
        BigDecimal heightCm,
        BigDecimal weightKg
    ) {
        public static UserView from(User user) {
            return new UserView(
                user.id(),
                maskPhone(user.phone()),
                user.phone() != null && !user.phone().isBlank(),
                user.nickname(),
                user.avatarUrl(),
                user.heightCm(),
                user.weightKg()
            );
        }

        private static String maskPhone(String phone) {
            if (phone == null || phone.length() < 7) {
                return phone;
            }
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
    }
}
