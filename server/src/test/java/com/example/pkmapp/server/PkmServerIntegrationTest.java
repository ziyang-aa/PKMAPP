package com.example.pkmapp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PkmServerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${pkm.admin.token}")
    private String adminToken;

    @Test
    void fullAuthSyncFeedbackAndCleanupFlow() throws Exception {
        // 1. Health check
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.database").value("up"));

        String uniqueEmail = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "@songguo.test";
        String password = "Password123!";

        // 2. Register
        MvcResult regRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"%s","password":"%s","nickname":"测试小松鼠"}
                                """.formatted(uniqueEmail, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.data.user.nickname").value("测试小松鼠"))
                .andReturn();

        JsonNode regData = objectMapper.readTree(regRes.getResponse().getContentAsString()).path("data");
        String accessToken = regData.path("accessToken").asText();
        String refreshToken = regData.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        try {
            // 3. Wrong password rejected
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"identifier":"%s","password":"WrongPassword99"}
                                    """.formatted(uniqueEmail)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.ok").value(false));

            // 4. Correct login
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"identifier":"%s","password":"%s"}
                                    """.formatted(uniqueEmail, password)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

            // 5. Refresh token rotation
            MvcResult refRes = mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"%s"}
                                    """.formatted(refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn();
            String rotatedAccess = objectMapper.readTree(refRes.getResponse().getContentAsString())
                    .path("data").path("accessToken").asText();
            assertThat(rotatedAccess).isNotBlank();
            accessToken = rotatedAccess;

            // Old refresh token is now revoked
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"%s"}
                                    """.formatted(refreshToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.ok").value(false));

            // 6. Sync push (matching Android SyncRepository JSON format)
            long now = System.currentTimeMillis();
            String syncPayload = """
                    {
                      "ledgers": [{
                        "id": "ledger_main",
                        "name": "日常账本",
                        "initialAssetsCents": 100000,
                        "liabilitiesCents": 0,
                        "monthlyBudgetCents": 300000,
                        "isDefault": true,
                        "isCurrent": true,
                        "sortOrder": 0,
                        "updatedAt": %d,
                        "deletedAt": 0,
                        "clientRequestId": "req_ledger_1"
                      }],
                      "transactions": [{
                        "id": "tx_1",
                        "ledgerId": "ledger_main",
                        "type": "EXPENSE",
                        "category": "餐饮",
                        "amountCents": 3500,
                        "occurredAt": %d,
                        "note": "抹茶拿铁",
                        "updatedAt": %d,
                        "deletedAt": 0,
                        "clientRequestId": "req_tx_1"
                      }],
                      "todos": [{
                        "id": "todo_1",
                        "title": "复习线性代数",
                        "note": "",
                        "dueDate": "2026-11-27",
                        "dueTime": "19:00",
                        "remind": true,
                        "remindBeforeMinutes": 30,
                        "completedAt": 0,
                        "updatedAt": %d,
                        "deletedAt": 0,
                        "clientRequestId": "req_todo_1"
                      }]
                    }
                    """.formatted(now, now, now, now);

            MvcResult pushRes = mockMvc.perform(post("/api/v1/sync/push")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(syncPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data.acceptedCount").value(3))
                    .andReturn();
            long serverSeq = objectMapper.readTree(pushRes.getResponse().getContentAsString())
                    .path("data").path("serverSeq").asLong();
            assertThat(serverSeq).isPositive();

            // 7. Sync pull since=0 returns pushed records
            mockMvc.perform(get("/api/v1/sync/pull")
                            .param("since", "0")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data.nextCursor").value(serverSeq))
                    .andExpect(jsonPath("$.data.ledgers[0].name").value("日常账本"))
                    .andExpect(jsonPath("$.data.ledgers[0].monthlyBudgetCents").value(300000))
                    .andExpect(jsonPath("$.data.transactions[0].note").value("抹茶拿铁"))
                    .andExpect(jsonPath("$.data.todos[0].title").value("复习线性代数"));

            // Pull since=serverSeq returns empty delta
            mockMvc.perform(get("/api/v1/sync/pull")
                            .param("since", String.valueOf(serverSeq))
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.transactions").isEmpty());

            // 8. Feedback submit + admin reply + mine fetch
            MvcResult fbRes = mockMvc.perform(post("/api/v1/feedbacks")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "category": "suggestion",
                                      "content": "希望小松鼠可以换冬天的围巾配色",
                                      "contact": "test@songguo.test",
                                      "appVersion": "4.0.0",
                                      "platform": "android",
                                      "clientRequestId": "req_fb_1"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true))
                    .andReturn();
            String fbId = objectMapper.readTree(fbRes.getResponse().getContentAsString())
                    .path("data").path("id").asText();

            mockMvc.perform(put("/api/admin/v1/feedbacks/" + fbId + "/reply")
                            .header("X-Admin-Token", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"reply":"已收到建议，冬季皮肤排期中！","status":"resolved"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));

            mockMvc.perform(get("/api/v1/feedbacks/mine")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].status").value("resolved"))
                    .andExpect(jsonPath("$.data.items[0].reply").value("已收到建议，冬季皮肤排期中！"));
        } finally {
            // 9. Clean up test account from MySQL
            mockMvc.perform(delete("/api/v1/auth/account")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ok").value(true));
        }
    }
}
