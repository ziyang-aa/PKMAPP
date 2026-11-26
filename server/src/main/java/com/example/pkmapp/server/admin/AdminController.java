package com.example.pkmapp.server.admin;

import com.example.pkmapp.server.common.ApiException;
import com.example.pkmapp.server.common.ApiResponse;
import com.example.pkmapp.server.feedback.FeedbackService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1")
public class AdminController {
    private final JdbcTemplate jdbcTemplate;
    private final FeedbackService feedbackService;

    public AdminController(JdbcTemplate jdbcTemplate, FeedbackService feedbackService) {
        this.jdbcTemplate = jdbcTemplate;
        this.feedbackService = feedbackService;
    }

    public record ReplyRequest(String reply, String status, String repliedBy) {}

    @GetMapping("/stats/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", count("SELECT COUNT(*) FROM sys_users WHERE status = 1"));
        data.put("ledgerCount", count("SELECT COUNT(*) FROM biz_ledgers WHERE deleted_at = 0"));
        data.put("transactionCount", count("SELECT COUNT(*) FROM biz_transactions WHERE deleted_at = 0"));
        data.put("studyRecordCount", count("SELECT COUNT(*) FROM biz_study_records WHERE deleted_at = 0"));
        data.put("todoCount", count("SELECT COUNT(*) FROM biz_todo_items WHERE deleted_at = 0"));
        data.put("pendingFeedbackCount", count("SELECT COUNT(*) FROM ops_feedbacks WHERE status = 'pending'"));
        return ApiResponse.success(data);
    }

    @GetMapping("/feedbacks")
    public ApiResponse<Map<String, Object>> listFeedbacks(
            @RequestParam(name = "status", required = false) String status
    ) {
        List<Map<String, Object>> items;
        if (status != null && !status.isBlank()) {
            items = jdbcTemplate.query(
                    "SELECT * FROM ops_feedbacks WHERE status = ? ORDER BY created_at DESC LIMIT 200",
                    (rs, i) -> feedbackService.mapFeedback(rs),
                    status.trim()
            );
        } else {
            items = jdbcTemplate.query(
                    "SELECT * FROM ops_feedbacks ORDER BY created_at DESC LIMIT 200",
                    (rs, i) -> feedbackService.mapFeedback(rs)
            );
        }
        return ApiResponse.success(Map.of("items", items));
    }

    @PutMapping("/feedbacks/{id}/reply")
    public ApiResponse<Map<String, Object>> replyFeedback(
            @PathVariable("id") String id,
            @RequestBody ReplyRequest body
    ) {
        String reply = body.reply() == null ? "" : body.reply().trim();
        if (reply.isEmpty()) {
            throw ApiException.badRequest("EMPTY_REPLY", "回复内容不能为空");
        }
        String nextStatus = (body.status() == null || body.status().isBlank()) ? "resolved" : body.status().trim();
        String operator = (body.repliedBy() == null || body.repliedBy().isBlank()) ? "admin" : body.repliedBy().trim();
        long now = System.currentTimeMillis();

        int updated = jdbcTemplate.update(
                "UPDATE ops_feedbacks SET reply = ?, status = ?, replied_by = ?, replied_at = ?, updated_at = ? WHERE id = ?",
                reply, nextStatus, operator, now, now, id
        );
        if (updated == 0) {
            throw ApiException.notFound("FEEDBACK_NOT_FOUND", "未找到该意见反馈记录");
        }
        return ApiResponse.success(Map.of("id", id, "status", nextStatus, "reply", reply));
    }

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> listLogs() {
        List<Map<String, Object>> items = jdbcTemplate.query(
                "SELECT * FROM ops_client_error_logs ORDER BY created_at DESC LIMIT 100",
                (rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("userId", rs.getString("user_id"));
                    m.put("platform", rs.getString("platform"));
                    m.put("appVersion", rs.getString("app_version"));
                    m.put("deviceInfo", rs.getString("device_info"));
                    m.put("errorCode", rs.getString("error_code"));
                    m.put("stackTrace", rs.getString("stack_trace"));
                    m.put("createdAt", rs.getLong("created_at"));
                    return m;
                }
        );
        return ApiResponse.success(Map.of("items", items));
    }

    private long count(String sql) {
        Long val = jdbcTemplate.queryForObject(sql, Long.class);
        return val != null ? val : 0L;
    }
}
