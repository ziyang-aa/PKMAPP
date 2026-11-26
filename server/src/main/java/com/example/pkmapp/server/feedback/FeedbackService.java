package com.example.pkmapp.server.feedback;

import com.example.pkmapp.server.common.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
    private final JdbcTemplate jdbcTemplate;

    public FeedbackService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> submit(
            String userId,
            String category,
            String rawContent,
            String contact,
            String appVersion,
            String platform,
            String clientRequestId
    ) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.length() < 5) {
            throw ApiException.badRequest("CONTENT_TOO_SHORT", "请至少输入 5 个字的详细说明");
        }
        if (content.length() > 500) {
            throw ApiException.badRequest("CONTENT_TOO_LONG", "反馈内容请控制在 500 字以内");
        }
        String safeCategory = (category == null || category.isBlank()) ? "other" : category.trim();
        String safeContact = contact == null ? "" : contact.trim();
        if (safeContact.length() > 64) safeContact = safeContact.substring(0, 64);
        String safeVersion = (appVersion == null || appVersion.isBlank()) ? "4.0.0" : appVersion.trim();
        String safePlatform = (platform == null || platform.isBlank()) ? "android" : platform.trim();
        String reqId = (clientRequestId == null || clientRequestId.isBlank())
                ? "fb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : clientRequestId.trim();
        if (reqId.length() > 64) reqId = reqId.substring(0, 64);

        long now = System.currentTimeMillis();
        String id = "fb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        jdbcTemplate.update(
                """
                INSERT INTO ops_feedbacks (
                    id, user_id, platform, category, content, contact, app_version,
                    status, reply, client_request_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', '', ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    updated_at = VALUES(updated_at)
                """,
                id, userId, safePlatform, safeCategory, content, safeContact, safeVersion,
                reqId, now, now
        );

        String resolvedId = jdbcTemplate.queryForObject(
                "SELECT id FROM ops_feedbacks WHERE user_id = ? AND client_request_id = ?",
                String.class,
                userId, reqId
        );
        return Map.of("id", resolvedId != null ? resolvedId : id, "status", "pending");
    }

    public List<Map<String, Object>> listMine(String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM ops_feedbacks WHERE user_id = ? ORDER BY created_at DESC LIMIT 100",
                (rs, i) -> mapFeedback(rs),
                userId
        );
    }

    public void recordClientError(
            String userId,
            String platform,
            String appVersion,
            String deviceInfo,
            String errorCode,
            String stackTrace
    ) {
        String safeTrace = stackTrace == null ? "" : stackTrace;
        if (safeTrace.isBlank()) {
            throw ApiException.badRequest("EMPTY_STACK_TRACE", "错误堆栈不能为空");
        }
        jdbcTemplate.update(
                """
                INSERT INTO ops_client_error_logs (
                    user_id, platform, app_version, device_info, error_code, stack_trace, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                platform == null || platform.isBlank() ? "android" : platform.trim(),
                appVersion == null || appVersion.isBlank() ? "4.0.0" : appVersion.trim(),
                deviceInfo == null ? "" : deviceInfo.trim().substring(0, Math.min(deviceInfo.trim().length(), 128)),
                errorCode == null ? "" : errorCode.trim().substring(0, Math.min(errorCode.trim().length(), 64)),
                safeTrace,
                System.currentTimeMillis()
        );
    }

    public Map<String, Object> mapFeedback(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("userId", rs.getString("user_id"));
        m.put("platform", rs.getString("platform"));
        m.put("category", rs.getString("category"));
        m.put("content", rs.getString("content"));
        m.put("contact", rs.getString("contact"));
        m.put("appVersion", rs.getString("app_version"));
        m.put("status", rs.getString("status"));
        String reply = rs.getString("reply");
        m.put("reply", reply != null ? reply : "");
        m.put("repliedBy", rs.getString("replied_by"));
        m.put("repliedAt", rs.getObject("replied_at"));
        m.put("clientRequestId", rs.getString("client_request_id"));
        m.put("createdAt", rs.getLong("created_at"));
        m.put("updatedAt", rs.getLong("updated_at"));
        return m;
    }
}
