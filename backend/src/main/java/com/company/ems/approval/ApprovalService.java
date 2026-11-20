package com.company.ems.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.notification.NotificationService;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysApprovalAction;
import com.company.ems.system.entity.SysApprovalPolicy;
import com.company.ems.system.entity.SysApprovalRequest;
import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysApprovalActionMapper;
import com.company.ems.system.mapper.SysApprovalPolicyMapper;
import com.company.ems.system.mapper.SysApprovalRequestMapper;
import com.company.ems.system.mapper.SysConfigMapper;
import com.company.ems.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

@Service
public class ApprovalService {

    private final SysApprovalPolicyMapper policyMapper;
    private final SysApprovalRequestMapper requestMapper;
    private final SysApprovalActionMapper actionMapper;
    private final SysConfigMapper configMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ApprovalConflictService conflictService;

    public ApprovalService(SysApprovalPolicyMapper policyMapper,
                           SysApprovalRequestMapper requestMapper,
                           SysApprovalActionMapper actionMapper,
                           SysConfigMapper configMapper,
                           SysUserMapper userMapper,
                           ObjectMapper objectMapper,
                           ApplicationEventPublisher eventPublisher,
                           AuditService auditService,
                           NotificationService notificationService,
                           ApprovalConflictService conflictService) {
        this.policyMapper = policyMapper;
        this.requestMapper = requestMapper;
        this.actionMapper = actionMapper;
        this.configMapper = configMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.conflictService = conflictService;
    }

    @Transactional
    public ApprovalDtos.ApprovalView submit(ApprovalDtos.SubmitRequest request, LoginUser user) {
        SysApprovalPolicy policy = policyMapper.selectByCode(request.getPolicyCode());
        if (policy == null || !Boolean.TRUE.equals(policy.getEnabled())) {
            throw new BusinessException(40001, "审批策略不存在或已停用");
        }
        Map<String, Object> payload = request.getPayload() == null
                ? Collections.emptyMap()
                : request.getPayload();
        int required = Math.max(1, policy.getQuorum() == null ? 1 : policy.getQuorum());
        if ("BOSS_ACCESS".equals(policy.getPolicyCode())) {
            // Companies set their own required sign-off count for sensitive access.
            required = Math.max(1, configInt("boss.access.approverCount", 2));
        }
        if ("CHAT_ACCESS".equals(policy.getPolicyCode())) {
            // Chat viewing is sensitive; the company decides how many people must agree.
            required = Math.max(1, configInt("chat.adminAccess.quorum", 2));
        }
        if ("ENTITY_ROLLBACK".equals(policy.getPolicyCode())) {
            required = Math.max(1, configInt("audit.version.rollbackApproverCount", 2));
        }
        boolean applicantIncluded = Boolean.TRUE.equals(policy.getApplicantIncluded());
        String entityType = StringUtils.hasText(request.getEntityType())
                ? request.getEntityType() : policy.getEntityType();

        if ("BLOCK".equals(conflictService.pendingAction())
                && !conflictService.pendingConflicts(policy.getPolicyCode(), payload, entityType,
                        request.getEntityId(), null).isEmpty()) {
            throw new BusinessException(40901, "该对象已有待审批申请，请等待处理或驳回后再提交");
        }

        SysApprovalRequest entity = new SysApprovalRequest();
        entity.setPolicyCode(policy.getPolicyCode());
        entity.setEntityType(entityType);
        entity.setEntityId(request.getEntityId());
        entity.setPayloadJson(toJson(payload));
        entity.setReason(request.getReason());
        entity.setStatus("PENDING");
        entity.setRequesterId(user.userId());
        entity.setCurrentApprovals(applicantIncluded ? 1 : 0);
        entity.setRequiredApprovals(required);
        entity.setExpiresAt(LocalDateTime.now().plusHours(expireHours()));
        requestMapper.insert(entity);

        if (applicantIncluded) {
            SysApprovalAction applicantAction = new SysApprovalAction();
            applicantAction.setRequestId(entity.getId());
            applicantAction.setApproverId(user.userId());
            applicantAction.setAction("APPLICANT");
            applicantAction.setComment("申请人计入通过人数");
            actionMapper.insert(applicantAction);
        }

        if (entity.getCurrentApprovals() >= required) {
            entity.setStatus("APPROVED");
            requestMapper.updateById(entity);
            publishCompleted(entity, payload);
        }

        auditService.record("APPROVAL", "approval", "SUBMIT", "APPROVAL_REQUEST", entity.getId(),
                null, Map.of("policyCode", policy.getPolicyCode(), "payload", payload), request.getReason(), entity.getId());
        if ("APPROVED".equals(entity.getStatus())) {
            notificationService.notify(entity.getRequesterId(), "APPROVAL",
                    "审批已通过", policy.getName(), "/approvals");
        } else {
            notificationService.notifyPermissionHolders("approval:approve", user.userId(),
                    "APPROVAL", "新的审批申请：" + policy.getName(),
                    "申请人：" + user.displayName()
                            + (StringUtils.hasText(request.getReason()) ? "，原因：" + request.getReason() : ""),
                    "/approvals");
        }
        return toView(entity, user);
    }

    @Transactional
    public ApprovalDtos.ApprovalView approve(Long requestId, String comment, LoginUser user) {
        SysApprovalRequest entity = loadPending(requestId);
        if (Objects.equals(entity.getRequesterId(), user.userId())) {
            throw new BusinessException(40301, "不能审批自己提交的申请");
        }
        ensureNoAction(requestId, user.userId());

        List<String> staleFields = conflictService.staleFields(entity);
        if (!staleFields.isEmpty()) {
            if ("BLOCK".equals(conflictService.staleAction())) {
                throw new BusinessException(40902, "关键信息在审批期间已被修改（"
                        + String.join("、", staleFields) + "），请驳回后由申请人重新提交");
            }
            auditService.record("APPROVAL", "approval", "CONFLICT_DETECTED", "APPROVAL_REQUEST",
                    requestId, null, Map.of("fields", staleFields), null, requestId);
            notificationService.notify(entity.getRequesterId(), "APPROVAL", "审批冲突提示",
                    "审批期间以下关键信息已被他人修改，本次审批通过后会覆盖新值："
                            + String.join("、", staleFields), "/approvals");
        }

        SysApprovalAction action = new SysApprovalAction();
        action.setRequestId(requestId);
        action.setApproverId(user.userId());
        action.setAction("APPROVE");
        action.setComment(comment);
        try {
            actionMapper.insert(action);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(40901, "请勿重复审批");
        }

        int advanced = requestMapper.update(null, new LambdaUpdateWrapper<SysApprovalRequest>()
                .eq(SysApprovalRequest::getId, requestId)
                .eq(SysApprovalRequest::getStatus, "PENDING")
                .setSql("current_approvals = COALESCE(current_approvals, 0) + 1"));
        if (advanced == 0) {
            throw new BusinessException(40901, "审批已结束");
        }
        int completed = requestMapper.update(null, new LambdaUpdateWrapper<SysApprovalRequest>()
                .eq(SysApprovalRequest::getId, requestId)
                .eq(SysApprovalRequest::getStatus, "PENDING")
                .ge(SysApprovalRequest::getCurrentApprovals, entity.getRequiredApprovals())
                .set(SysApprovalRequest::getStatus, "APPROVED"));
        SysApprovalRequest refreshed = requestMapper.selectById(requestId);
        if (completed == 1) {
            publishCompleted(refreshed, fromJson(refreshed.getPayloadJson()));
            notificationService.notify(refreshed.getRequesterId(), "APPROVAL",
                    "审批已通过", "审批申请已通过，相关变更已生效", "/approvals");
        } else if (!"APPROVED".equals(refreshed.getStatus())) {
            notificationService.notify(refreshed.getRequesterId(), "APPROVAL",
                    "审批进度更新",
                    "已有 " + refreshed.getCurrentApprovals() + "/"
                            + refreshed.getRequiredApprovals() + " 人同意",
                    "/approvals");
        }
        auditService.record("APPROVAL", "approval", "APPROVE", "APPROVAL_REQUEST", requestId,
                null, Map.of("comment", comment == null ? "" : comment), null, requestId);
        return toView(refreshed, user);
    }

    @Transactional
    public ApprovalDtos.ApprovalView reject(Long requestId, String comment, LoginUser user) {
        SysApprovalRequest entity = loadPending(requestId);
        if (Objects.equals(entity.getRequesterId(), user.userId())) {
            throw new BusinessException(40301, "不能审批自己提交的申请");
        }
        ensureNoAction(requestId, user.userId());

        SysApprovalAction action = new SysApprovalAction();
        action.setRequestId(requestId);
        action.setApproverId(user.userId());
        action.setAction("REJECT");
        action.setComment(comment);
        try {
            actionMapper.insert(action);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(40901, "请勿重复审批");
        }

        int rejected = requestMapper.update(null, new LambdaUpdateWrapper<SysApprovalRequest>()
                .eq(SysApprovalRequest::getId, requestId)
                .eq(SysApprovalRequest::getStatus, "PENDING")
                .set(SysApprovalRequest::getStatus, "REJECTED"));
        if (rejected == 0) {
            throw new BusinessException(40901, "审批已结束");
        }
        SysApprovalRequest refreshed = requestMapper.selectById(requestId);
        notificationService.notify(refreshed.getRequesterId(), "APPROVAL",
                "审批被驳回", StringUtils.hasText(comment) ? comment : "审批申请已被驳回", "/approvals");
        auditService.record("APPROVAL", "approval", "REJECT", "APPROVAL_REQUEST", requestId,
                null, Map.of("comment", comment == null ? "" : comment), null, requestId);
        return toView(refreshed, user);
    }

    @Transactional
    public ApprovalDtos.ApprovalView cancel(Long requestId, LoginUser user) {
        SysApprovalRequest entity = requestMapper.selectById(requestId);
        if (entity == null) {
            throw new BusinessException(40401, "审批申请不存在");
        }
        if (!Objects.equals(entity.getRequesterId(), user.userId())) {
            throw new BusinessException(40301, "只能撤回自己的申请");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new BusinessException(40901, "当前状态不可撤回");
        }
        int cancelled = requestMapper.update(null, new LambdaUpdateWrapper<SysApprovalRequest>()
                .eq(SysApprovalRequest::getId, requestId)
                .eq(SysApprovalRequest::getStatus, "PENDING")
                .set(SysApprovalRequest::getStatus, "CANCELLED"));
        if (cancelled == 0) {
            throw new BusinessException(40901, "当前状态不可撤回");
        }
        SysApprovalRequest refreshed = requestMapper.selectById(requestId);
        auditService.record("APPROVAL", "approval", "CANCEL", "APPROVAL_REQUEST", requestId,
                null, null, null, requestId);
        return toView(refreshed, user);
    }

    public PageResult<ApprovalDtos.ApprovalView> page(long page, long size,
                                                      String status,
                                                      boolean mine,
                                                      boolean pendingForMe,
                                                      LoginUser user) {
        Page<SysApprovalRequest> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<SysApprovalRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(status), SysApprovalRequest::getStatus, status);
        boolean privileged = user.hasPermission("approval:approve") || user.hasPermission("audit:read");
        if (!privileged) {
            wrapper.eq(SysApprovalRequest::getRequesterId, user.userId());
        }
        if (mine) {
            wrapper.eq(SysApprovalRequest::getRequesterId, user.userId());
        }
        if (pendingForMe) {
            wrapper.eq(SysApprovalRequest::getStatus, "PENDING")
                    .ne(SysApprovalRequest::getRequesterId, user.userId());
            List<Long> acted = actionMapper.selectList(new LambdaQueryWrapper<SysApprovalAction>()
                            .eq(SysApprovalAction::getApproverId, user.userId()))
                    .stream().map(SysApprovalAction::getRequestId).toList();
            if (!acted.isEmpty()) {
                wrapper.notIn(SysApprovalRequest::getId, acted);
            }
        }
        wrapper.orderByDesc(SysApprovalRequest::getCreatedAt);
        Page<SysApprovalRequest> result = requestMapper.selectPage(pageRequest, wrapper);
        List<ApprovalDtos.ApprovalView> views = result.getRecords().stream()
                .map(entity -> toView(entity, user))
                .toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), views);
    }

    public ApprovalDtos.ApprovalView get(Long requestId, LoginUser user) {
        SysApprovalRequest entity = requestMapper.selectById(requestId);
        if (entity == null) {
            throw new BusinessException(40401, "审批申请不存在");
        }
        boolean privileged = user.hasPermission("approval:approve") || user.hasPermission("audit:read");
        if (!privileged && !Objects.equals(entity.getRequesterId(), user.userId())) {
            throw new BusinessException(40301, "无权查看该审批申请");
        }
        return toView(entity, user);
    }

    public List<SysApprovalPolicy> policies() {
        return policyMapper.selectList(new LambdaQueryWrapper<SysApprovalPolicy>()
                .orderByAsc(SysApprovalPolicy::getPolicyCode));
    }

    @Transactional
    public SysApprovalPolicy updatePolicy(Long id, SysApprovalPolicy changes) {
        SysApprovalPolicy existing = policyMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(40401, "审批策略不存在");
        }
        String before = existing.toString();
        if (changes.getName() != null) {
            existing.setName(changes.getName());
        }
        if (changes.getQuorum() != null) {
            existing.setQuorum(changes.getQuorum());
        }
        if (changes.getApplicantIncluded() != null) {
            existing.setApplicantIncluded(changes.getApplicantIncluded());
        }
        if (changes.getChangeQuorum() != null) {
            existing.setChangeQuorum(changes.getChangeQuorum());
        }
        if (changes.getEnabled() != null) {
            existing.setEnabled(changes.getEnabled());
        }
        policyMapper.updateById(existing);
        auditService.record("CONFIG", "approval", "UPDATE_POLICY", "APPROVAL_POLICY", id,
                before, existing, null, null);
        return existing;
    }

    public void applyApprovedChange(String policyCode, Long approvalId, Map<String, Object> payload) {
        if ("CONFIG_CHANGE".equals(policyCode)) {
            Object policyId = payload.get("policyId");
            if (policyId != null) {
                SysApprovalPolicy policy = policyMapper.selectById(Long.valueOf(String.valueOf(policyId)));
                if (policy != null) {
                    if (payload.get("quorum") != null) {
                        policy.setQuorum(Integer.valueOf(String.valueOf(payload.get("quorum"))));
                    }
                    if (payload.get("changeQuorum") != null) {
                        policy.setChangeQuorum(Integer.valueOf(String.valueOf(payload.get("changeQuorum"))));
                    }
                    if (payload.get("applicantIncluded") != null) {
                        policy.setApplicantIncluded(Boolean.valueOf(String.valueOf(payload.get("applicantIncluded"))));
                    }
                    if (payload.get("enabled") != null) {
                        policy.setEnabled(Boolean.valueOf(String.valueOf(payload.get("enabled"))));
                    }
                    policyMapper.updateById(policy);
                    auditService.record("CONFIG", "approval", "UPDATE_POLICY_APPROVED",
                            "APPROVAL_POLICY", policy.getId(), null, policy,
                            "审批通过后生效", approvalId);
                }
            }
        }
    }

    private SysApprovalRequest loadPending(Long requestId) {
        SysApprovalRequest entity = requestMapper.selectById(requestId);
        if (entity == null) {
            throw new BusinessException(40401, "审批申请不存在");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new BusinessException(40901, "审批申请已结束");
        }
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            entity.setStatus("EXPIRED");
            requestMapper.updateById(entity);
            throw new BusinessException(40902, "审批申请已过期");
        }
        return entity;
    }

    private void ensureNoAction(Long requestId, Long approverId) {
        Long count = actionMapper.selectCount(new LambdaQueryWrapper<SysApprovalAction>()
                .eq(SysApprovalAction::getRequestId, requestId)
                .eq(SysApprovalAction::getApproverId, approverId));
        if (count != null && count > 0) {
            throw new BusinessException(40901, "您已处理过该申请");
        }
    }

    private void publishCompleted(SysApprovalRequest entity, Map<String, Object> payload) {
        eventPublisher.publishEvent(new ApprovalCompletedEvent(
                entity.getId(),
                entity.getPolicyCode(),
                entity.getEntityType(),
                entity.getEntityId(),
                payload,
                entity.getRequesterId(),
                entity.getReason()
        ));
    }

    private ApprovalDtos.ApprovalView toView(SysApprovalRequest entity, LoginUser user) {
        if ("PENDING".equals(entity.getStatus())
                && entity.getExpiresAt() != null
                && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            entity.setStatus("EXPIRED");
            requestMapper.updateById(entity);
        }
        SysApprovalPolicy policy = policyMapper.selectByCode(entity.getPolicyCode());
        List<SysApprovalAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<SysApprovalAction>()
                        .eq(SysApprovalAction::getRequestId, entity.getId())
                        .orderByAsc(SysApprovalAction::getCreatedAt));
        Map<Long, SysUser> users = loadUsers(actions);

        List<ApprovalDtos.ActionView> actionViews = new ArrayList<>();
        String myAction = null;
        for (SysApprovalAction action : actions) {
            SysUser actor = users.get(action.getApproverId());
            actionViews.add(new ApprovalDtos.ActionView(
                    action.getId(),
                    action.getApproverId(),
                    actor == null ? null : actor.getDisplayName(),
                    action.getAction(),
                    action.getComment(),
                    action.getCreatedAt()
            ));
            if (Objects.equals(action.getApproverId(), user.userId())) {
                myAction = action.getAction();
            }
        }
        SysUser requester = userMapper.selectById(entity.getRequesterId());
        boolean pending = "PENDING".equals(entity.getStatus());
        Map<String, Object> payload = fromJson(entity.getPayloadJson());
        List<Long> conflicts = pending
                ? conflictService.pendingConflicts(entity.getPolicyCode(), payload,
                        entity.getEntityType(), entity.getEntityId(), entity.getId())
                : Collections.emptyList();
        List<String> staleFields = pending ? conflictService.staleFields(entity) : Collections.emptyList();
        // Internal baseline used for stale checks; keep it out of API responses.
        payload.remove("baseline");
        return new ApprovalDtos.ApprovalView(
                entity.getId(),
                entity.getPolicyCode(),
                policy == null ? null : policy.getName(),
                entity.getEntityType(),
                entity.getEntityId(),
                payload,
                entity.getReason(),
                entity.getStatus(),
                entity.getRequesterId(),
                requester == null ? null : requester.getDisplayName(),
                entity.getCurrentApprovals() == null ? 0 : entity.getCurrentApprovals(),
                entity.getRequiredApprovals() == null ? 0 : entity.getRequiredApprovals(),
                myAction,
                entity.getExpiresAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                !conflicts.isEmpty(),
                conflicts,
                staleFields,
                actionViews
        );
    }

    private Map<Long, SysUser> loadUsers(List<SysApprovalAction> actions) {
        Set<Long> ids = actions.stream().map(SysApprovalAction::getApproverId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private int expireHours() {
        return configInt("approval.expireHours", 72);
    }

    private int configInt(String key, int defaultValue) {
        SysConfig config = configMapper.selectByKey(key);
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(config.getConfigValue());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(40001, "审批数据格式错误");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }
}
