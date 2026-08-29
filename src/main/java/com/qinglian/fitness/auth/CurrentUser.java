package com.qinglian.fitness.auth;

import jakarta.servlet.http.HttpServletRequest;

public final class CurrentUser {

    public static final String USER_ID_ATTRIBUTE = CurrentUser.class.getName() + ".userId";
    public static final String RAW_TOKEN_ATTRIBUTE = CurrentUser.class.getName() + ".rawToken";

    private CurrentUser() {
    }

    public static long id(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }
        throw new IllegalStateException("Authenticated user is missing from request");
    }

    public static String rawToken(HttpServletRequest request) {
        Object value = request.getAttribute(RAW_TOKEN_ATTRIBUTE);
        return value instanceof String token ? token : null;
    }
}
