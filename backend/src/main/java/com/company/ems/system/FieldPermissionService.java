package com.company.ems.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.boss.BossAccessService;
import com.company.ems.employee.EmployeeVisibilityService;
import com.company.ems.employee.entity.ReportingLine;
import com.company.ems.employee.mapper.ReportingLineMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysFieldPermission;
import com.company.ems.system.entity.SysRole;
import com.company.ems.system.mapper.SysFieldPermissionMapper;
import com.company.ems.system.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves per-user field visibility, masking and editability from sys_field_permission.
 * No business rule is hard-coded: every field rule is configured by the company.
 */
@Service
public class FieldPermissionService {

    private final SysFieldPermissionMapper fieldPermissionMapper;
    private final SysRoleMapper roleMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final BossAccessService bossAccessService;
    private final EmployeeVisibilityService employeeVisibilityService;

    public FieldPermissionService(SysFieldPermissionMapper fieldPermissionMapper,
                                  SysRoleMapper roleMapper,
                                  ReportingLineMapper reportingLineMapper,
                                  BossAccessService bossAccessService,
                                  EmployeeVisibilityService employeeVisibilityService) {
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.roleMapper = roleMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.bossAccessService = bossAccessService;
        this.employeeVisibilityService = employeeVisibilityService;
    }

    public FieldPolicy resolve(String entityType, LoginUser user, Long targetEmployeeId) {
        List<SysFieldPermission> rules = fieldPermissionMapper.selectByEntityType(entityType);
        Map<String, Long> roleIds = roleMapper.selectByUserId(user.userId()).stream()
                .collect(Collectors.toMap(SysRole::getCode, SysRole::getId, (a, b) -> a));

        boolean self = user.employeeId() != null && user.employeeId().equals(targetEmployeeId);
        boolean directReport = !self && isDirectReport(user, targetEmployeeId);
        boolean sensitiveReader = isSensitiveReader(user, targetEmployeeId);

        Map<String, List<SysFieldPermission>> byField = new HashMap<>();
        for (SysFieldPermission rule : rules) {
            byField.computeIfAbsent(rule.getFieldCode(), key -> new ArrayList<>()).add(rule);
        }

        Map<String, FieldAccess> result = new HashMap<>();
        for (Map.Entry<String, List<SysFieldPermission>> entry : byField.entrySet()) {
            SysFieldPermission rule = pickRule(entry.getValue(), roleIds);
            if (rule == null) {
                continue;
            }
            Set<String> visibility = splitTokens(rule.getVisibility());
            boolean visible = visibility.contains("ALL")
                    || (self && visibility.contains("SELF"))
                    || (directReport && visibility.contains("DIRECT_REPORTS"))
                    || visibility.stream().anyMatch(roleIds::containsKey);
            boolean masked = visible
                    && !self
                    && !sensitiveReader
                    && StringUtils.hasText(rule.getMaskRule())
                    && !"NONE".equalsIgnoreCase(rule.getMaskRule());
            result.put(entry.getKey(), new FieldAccess(
                    visible,
                    masked,
                    rule.getMaskRule(),
                    Boolean.TRUE.equals(rule.getSelfEdit()),
                    Boolean.TRUE.equals(rule.getIsSensitive()),
                    Boolean.TRUE.equals(rule.getAiReadable()),
                    Boolean.TRUE.equals(rule.getKeyField())
            ));
        }
        // The employee's own privacy switches win over role defaults, except
        // for the employee themself and configured exempt permissions.
        for (String hidden : employeeVisibilityService.hiddenFields(user, targetEmployeeId)) {
            FieldAccess access = result.getOrDefault(hidden, FieldAccess.DEFAULT);
            result.put(hidden, new FieldAccess(false, false, access.maskRule(),
                    access.selfEdit(), access.sensitive(), access.aiReadable(),
                    access.keyField()));
        }
        return new FieldPolicy(result);
    }

    /**
     * Evaluates a visibility token list (ALL,SELF,DIRECT_REPORTS,ROLE_CODE...)
     * for a user against a target employee. Used by custom attributes as well.
     */
    public boolean canViewVisibility(String visibility, LoginUser user, Long targetEmployeeId) {
        Set<String> tokens = splitTokens(visibility);
        if (tokens.contains("ALL")) {
            return true;
        }
        boolean self = user.employeeId() != null && user.employeeId().equals(targetEmployeeId);
        if (self && tokens.contains("SELF")) {
            return true;
        }
        if (!self && tokens.contains("DIRECT_REPORTS") && isDirectReport(user, targetEmployeeId)) {
            return true;
        }
        Map<String, Long> roleIds = roleMapper.selectByUserId(user.userId()).stream()
                .collect(Collectors.toMap(SysRole::getCode, SysRole::getId, (a, b) -> a));
        return tokens.stream().anyMatch(roleIds::containsKey);
    }

    public boolean isSensitiveReader(LoginUser user) {
        return user.hasPermission("employee:sensitive:read");
    }

    /**
     * A user counts as a sensitive reader when the role grants it directly, or
     * when a live boss-access grant covers the target employee.
     */
    public boolean isSensitiveReader(LoginUser user, Long targetEmployeeId) {
        return user.hasPermission("employee:sensitive:read")
                || bossAccessService.hasActiveGrant(user.userId(), targetEmployeeId);
    }

    public String mask(String value, String maskRule) {
        if (value == null || !StringUtils.hasText(maskRule) || "NONE".equalsIgnoreCase(maskRule)) {
            return value;
        }
        String rule = maskRule.toUpperCase();
        return switch (rule) {
            case "PHONE" -> maskPhone(value);
            case "EMAIL" -> maskEmail(value);
            case "ID_CARD" -> maskIdCard(value);
            case "ADDRESS" -> maskAddress(value);
            case "CONTACT" -> maskContact(value);
            default -> maskGeneric(value);
        };
    }

    private SysFieldPermission pickRule(List<SysFieldPermission> rules, Map<String, Long> roleIds) {
        SysFieldPermission roleRule = null;
        SysFieldPermission defaultRule = null;
        for (SysFieldPermission rule : rules) {
            if (rule.getRoleId() == null || rule.getRoleId() == 0L) {
                defaultRule = rule;
            } else if (roleIds.containsValue(rule.getRoleId())) {
                if (roleRule == null || rule.getRoleId() < roleRule.getRoleId()) {
                    roleRule = rule;
                }
            }
        }
        return roleRule != null ? roleRule : defaultRule;
    }

    private boolean isDirectReport(LoginUser user, Long targetEmployeeId) {
        if (user.employeeId() == null || targetEmployeeId == null) {
            return false;
        }
        LambdaQueryWrapper<ReportingLine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReportingLine::getEmployeeId, targetEmployeeId)
                .eq(ReportingLine::getManagerId, user.employeeId())
                .and(w -> w.isNull(ReportingLine::getEndDate).or().ge(ReportingLine::getEndDate, LocalDate.now()))
                .last("LIMIT 1");
        return reportingLineMapper.selectCount(wrapper) > 0;
    }

    private Set<String> splitTokens(String value) {
        Set<String> tokens = new HashSet<>();
        if (!StringUtils.hasText(value)) {
            return tokens;
        }
        for (String token : value.split(",")) {
            if (StringUtils.hasText(token)) {
                tokens.add(token.trim().toUpperCase());
            }
        }
        return tokens;
    }

    private String maskPhone(String value) {
        return value.length() >= 7
                ? value.substring(0, 3) + "****" + value.substring(value.length() - 4)
                : maskGeneric(value);
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return maskGeneric(value);
        }
        String name = value.substring(0, at);
        String domain = value.substring(at);
        return name.charAt(0) + "***" + domain;
    }

    private String maskIdCard(String value) {
        if (value.length() <= 8) {
            return maskGeneric(value);
        }
        return value.substring(0, 4) + "********" + value.substring(value.length() - 4);
    }

    private String maskAddress(String value) {
        int visible = Math.min(6, value.length());
        return value.substring(0, visible) + "****";
    }

    private String maskContact(String value) {
        return value.charAt(0) + "***";
    }

    private String maskGeneric(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.charAt(0) + "***";
    }

    public record FieldPolicy(Map<String, FieldAccess> fields) {

        public FieldAccess access(String fieldCode) {
            return fields.getOrDefault(fieldCode, FieldAccess.DEFAULT);
        }

        public boolean canView(String fieldCode) {
            return access(fieldCode).visible();
        }

        public boolean canSelfEdit(String fieldCode) {
            return access(fieldCode).selfEdit();
        }
    }

    public record FieldAccess(boolean visible,
                              boolean masked,
                              String maskRule,
                              boolean selfEdit,
                              boolean sensitive,
                              boolean aiReadable,
                              boolean keyField) {
        public static final FieldAccess DEFAULT = new FieldAccess(true, false, "NONE", false, false, true, false);
    }
}
