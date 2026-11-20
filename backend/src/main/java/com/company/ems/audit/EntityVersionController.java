package com.company.ems.audit;

import com.company.ems.common.ApiResponse;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit")
public class EntityVersionController {

    private final EntityVersionService versionService;
    private final EntityRollbackService rollbackService;

    public EntityVersionController(EntityVersionService versionService,
                                   EntityRollbackService rollbackService) {
        this.versionService = versionService;
        this.rollbackService = rollbackService;
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('audit:version:read')")
    public ApiResponse<PageResult<EntityVersionDtos.VersionView>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(versionService.page(page, size, entityType, entityId,
                operatorId, from, to));
    }

    @GetMapping("/versions/{id}")
    @PreAuthorize("hasAuthority('audit:version:read')")
    public ApiResponse<EntityVersionDtos.VersionDetail> get(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(versionService.get(id, user));
    }

    @PostMapping("/rollback")
    @PreAuthorize("hasAuthority('audit:rollback')")
    public ApiResponse<EntityVersionDtos.RollbackResult> rollback(
            @Valid @RequestBody EntityVersionDtos.RollbackRequest request,
            @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(rollbackService.request(request, user));
    }
}
