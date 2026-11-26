package com.example.pkmapp.server.auth;

import com.example.pkmapp.server.common.ApiResponse;
import com.example.pkmapp.server.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(String identifier, String password, String nickname) {}

    public record LoginRequest(String identifier, String password) {}

    public record RefreshRequest(String refreshToken) {}

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(
            @RequestBody RegisterRequest body,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        return ApiResponse.success(
                authService.register(body.identifier(), body.password(), body.nickname(), userAgent)
        );
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(
            @RequestBody LoginRequest body,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        return ApiResponse.success(
                authService.login(body.identifier(), body.password(), userAgent)
        );
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(
            @RequestBody RefreshRequest body,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        return ApiResponse.success(authService.refresh(body.refreshToken(), userAgent));
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        String userId = AuthContext.requireUserId(request);
        return ApiResponse.success(authService.currentUserProfile(userId));
    }

    @DeleteMapping("/account")
    public ApiResponse<Map<String, Object>> deleteAccount(HttpServletRequest request) {
        String userId = AuthContext.requireUserId(request);
        authService.deleteAccount(userId);
        return ApiResponse.success(Map.of("deleted", true));
    }
}
