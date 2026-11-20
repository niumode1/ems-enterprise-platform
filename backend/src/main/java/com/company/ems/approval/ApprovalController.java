package com.company.ems.approval;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysApprovalPolicy;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalReminderService reminderService;

    public ApprovalController(ApprovalService approvalService,
                              ApprovalReminderService reminderService) {
        this.approvalService = approvalService;
        this.reminderService = reminderService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<ApprovalDtos.ApprovalView> submit(@Valid @RequestBody ApprovalDtos.SubmitRequest request,
                                                         @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(approvalService.submit(request, user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<PageResult<ApprovalDtos.ApprovalView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean pendingForMe,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(approvalService.page(page, size, status, mine, pendingForMe, user));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<ApprovalDtos.ApprovalView> get(@PathVariable Long id,
                                                      @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(approvalService.get(id, user));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('approval:approve')")
    public ApiResponse<ApprovalDtos.ApprovalView> approve(@PathVariable Long id,
                                                          @RequestBody(required = false) ApprovalDtos.ActionRequest request,
                                                          @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(approvalService.approve(id, request == null ? null : request.getComment(), user));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('approval:approve')")
    public ApiResponse<ApprovalDtos.ApprovalView> reject(@PathVariable Long id,
                                                         @RequestBody(required = false) ApprovalDtos.ActionRequest request,
                                                         @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(approvalService.reject(id, request == null ? null : request.getComment(), user));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<ApprovalDtos.ApprovalView> cancel(@PathVariable Long id,
                                                         @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(approvalService.cancel(id, user));
    }

    @PostMapping("/reminders/run")
    @PreAuthorize("hasAuthority('approval:approve')")
    public ApiResponse<ApprovalDtos.ReminderResult> runReminders() {
        return ApiResponse.ok(reminderService.run());
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<List<SysApprovalPolicy>> policies() {
        return ApiResponse.ok(approvalService.policies());
    }
}
