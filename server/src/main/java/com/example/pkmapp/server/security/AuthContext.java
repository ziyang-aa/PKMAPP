package com.example.pkmapp.server.security;

import com.example.pkmapp.server.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;

public final class AuthContext {
    public static final String ATTR_USER_ID = "pkm.auth.userId";

    private AuthContext() {}

    public static String requireUserId(HttpServletRequest request) {
        Object val = request.getAttribute(ATTR_USER_ID);
        if (val instanceof String userId && !userId.isBlank()) {
            return userId;
        }
        throw ApiException.unauthorized("UNAUTHORIZED", "登录状态已失效，请重新登录");
    }

    public static String optionalUserId(HttpServletRequest request) {
        Object val = request.getAttribute(ATTR_USER_ID);
        return (val instanceof String userId && !userId.isBlank()) ? userId : null;
    }
}
