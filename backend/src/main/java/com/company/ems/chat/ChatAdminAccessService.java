package com.company.ems.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.ems.approval.ApprovalDtos;
import com.company.ems.approval.ApprovalService;
import com.company.ems.audit.AuditService;
import com.company.ems.chat.entity.ChatAccessRequest;
import com.company.ems.chat.entity.ChatConversation;
import com.company.ems.chat.entity.ChatMember;
import com.company.ems.chat.mapper.ChatAccessRequestMapper;
import com.company.ems.chat.mapper.ChatConversationMapper;
import com.company.ems.chat.mapper.ChatMemberMapper;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.notification.NotificationService;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Approval-gated, time-bound, read-only access to chat history for admins.
 * The requester must not already be a member; a grant never allows sending,
 * recalling or changing conversation settings. Every read is audited.
 */
@Service
public class ChatAdminAccessService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REVOKED = "REVOKED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final ChatAccessRequestMapper accessMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatMemberMapper memberMapper;
    private final EmployeeMapper employeeMapper;
    private final SysUserMapper userMapper;
    private final ConfigService configService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public ChatAdminAccessService(ChatAccessRequestMapper accessMapper,
                                  ChatConversationMapper conversationMapper,
                                  ChatMemberMapper memberMapper,
                                  EmployeeMapper employeeMapper,
                                  SysUserMapper userMapper,
                                  ConfigService configService,
                                  ApprovalService approvalService,
                                  AuditService auditService,
                                  NotificationService notificationService) {
        this.accessMapper = accessMapper;
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.employeeMapper = employeeMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public ChatAdminAccessDtos.StatusView status(LoginUser user) {
        boolean enabled = enabled();
        String mode = mode();
        List<ChatAdminAccessDtos.GrantView> grants = toViews(
                accessMapper.selectList(new LambdaQueryWrapper<ChatAccessRequest>()
                        .eq(ChatAccessRequest::getRequesterId, user.userId())
                        .in(ChatAccessRequest::getStatus, STATUS_PENDING, STATUS_ACTIVE)
                        .orderByDesc(ChatAccessRequest::getCreatedAt)),
                user);
        return new ChatAdminAccessDtos.StatusView(mode, enabled,
                enabled && !"DISABLED".equals(mode) && user.hasPermission("chat:access:request"),
                configService.getBoolean("chat.adminAccess.requireReason", true),
                approverCount(), defaultDuration(), maxDuration(), grants);
    }

    @Transactional
    public ChatAdminAccessDtos.RequestResult request(ChatAdminAccessDtos.Request request, LoginUser user) {
        requireEnabled();
        if (!user.hasPermission("chat:access:request")) {
            throw new BusinessException(40301, "无聊天查看申请权限");
        }
        if (request == null || request.getConversationId() == null) {
            throw new BusinessException(40001, "请选择会话");
        }
        ChatConversation conversation = conversationMapper.selectById(request.getConversationId());
        if (conversation == null || !"ACTIVE".equals(conversation.getStatus())) {
            throw new BusinessException(40401, "会话不存在");
        }
        if (user.employeeId() != null && isMember(conversation.getId(), user.employeeId())) {
            throw new BusinessException(40001, "您已是会话成员，无需申请");
        }
        String reason = request.getReason() == null ? null : request.getReason().trim();
        if (configService.getBoolean("chat.adminAccess.requireReason", true)
                && !StringUtils.hasText(reason)) {
            throw new BusinessException(40001, "请填写查看用途");
        }
        int duration = duration(request.getDurationMinutes());
        LocalDateTime now = LocalDateTime.now();
        ChatAccessRequest entity = new ChatAccessRequest();
        entity.setConversationId(conversation.getId());
        entity.setRequesterId(user.userId());
        entity.setReason(reason);
        entity.setStatus(STATUS_PENDING);
        entity.setDurationMinutes(duration);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        accessMapper.insert(entity);

        String mode = mode();
        if ("DIRECT".equals(mode)) {
            apply(entity, null);
            auditService.record("SECURITY", "chat", "ACCESS_DIRECT", "CHAT_CONVERSATION",
                    conversation.getId(), null,
                    Map.of("requestId", entity.getId(), "durationMinutes", duration),
                    reason, null);
            return new ChatAdminAccessDtos.RequestResult(STATUS_ACTIVE, null, entity.getId(),
                    "已直接授权，查看到期后自动失效",
                    toViews(List.of(entity), user).get(0));
        }

        ApprovalDtos.SubmitRequest submit = new ApprovalDtos.SubmitRequest();
        submit.setPolicyCode("CHAT_ACCESS");
        submit.setEntityType("CHAT_CONVERSATION");
        submit.setEntityId(conversation.getId());
        submit.setPayload(Map.of("requestId", entity.getId(),
                "conversationId", conversation.getId()));
        submit.setReason(reason);
        ApprovalDtos.ApprovalView view = approvalService.submit(submit, user);
        // A quorum of 1 can complete inside submit(); only patch approval_id so
        // the listener's ACTIVE status is not overwritten with stale PENDING.
        accessMapper.update(null, new LambdaUpdateWrapper<ChatAccessRequest>()
                .eq(ChatAccessRequest::getId, entity.getId())
                .set(ChatAccessRequest::getApprovalId, view.id())
                .set(ChatAccessRequest::getUpdatedAt, LocalDateTime.now()));
        ChatAccessRequest stored = accessMapper.selectById(entity.getId());
        auditService.record("SECURITY", "chat", "ACCESS_REQUEST", "CHAT_CONVERSATION",
                conversation.getId(), null,
                Map.of("requestId", entity.getId(), "durationMinutes", duration),
                reason, view.id());
        return new ChatAdminAccessDtos.RequestResult(stored.getStatus(), view.id(), stored.getId(),
                STATUS_ACTIVE.equals(stored.getStatus()) ? "审批已通过，授权生效" : "已提交查看申请，等待审批",
                toViews(List.of(stored), user).get(0));
    }

    @Transactional
    public void applyApproved(Map<String, Object> payload, Long approvalId, Long requesterId) {
        if (payload == null || payload.get("requestId") == null) {
            return;
        }
        Long requestId = Long.valueOf(String.valueOf(payload.get("requestId")));
        ChatAccessRequest entity = accessMapper.selectById(requestId);
        if (entity == null || !STATUS_PENDING.equals(entity.getStatus())) {
            return;
        }
        entity.setApprovalId(approvalId);
        apply(entity, requesterId);
    }

    private void apply(ChatAccessRequest entity, Long requesterId) {
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_ACTIVE);
        entity.setStartsAt(now);
        entity.setExpiresAt(now.plusMinutes(entity.getDurationMinutes() == null
                ? defaultDuration() : entity.getDurationMinutes()));
        entity.setUpdatedAt(now);
        accessMapper.updateById(entity);
        notificationService.notify(requesterId == null ? entity.getRequesterId() : requesterId,
                "CHAT", "聊天查看申请已通过",
                "授权已生效，到期时间 " + entity.getExpiresAt(), "/chat");
        auditService.record("SECURITY", "chat", "ACCESS_APPLY", "CHAT_CONVERSATION",
                entity.getConversationId(), null,
                Map.of("requestId", entity.getId(),
                        "expiresAt", entity.getExpiresAt().toString()),
                entity.getReason(), entity.getApprovalId());
    }

    public List<ChatAdminAccessDtos.GrantView> list(boolean all, LoginUser user) {
        LambdaQueryWrapper<ChatAccessRequest> wrapper = new LambdaQueryWrapper<>();
        if (all) {
            if (!canReview(user)) {
                throw new BusinessException(40301, "无权查看全部聊天访问申请");
            }
        } else {
            wrapper.eq(ChatAccessRequest::getRequesterId, user.userId());
        }
        wrapper.orderByDesc(ChatAccessRequest::getCreatedAt);
        return toViews(accessMapper.selectList(wrapper), user);
    }

    @Transactional
    public ChatAdminAccessDtos.GrantView revoke(Long id, LoginUser user) {
        ChatAccessRequest entity = accessMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(40401, "查看授权不存在");
        }
        boolean own = entity.getRequesterId().equals(user.userId());
        if (!own && !canReview(user)) {
            throw new BusinessException(40301, "无权撤销该授权");
        }
        if (STATUS_PENDING.equals(entity.getStatus())) {
            entity.setStatus(STATUS_CANCELLED);
            entity.setRevokedAt(LocalDateTime.now());
            entity.setRevokedBy(user.userId());
            accessMapper.updateById(entity);
            auditService.record("SECURITY", "chat", "ACCESS_CANCEL", "CHAT_CONVERSATION",
                    entity.getConversationId(), null, Map.of("requestId", id),
                    "撤回申请", entity.getApprovalId());
        } else if (STATUS_ACTIVE.equals(entity.getStatus())) {
            entity.setStatus(STATUS_REVOKED);
            entity.setRevokedAt(LocalDateTime.now());
            entity.setRevokedBy(user.userId());
            accessMapper.updateById(entity);
            auditService.record("SECURITY", "chat", "ACCESS_REVOKE", "CHAT_CONVERSATION",
                    entity.getConversationId(), null, Map.of("requestId", id),
                    "撤销查看授权", entity.getApprovalId());
        } else {
            throw new BusinessException(40901, "该查看授权已结束");
        }
        return toViews(List.of(entity), user).get(0);
    }

    public ChatAccessRequest activeGrant(Long userId, Long conversationId) {
        ChatAccessRequest grant = accessMapper.selectOne(new LambdaQueryWrapper<ChatAccessRequest>()
                .eq(ChatAccessRequest::getRequesterId, userId)
                .eq(ChatAccessRequest::getConversationId, conversationId)
                .eq(ChatAccessRequest::getStatus, STATUS_ACTIVE)
                .gt(ChatAccessRequest::getExpiresAt, LocalDateTime.now())
                .orderByDesc(ChatAccessRequest::getId)
                .last("LIMIT 1"));
        if (grant == null) {
            throw new BusinessException(40301, "没有有效的聊天查看授权");
        }
        return grant;
    }

    public void recordRead(ChatAccessRequest grant, LoginUser user) {
        auditService.access("CHAT", grant.getConversationId(), "ADMIN_READ", true);
        auditService.record("SECURITY", "chat", "ACCESS_READ", "CHAT_CONVERSATION",
                grant.getConversationId(), null,
                Map.of("requestId", grant.getId(),
                        "expiresAt", grant.getExpiresAt().toString()),
                grant.getReason(), grant.getApprovalId());
    }

    private boolean isMember(Long conversationId, Long employeeId) {
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<ChatMember>()
                .eq(ChatMember::getConversationId, conversationId)
                .eq(ChatMember::getEmployeeId, employeeId)
                .eq(ChatMember::getStatus, "ACTIVE"));
        return count != null && count > 0;
    }

    private boolean canReview(LoginUser user) {
        return user.hasPermission("chat:access:approve")
                || user.hasPermission("audit:read")
                || user.hasPermission("config:read");
    }

    private boolean enabled() {
        return configService.getBoolean("chat.adminAccess.enabled", true)
                && configService.getBoolean("chat.enabled", true);
    }

    private String mode() {
        String value = configService.getString("chat.adminAccess.mode", "REQUEST");
        String normalized = value == null ? "REQUEST" : value.trim().toUpperCase();
        return Set.of("REQUEST", "DIRECT", "DISABLED").contains(normalized)
                ? normalized : "REQUEST";
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new BusinessException(40001, "管理员查看聊天未启用");
        }
        if ("DISABLED".equals(mode())) {
            throw new BusinessException(40001, "管理员查看聊天已关闭");
        }
    }

    private int approverCount() {
        return Math.max(1, configService.getInt("chat.adminAccess.quorum", 2));
    }

    private int defaultDuration() {
        return Math.max(1, configService.getInt("chat.adminAccess.durationMinutes", 60));
    }

    private int maxDuration() {
        return Math.max(defaultDuration(),
                configService.getInt("chat.adminAccess.maxDurationMinutes", 1440));
    }

    private int duration(Integer requested) {
        int value = requested == null ? defaultDuration() : requested;
        return Math.max(1, Math.min(value, maxDuration()));
    }

    private List<ChatAdminAccessDtos.GrantView> toViews(List<ChatAccessRequest> rows, LoginUser user) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> conversationIds = rows.stream()
                .map(ChatAccessRequest::getConversationId).collect(Collectors.toSet());
        Map<Long, ChatConversation> conversations = conversationMapper.selectBatchIds(conversationIds)
                .stream().collect(Collectors.toMap(ChatConversation::getId, c -> c));
        Map<Long, SysUser> users = userMapper.selectBatchIds(rows.stream()
                        .map(ChatAccessRequest::getRequesterId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, String> labels = new java.util.HashMap<>();
        for (Long conversationId : conversationIds) {
            labels.put(conversationId, label(conversations.get(conversationId)));
        }
        LocalDateTime now = LocalDateTime.now();
        List<ChatAdminAccessDtos.GrantView> views = new ArrayList<>();
        for (ChatAccessRequest row : rows) {
            SysUser requester = users.get(row.getRequesterId());
            boolean active = STATUS_ACTIVE.equals(row.getStatus())
                    && row.getExpiresAt() != null && row.getExpiresAt().isAfter(now);
            views.add(new ChatAdminAccessDtos.GrantView(row.getId(), row.getConversationId(),
                    labels.get(row.getConversationId()), row.getRequesterId(),
                    requester == null ? null : requester.getDisplayName(),
                    row.getReason(), row.getStatus(), row.getStartsAt(), row.getExpiresAt(),
                    row.getApprovalId(), row.getCreatedAt(), active));
        }
        return views;
    }

    private String label(ChatConversation conversation) {
        if (conversation == null) {
            return null;
        }
        if ("GROUP".equals(conversation.getType())) {
            return conversation.getName();
        }
        List<ChatMember> members = memberMapper.selectList(new LambdaQueryWrapper<ChatMember>()
                .eq(ChatMember::getConversationId, conversation.getId())
                .eq(ChatMember::getStatus, "ACTIVE")
                .last("LIMIT 2"));
        List<Long> ids = members.stream().map(ChatMember::getEmployeeId).toList();
        if (ids.isEmpty()) {
            return "单聊";
        }
        Map<Long, Employee> employees = employeeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        return "单聊 · " + ids.stream()
                .map(id -> employees.get(id) == null ? "" : employees.get(id).getName())
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" / "));
    }
}
