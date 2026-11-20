package com.company.ems.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.config.ConfigService;
import com.company.ems.notification.NotificationService;
import com.company.ems.system.entity.SysApprovalPolicy;
import com.company.ems.system.entity.SysApprovalRequest;
import com.company.ems.system.mapper.SysApprovalPolicyMapper;
import com.company.ems.system.mapper.SysApprovalRequestMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configurable approval timeout reminder. The company decides whether reminders
 * are enabled, how many hours count as "waiting too long", and how many requests
 * one run may touch. Reminders go to users holding approval:approve, never to the
 * requester, and each request is reminded at most once per timeout window.
 */
@Service
public class ApprovalReminderService {

    private final SysApprovalRequestMapper requestMapper;
    private final SysApprovalPolicyMapper policyMapper;
    private final ConfigService configService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public ApprovalReminderService(SysApprovalRequestMapper requestMapper,
                                   SysApprovalPolicyMapper policyMapper,
                                   ConfigService configService,
                                   NotificationService notificationService,
                                   AuditService auditService) {
        this.requestMapper = requestMapper;
        this.policyMapper = policyMapper;
        this.configService = configService;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Scheduled(cron = "0 5 * * * *")
    public void scheduledRun() {
        if (configService.getBoolean("approval.reminder.enabled", true)) {
            run();
        }
    }

    public ApprovalDtos.ReminderResult run() {
        if (!configService.getBoolean("approval.reminder.enabled", true)) {
            return new ApprovalDtos.ReminderResult(0, 0, 0);
        }
        int timeoutHours = Math.max(1, configService.getInt("approval.timeoutHours", 24));
        int maxPerRun = Math.max(1, configService.getInt("approval.reminder.maxPerRun", 100));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(timeoutHours);

        List<SysApprovalRequest> rows = requestMapper.selectList(
                new LambdaQueryWrapper<SysApprovalRequest>()
                        .eq(SysApprovalRequest::getStatus, "PENDING")
                        .le(SysApprovalRequest::getCreatedAt, cutoff)
                        .and(w -> w.isNull(SysApprovalRequest::getLastRemindAt)
                                .or().le(SysApprovalRequest::getLastRemindAt, cutoff))
                        .orderByAsc(SysApprovalRequest::getCreatedAt)
                        .last("LIMIT " + maxPerRun));

        Map<String, String> policyNames = new HashMap<>();
        int reminded = 0;
        int expired = 0;
        for (SysApprovalRequest request : rows) {
            if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(now)) {
                request.setStatus("EXPIRED");
                requestMapper.updateById(request);
                expired++;
                continue;
            }
            String policyName = policyNames.computeIfAbsent(request.getPolicyCode(), code -> {
                SysApprovalPolicy policy = policyMapper.selectByCode(code);
                return policy == null || !StringUtils.hasText(policy.getName()) ? code : policy.getName();
            });
            long hours = Duration.between(
                    request.getCreatedAt() == null ? now : request.getCreatedAt(), now).toHours();
            notificationService.notifyPermissionHolders("approval:approve", request.getRequesterId(),
                    "APPROVAL", "审批超时提醒：" + policyName,
                    "有一笔审批已等待 " + hours + " 小时仍未完成，请及时处理", "/approvals");
            request.setLastRemindAt(now);
            requestMapper.updateById(request);
            auditService.record("APPROVAL", "approval", "REMIND", "APPROVAL_REQUEST",
                    request.getId(), null, Map.of("waitedHours", hours, "timeoutHours", timeoutHours),
                    null, request.getId());
            reminded++;
        }
        return new ApprovalDtos.ReminderResult(rows.size(), reminded, expired);
    }
}
