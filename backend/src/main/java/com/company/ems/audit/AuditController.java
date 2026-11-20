package com.company.ems.audit;

import com.company.ems.audit.entity.AccessLog;
import com.company.ems.audit.entity.AuditLog;
import com.company.ems.common.ApiResponse;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<PageResult<AuditLog>> logs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(auditService.page(page, size, category, module, actorId,
                entityType, entityId, from, to));
    }

    @GetMapping("/access-logs")
    @PreAuthorize("hasAuthority('audit:read')")
    public ApiResponse<PageResult<AccessLog>> accessLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Boolean sensitive,
            @AuthenticationPrincipal LoginUser user) {
        boolean canReadSensitive = user.hasPermission("audit:sensitive:read");
        if (Boolean.TRUE.equals(sensitive) && !canReadSensitive) {
            throw new BusinessException(40301, "无敏感访问日志查看权限");
        }
        Boolean effectiveSensitive = sensitive;
        if (!canReadSensitive) {
            effectiveSensitive = Boolean.FALSE;
        }
        return ApiResponse.ok(auditService.accessPage(page, size, userId, resourceType, effectiveSensitive));
    }
}
