package com.qinglian.fitness.presence;

import com.qinglian.fitness.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/presence")
public class PresenceCompatibilityController {

    private static final int MAX_CLIENT_ID_LENGTH = 128;

    private final PresenceService presenceService;

    public PresenceCompatibilityController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(
        HttpServletRequest request,
        @RequestHeader(value = "X-Visitor-Id", required = false) String clientId
    ) {
        presenceService.heartbeat(CurrentUser.id(request), normalizeClientId(clientId));
    }

    @PostMapping("/offline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void offline(
        HttpServletRequest request,
        @RequestHeader(value = "X-Visitor-Id", required = false) String clientId
    ) {
        presenceService.offline(CurrentUser.id(request), normalizeClientId(clientId));
    }

    private String normalizeClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "legacy-default";
        }
        String normalized = clientId.trim();
        return normalized.length() <= MAX_CLIENT_ID_LENGTH
            ? normalized : normalized.substring(0, MAX_CLIENT_ID_LENGTH);
    }
}
