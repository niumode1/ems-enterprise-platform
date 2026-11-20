package com.company.ems.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.ems.ai.entity.AiQueryLog;
import com.company.ems.ai.mapper.AiQueryLogMapper;
import com.company.ems.audit.AuditService;
import com.company.ems.audit.entity.AccessLog;
import com.company.ems.audit.entity.AuditLog;
import com.company.ems.audit.mapper.AccessLogMapper;
import com.company.ems.audit.mapper.AuditLogMapper;
import com.company.ems.chat.entity.ChatAttachment;
import com.company.ems.chat.entity.ChatConversation;
import com.company.ems.chat.entity.ChatMessage;
import com.company.ems.chat.mapper.ChatAttachmentMapper;
import com.company.ems.chat.mapper.ChatConversationMapper;
import com.company.ems.chat.mapper.ChatMessageMapper;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.notification.NotificationService;
import com.company.ems.notification.entity.Notification;
import com.company.ems.notification.mapper.NotificationMapper;
import com.company.ems.project.FileStorageService;
import com.company.ems.project.entity.DocFolder;
import com.company.ems.project.entity.Document;
import com.company.ems.project.entity.DocumentPermission;
import com.company.ems.project.entity.DocumentVersion;
import com.company.ems.project.entity.Milestone;
import com.company.ems.project.entity.Project;
import com.company.ems.project.entity.ProjectMember;
import com.company.ems.project.entity.ProjectProgressSnapshot;
import com.company.ems.project.entity.Task;
import com.company.ems.project.entity.TaskComment;
import com.company.ems.project.entity.TaskDependency;
import com.company.ems.project.entity.TaskParticipant;
import com.company.ems.project.mapper.DocFolderMapper;
import com.company.ems.project.mapper.DocumentMapper;
import com.company.ems.project.mapper.DocumentPermissionMapper;
import com.company.ems.project.mapper.DocumentVersionMapper;
import com.company.ems.project.mapper.MilestoneMapper;
import com.company.ems.project.mapper.ProjectMapper;
import com.company.ems.project.mapper.ProjectMemberMapper;
import com.company.ems.project.mapper.ProjectProgressSnapshotMapper;
import com.company.ems.project.mapper.TaskCommentMapper;
import com.company.ems.project.mapper.TaskDependencyMapper;
import com.company.ems.project.mapper.TaskMapper;
import com.company.ems.project.mapper.TaskParticipantMapper;
import com.company.ems.recovery.entity.RecoveryRequest;
import com.company.ems.recovery.entity.RecoverySignature;
import com.company.ems.recovery.mapper.RecoveryRequestMapper;
import com.company.ems.recovery.mapper.RecoverySignatureMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysLoginLog;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysLoginLogMapper;
import com.company.ems.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configurable retention cleanup. Every scope is driven by sys_config and
 * defaults to a dry-run for manual calls. Destructive project/document actions
 * additionally require the company to opt in with *.retention.autoPurge=true.
 * Audit logs can never be deleted through any other endpoint.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);
    private static final List<String> ALL_SCOPES = List.of(
            "DOCUMENT", "PROJECT", "AUDIT", "CHAT", "NOTIFICATION", "AI", "RECOVERY");

    private final AuditLogMapper auditLogMapper;
    private final AccessLogMapper accessLogMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatAttachmentMapper attachmentMapper;
    private final NotificationMapper notificationMapper;
    private final AiQueryLogMapper aiQueryLogMapper;
    private final RecoveryRequestMapper recoveryRequestMapper;
    private final RecoverySignatureMapper recoverySignatureMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final DocumentMapper documentMapper;
    private final DocumentVersionMapper documentVersionMapper;
    private final DocumentPermissionMapper documentPermissionMapper;
    private final DocFolderMapper folderMapper;
    private final TaskMapper taskMapper;
    private final TaskCommentMapper taskCommentMapper;
    private final TaskDependencyMapper taskDependencyMapper;
    private final TaskParticipantMapper taskParticipantMapper;
    private final MilestoneMapper milestoneMapper;
    private final ProjectProgressSnapshotMapper progressSnapshotMapper;
    private final FileStorageService fileStorageService;
    private final SysLoginLogMapper loginLogMapper;
    private final SysUserMapper userMapper;
    private final ConfigService configService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public RetentionService(AuditLogMapper auditLogMapper,
                            AccessLogMapper accessLogMapper,
                            ChatConversationMapper conversationMapper,
                            ChatMessageMapper messageMapper,
                            ChatAttachmentMapper attachmentMapper,
                            NotificationMapper notificationMapper,
                            AiQueryLogMapper aiQueryLogMapper,
                            RecoveryRequestMapper recoveryRequestMapper,
                            RecoverySignatureMapper recoverySignatureMapper,
                            ProjectMapper projectMapper,
                            ProjectMemberMapper projectMemberMapper,
                            DocumentMapper documentMapper,
                            DocumentVersionMapper documentVersionMapper,
                            DocumentPermissionMapper documentPermissionMapper,
                            DocFolderMapper folderMapper,
                            TaskMapper taskMapper,
                            TaskCommentMapper taskCommentMapper,
                            TaskDependencyMapper taskDependencyMapper,
                            TaskParticipantMapper taskParticipantMapper,
                            MilestoneMapper milestoneMapper,
                            ProjectProgressSnapshotMapper progressSnapshotMapper,
                            FileStorageService fileStorageService,
                            SysLoginLogMapper loginLogMapper,
                            SysUserMapper userMapper,
                            ConfigService configService,
                            AuditService auditService,
                            NotificationService notificationService) {
        this.auditLogMapper = auditLogMapper;
        this.accessLogMapper = accessLogMapper;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.attachmentMapper = attachmentMapper;
        this.notificationMapper = notificationMapper;
        this.aiQueryLogMapper = aiQueryLogMapper;
        this.recoveryRequestMapper = recoveryRequestMapper;
        this.recoverySignatureMapper = recoverySignatureMapper;
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.documentMapper = documentMapper;
        this.documentVersionMapper = documentVersionMapper;
        this.documentPermissionMapper = documentPermissionMapper;
        this.folderMapper = folderMapper;
        this.taskMapper = taskMapper;
        this.taskCommentMapper = taskCommentMapper;
        this.taskDependencyMapper = taskDependencyMapper;
        this.taskParticipantMapper = taskParticipantMapper;
        this.milestoneMapper = milestoneMapper;
        this.progressSnapshotMapper = progressSnapshotMapper;
        this.fileStorageService = fileStorageService;
        this.loginLogMapper = loginLogMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public RetentionDtos.StatusView status() {
        List<RetentionDtos.ScopeStatus> scopes = new ArrayList<>();
        scopes.add(new RetentionDtos.ScopeStatus("AUDIT",
                configService.getInt("audit.retentionDays", 365) > 0,
                configService.getInt("audit.retentionDays", 365),
                "DELETE",
                "审计、访问与登录日志；自动清理开关=" + configService.getBoolean("audit.autoClean", true)));
        scopes.add(new RetentionDtos.ScopeStatus("CHAT",
                configService.getInt("chat.retentionDays", 365) > 0,
                configService.getInt("chat.retentionDays", 365),
                "DELETE", "按会话覆盖，0=永久"));
        scopes.add(new RetentionDtos.ScopeStatus("NOTIFICATION",
                configService.getInt("notification.retentionDays", 180) > 0,
                configService.getInt("notification.retentionDays", 180),
                "DELETE", "0=永久"));
        scopes.add(new RetentionDtos.ScopeStatus("AI",
                configService.getInt("ai.log.retentionDays", 180) > 0,
                configService.getInt("ai.log.retentionDays", 180),
                "DELETE", "0=永久"));
        scopes.add(new RetentionDtos.ScopeStatus("RECOVERY",
                configService.getInt("recovery.logRetentionDays", 0) > 0,
                configService.getInt("recovery.logRetentionDays", 0),
                "DELETE", "0=永久"));
        scopes.add(new RetentionDtos.ScopeStatus("PROJECT",
                !"NONE".equals(projectAction()),
                configService.getInt("project.retentionDays.default", 3650),
                projectAction(),
                "项目删除需 project.retention.autoPurge=true"));
        scopes.add(new RetentionDtos.ScopeStatus("DOCUMENT",
                !"NONE".equals(docAction()),
                configService.getInt("doc.retentionDays.default", 3650),
                docAction(),
                "文档删除需 doc.retention.autoPurge=true"));
        return new RetentionDtos.StatusView(
                configService.getBoolean("retention.job.enabled", true), scopes);
    }

    public RetentionDtos.RunResult run(RetentionDtos.RunRequest request, LoginUser user) {
        boolean dryRun = request == null || request.getDryRun() == null || request.getDryRun();
        List<String> scopes = normalizeScopes(request == null ? null : request.getScopes());
        List<RetentionDtos.ScopeResult> results = runScopes(scopes, dryRun, true);
        auditService.record("SECURITY", "retention", "RUN", "RETENTION", null,
                null, Map.of("dryRun", dryRun, "scopes", scopes,
                        "results", results.stream().map(this::resultMap).toList()),
                dryRun ? "手动预演" : "手动执行", null);
        return new RetentionDtos.RunResult(dryRun, results);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void scheduled() {
        if (!configService.getBoolean("retention.job.enabled", true)) {
            return;
        }
        try {
            List<RetentionDtos.ScopeResult> results = runScopes(ALL_SCOPES, false, false);
            auditService.record("SECURITY", "retention", "RUN", "RETENTION", null,
                    null, Map.of("dryRun", false, "scopes", ALL_SCOPES,
                            "results", results.stream().map(this::resultMap).toList()),
                    "每日保留期清理", null);
        } catch (Exception ex) {
            log.warn("Retention job failed: {}", ex.getMessage(), ex);
        }
    }

    private List<RetentionDtos.ScopeResult> runScopes(List<String> scopes, boolean dryRun, boolean manual) {
        List<RetentionDtos.ScopeResult> results = new ArrayList<>();
        for (String scope : scopes) {
            results.add(switch (scope) {
                case "DOCUMENT" -> cleanDocuments(dryRun);
                case "PROJECT" -> cleanProjects(dryRun);
                case "AUDIT" -> cleanAudit(dryRun, manual);
                case "CHAT" -> cleanChat(dryRun);
                case "NOTIFICATION" -> cleanNotifications(dryRun);
                case "AI" -> cleanAiLogs(dryRun);
                case "RECOVERY" -> cleanRecovery(dryRun);
                default -> throw new BusinessException(40001, "不支持的清理范围：" + scope);
            });
        }
        return results;
    }

    private List<String> normalizeScopes(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return ALL_SCOPES;
        }
        List<String> scopes = new ArrayList<>();
        for (String scope : requested) {
            if (!StringUtils.hasText(scope)) {
                continue;
            }
            String normalized = scope.trim().toUpperCase();
            if (!ALL_SCOPES.contains(normalized)) {
                throw new BusinessException(40001, "不支持的清理范围：" + scope);
            }
            if (!scopes.contains(normalized)) {
                scopes.add(normalized);
            }
        }
        return scopes.isEmpty() ? ALL_SCOPES : scopes;
    }

    private RetentionDtos.ScopeResult cleanAudit(boolean dryRun, boolean manual) {
        int days = configService.getInt("audit.retentionDays", 365);
        if (days <= 0) {
            return result("AUDIT", days, "DELETE", 0, 0, dryRun, "0=永久保留");
        }
        if (!manual && !configService.getBoolean("audit.autoClean", true)) {
            return result("AUDIT", days, "DELETE", 0, 0, dryRun, "自动清理已关闭");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        Long auditCount = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff)
                .and(w -> w.isNull(AuditLog::getModule).or().ne(AuditLog::getModule, "retention")));
        Long accessCount = accessLogMapper.selectCount(new LambdaQueryWrapper<AccessLog>()
                .lt(AccessLog::getCreatedAt, cutoff));
        Long loginCount = loginLogMapper.selectCount(new LambdaQueryWrapper<SysLoginLog>()
                .lt(SysLoginLog::getCreatedAt, cutoff));
        int cleanDays = configService.getInt("audit.cleanLogRetentionDays", 365);
        LocalDateTime cleanCutoff = cleanDays <= 0 ? null : LocalDateTime.now().minusDays(cleanDays);
        Long cleanCount = cleanCutoff == null ? 0L
                : auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getModule, "retention")
                .lt(AuditLog::getCreatedAt, cleanCutoff));
        long matched = n(auditCount) + n(accessCount) + n(loginCount) + n(cleanCount);
        long affected = matched;
        if (!dryRun) {
            auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                    .lt(AuditLog::getCreatedAt, cutoff)
                    .and(w -> w.isNull(AuditLog::getModule).or().ne(AuditLog::getModule, "retention")));
            accessLogMapper.delete(new LambdaQueryWrapper<AccessLog>()
                    .lt(AccessLog::getCreatedAt, cutoff));
            loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                    .lt(SysLoginLog::getCreatedAt, cutoff));
            if (cleanCutoff != null) {
                auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                        .eq(AuditLog::getModule, "retention")
                        .lt(AuditLog::getCreatedAt, cleanCutoff));
            }
        }
        return result("AUDIT", days, "DELETE", matched, affected, dryRun,
                "含审计日志、访问日志与清理日志");
    }

    private RetentionDtos.ScopeResult cleanChat(boolean dryRun) {
        int globalDays = configService.getInt("chat.retentionDays", 365);
        List<ChatConversation> conversations = conversationMapper.selectList(null);
        long matched = 0;
        long affected = 0;
        LocalDateTime now = LocalDateTime.now();

        // Uploaded-but-never-sent attachments would otherwise stay on disk forever.
        int orphanHours = configService.getInt("chat.attachment.orphanHours", 24);
        if (orphanHours > 0) {
            List<ChatAttachment> orphans = attachmentMapper.selectList(
                    new LambdaQueryWrapper<ChatAttachment>()
                            .eq(ChatAttachment::getStatus, "PENDING")
                            .lt(ChatAttachment::getCreatedAt, now.minusHours(orphanHours)));
            matched += orphans.size();
            affected += orphans.size();
            if (!dryRun && !orphans.isEmpty()) {
                for (ChatAttachment orphan : orphans) {
                    if (StringUtils.hasText(orphan.getStorageKey())) {
                        fileStorageService.delete(orphan.getStorageKey());
                    }
                }
                attachmentMapper.deleteBatchIds(orphans.stream()
                        .map(ChatAttachment::getId).toList());
            }
        }

        for (ChatConversation conversation : conversations) {
            int days = conversation.getRetentionDays() == null ? globalDays : conversation.getRetentionDays();
            if (days <= 0) {
                continue;
            }
            LocalDateTime cutoff = now.minusDays(days);
            List<ChatMessage> expired = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getConversationId, conversation.getId())
                    .lt(ChatMessage::getCreatedAt, cutoff));
            if (expired.isEmpty()) {
                continue;
            }
            matched += expired.size();
            affected += expired.size();
            if (!dryRun) {
                List<Long> attachmentIds = expired.stream().map(ChatMessage::getAttachmentId)
                        .filter(Objects::nonNull).distinct().toList();
                if (!attachmentIds.isEmpty()) {
                    List<ChatAttachment> attachments = attachmentMapper.selectBatchIds(attachmentIds);
                    for (ChatAttachment attachment : attachments) {
                        if (StringUtils.hasText(attachment.getStorageKey())) {
                            fileStorageService.delete(attachment.getStorageKey());
                        }
                    }
                    attachmentMapper.deleteBatchIds(attachmentIds);
                }
                messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversation.getId())
                        .lt(ChatMessage::getCreatedAt, cutoff));
                refreshConversationPreview(conversation.getId());
            }
        }
        return result("CHAT", globalDays, "DELETE", matched, affected, dryRun,
                "按会话 retentionDays 覆盖；含未发送附件清理");
    }

    private void refreshConversationPreview(Long conversationId) {
        ChatMessage latest = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getRecalled, false)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        String preview = latest == null ? null
                : latest.getBurnAt() != null ? "[消息已焚毁]" : preview(latest.getContent());
        conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .set(ChatConversation::getLastMessageAt,
                        latest == null ? null : latest.getCreatedAt())
                .set(ChatConversation::getLastMessagePreview, preview));
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        String singleLine = content.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 120 ? singleLine.substring(0, 120) : singleLine;
    }

    private RetentionDtos.ScopeResult cleanNotifications(boolean dryRun) {
        int days = configService.getInt("notification.retentionDays", 180);
        if (days <= 0) {
            return result("NOTIFICATION", days, "DELETE", 0, 0, dryRun, "0=永久保留");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .lt(Notification::getCreatedAt, cutoff);
        long matched = n(notificationMapper.selectCount(wrapper));
        if (!dryRun && matched > 0) {
            notificationMapper.delete(wrapper);
        }
        return result("NOTIFICATION", days, "DELETE", matched, matched, dryRun, null);
    }

    private RetentionDtos.ScopeResult cleanAiLogs(boolean dryRun) {
        int days = configService.getInt("ai.log.retentionDays", 180);
        if (days <= 0) {
            return result("AI", days, "DELETE", 0, 0, dryRun, "0=永久保留");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        LambdaQueryWrapper<AiQueryLog> wrapper = new LambdaQueryWrapper<AiQueryLog>()
                .lt(AiQueryLog::getCreatedAt, cutoff);
        long matched = n(aiQueryLogMapper.selectCount(wrapper));
        if (!dryRun && matched > 0) {
            aiQueryLogMapper.delete(wrapper);
        }
        return result("AI", days, "DELETE", matched, matched, dryRun, null);
    }

    private RetentionDtos.ScopeResult cleanRecovery(boolean dryRun) {
        int days = configService.getInt("recovery.logRetentionDays", 0);
        if (days <= 0) {
            return result("RECOVERY", days, "DELETE", 0, 0, dryRun, "0=永久保留");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        List<RecoveryRequest> stale = recoveryRequestMapper.selectList(
                new LambdaQueryWrapper<RecoveryRequest>()
                        .lt(RecoveryRequest::getCreatedAt, cutoff));
        long matched = stale.size();
        if (!dryRun && matched > 0) {
            List<Long> ids = stale.stream().map(RecoveryRequest::getId).toList();
            recoverySignatureMapper.delete(new LambdaQueryWrapper<RecoverySignature>()
                    .in(RecoverySignature::getRequestId, ids));
            recoveryRequestMapper.delete(new LambdaQueryWrapper<RecoveryRequest>()
                    .lt(RecoveryRequest::getCreatedAt, cutoff));
        }
        return result("RECOVERY", days, "DELETE", matched, matched, dryRun, null);
    }

    private RetentionDtos.ScopeResult cleanProjects(boolean dryRun) {
        String action = projectAction();
        int defaultDays = configService.getInt("project.retentionDays.default", 3650);
        if ("NONE".equals(action)) {
            return result("PROJECT", defaultDays, action, 0, 0, dryRun, "已关闭");
        }
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .ne(Project::getStatus, "ARCHIVED"));
        long matched = 0;
        long affected = 0;
        boolean purgeAllowed = configService.getBoolean("project.retention.autoPurge", false);
        LocalDate today = LocalDate.now();
        for (Project project : projects) {
            LocalDate expiry = expiryDate(project, defaultDays);
            if (expiry == null || !expiry.isBefore(today)) {
                continue;
            }
            matched++;
            switch (action) {
                case "ARCHIVE" -> {
                    affected++;
                    if (!dryRun) {
                        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                                .eq(Project::getId, project.getId())
                                .set(Project::getStatus, "ARCHIVED")
                                .set(Project::getRetentionNotifiedAt, LocalDateTime.now()));
                    }
                }
                case "NOTIFY" -> {
                    if (project.getRetentionNotifiedAt() == null) {
                        affected++;
                        if (!dryRun) {
                            notifyEmployee(project.getOwnerId(), "项目已到保留期",
                                    "项目「" + project.getName() + "」已超过保留期，请确认归档或删除",
                                    "/projects/" + project.getId());
                            projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                                    .eq(Project::getId, project.getId())
                                    .set(Project::getRetentionNotifiedAt, LocalDateTime.now()));
                        }
                    }
                }
                case "PURGE" -> {
                    if (!purgeAllowed) {
                        continue;
                    }
                    affected++;
                    if (!dryRun) {
                        purgeProject(project);
                    }
                }
                default -> {
                }
            }
        }
        String note = "PURGE".equals(action) && !purgeAllowed
                ? "未开启 project.retention.autoPurge，仅统计" : null;
        return result("PROJECT", defaultDays, action, matched, affected, dryRun, note);
    }

    private RetentionDtos.ScopeResult cleanDocuments(boolean dryRun) {
        String action = docAction();
        int defaultDays = configService.getInt("doc.retentionDays.default", 3650);
        if ("NONE".equals(action)) {
            return result("DOCUMENT", defaultDays, action, 0, 0, dryRun, "已关闭");
        }
        List<Project> projects = projectMapper.selectList(null);
        Map<Long, Project> byId = projects.stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        String projectAction = projectAction();
        boolean purgeAllowed = configService.getBoolean("doc.retention.autoPurge", false);
        LocalDate today = LocalDate.now();
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getStatus, "ACTIVE"));
        long matched = 0;
        long affected = 0;
        for (Document document : documents) {
            Project project = byId.get(document.getProjectId());
            if (project == null) {
                continue;
            }
            LocalDate expiry = expiryDate(project, defaultDays);
            if (expiry == null || !expiry.isBefore(today)) {
                continue;
            }
            if ("PURGE".equals(projectAction)
                    && configService.getBoolean("project.retention.autoPurge", false)) {
                continue; // the project scope removes the whole project
            }
            matched++;
            switch (action) {
                case "ARCHIVE" -> {
                    affected++;
                    if (!dryRun) {
                        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                                .eq(Document::getId, document.getId())
                                .set(Document::getStatus, "ARCHIVED")
                                .set(Document::getRetentionNotifiedAt, LocalDateTime.now()));
                    }
                }
                case "NOTIFY" -> {
                    if (document.getRetentionNotifiedAt() == null) {
                        affected++;
                        if (!dryRun) {
                            notifyEmployee(document.getUploaderId(), "文档已到保留期",
                                    "文档「" + document.getName() + "」已超过保留期，请确认归档或删除",
                                    "/projects/" + document.getProjectId());
                            documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                                    .eq(Document::getId, document.getId())
                                    .set(Document::getRetentionNotifiedAt, LocalDateTime.now()));
                        }
                    }
                }
                case "PURGE" -> {
                    if (!purgeAllowed) {
                        continue;
                    }
                    affected++;
                    if (!dryRun) {
                        purgeDocument(document);
                    }
                }
                default -> {
                }
            }
        }
        String note = "PURGE".equals(action) && !purgeAllowed
                ? "未开启 doc.retention.autoPurge，仅统计" : null;
        return result("DOCUMENT", defaultDays, action, matched, affected, dryRun, note);
    }

    private LocalDate expiryDate(Project project, int defaultDays) {
        int days = project.getRetentionDays() == null ? defaultDays : project.getRetentionDays();
        if (days <= 0) {
            return null;
        }
        LocalDate base = project.getEndDate() != null ? project.getEndDate()
                : project.getCreatedAt() == null ? null : project.getCreatedAt().toLocalDate();
        return base == null ? null : base.plusDays(days);
    }

    private void purgeProject(Project project) {
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getProjectId, project.getId()));
        List<Long> documentIds = documents.stream().map(Document::getId).toList();
        if (!documentIds.isEmpty()) {
            List<DocumentVersion> versions = documentVersionMapper.selectList(
                    new LambdaQueryWrapper<DocumentVersion>()
                            .in(DocumentVersion::getDocumentId, documentIds));
            versions.forEach(version -> fileStorageService.delete(version.getStorageKey()));
            documents.forEach(document -> fileStorageService.delete(document.getStorageKey()));
            documentPermissionMapper.delete(new LambdaQueryWrapper<DocumentPermission>()
                    .in(DocumentPermission::getDocumentId, documentIds));
            documentVersionMapper.delete(new LambdaQueryWrapper<DocumentVersion>()
                    .in(DocumentVersion::getDocumentId, documentIds));
            documentMapper.delete(new LambdaQueryWrapper<Document>()
                    .eq(Document::getProjectId, project.getId()));
        }
        folderMapper.delete(new LambdaQueryWrapper<DocFolder>()
                .eq(DocFolder::getProjectId, project.getId()));

        List<Task> tasks = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, project.getId()));
        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        if (!taskIds.isEmpty()) {
            taskCommentMapper.delete(new LambdaQueryWrapper<TaskComment>()
                    .in(TaskComment::getTaskId, taskIds));
            taskParticipantMapper.delete(new LambdaQueryWrapper<TaskParticipant>()
                    .in(TaskParticipant::getTaskId, taskIds));
            taskDependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>()
                    .and(w -> w.in(TaskDependency::getTaskId, taskIds)
                            .or().in(TaskDependency::getDependsOnTaskId, taskIds)));
            taskMapper.delete(new LambdaQueryWrapper<Task>()
                    .eq(Task::getProjectId, project.getId()));
        }
        milestoneMapper.delete(new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, project.getId()));
        progressSnapshotMapper.delete(new LambdaQueryWrapper<ProjectProgressSnapshot>()
                .eq(ProjectProgressSnapshot::getProjectId, project.getId()));
        projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, project.getId()));
        projectMapper.deleteById(project.getId());
        auditService.record("PROJECT", "retention", "PURGE", "PROJECT", project.getId(),
                Map.of("name", project.getName(), "code", project.getCode() == null ? "" : project.getCode()),
                null, "保留期清理", null);
    }

    private void purgeDocument(Document document) {
        List<DocumentVersion> versions = documentVersionMapper.selectList(
                new LambdaQueryWrapper<DocumentVersion>()
                        .eq(DocumentVersion::getDocumentId, document.getId()));
        versions.forEach(version -> fileStorageService.delete(version.getStorageKey()));
        fileStorageService.delete(document.getStorageKey());
        documentPermissionMapper.delete(new LambdaQueryWrapper<DocumentPermission>()
                .eq(DocumentPermission::getDocumentId, document.getId()));
        documentVersionMapper.delete(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, document.getId()));
        documentMapper.deleteById(document.getId());
        auditService.record("DOCUMENT", "retention", "PURGE", "DOCUMENT", document.getId(),
                Map.of("name", document.getName(), "projectId", document.getProjectId()),
                null, "保留期清理", null);
    }

    private void notifyEmployee(Long employeeId, String title, String content, String link) {
        if (employeeId == null) {
            return;
        }
        SysUser user = userMapper.selectByEmployeeId(employeeId);
        if (user != null) {
            notificationService.notify(user.getId(), "RETENTION", title, content, link);
        }
    }

    private String projectAction() {
        return action("project.retention.action");
    }

    private String docAction() {
        return action("doc.retention.action");
    }

    private String action(String key) {
        String value = configService.getString(key, "ARCHIVE");
        String normalized = value == null ? "ARCHIVE" : value.trim().toUpperCase();
        return Set.of("ARCHIVE", "NOTIFY", "PURGE", "NONE").contains(normalized)
                ? normalized : "ARCHIVE";
    }

    private RetentionDtos.ScopeResult result(String scope, int days, String action,
                                             long matched, long affected, boolean dryRun, String note) {
        return new RetentionDtos.ScopeResult(scope, days, action, matched, affected, dryRun, note);
    }

    private Map<String, Object> resultMap(RetentionDtos.ScopeResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("scope", result.scope());
        map.put("matched", result.matched());
        map.put("affected", result.affected());
        map.put("action", result.action());
        return map;
    }

    private long n(Long value) {
        return value == null ? 0 : value;
    }
}
