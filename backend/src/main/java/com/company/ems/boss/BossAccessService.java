package com.company.ems.boss;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.ems.approval.ApprovalDtos;
import com.company.ems.approval.ApprovalService;
import com.company.ems.audit.AuditService;
import com.company.ems.boss.entity.BossAccessGrant;
import com.company.ems.boss.mapper.BossAccessGrantMapper;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Boss sensitive-field access. A grant only removes masking for fields the
 * requester's role can already view; it never widens field visibility.
 * Every policy knob (mode, duration, reason, approver count, all-employees)
 * is read from sys_config so each company can set its own rules.
 */
@Service
public class BossAccessService {

    private final BossAccessGrantMapper grantMapper;
    private final EmployeeMapper employeeMapper;
    private final SysUserMapper userMapper;
    private final ConfigService configService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public BossAccessService(BossAccessGrantMapper grantMapper,
                             EmployeeMapper employeeMapper,
                             SysUserMapper userMapper,
                             ConfigService configService,
                             ApprovalService approvalService,
                             AuditService auditService,
                             NotificationService notificationService) {
        this.grantMapper = grantMapper;
        this.employeeMapper = employeeMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    public BossAccessDtos.StatusView status(LoginUser user) {
        boolean enabled = isEnabled();
        String mode = mode();
        int max = maxDuration();
        int approverCount = approverCount();
        boolean canRequest = enabled
                && !"DISABLED".equals(mode)
                && user.hasPermission("employee:sensitive:request")
                && !user.hasPermission("employee:sensitive:read");
        List<BossAccessDtos.GrantView> grants = toViews(activeGrants(user.userId()));
        return new BossAccessDtos.StatusView(
                mode,
                enabled,
                canRequest,
                configService.getBoolean("boss.access.requireReason", true),
                approverCount,
                defaultDuration(),
                max,
                configService.getBoolean("boss.access.allowAllEmployees", false),
                grants);
    }

    @Transactional
    public BossAccessDtos.RequestResult request(BossAccessDtos.Request request, LoginUser user) {
        if (!isEnabled()) {
            throw new BusinessException(40001, "老板敏感信息访问未启用");
        }
        String mode = mode();
        if ("DISABLED".equals(mode)) {
            throw new BusinessException(40001, "老板敏感信息访问已关闭");
        }
        if (!user.hasPermission("employee:sensitive:request")) {
            throw new BusinessException(40301, "无权申请敏感信息访问");
        }
        if (user.hasPermission("employee:sensitive:read")) {
            return new BossAccessDtos.RequestResult("NOT_REQUIRED", null, null,
                    "您已具备敏感信息查看权限，无需申请", null);
        }

        Long employeeId = request.getEmployeeId();
        if (employeeId != null && Objects.equals(employeeId, user.employeeId())) {
            throw new BusinessException(40001, "无需申请自己的敏感信息");
        }
        if (employeeId != null && employeeMapper.selectById(employeeId) == null) {
            throw new BusinessException(40401, "员工不存在");
        }
        if (employeeId == null && !configService.getBoolean("boss.access.allowAllEmployees", false)) {
            throw new BusinessException(40001, "未开启全部员工申请，请指定员工");
        }

        String reason = request.getReason() == null ? null : request.getReason().strip();
        if (configService.getBoolean("boss.access.requireReason", true) && !StringUtils.hasText(reason)) {
            throw new BusinessException(40001, "请填写敏感信息访问事由");
        }
        int duration = durationMinutes(request.getDurationMinutes());

        if ("DIRECT".equals(mode)) {
            BossAccessGrant grant = createGrant(user.userId(), employeeId, reason, duration, null);
            return new BossAccessDtos.RequestResult("GRANTED", null, grant.getId(),
                    "已直接授予敏感信息访问权限", toViews(List.of(grant)).get(0));
        }

        Map<String, Object> payload = new HashMap<>();
        if (employeeId != null) {
            payload.put("employeeId", employeeId);
        }
        payload.put("durationMinutes", duration);
        payload.put("reason", reason == null ? "" : reason);

        ApprovalDtos.SubmitRequest submit = new ApprovalDtos.SubmitRequest();
        submit.setPolicyCode("BOSS_ACCESS");
        submit.setEntityType("EMPLOYEE");
        submit.setEntityId(employeeId);
        submit.setPayload(payload);
        submit.setReason(reason);
        ApprovalDtos.ApprovalView approval = approvalService.submit(submit, user);
        return new BossAccessDtos.RequestResult("PENDING_APPROVAL", approval.id(), null,
                "申请已提交，审批通过后可查看敏感字段", null);
    }

    /**
     * Called from the approval listener after a BOSS_ACCESS request is approved.
     */
    @Transactional
    public BossAccessGrant applyApproved(Map<String, Object> payload, Long approvalId, Long requesterId) {
        if (requesterId == null) {
            return null;
        }
        Long employeeId = payload.get("employeeId") == null
                ? null : Long.valueOf(String.valueOf(payload.get("employeeId")));
        String reason = payload.get("reason") == null ? null : String.valueOf(payload.get("reason"));
        Integer duration = payload.get("durationMinutes") == null
                ? null : Integer.valueOf(String.valueOf(payload.get("durationMinutes")));
        BossAccessGrant grant = createGrant(requesterId, employeeId, reason,
                durationMinutes(duration), approvalId);
        notificationService.notify(requesterId, "SECURITY", "敏感信息访问已开通",
                "您可以在 " + grant.getExpiresAt() + " 前查看获批范围内的敏感字段"
                        + (StringUtils.hasText(reason) ? "（事由：" + reason + "）" : ""),
                "/employees");
        return grant;
    }

    @Transactional
    public BossAccessDtos.GrantView revoke(Long id, LoginUser user) {
        BossAccessGrant grant = grantMapper.selectById(id);
        if (grant == null) {
            throw new BusinessException(40401, "授权记录不存在");
        }
        boolean privileged = user.hasPermission("audit:read") || user.hasPermission("config:read");
        if (!privileged && !Objects.equals(grant.getUserId(), user.userId())) {
            throw new BusinessException(40301, "无权撤销该授权");
        }
        if (!isGrantActive(grant)) {
            throw new BusinessException(40901, "该授权已失效");
        }
        grant.setStatus("REVOKED");
        grant.setRevokedAt(LocalDateTime.now());
        grant.setRevokedBy(user.userId());
        grantMapper.updateById(grant);
        auditService.record("SECURITY", "boss", "REVOKE", "BOSS_ACCESS_GRANT", grant.getId(),
                Map.of("status", "ACTIVE"), Map.of("status", "REVOKED"), null, grant.getApprovalId());
        if (!Objects.equals(grant.getUserId(), user.userId())) {
            notificationService.notify(grant.getUserId(), "SECURITY", "敏感信息访问已撤销",
                    user.displayName() + " 撤销了您的敏感信息访问授权", "/employees");
        }
        return toViews(List.of(grant)).get(0);
    }

    public List<BossAccessDtos.GrantView> list(LoginUser user, boolean all) {
        if (all && !(user.hasPermission("audit:read") || user.hasPermission("config:read"))) {
            throw new BusinessException(40301, "无权查看全部授权记录");
        }
        LambdaQueryWrapper<BossAccessGrant> wrapper = new LambdaQueryWrapper<>();
        if (!all) {
            wrapper.eq(BossAccessGrant::getUserId, user.userId());
        }
        wrapper.orderByDesc(BossAccessGrant::getCreatedAt).last("LIMIT 200");
        return toViews(grantMapper.selectList(wrapper));
    }

    /**
     * True when the user holds a live grant covering the target employee
     * (or a grant for all employees when employeeId is null).
     */
    public boolean hasActiveGrant(Long userId, Long targetEmployeeId) {
        if (userId == null || !isEnabled()) {
            return false;
        }
        LambdaQueryWrapper<BossAccessGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BossAccessGrant::getUserId, userId)
                .eq(BossAccessGrant::getStatus, "ACTIVE")
                .le(BossAccessGrant::getStartsAt, LocalDateTime.now())
                .gt(BossAccessGrant::getExpiresAt, LocalDateTime.now());
        if (targetEmployeeId == null) {
            wrapper.isNull(BossAccessGrant::getEmployeeId);
        } else {
            wrapper.and(w -> w.eq(BossAccessGrant::getEmployeeId, targetEmployeeId)
                    .or().isNull(BossAccessGrant::getEmployeeId));
        }
        Long count = grantMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    public List<BossAccessGrant> activeGrants(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return grantMapper.selectList(new LambdaQueryWrapper<BossAccessGrant>()
                .eq(BossAccessGrant::getUserId, userId)
                .eq(BossAccessGrant::getStatus, "ACTIVE")
                .le(BossAccessGrant::getStartsAt, LocalDateTime.now())
                .gt(BossAccessGrant::getExpiresAt, LocalDateTime.now())
                .orderByDesc(BossAccessGrant::getCreatedAt));
    }

    private BossAccessGrant createGrant(Long userId, Long employeeId, String reason,
                                        int durationMinutes, Long approvalId) {
        LocalDateTime now = LocalDateTime.now();
        // Only grants that overlap the new scope are replaced; grants for other
        // employees keep running until their own expiry.
        LambdaUpdateWrapper<BossAccessGrant> replace = new LambdaUpdateWrapper<BossAccessGrant>()
                .eq(BossAccessGrant::getUserId, userId)
                .eq(BossAccessGrant::getStatus, "ACTIVE")
                .set(BossAccessGrant::getStatus, "REPLACED")
                .set(BossAccessGrant::getRevokedAt, now);
        if (employeeId != null) {
            replace.and(w -> w.isNull(BossAccessGrant::getEmployeeId)
                    .or().eq(BossAccessGrant::getEmployeeId, employeeId));
        }
        grantMapper.update(null, replace);

        BossAccessGrant grant = new BossAccessGrant();
        grant.setUserId(userId);
        grant.setEmployeeId(employeeId);
        grant.setReason(reason);
        grant.setApprovalId(approvalId);
        grant.setStatus("ACTIVE");
        grant.setStartsAt(now);
        grant.setExpiresAt(now.plusMinutes(durationMinutes));
        grant.setCreatedAt(now);
        grantMapper.insert(grant);

        Map<String, Object> after = new HashMap<>();
        after.put("userId", userId);
        after.put("employeeId", employeeId == null ? "ALL" : employeeId);
        after.put("durationMinutes", durationMinutes);
        after.put("expiresAt", grant.getExpiresAt().toString());
        auditService.record("SECURITY", "boss", "GRANT", "BOSS_ACCESS_GRANT", grant.getId(),
                null, after, reason, approvalId);
        return grant;
    }

    private List<BossAccessDtos.GrantView> toViews(List<BossAccessGrant> grants) {
        if (grants.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = grants.stream().map(BossAccessGrant::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> employeeIds = grants.stream().map(BossAccessGrant::getEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> users = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Employee> employees = employeeIds.isEmpty() ? Collections.emptyMap()
                : employeeMapper.selectBatchIds(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));

        return grants.stream().map(grant -> {
            SysUser user = users.get(grant.getUserId());
            Employee employee = grant.getEmployeeId() == null ? null : employees.get(grant.getEmployeeId());
            return new BossAccessDtos.GrantView(
                    grant.getId(),
                    grant.getUserId(),
                    user == null ? null : user.getDisplayName(),
                    grant.getEmployeeId(),
                    employee == null ? (grant.getEmployeeId() == null ? "全部员工" : null) : employee.getName(),
                    grant.getReason(),
                    grant.getApprovalId(),
                    grant.getStatus(),
                    isGrantActive(grant),
                    grant.getStartsAt(),
                    grant.getExpiresAt(),
                    grant.getRevokedAt(),
                    grant.getCreatedAt());
        }).toList();
    }

    private boolean isGrantActive(BossAccessGrant grant) {
        LocalDateTime now = LocalDateTime.now();
        return "ACTIVE".equals(grant.getStatus())
                && grant.getStartsAt() != null && !grant.getStartsAt().isAfter(now)
                && grant.getExpiresAt() != null && grant.getExpiresAt().isAfter(now);
    }

    private boolean isEnabled() {
        return configService.getBoolean("boss.access.enabled", true);
    }

    private String mode() {
        String mode = configService.getString("boss.access.mode", "REQUEST");
        return mode == null ? "REQUEST" : mode.trim().toUpperCase();
    }

    private int maxDuration() {
        return Math.max(1, configService.getInt("boss.access.maxDurationMinutes", 240));
    }

    private int defaultDuration() {
        int value = configService.getInt("boss.access.durationMinutes", 30);
        return durationMinutes(value);
    }

    private int approverCount() {
        return Math.max(1, configService.getInt("boss.access.approverCount", 2));
    }

    private int durationMinutes(Integer requested) {
        int max = maxDuration();
        int value = requested == null ? configService.getInt("boss.access.durationMinutes", 30) : requested;
        if (value < 1) {
            value = 1;
        }
        return Math.min(value, max);
    }
}
