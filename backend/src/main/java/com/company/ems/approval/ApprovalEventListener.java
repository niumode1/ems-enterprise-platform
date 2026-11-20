package com.company.ems.approval;

import com.company.ems.audit.EntityRollbackService;
import com.company.ems.boss.BossAccessService;
import com.company.ems.chat.ChatAdminAccessService;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.EmployeeService;
import com.company.ems.roster.RosterImportService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApprovalEventListener {

    private final ConfigService configService;
    private final EmployeeService employeeService;
    private final ApprovalService approvalService;
    private final RosterImportService rosterImportService;
    private final BossAccessService bossAccessService;
    private final ChatAdminAccessService chatAdminAccessService;
    private final EntityRollbackService entityRollbackService;

    public ApprovalEventListener(ConfigService configService,
                                 EmployeeService employeeService,
                                 ApprovalService approvalService,
                                 RosterImportService rosterImportService,
                                 BossAccessService bossAccessService,
                                 ChatAdminAccessService chatAdminAccessService,
                                 EntityRollbackService entityRollbackService) {
        this.configService = configService;
        this.employeeService = employeeService;
        this.approvalService = approvalService;
        this.rosterImportService = rosterImportService;
        this.bossAccessService = bossAccessService;
        this.chatAdminAccessService = chatAdminAccessService;
        this.entityRollbackService = entityRollbackService;
    }

    @EventListener
    public void onApproved(ApprovalCompletedEvent event) {
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        if ("CONFIG_CHANGE".equals(event.policyCode())) {
            Object key = payload.get("key");
            if (key != null) {
                Object value = payload.get("value");
                configService.applyApprovedChange(String.valueOf(key),
                        value == null ? null : String.valueOf(value),
                        event.requesterId(), event.requestId());
            } else {
                approvalService.applyApprovedChange(event.policyCode(), event.requestId(), payload);
            }
            return;
        }
        if ("KEY_INFO".equals(event.policyCode())) {
            Object employeeId = payload.get("employeeId");
            Object changes = payload.get("changes");
            if (employeeId != null && changes instanceof Map<?, ?> rawChanges) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedChanges = (Map<String, Object>) rawChanges;
                employeeService.applyApprovedChanges(
                        Long.valueOf(String.valueOf(employeeId)),
                        typedChanges,
                        event.requestId(),
                        event.reason(),
                        event.requesterId()
                );
            }
            return;
        }
        if ("IMPORT_COMMIT".equals(event.policyCode())) {
            Object batchId = payload.get("batchId");
            if (batchId != null) {
                rosterImportService.applyBatch(
                        Long.valueOf(String.valueOf(batchId)),
                        event.requestId(),
                        event.requesterId()
                );
            }
            return;
        }
        if ("BOSS_ACCESS".equals(event.policyCode())) {
            bossAccessService.applyApproved(payload, event.requestId(), event.requesterId());
            return;
        }
        if ("CHAT_ACCESS".equals(event.policyCode())) {
            chatAdminAccessService.applyApproved(payload, event.requestId(), event.requesterId());
            return;
        }
        if ("ENTITY_ROLLBACK".equals(event.policyCode())) {
            entityRollbackService.applyApproved(payload, event.requestId(), event.reason(),
                    event.requesterId());
        }
    }
}
