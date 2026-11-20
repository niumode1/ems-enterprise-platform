package com.company.ems.recovery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.notification.NotificationService;
import com.company.ems.recovery.entity.RecoveryRequest;
import com.company.ems.recovery.entity.RecoverySignature;
import com.company.ems.recovery.mapper.RecoveryRequestMapper;
import com.company.ems.recovery.mapper.RecoverySignatureMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysRole;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.entity.SysUserRole;
import com.company.ems.system.mapper.SysRoleMapper;
import com.company.ems.system.mapper.SysUserMapper;
import com.company.ems.system.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Emergency recovery path. A request is either signed by the configured number
 * of signer roles (dual-sign) or executed directly by a deployment account.
 * Every step is audited under SECURITY/recovery; passwords are stored only as
 * BCrypt hashes and are never returned by any endpoint.
 */
@Service
public class RecoveryService {

    private static final Set<String> ACTIONS = Set.of("RESET_PASSWORD", "UNLOCK", "GRANT_ADMIN");

    private final RecoveryRequestMapper requestMapper;
    private final RecoverySignatureMapper signatureMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final ConfigService configService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public RecoveryService(RecoveryRequestMapper requestMapper,
                           RecoverySignatureMapper signatureMapper,
                           SysUserMapper userMapper,
                           SysUserRoleMapper userRoleMapper,
                           SysRoleMapper roleMapper,
                           ConfigService configService,
                           AuditService auditService,
                           NotificationService notificationService,
                           PasswordEncoder passwordEncoder,
                           ObjectMapper objectMapper) {
        this.requestMapper = requestMapper;
        this.signatureMapper = signatureMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.configService = configService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    public RecoveryDtos.StatusView status(LoginUser user) {
        boolean enabled = enabled();
        List<String> signers = tokenList("recovery.signers", List.of("BOSS", "AUDITOR"));
        boolean deploymentEnabled = configService.getBoolean("recovery.deploymentAccount.enabled", true);
        return new RecoveryDtos.StatusView(
                enabled,
                mode(),
                requiredSigners(),
                signers,
                tokenList("recovery.allowedActions", new ArrayList<>(ACTIONS)),
                deploymentEnabled,
                isDeploymentAccount(user),
                signerRole(user) != null,
                enabled && user.hasPermission("config:recovery"));
    }

    @Transactional
    public RecoveryDtos.RequestResult request(RecoveryDtos.Request request, LoginUser user) {
        if (!enabled()) {
            throw new BusinessException(40001, "紧急恢复未启用");
        }
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase();
        if (!ACTIONS.contains(action)) {
            throw new BusinessException(40001, "不支持的恢复操作");
        }
        if (!tokenList("recovery.allowedActions", new ArrayList<>(ACTIONS)).contains(action)) {
            throw new BusinessException(40001, "该恢复操作已被关闭");
        }
        SysUser target = resolveTarget(request);
        String reason = request.getReason() == null ? null : request.getReason().strip();

        Map<String, Object> payload = new HashMap<>();
        if ("RESET_PASSWORD".equals(action)) {
            validatePassword(request.getNewPassword());
            payload.put("passwordHash", passwordEncoder.encode(request.getNewPassword()));
        } else if (request.getNewPassword() != null) {
            throw new BusinessException(40001, "该操作不需要新密码");
        }

        boolean deployment = isDeploymentAccount(user);
        boolean immediate = "DIRECT".equals(mode()) || deployment;
        LocalDateTime now = LocalDateTime.now();

        RecoveryRequest entity = new RecoveryRequest();
        entity.setAction(action);
        entity.setTargetUserId(target.getId());
        entity.setPayloadJson(payload.isEmpty() ? null : toJson(payload));
        entity.setReason(reason);
        entity.setRequesterId(user.userId());
        entity.setRequiredSigners(immediate ? 0 : requiredSigners());
        entity.setStatus(immediate ? "APPLIED" : "PENDING");
        entity.setExpiresAt(immediate ? null : now.plusHours(expireHours()));
        entity.setAppliedAt(immediate ? now : null);
        entity.setCreatedAt(now);
        requestMapper.insert(entity);

        auditService.record("SECURITY", "recovery", "REQUEST", "RECOVERY_REQUEST", entity.getId(),
                null, Map.of("action", action, "targetUserId", target.getId(),
                        "targetUsername", target.getUsername(), "immediate", immediate),
                reason, null);

        if (immediate) {
            applyAction(entity, target);
            auditService.record("SECURITY", "recovery", "APPLY", "RECOVERY_REQUEST", entity.getId(),
                    null, Map.of("action", action, "targetUserId", target.getId(),
                            "executedBy", user.username(), "mode", deployment ? "DEPLOYMENT" : "DIRECT"),
                    reason, null);
            notifyApplied(entity, target);
            return new RecoveryDtos.RequestResult("APPLIED", toViews(List.of(entity)).get(0),
                    deployment ? "部署级恢复账号已直接执行" : "已按 DIRECT 模式直接执行");
        }

        notifySigners(entity, user, target);
        return new RecoveryDtos.RequestResult("PENDING", toViews(List.of(entity)).get(0),
                "恢复申请已提交，等待 " + entity.getRequiredSigners() + " 名签署人确认");
    }

    @Transactional
    public RecoveryDtos.RequestView sign(Long id, String comment, LoginUser user) {
        RecoveryRequest entity = loadPending(id);
        String role = signerRole(user);
        if (role == null) {
            throw new BusinessException(40301, "当前角色无恢复申请签署资格");
        }
        if (Objects.equals(entity.getRequesterId(), user.userId())) {
            throw new BusinessException(40301, "不能签署自己提交的恢复申请");
        }
        Long existing = signatureMapper.selectCount(new LambdaQueryWrapper<RecoverySignature>()
                .eq(RecoverySignature::getRequestId, id)
                .eq(RecoverySignature::getSignerId, user.userId()));
        if (existing != null && existing > 0) {
            throw new BusinessException(40901, "您已签署过该恢复申请");
        }

        RecoverySignature signature = new RecoverySignature();
        signature.setRequestId(id);
        signature.setSignerId(user.userId());
        signature.setSignerRole(role);
        signature.setComment(comment);
        signature.setCreatedAt(LocalDateTime.now());
        signatureMapper.insert(signature);
        auditService.record("SECURITY", "recovery", "SIGN", "RECOVERY_REQUEST", id,
                null, Map.of("signerId", user.userId(), "signerRole", role,
                        "comment", comment == null ? "" : comment), null, null);

        long count = signatureMapper.selectCount(new LambdaQueryWrapper<RecoverySignature>()
                .eq(RecoverySignature::getRequestId, id));
        if (count >= entity.getRequiredSigners()) {
            entity.setStatus("APPLIED");
            entity.setAppliedAt(LocalDateTime.now());
            requestMapper.updateById(entity);
            SysUser target = requireUser(entity.getTargetUserId());
            applyAction(entity, target);
            auditService.record("SECURITY", "recovery", "APPLY", "RECOVERY_REQUEST", id,
                    null, Map.of("action", entity.getAction(), "targetUserId", target.getId(),
                            "signatures", count), entity.getReason(), null);
            notifyApplied(entity, target);
        } else {
            notificationService.notify(entity.getRequesterId(), "SECURITY", "恢复申请签署进度",
                    "已有 " + count + "/" + entity.getRequiredSigners() + " 名签署人确认", "/recovery");
        }
        return toViews(List.of(entity)).get(0);
    }

    @Transactional
    public RecoveryDtos.RequestView reject(Long id, String comment, LoginUser user) {
        RecoveryRequest entity = loadPending(id);
        if (signerRole(user) == null) {
            throw new BusinessException(40301, "当前角色无恢复申请签署资格");
        }
        if (Objects.equals(entity.getRequesterId(), user.userId())) {
            throw new BusinessException(40301, "不能处理自己提交的恢复申请");
        }
        entity.setStatus("REJECTED");
        requestMapper.updateById(entity);
        auditService.record("SECURITY", "recovery", "REJECT", "RECOVERY_REQUEST", id,
                null, Map.of("signerId", user.userId(), "comment", comment == null ? "" : comment),
                comment, null);
        notificationService.notify(entity.getRequesterId(), "SECURITY", "恢复申请被驳回",
                StringUtils.hasText(comment) ? comment : "恢复申请已被签署人驳回", "/recovery");
        return toViews(List.of(entity)).get(0);
    }

    @Transactional
    public RecoveryDtos.RequestView cancel(Long id, LoginUser user) {
        RecoveryRequest entity = requestMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(40401, "恢复申请不存在");
        }
        if (!Objects.equals(entity.getRequesterId(), user.userId())) {
            throw new BusinessException(40301, "只能撤回自己的恢复申请");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new BusinessException(40901, "当前状态不可撤回");
        }
        entity.setStatus("CANCELLED");
        requestMapper.updateById(entity);
        auditService.record("SECURITY", "recovery", "CANCEL", "RECOVERY_REQUEST", id,
                null, null, null, null);
        return toViews(List.of(entity)).get(0);
    }

    public List<RecoveryDtos.RequestView> list(LoginUser user) {
        LambdaQueryWrapper<RecoveryRequest> wrapper = new LambdaQueryWrapper<>();
        boolean privileged = user.hasPermission("config:recovery") || user.hasPermission("audit:read");
        if (!privileged) {
            if (signerRole(user) != null) {
                wrapper.and(w -> w.eq(RecoveryRequest::getStatus, "PENDING")
                        .or().eq(RecoveryRequest::getRequesterId, user.userId()));
            } else {
                wrapper.eq(RecoveryRequest::getRequesterId, user.userId());
            }
        }
        wrapper.orderByDesc(RecoveryRequest::getCreatedAt).last("LIMIT 200");
        List<RecoveryRequest> requests = requestMapper.selectList(wrapper);
        expireStale(requests);
        return toViews(requests);
    }

    public RecoveryDtos.RequestView get(Long id, LoginUser user) {
        RecoveryRequest entity = expiryCheck(requestMapper.selectById(id));
        if (entity == null) {
            throw new BusinessException(40401, "恢复申请不存在");
        }
        boolean privileged = user.hasPermission("config:recovery") || user.hasPermission("audit:read");
        boolean involved = Objects.equals(entity.getRequesterId(), user.userId())
                || signerRole(user) != null;
        if (!privileged && !involved) {
            throw new BusinessException(40301, "无权查看该恢复申请");
        }
        return toViews(List.of(entity)).get(0);
    }

    private void applyAction(RecoveryRequest entity, SysUser target) {
        switch (entity.getAction()) {
            case "RESET_PASSWORD" -> {
                String hash = payload(entity).get("passwordHash") == null
                        ? null : String.valueOf(payload(entity).get("passwordHash"));
                if (!StringUtils.hasText(hash)) {
                    throw new BusinessException(40901, "恢复申请缺少密码数据");
                }
                userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                        .eq(SysUser::getId, target.getId())
                        .set(SysUser::getPasswordHash, hash)
                        .set(SysUser::getFailedAttempts, 0)
                        .set(SysUser::getLockedUntil, null));
                notificationService.notify(target.getId(), "SECURITY", "密码已由恢复通道重置",
                        "您的密码已通过紧急恢复流程重置，请使用新密码登录", "/");
            }
            case "UNLOCK" -> {
                userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                        .eq(SysUser::getId, target.getId())
                        .set(SysUser::getFailedAttempts, 0)
                        .set(SysUser::getLockedUntil, null));
                notificationService.notify(target.getId(), "SECURITY", "账号已解锁",
                        "您的账号已通过紧急恢复流程解锁", "/");
            }
            case "GRANT_ADMIN" -> grantAdmin(entity, target);
            default -> throw new BusinessException(40901, "不支持的恢复操作");
        }
    }

    private void grantAdmin(RecoveryRequest entity, SysUser target) {
        SysRole adminRole = roleMapper.selectByCode("SYS_ADMIN");
        if (adminRole == null) {
            throw new BusinessException(40901, "管理员角色不存在");
        }
        int days = configService.getInt("recovery.adminGrantDays", 1);
        LocalDate today = LocalDate.now();
        LocalDate end = days <= 0 ? null : today.plusDays(days);

        List<SysUserRole> assignments = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, target.getId())
                .eq(SysUserRole::getRoleId, adminRole.getId()));
        SysUserRole effective = assignments.stream()
                .filter(row -> "ACTIVE".equals(row.getStatus()))
                .filter(row -> row.getStartDate() == null || !row.getStartDate().isAfter(today))
                .filter(row -> row.getEndDate() == null || !row.getEndDate().isBefore(today))
                .findFirst().orElse(null);
        if (effective != null) {
            if (effective.getEndDate() != null && (end == null || end.isAfter(effective.getEndDate()))) {
                if (end == null) {
                    userRoleMapper.update(null, new LambdaUpdateWrapper<SysUserRole>()
                            .eq(SysUserRole::getId, effective.getId())
                            .set(SysUserRole::getEndDate, null));
                } else {
                    effective.setEndDate(end);
                    userRoleMapper.updateById(effective);
                }
            }
        } else {
            SysUserRole row = new SysUserRole();
            row.setUserId(target.getId());
            row.setRoleId(adminRole.getId());
            row.setStatus("ACTIVE");
            row.setStartDate(today);
            row.setEndDate(end);
            row.setNote("紧急恢复通道");
            row.setGrantedBy(entity.getRequesterId());
            userRoleMapper.insert(row);
        }
        notificationService.notify(target.getId(), "SECURITY", "已获得临时管理员权限",
                "紧急恢复流程已为您开通 SYS_ADMIN" + (end == null ? "（长期，请尽快调整）" : "，有效期至 " + end),
                "/users");
    }

    private void notifySigners(RecoveryRequest entity, LoginUser requester, SysUser target) {
        List<String> roles = tokenList("recovery.signers", List.of("BOSS", "AUDITOR"));
        if (roles.isEmpty()) {
            return;
        }
        List<Long> userIds = userMapper.selectUserIdsByRoleCodes(roles);
        for (Long userId : userIds) {
            if (Objects.equals(userId, requester.userId())) {
                continue;
            }
            notificationService.notify(userId, "SECURITY", "紧急恢复申请待签署",
                    entity.getAction() + " / " + target.getUsername()
                            + (StringUtils.hasText(entity.getReason()) ? "：" + entity.getReason() : ""),
                    "/recovery");
        }
    }

    private void notifyApplied(RecoveryRequest entity, SysUser target) {
        notificationService.notify(entity.getRequesterId(), "SECURITY", "恢复操作已执行",
                entity.getAction() + " 已对账号 " + target.getUsername() + " 执行", "/recovery");
    }

    private RecoveryRequest loadPending(Long id) {
        RecoveryRequest entity = expiryCheck(requestMapper.selectById(id));
        if (entity == null) {
            throw new BusinessException(40401, "恢复申请不存在");
        }
        if (!"PENDING".equals(entity.getStatus())) {
            throw new BusinessException(40901, "恢复申请已结束");
        }
        return entity;
    }

    private RecoveryRequest expiryCheck(RecoveryRequest entity) {
        if (entity != null && "PENDING".equals(entity.getStatus())
                && entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            entity.setStatus("EXPIRED");
            requestMapper.updateById(entity);
        }
        return entity;
    }

    private void expireStale(List<RecoveryRequest> requests) {
        for (RecoveryRequest entity : requests) {
            expiryCheck(entity);
        }
    }

    private SysUser resolveTarget(RecoveryDtos.Request request) {
        SysUser target = null;
        if (request.getUserId() != null) {
            target = userMapper.selectById(request.getUserId());
        } else if (StringUtils.hasText(request.getUsername())) {
            target = userMapper.selectByUsername(request.getUsername().trim());
        } else {
            throw new BusinessException(40001, "请指定恢复目标账号");
        }
        if (target == null) {
            throw new BusinessException(40401, "目标账号不存在");
        }
        return target;
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(40401, "目标账号不存在");
        }
        return user;
    }

    private void validatePassword(String password) {
        int minLength = configService.getInt("security.password.minLength", 8);
        if (password == null || password.length() < minLength) {
            throw new BusinessException(40001, "密码长度不能少于 " + minLength + " 位");
        }
    }

    private List<RecoveryDtos.RequestView> toViews(List<RecoveryRequest> requests) {
        if (requests.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (RecoveryRequest request : requests) {
            userIds.add(request.getRequesterId());
            userIds.add(request.getTargetUserId());
        }
        List<RecoverySignature> signatures = signatureMapper.selectList(
                new LambdaQueryWrapper<RecoverySignature>()
                        .in(RecoverySignature::getRequestId,
                                requests.stream().map(RecoveryRequest::getId).toList())
                        .orderByAsc(RecoverySignature::getCreatedAt));
        signatures.forEach(signature -> userIds.add(signature.getSignerId()));
        userIds.remove(null);
        Map<Long, SysUser> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, List<RecoverySignature>> byRequest = signatures.stream()
                .collect(Collectors.groupingBy(RecoverySignature::getRequestId));

        return requests.stream().map(request -> {
            SysUser requester = users.get(request.getRequesterId());
            SysUser target = users.get(request.getTargetUserId());
            List<RecoverySignature> requestSignatures = byRequest.getOrDefault(request.getId(), List.of());
            List<RecoveryDtos.SignatureView> signatureViews = requestSignatures.stream()
                    .map(signature -> {
                        SysUser signer = users.get(signature.getSignerId());
                        return new RecoveryDtos.SignatureView(
                                signature.getId(),
                                signature.getSignerId(),
                                signer == null ? null : signer.getDisplayName(),
                                signature.getSignerRole(),
                                signature.getComment(),
                                signature.getCreatedAt());
                    }).toList();
            return new RecoveryDtos.RequestView(
                    request.getId(),
                    request.getAction(),
                    request.getTargetUserId(),
                    target == null ? null : target.getUsername(),
                    target == null ? null : target.getDisplayName(),
                    request.getReason(),
                    request.getStatus(),
                    request.getRequesterId(),
                    requester == null ? null : requester.getDisplayName(),
                    request.getRequiredSigners() == null ? 0 : request.getRequiredSigners(),
                    signatureViews.size(),
                    request.getExpiresAt(),
                    request.getAppliedAt(),
                    request.getCreatedAt(),
                    signatureViews);
        }).toList();
    }

    private boolean enabled() {
        return configService.getBoolean("recovery.enabled", true);
    }

    private String mode() {
        String mode = configService.getString("recovery.mode", "DUAL_SIGN");
        String normalized = mode == null ? "DUAL_SIGN" : mode.trim().toUpperCase();
        return List.of("DUAL_SIGN", "DIRECT").contains(normalized) ? normalized : "DUAL_SIGN";
    }

    private int requiredSigners() {
        return Math.max(1, configService.getInt("recovery.requiredSigners", 2));
    }

    private int expireHours() {
        return Math.max(1, configService.getInt("recovery.expireHours", 24));
    }

    private String signerRole(LoginUser user) {
        for (String role : tokenList("recovery.signers", List.of("BOSS", "AUDITOR"))) {
            if (user.roles().contains(role)) {
                return role;
            }
        }
        return null;
    }

    private boolean isDeploymentAccount(LoginUser user) {
        if (!configService.getBoolean("recovery.deploymentAccount.enabled", true)) {
            return false;
        }
        return tokenList("recovery.deploymentAccount.usernames", List.of("deploy"))
                .stream().anyMatch(name -> name.equalsIgnoreCase(user.username()));
    }

    private List<String> tokenList(String key, List<String> defaults) {
        String value = configService.getString(key, null);
        if (!StringUtils.hasText(value)) {
            return defaults;
        }
        List<String> tokens = new ArrayList<>();
        for (String token : value.split(",")) {
            if (StringUtils.hasText(token)) {
                tokens.add(token.trim().toUpperCase());
            }
        }
        return tokens;
    }

    private Map<String, Object> payload(RecoveryRequest entity) {
        if (!StringUtils.hasText(entity.getPayloadJson())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(entity.getPayloadJson(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception ex) {
            throw new BusinessException(40901, "恢复申请数据损坏");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(40001, "恢复申请数据格式错误");
        }
    }
}
