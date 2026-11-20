package com.company.ems.audit;

import com.company.ems.approval.ApprovalDtos;
import com.company.ems.approval.ApprovalService;
import com.company.ems.audit.entity.EntityVersion;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.EmployeeService;
import com.company.ems.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class EntityRollbackService {

    private final EntityVersionService versionService;
    private final EmployeeService employeeService;
    private final ApprovalService approvalService;
    private final ConfigService configService;
    private final AuditService auditService;

    public EntityRollbackService(EntityVersionService versionService,
                                 EmployeeService employeeService,
                                 ApprovalService approvalService,
                                 ConfigService configService,
                                 AuditService auditService) {
        this.versionService = versionService;
        this.employeeService = employeeService;
        this.approvalService = approvalService;
        this.configService = configService;
        this.auditService = auditService;
    }

    @Transactional
    public EntityVersionDtos.RollbackResult request(EntityVersionDtos.RollbackRequest request,
                                                    LoginUser user) {
        if (request == null || request.versionId() == null) {
            throw new BusinessException(40001, "请选择要回滚的版本");
        }
        String reason = request.reason() == null ? null : request.reason().trim();
        if (configService.getBoolean("audit.version.rollbackRequireReason", true)
                && !StringUtils.hasText(reason)) {
            throw new BusinessException(40001, "请填写回滚原因");
        }
        EntityVersion version = versionService.require(request.versionId());
        Map<String, Object> fields = versionService.rollbackFields(version);
        if (fields.isEmpty()) {
            throw new BusinessException(40001, "该版本没有可回滚字段");
        }

        if (!versionService.rollbackApprovalRequired()) {
            Long newVersionId = apply(version, fields, null, reason, user.userId());
            auditService.record("AUDIT", "version", "ROLLBACK_DIRECT", "ENTITY_VERSION",
                    version.getId(), null,
                    Map.of("entityType", version.getEntityType(),
                            "entityId", version.getEntityId(),
                            "newVersionId", newVersionId == null ? 0L : newVersionId),
                    reason, null);
            return new EntityVersionDtos.RollbackResult("APPLIED", null, newVersionId,
                    "回滚已生效");
        }

        ApprovalDtos.SubmitRequest submit = new ApprovalDtos.SubmitRequest();
        submit.setPolicyCode("ENTITY_ROLLBACK");
        submit.setEntityType("ENTITY_VERSION");
        submit.setEntityId(version.getId());
        submit.setPayload(Map.of("versionId", version.getId(),
                "entityType", version.getEntityType(),
                "entityId", version.getEntityId()));
        submit.setReason(reason);
        ApprovalDtos.ApprovalView approval = approvalService.submit(submit, user);
        auditService.record("AUDIT", "version", "ROLLBACK_REQUEST", "ENTITY_VERSION",
                version.getId(), null,
                Map.of("entityType", version.getEntityType(),
                        "entityId", version.getEntityId()),
                reason, approval.id());
        return new EntityVersionDtos.RollbackResult("PENDING_APPROVAL", approval.id(), null,
                "回滚申请已提交审批，通过后生效");
    }

    @Transactional
    public void applyApproved(Map<String, Object> payload, Long approvalId, String reason,
                              Long requesterId) {
        if (payload == null || payload.get("versionId") == null) {
            return;
        }
        Long versionId = Long.valueOf(String.valueOf(payload.get("versionId")));
        EntityVersion version = versionService.require(versionId);
        Map<String, Object> fields = versionService.rollbackFields(version);
        apply(version, fields, approvalId, reason, requesterId);
        auditService.record("AUDIT", "version", "ROLLBACK_APPROVED", "ENTITY_VERSION",
                version.getId(), null,
                Map.of("entityType", version.getEntityType(),
                        "entityId", version.getEntityId()),
                reason, approvalId);
    }

    private Long apply(EntityVersion version, Map<String, Object> fields, Long approvalId,
                       String reason, Long operatorId) {
        if (!"EMPLOYEE".equals(version.getEntityType())) {
            throw new BusinessException(40001, "暂不支持该实体类型的回滚");
        }
        return employeeService.applyVersionRollback(version.getEntityId(), fields,
                approvalId, reason, operatorId);
    }
}
