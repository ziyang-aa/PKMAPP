package com.example.pkmapp.server.security;

import com.example.pkmapp.server.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int MAX_RATE_BUCKETS = 4096;
    private static final int AUTH_RATE_LIMIT = 30;
    private static final int ERROR_LOG_RATE_LIMIT = 15;
    private static final int ADMIN_RATE_LIMIT = 60;

    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;
    private final byte[] adminTokenBytes;
    private final ConcurrentHashMap<String, RateBucket> rateBuckets = new ConcurrentHashMap<>();

    public AuthInterceptor(
            JwtService jwtService,
            JdbcTemplate jdbcTemplate,
            @Value("${pkm.admin.token}") String adminToken
    ) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.adminTokenBytes = (adminToken == null || adminToken.isBlank())
                ? new byte[0]
                : adminToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();

        enforceRateLimit(request, path);

        if (path.startsWith("/api/admin/")) {
            String supplied = request.getHeader("X-Admin-Token");
            if (adminTokenBytes.length == 0
                    || supplied == null
                    || !MessageDigest.isEqual(adminTokenBytes, supplied.getBytes(StandardCharsets.UTF_8))) {
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

    private void enforceRateLimit(HttpServletRequest request, String path) {
        int limit;
        String bucketScope;
        if ("/api/v1/auth/login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/refresh".equals(path)) {
            limit = AUTH_RATE_LIMIT;
            bucketScope = "auth";
        } else if ("/api/v1/logs/client-error".equals(path)) {
            limit = ERROR_LOG_RATE_LIMIT;
            bucketScope = "errlog";
        } else if (path.startsWith("/api/admin/")) {
            limit = ADMIN_RATE_LIMIT;
            bucketScope = "admin";
        } else {
            return;
        }

        long now = System.currentTimeMillis();
        if (rateBuckets.size() >= MAX_RATE_BUCKETS) {
            rateBuckets.entrySet().removeIf(e -> now - e.getValue().windowStartMs >= RATE_WINDOW_MS);
            if (rateBuckets.size() >= MAX_RATE_BUCKETS) {
                rateBuckets.clear();
            }
        }

        String key = bucketScope + ":" + resolveClientIp(request);
        RateBucket bucket = rateBuckets.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs >= RATE_WINDOW_MS) {
                return new RateBucket(now);
            }
            return existing;
        });
        if (bucket.count.incrementAndGet() > limit) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "请求过于频繁，请稍后再试");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            return "unknown";
        }
        if (isTrustedProxy(remote)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                int comma = xff.indexOf(',');
                String first = (comma >= 0 ? xff.substring(0, comma) : xff).trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return remote;
    }

    private boolean isTrustedProxy(String ip) {
        return "127.0.0.1".equals(ip)
                || "::1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("10.")
                || ip.startsWith("172.");
    }

    private static final class RateBucket {
        final long windowStartMs;
        final AtomicInteger count = new AtomicInteger(0);

        RateBucket(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
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
