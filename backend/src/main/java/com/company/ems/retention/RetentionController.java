package com.company.ems.retention;

import com.company.ems.common.ApiResponse;
import com.company.ems.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/retention")
public class RetentionController {

    private final RetentionService retentionService;

    public RetentionController(RetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAnyAuthority('config:read','config:recovery')")
    public ApiResponse<RetentionDtos.StatusView> status() {
        return ApiResponse.ok(retentionService.status());
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('config:recovery')")
    public ApiResponse<RetentionDtos.RunResult> run(
            @RequestBody(required = false) RetentionDtos.RunRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(retentionService.run(request, user));
    }
}
