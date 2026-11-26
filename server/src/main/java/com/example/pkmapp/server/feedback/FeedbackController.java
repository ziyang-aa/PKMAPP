package com.example.pkmapp.server.feedback;

import com.example.pkmapp.server.common.ApiResponse;
import com.example.pkmapp.server.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FeedbackController {
    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    public record SubmitFeedbackRequest(
            String category,
            String content,
            String contact,
            String appVersion,
            String platform,
            String clientRequestId
    ) {}

    public record ClientErrorRequest(
            String platform,
            String appVersion,
            String deviceInfo,
            String errorCode,
            String stackTrace
    ) {}

    @PostMapping("/feedbacks")
    public ApiResponse<Map<String, Object>> submit(
            HttpServletRequest request,
            @RequestBody SubmitFeedbackRequest body
    ) {
        String userId = AuthContext.requireUserId(request);
        return ApiResponse.success(feedbackService.submit(
                userId,
                body.category(),
                body.content(),
                body.contact(),
                body.appVersion(),
                body.platform(),
                body.clientRequestId()
        ));
    }

    @GetMapping("/feedbacks/mine")
    public ApiResponse<Map<String, Object>> listMine(HttpServletRequest request) {
        String userId = AuthContext.requireUserId(request);
        return ApiResponse.success(Map.of("items", feedbackService.listMine(userId)));
    }

    @PostMapping("/logs/client-error")
    public ApiResponse<Map<String, Object>> reportError(
            HttpServletRequest request,
            @RequestBody ClientErrorRequest body
    ) {
        String userId = AuthContext.optionalUserId(request);
        feedbackService.recordClientError(
                userId,
                body.platform(),
                body.appVersion(),
                body.deviceInfo(),
                body.errorCode(),
                body.stackTrace()
        );
        return ApiResponse.success(Map.of("recorded", true));
    }
}
