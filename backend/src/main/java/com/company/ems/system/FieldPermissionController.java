package com.company.ems.system;

import com.company.ems.audit.AuditService;
import com.company.ems.common.ApiResponse;
import com.company.ems.common.BusinessException;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysFieldPermission;
import com.company.ems.system.mapper.SysFieldPermissionMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/field-permissions")
public class FieldPermissionController {

    private final SysFieldPermissionMapper fieldPermissionMapper;
    private final FieldPermissionService fieldPermissionService;
    private final AuditService auditService;

    public FieldPermissionController(SysFieldPermissionMapper fieldPermissionMapper,
                                     FieldPermissionService fieldPermissionService,
                                     AuditService auditService) {
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.fieldPermissionService = fieldPermissionService;
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('field:read')")
    public ApiResponse<List<SysFieldPermission>> list(@RequestParam String entityType) {
        return ApiResponse.ok(fieldPermissionMapper.selectByEntityType(entityType));
    }

    @GetMapping("/effective")
    public ApiResponse<FieldPermissionService.FieldPolicy> effective(@RequestParam String entityType,
                                                                    @RequestParam(required = false) Long employeeId,
                                                                    @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(fieldPermissionService.resolve(entityType, user, employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('field:update')")
    public ApiResponse<SysFieldPermission> create(@Valid @RequestBody FieldPermissionRequest request) {
        SysFieldPermission rule = new SysFieldPermission();
        apply(rule, request);
        rule.setRoleId(request.getRoleId() == null ? 0L : request.getRoleId());
        fieldPermissionMapper.insert(rule);
        auditService.record("CONFIG", "field", "CREATE", "FIELD_PERMISSION", rule.getId(),
                null, rule, null, null);
        return ApiResponse.ok(rule);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('field:update')")
    public ApiResponse<SysFieldPermission> update(@PathVariable Long id,
                                                  @Valid @RequestBody FieldPermissionRequest request) {
        SysFieldPermission existing = fieldPermissionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(40401, "字段权限规则不存在");
        }
        String before = existing.toString();
        apply(existing, request);
        if (request.getRoleId() != null) {
            existing.setRoleId(request.getRoleId());
        }
        fieldPermissionMapper.updateById(existing);
        auditService.record("CONFIG", "field", "UPDATE", "FIELD_PERMISSION", id,
                before, existing, null, null);
        return ApiResponse.ok(existing);
    }

    private void apply(SysFieldPermission rule, FieldPermissionRequest request) {
        rule.setEntityType(request.getEntityType());
        rule.setFieldCode(request.getFieldCode());
        rule.setScopeType(request.getScopeType() == null ? "ALL" : request.getScopeType());
        rule.setVisibility(request.getVisibility());
        rule.setMaskRule(request.getMaskRule() == null ? "NONE" : request.getMaskRule());
        rule.setSelfEdit(Boolean.TRUE.equals(request.getSelfEdit()));
        rule.setExportable(Boolean.TRUE.equals(request.getExportable()));
        rule.setAiReadable(request.getAiReadable() == null || request.getAiReadable());
        rule.setIsSensitive(Boolean.TRUE.equals(request.getSensitive()));
        rule.setKeyField(Boolean.TRUE.equals(request.getKeyField()));
    }

    @Data
    public static class FieldPermissionRequest {
        @NotBlank
        private String entityType;
        @NotBlank
        private String fieldCode;
        private Long roleId;
        private String scopeType;
        @NotBlank
        private String visibility;
        private String maskRule;
        private Boolean selfEdit;
        private Boolean exportable;
        private Boolean aiReadable;
        private Boolean sensitive;
        private Boolean keyField;
    }
}
