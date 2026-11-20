package com.company.ems.config;

import com.company.ems.approval.ApprovalDtos;
import com.company.ems.approval.ApprovalService;
import com.company.ems.common.ApiResponse;
import com.company.ems.common.BusinessException;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysApprovalPolicy;
import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.entity.SysConfigVersion;
import com.company.ems.system.mapper.SysApprovalPolicyMapper;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final ConfigService configService;
    private final ApprovalService approvalService;
    private final SysApprovalPolicyMapper policyMapper;

    public ConfigController(ConfigService configService,
                            ApprovalService approvalService,
                            SysApprovalPolicyMapper policyMapper) {
        this.configService = configService;
        this.approvalService = approvalService;
        this.policyMapper = policyMapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('config:read')")
    public ApiResponse<List<SysConfig>> list(@RequestParam(required = false) String group,
                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(configService.list(group, keyword));
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasAuthority('config:read')")
    public ApiResponse<SysConfig> get(@PathVariable String key) {
        return ApiResponse.ok(configService.get(key));
    }

    @GetMapping("/{key}/history")
    @PreAuthorize("hasAuthority('config:read')")
    public ApiResponse<List<SysConfigVersion>> history(@PathVariable String key) {
        return ApiResponse.ok(configService.history(key));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('config:update')")
    public ApiResponse<ConfigDtos.UpdateResult> update(@PathVariable String key,
                                                       @Valid @RequestBody ConfigDtos.UpdateRequest request,
                                                       @AuthenticationPrincipal LoginUser user) {
        return ApiResponse.ok(configService.requestUpdate(key, request, user));
    }

    @GetMapping("/approval-policies")
    @PreAuthorize("hasAuthority('approval:read')")
    public ApiResponse<List<SysApprovalPolicy>> policies() {
        return ApiResponse.ok(approvalService.policies());
    }

    @PutMapping("/approval-policies/{id}")
    @PreAuthorize("hasAuthority('config:approval:update')")
    public ApiResponse<ApprovalDtos.ApprovalView> updatePolicy(@PathVariable Long id,
                                                               @RequestBody SysApprovalPolicy request,
                                                               @AuthenticationPrincipal LoginUser user) {
        SysApprovalPolicy existing = policyMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(40401, "审批策略不存在");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("policyId", id);
        if (request.getQuorum() != null) {
            payload.put("quorum", request.getQuorum());
        }
        if (request.getChangeQuorum() != null) {
            payload.put("changeQuorum", request.getChangeQuorum());
        }
        if (request.getApplicantIncluded() != null) {
            payload.put("applicantIncluded", request.getApplicantIncluded());
        }
        if (request.getEnabled() != null) {
            payload.put("enabled", request.getEnabled());
        }
        if (payload.size() == 1) {
            throw new BusinessException(40001, "没有需要修改的审批配置");
        }
        ApprovalDtos.SubmitRequest submit = new ApprovalDtos.SubmitRequest();
        submit.setPolicyCode("CONFIG_CHANGE");
        submit.setEntityType("APPROVAL_POLICY");
        submit.setEntityId(id);
        submit.setPayload(payload);
        submit.setReason("修改审批策略：" + existing.getName());
        return ApiResponse.ok(approvalService.submit(submit, user));
    }
}
