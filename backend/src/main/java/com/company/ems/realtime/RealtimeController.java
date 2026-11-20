package com.company.ems.realtime;

import com.company.ems.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realtime")
public class RealtimeController {

    private final RealtimePushService pushService;

    public RealtimeController(RealtimePushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/capabilities")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<RealtimeCapabilities> capabilities() {
        return ApiResponse.ok(pushService.capabilities());
    }
}
