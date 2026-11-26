package com.example.pkmapp.server.sync;

import com.example.pkmapp.server.common.ApiResponse;
import com.example.pkmapp.server.security.AuthContext;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public ApiResponse<Map<String, Object>> push(
            HttpServletRequest request,
            @RequestBody(required = false) JsonNode payload
    ) {
        String userId = AuthContext.requireUserId(request);
        return ApiResponse.success(syncService.push(userId, payload));
    }

    @GetMapping("/pull")
    public ApiResponse<Map<String, Object>> pull(
            HttpServletRequest request,
            @RequestParam(name = "since", defaultValue = "0") long since
    ) {
        String userId = AuthContext.requireUserId(request);
        return ApiResponse.success(syncService.pull(userId, since));
    }
}
