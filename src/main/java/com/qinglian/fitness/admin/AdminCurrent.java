package com.qinglian.fitness.admin;

import jakarta.servlet.http.HttpServletRequest;

public final class AdminCurrent {

    public static final String ADMIN_ID_ATTRIBUTE = "adminId";
    public static final String ADMIN_USERNAME_ATTRIBUTE = "adminUsername";
    public static final String RAW_TOKEN_ATTRIBUTE = "adminRawToken";

    private AdminCurrent() {
    }

    public static long id(HttpServletRequest request) {
        Object value = request.getAttribute(ADMIN_ID_ATTRIBUTE);
        if (value instanceof Long id) return id;
        throw new IllegalStateException("Admin context is missing");
    }

    public static String username(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(ADMIN_USERNAME_ATTRIBUTE));
    }
}
