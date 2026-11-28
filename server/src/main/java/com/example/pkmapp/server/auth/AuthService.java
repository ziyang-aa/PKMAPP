package com.example.pkmapp.server.auth;

import com.example.pkmapp.server.common.ApiException;
import com.example.pkmapp.server.security.JwtService;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;

    public AuthService(JdbcTemplate jdbcTemplate, JwtService jwtService) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
    }

    public record UserRow(
            String id,
            String phone,
            String email,
            String passwordHash,
            String nickname,
            String avatarUrl,
            String checkedDatesJson,
            int status,
            long createdAt,
            long updatedAt
    ) {
        public String identifier() {
            if (phone != null && !phone.isBlank()) return phone;
            if (email != null && !email.isBlank()) return email;
            return id;
        }

        public Map<String, Object> toPublicMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("identifier", identifier());
            map.put("phone", phone);
            map.put("email", email);
            map.put("nickname", nickname);
            map.put("avatarUrl", avatarUrl);
            map.put("createdAt", createdAt);
            map.put("updatedAt", updatedAt);
            return map;
        }
    }

    @Transactional
    public Map<String, Object> register(String rawIdentifier, String password, String rawNickname, String deviceInfo) {
        NormalizedIdentifier idf = parseIdentifier(rawIdentifier);
        validatePassword(password);
        String nickname = resolveNickname(rawNickname, idf.value());

        UserRow existing = findByIdentifier(idf);
        if (existing != null) {
            throw ApiException.conflict("ACCOUNT_EXISTS", "该手机号或邮箱已注册，请直接登录");
        }

        long now = System.currentTimeMillis();
        String userId = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String passwordHash = jwtService.hashPassword(password);

        jdbcTemplate.update(
                """
                INSERT INTO sys_users (
                    id, phone, email, password_hash, nickname, avatar_url,
                    checked_dates_json, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, '', '[]', 1, ?, ?)
                """,
                userId,
                idf.isEmail() ? null : idf.value(),
                idf.isEmail() ? idf.value() : null,
                passwordHash,
                nickname,
                now,
                now
        );

        UserRow created = new UserRow(
                userId,
                idf.isEmail() ? null : idf.value(),
                idf.isEmail() ? idf.value() : null,
                passwordHash,
                nickname,
                "",
                "[]",
                1,
                now,
                now
        );
        return issueTokenBundle(created, deviceInfo);
    }

    @Transactional
    public Map<String, Object> login(String rawIdentifier, String password, String deviceInfo) {
        NormalizedIdentifier idf = parseIdentifier(rawIdentifier);
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("INVALID_CREDENTIALS", "账号或密码不正确");
        }
        UserRow user = findByIdentifier(idf);
        if (user == null || user.status() != 1) {
            throw ApiException.unauthorized("ACCOUNT_NOT_FOUND", "账号不存在或已注销，请先注册");
        }
        if (!jwtService.verifyPassword(password, user.passwordHash())) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "账号或密码不正确");
        }
        return issueTokenBundle(user, deviceInfo);
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken, String deviceInfo) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "刷新令牌无效，请重新登录");
        }
        String hash = jwtService.sha256Hex(refreshToken.trim());
        long now = System.currentTimeMillis();

        Map<String, Object> tokenRow = jdbcTemplate.query(
                "SELECT id, user_id, expires_at, revoked FROM sys_user_tokens WHERE refresh_token_hash = ?",
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("user_id", rs.getString("user_id"));
                    m.put("expires_at", rs.getLong("expires_at"));
                    m.put("revoked", rs.getInt("revoked"));
                    return m;
                },
                hash
        );
        if (tokenRow == null || ((Integer) tokenRow.get("revoked")) != 0 || ((Long) tokenRow.get("expires_at")) < now) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "登录已过期，请重新登录");
        }

        String userId = (String) tokenRow.get("user_id");
        UserRow user = findById(userId);
        if (user == null || user.status() != 1) {
            throw ApiException.unauthorized("ACCOUNT_DISABLED", "账号不可用");
        }

        jdbcTemplate.update("UPDATE sys_user_tokens SET revoked = 1 WHERE id = ?", tokenRow.get("id"));
        return issueTokenBundle(user, deviceInfo);
    }

    public Map<String, Object> currentUserProfile(String userId) {
        UserRow user = findById(userId);
        if (user == null || user.status() != 1) {
            throw ApiException.notFound("USER_NOT_FOUND", "用户不存在");
        }
        return Map.of("user", user.toPublicMap());
    }

    @Transactional
    public void deleteAccount(String userId) {
        jdbcTemplate.update("DELETE FROM sys_user_tokens WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_transactions WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_borrowing_records WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_ledgers WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_savings_goals WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_custom_categories WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_study_reviews WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_study_records WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_study_tasks WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_study_subjects WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_study_plans WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_study_garden_state WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_todo_items WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM biz_recurring_rules WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM ops_feedbacks WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM sys_users WHERE id = ?", userId);
    }

    private Map<String, Object> issueTokenBundle(UserRow user, String deviceInfo) {
        long now = System.currentTimeMillis();
        String accessToken = jwtService.issueAccessToken(user.id());
        String refreshToken = jwtService.generateRefreshToken();
        String refreshHash = jwtService.sha256Hex(refreshToken);
        long expiresAt = jwtService.refreshExpiresAtMillis(now);

        jdbcTemplate.update(
                """
                INSERT INTO sys_user_tokens (
                    user_id, refresh_token_hash, device_info, expires_at, revoked, created_at
                ) VALUES (?, ?, ?, ?, 0, ?)
                """,
                user.id(),
                refreshHash,
                deviceInfo != null ? deviceInfo.substring(0, Math.min(deviceInfo.length(), 120)) : "",
                expiresAt,
                now
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user.toPublicMap());
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        return data;
    }

    private UserRow findByIdentifier(NormalizedIdentifier idf) {
        String sql = idf.isEmail()
                ? "SELECT * FROM sys_users WHERE email = ?"
                : "SELECT * FROM sys_users WHERE phone = ?";
        return jdbcTemplate.query(sql, rs -> rs.next() ? mapUser(rs) : null, idf.value());
    }

    private UserRow findById(String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM sys_users WHERE id = ?",
                rs -> rs.next() ? mapUser(rs) : null,
                userId
        );
    }

    private UserRow mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserRow(
                rs.getString("id"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getString("checked_dates_json"),
                rs.getInt("status"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private record NormalizedIdentifier(String value, boolean isEmail) {}

    private NormalizedIdentifier parseIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("INVALID_IDENTIFIER", "请输入有效的手机号或邮箱");
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (PHONE_PATTERN.matcher(trimmed).matches()) {
            return new NormalizedIdentifier(trimmed, false);
        }
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            return new NormalizedIdentifier(trimmed, true);
        }
        throw ApiException.badRequest("INVALID_IDENTIFIER", "请输入有效的手机号或邮箱");
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw ApiException.badRequest("INVALID_PASSWORD", "密码长度需在 8 到 64 位之间");
        }
    }

    private String resolveNickname(String rawNickname, String identifier) {
        String trimmed = rawNickname == null ? "" : rawNickname.trim();
        if (!trimmed.isEmpty()) {
            if (trimmed.length() > 20) {
                throw ApiException.badRequest("INVALID_NICKNAME", "昵称最多 20 个字");
            }
            return trimmed;
        }
        if (identifier.contains("@")) {
            String prefix = identifier.substring(0, identifier.indexOf('@'));
            return prefix.length() > 16 ? prefix.substring(0, 16) : prefix;
        }
        return "松果友" + identifier.substring(Math.max(0, identifier.length() - 4));
    }
}
