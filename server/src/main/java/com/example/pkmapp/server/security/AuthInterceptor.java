package com.example.pkmapp.server.security;

import com.example.pkmapp.server.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final String adminToken;

    public AuthInterceptor(
            JwtService jwtService,
            JdbcTemplate jdbcTemplate,
            @Value("${pkm.admin.token}") String adminToken
    ) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.adminToken = adminToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();

        if (path.startsWith("/api/admin/")) {
            String supplied = request.getHeader("X-Admin-Token");
            if (supplied == null || !supplied.equals(adminToken)) {
                throw ApiException.forbidden("ADMIN_FORBIDDEN", "管理员凭证无效");
            }
            return true;
        }

        String token = extractBearer(request);
        if (token != null) {
            String userId = jwtService.verifyAccessTokenAndGetUserId(token);
            if (userId != null) {
                Integer status = jdbcTemplate.query(
                        "SELECT status FROM sys_users WHERE id = ?",
                        rs -> rs.next() ? rs.getInt(1) : null,
                        userId
                );
                if (status != null && status == 1) {
                    request.setAttribute(AuthContext.ATTR_USER_ID, userId);
                }
            }
        }

        if (isPublicApiPath(path)) {
            return true;
        }

        if (AuthContext.optionalUserId(request) == null) {
            throw ApiException.unauthorized("UNAUTHORIZED", "请先登录账号");
        }
        return true;
    }

    private boolean isPublicApiPath(String path) {
        return "/api/v1/health".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/refresh".equals(path)
                || "/api/v1/logs/client-error".equals(path);
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = trimmed.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
