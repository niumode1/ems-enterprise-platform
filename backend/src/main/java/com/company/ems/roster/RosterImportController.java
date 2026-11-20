package com.company.ems.roster;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/imports")
public class RosterImportController {

    private final RosterImportService importService;

    public RosterImportController(RosterImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('import:upload')")
    public ApiResponse<RosterDtos.BatchView> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.upload(file, user));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('import:read')")
    public ApiResponse<PageResult<RosterDtos.BatchView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(importService.page(page, size, status, keyword));
    }

    @GetMapping("/my-confirmations")
    @PreAuthorize("hasAuthority('import:confirm')")
    public ApiResponse<List<RosterDtos.RowView>> myConfirmations(
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.myConfirmations(user));
    }

    @GetMapping("/my-confirmations/fields")
    @PreAuthorize("hasAuthority('import:confirm')")
    public ApiResponse<List<String>> myConfirmationFields() {
        return ApiResponse.ok(importService.myEditableFields().stream().sorted().toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('import:read')")
    public ApiResponse<RosterDtos.BatchView> get(@PathVariable Long id) {
        return ApiResponse.ok(importService.get(id));
    }

    @PostMapping("/{id}/parse")
    @PreAuthorize("hasAuthority('import:parse')")
    public ApiResponse<RosterDtos.BatchView> parse(@PathVariable Long id,
                                                   @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.parse(id, user));
    }

    @GetMapping("/{id}/rows")
    @PreAuthorize("hasAuthority('import:read')")
    public ApiResponse<PageResult<RosterDtos.RowView>> rows(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long size,
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(importService.pageRows(id, page, size, status));
    }

    @PutMapping("/{id}/rows/{rowId}")
    @PreAuthorize("hasAuthority('import:review')")
    public ApiResponse<RosterDtos.RowView> review(
            @PathVariable Long id,
            @PathVariable Long rowId,
            @RequestBody RosterDtos.ReviewRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.reviewRow(rowId, request, user));
    }

    @PostMapping("/{id}/push")
    @PreAuthorize("hasAuthority('import:push')")
    public ApiResponse<RosterDtos.PushResult> push(@PathVariable Long id,
                                                   @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.push(id, user));
    }

    @PutMapping("/rows/{rowId}/confirm")
    @PreAuthorize("hasAuthority('import:confirm')")
    public ApiResponse<RosterDtos.RowView> confirm(
            @PathVariable Long rowId,
            @RequestBody(required = false) RosterDtos.ConfirmRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.confirm(rowId,
                request == null ? new RosterDtos.ConfirmRequest() : request, user));
    }

    @PostMapping("/rows/{rowId}/reject")
    @PreAuthorize("hasAuthority('import:confirm')")
    public ApiResponse<RosterDtos.RowView> reject(
            @PathVariable Long rowId,
            @Validated @RequestBody RosterDtos.RejectRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.reject(rowId, request, user));
    }

    @PostMapping("/{id}/commit")
    @PreAuthorize("hasAuthority('import:commit')")
    public ApiResponse<RosterDtos.CommitResult> commit(@PathVariable Long id,
                                                       @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.commit(id, user));
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAuthority('import:rollback')")
    public ApiResponse<RosterDtos.BatchView> rollback(@PathVariable Long id,
                                                      @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(importService.rollback(id, user));
    }
}
