package com.company.ems.recovery;

import com.company.ems.common.ApiResponse;
import com.company.ems.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recovery")
public class RecoveryController {

    private final RecoveryService recoveryService;

    public RecoveryController(RecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @GetMapping("/status")
    public ApiResponse<RecoveryDtos.StatusView> status(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(recoveryService.status(user));
    }

    @GetMapping("/requests")
    public ApiResponse<List<RecoveryDtos.RequestView>> requests(@AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(recoveryService.list(user));
    }

    @GetMapping("/requests/{id}")
    public ApiResponse<RecoveryDtos.RequestView> get(@PathVariable Long id,
                                                     @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(recoveryService.get(id, user));
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAuthority('config:recovery')")
    public ApiResponse<RecoveryDtos.RequestResult> request(@Valid @RequestBody RecoveryDtos.Request request,
                                                           @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(recoveryService.request(request, user));
    }

    @PostMapping("/requests/{id}/sign")
    public ApiResponse<RecoveryDtos.RequestView> sign(@PathVariable Long id,
                                                      @RequestBody(required = false) RecoveryDtos.SignRequest request,
                                                      @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(recoveryService.sign(id, request == null ? null : request.getComment(), user));
    }

    @PostMapping("/requests/{id}/reject")
    public ApiResponse<RecoveryDtos.RequestView> reject(@PathVariable Long id,
                                                        @RequestBody(required = false) RecoveryDtos.SignRequest request,
                                                        @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(recoveryService.reject(id, request == null ? null : request.getComment(), user));
    }

    @PostMapping("/requests/{id}/cancel")
    public ApiResponse<RecoveryDtos.RequestView> cancel(@PathVariable Long id,
                                                        @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(recoveryService.cancel(id, user));
    }
}
