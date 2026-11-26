package com.example.pkmapp.server.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "songguo-server");
        data.put("version", "4.0.0");
        data.put("database", one != null && one == 1 ? "up" : "down");
        data.put("serverTime", System.currentTimeMillis());
        return ApiResponse.success(data);
    }
}
