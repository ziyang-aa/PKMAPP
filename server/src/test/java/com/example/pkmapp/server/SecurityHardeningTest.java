package com.example.pkmapp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.pkmapp.server.common.ApiException;
import com.example.pkmapp.server.security.AuthInterceptor;
import com.example.pkmapp.server.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHardeningTest {

    @Test
    void jwtRejectsWrongHeaderAlgOrTyp() {
        ObjectMapper mapper = new ObjectMapper();
        JwtService jwtService = new JwtService(
                mapper,
                "0123456789abcdef0123456789abcdef0123456789abcdef",
                3600,
                86400
        );

        String valid = jwtService.issueAccessToken("u_test_1");
        assertThat(jwtService.verifyAccessTokenAndGetUserId(valid)).isEqualTo("u_test_1");

        String[] parts = valid.split("\\.");
        String forgedHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String forged = forgedHeader + "." + parts[1] + "." + parts[2];
        assertThat(jwtService.verifyAccessTokenAndGetUserId(forged)).isNull();
    }

    @Test
    void authInterceptorEnforcesAdminTokenAndRateLimitAndSecurityHeaders() {
        ObjectMapper mapper = new ObjectMapper();
        JwtService jwtService = new JwtService(
                mapper,
                "0123456789abcdef0123456789abcdef0123456789abcdef",
                3600,
                86400
        );
        AuthInterceptor interceptor = new AuthInterceptor(jwtService, null, "super-secret-admin-token");

        // Admin wrong token rejected
        MockHttpServletRequest badAdminReq = new MockHttpServletRequest("GET", "/api/admin/v1/stats/overview");
        badAdminReq.setRemoteAddr("203.0.113.10");
        badAdminReq.addHeader("X-Admin-Token", "wrong-token");
        MockHttpServletResponse badAdminRes = new MockHttpServletResponse();
        assertThatThrownBy(() -> interceptor.preHandle(badAdminReq, badAdminRes, new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(badAdminRes.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(badAdminRes.getHeader("X-Frame-Options")).isEqualTo("DENY");

        // Admin valid token accepted
        MockHttpServletRequest okAdminReq = new MockHttpServletRequest("GET", "/api/admin/v1/stats/overview");
        okAdminReq.setRemoteAddr("203.0.113.10");
        okAdminReq.addHeader("X-Admin-Token", "super-secret-admin-token");
        assertThat(interceptor.preHandle(okAdminReq, new MockHttpServletResponse(), new Object())).isTrue();

        // Client error log endpoint rate-limited after 15 calls from same IP
        for (int i = 0; i < 15; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/logs/client-error");
            req.setRemoteAddr("198.51.100.7");
            assertThat(interceptor.preHandle(req, new MockHttpServletResponse(), new Object())).isTrue();
        }
        MockHttpServletRequest overflowReq = new MockHttpServletRequest("POST", "/api/v1/logs/client-error");
        overflowReq.setRemoteAddr("198.51.100.7");
        assertThatThrownBy(() -> interceptor.preHandle(overflowReq, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Different IP is still allowed (and spoofed X-Forwarded-For from public IP is ignored)
        MockHttpServletRequest spoofReq = new MockHttpServletRequest("POST", "/api/v1/logs/client-error");
        spoofReq.setRemoteAddr("198.51.100.7");
        spoofReq.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThatThrownBy(() -> interceptor.preHandle(spoofReq, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
