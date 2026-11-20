package com.company.ems.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysRole;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.entity.SysUserRole;
import com.company.ems.system.mapper.SysRoleMapper;
import com.company.ems.system.mapper.SysUserMapper;
import com.company.ems.system.mapper.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;
    private final ConfigService configService;
    private final AuditService auditService;

    public UserService(SysUserMapper userMapper,
                       SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper,
                       EmployeeMapper employeeMapper,
                       PasswordEncoder passwordEncoder,
                       ConfigService configService,
                       AuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.employeeMapper = employeeMapper;
        this.passwordEncoder = passwordEncoder;
        this.configService = configService;
        this.auditService = auditService;
    }

    public PageResult<UserDtos.UserView> page(long page, long size, String keyword, String status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getDisplayName, keyword));
        }
        wrapper.eq(StringUtils.hasText(status), SysUser::getStatus, status)
                .orderByAsc(SysUser::getUsername);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(),
                toViews(result.getRecords()));
    }

    public SysUser get(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(40401, "账号不存在");
        }
        return user;
    }

    @Transactional
    public UserDtos.UserView create(UserDtos.CreateRequest request, LoginUser actor) {
        if (userMapper.selectByUsername(request.getUsername()) != null) {
            throw new BusinessException(40901, "用户名已存在");
        }
        validatePassword(request.getPassword());
        Employee employee = validateEmployeeLink(request.getEmployeeId(), null);
        if (employee == null && configService.getBoolean("account.requireEmployeeLink", true)) {
            throw new BusinessException(40001, "账号必须关联员工档案；一人只能有一个账号");
        }
        List<SysRole> roles = resolveRoles(request.getRoleCodes(), List.of("EMPLOYEE"));
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmployeeId(request.getEmployeeId());
        user.setStatus("ACTIVE");
        user.setMfaEnabled(false);
        userMapper.insert(user);
        LocalDate today = LocalDate.now();
        assignRoleRows(user.getId(), roles, today, null, null, actor.userId());
        auditService.record("ACCOUNT", "user", "CREATE", "SYS_USER", user.getId(),
                null, safeView(user, roles), null, null);
        return toView(user, roles);
    }

    @Transactional
    public UserDtos.UserView update(Long id, UserDtos.UpdateRequest request) {
        SysUser user = get(id);
        SysUser before = copy(user);
        if (StringUtils.hasText(request.getDisplayName())) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getEmployeeId() != null
                && !Objects.equals(request.getEmployeeId(), user.getEmployeeId())) {
            validateEmployeeLink(request.getEmployeeId(), id);
            user.setEmployeeId(request.getEmployeeId());
        }
        if (StringUtils.hasText(request.getStatus())) {
            if (!List.of("ACTIVE", "DISABLED").contains(request.getStatus())) {
                throw new BusinessException(40001, "账号状态不合法");
            }
            if ("DISABLED".equals(request.getStatus())) {
                ensureNotLastAdmin(user);
            }
            user.setStatus(request.getStatus());
        }
        userMapper.updateById(user);
        List<SysRole> roles = roleMapper.selectByUserId(id);
        auditService.record("ACCOUNT", "user", "UPDATE", "SYS_USER", id,
                before, safeView(user, roles), null, null);
        return toView(user, roles);
    }

    @Transactional
    public UserDtos.UserView assignRoles(Long id, UserDtos.RoleAssignRequest request, LoginUser actor) {
        SysUser user = get(id);
        List<SysRole> current = roleMapper.selectByUserId(id);
        List<UserDtos.RoleAssignment> assignments = normalizeAssignments(request);
        if (assignments.isEmpty()) {
            throw new BusinessException(40001, "至少保留一个角色");
        }

        Map<Long, UserDtos.RoleAssignment> targets = new LinkedHashMap<>();
        for (UserDtos.RoleAssignment assignment : assignments) {
            SysRole role = roleMapper.selectByCode(assignment.getRoleCode());
            if (role == null) {
                throw new BusinessException(40001, "角色不存在: " + assignment.getRoleCode());
            }
            targets.put(role.getId(), assignment);
        }
        Set<Long> targetRoleIds = targets.keySet();
        if (!targets.values().stream().anyMatch(item -> isRoleCode(item, "SYS_ADMIN"))
                && current.stream().anyMatch(role -> "SYS_ADMIN".equals(role.getCode()))) {
            ensureAnotherAdminExists(user);
        }

        List<SysUserRole> existing = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id));
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        for (SysUserRole row : existing) {
            if (!isEffective(row) || targetRoleIds.contains(row.getRoleId())) {
                continue;
            }
            row.setStatus("REVOKED");
            if (row.getEndDate() == null || row.getEndDate().isAfter(today)) {
                row.setEndDate(today);
            }
            row.setRevokedAt(now);
            userRoleMapper.updateById(row);
        }

        for (Map.Entry<Long, UserDtos.RoleAssignment> entry : targets.entrySet()) {
            SysRole role = roleMapper.selectById(entry.getKey());
            UserDtos.RoleAssignment assignment = entry.getValue();
            SysUserRole active = findEffective(existing, entry.getKey(), today);
            LocalDate start = assignment.getStartDate() != null ? assignment.getStartDate()
                    : active != null && active.getStartDate() != null ? active.getStartDate() : today;
            if (assignment.getEndDate() != null && assignment.getEndDate().isBefore(start)) {
                throw new BusinessException(40001, "角色失效日期不能早于生效日期");
            }
            if (active != null) {
                active.setStartDate(start);
                active.setEndDate(assignment.getEndDate());
                active.setNote(assignment.getNote());
                userRoleMapper.updateById(active);
                continue;
            }
            assignRoleRows(id, List.of(role), start, assignment.getEndDate(),
                    assignment.getNote(), actor.userId());
        }

        SysRole adminRole = roleMapper.selectByCode("SYS_ADMIN");
        if (adminRole != null
                && existing.stream().anyMatch(row -> isEffective(row)
                        && Objects.equals(row.getRoleId(), adminRole.getId()))
                && !targetRoleIds.contains(adminRole.getId())) {
            ensureAnotherAdminExists(user);
        }

        List<SysRole> roles = roleMapper.selectByUserId(id);
        auditService.record("ACCOUNT", "user", "ASSIGN_ROLES", "SYS_USER", id,
                current.stream().map(SysRole::getCode).toList(),
                roles.stream().map(SysRole::getCode).toList(), null, null);
        return toView(user, roles);
    }

    public List<UserDtos.RoleAssignmentView> roleHistory(Long id) {
        get(id);
        List<SysUserRole> rows = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, id)
                .orderByDesc(SysUserRole::getCreatedAt));
        Set<Long> roleIds = rows.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, SysRole> roles = roleIds.isEmpty() ? Collections.emptyMap()
                : roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        LocalDate today = LocalDate.now();
        return rows.stream().map(row -> {
            SysRole role = roles.get(row.getRoleId());
            return new UserDtos.RoleAssignmentView(row.getId(), row.getRoleId(),
                    role == null ? null : role.getCode(),
                    role == null ? null : role.getName(),
                    row.getStartDate(), row.getEndDate(), effectiveStatus(row, today),
                    row.getNote(), row.getGrantedBy(), row.getRevokedAt(), row.getCreatedAt());
        }).toList();
    }

    @Transactional
    public void disable(Long id, LoginUser actor) {
        SysUser user = get(id);
        if (Objects.equals(user.getId(), actor.userId())) {
            throw new BusinessException(40001, "不能禁用当前登录账号");
        }
        ensureNotLastAdmin(user);
        user.setStatus("DISABLED");
        userMapper.updateById(user);
        auditService.record("ACCOUNT", "user", "DISABLE", "SYS_USER", id,
                "ACTIVE", "DISABLED", null, null);
    }

    @Transactional
    public void resetPassword(Long id, String password) {
        SysUser user = get(id);
        validatePassword(password);
        user.setPasswordHash(passwordEncoder.encode(password));
        userMapper.updateById(user);
        auditService.record("ACCOUNT", "user", "RESET_PASSWORD", "SYS_USER", id,
                null, null, null, null);
    }

    @Transactional
    public void changeOwnPassword(LoginUser actor, String currentPassword, String newPassword) {
        SysUser user = get(actor.userId());
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(40001, "当前密码错误");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        auditService.record("ACCOUNT", "user", "CHANGE_PASSWORD", "SYS_USER", user.getId(),
                null, null, null, null);
    }

    public List<SysRole> roles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    private void validatePassword(String password) {
        int minLength = configService.getInt("security.password.minLength", 8);
        if (password == null || password.length() < minLength) {
            throw new BusinessException(40001, "密码长度不能少于 " + minLength + " 位");
        }
    }

    private List<SysRole> resolveRoles(List<String> roleCodes, List<String> defaults) {
        List<String> codes = roleCodes == null || roleCodes.isEmpty() ? defaults : roleCodes;
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysRole> roles = new ArrayList<>();
        for (String code : codes) {
            SysRole role = roleMapper.selectByCode(code);
            if (role == null) {
                throw new BusinessException(40001, "角色不存在: " + code);
            }
            roles.add(role);
        }
        return roles;
    }

    private void assignRoleRows(Long userId, List<SysRole> roles, LocalDate startDate,
                                LocalDate endDate, String note, Long grantedBy) {
        for (SysRole role : roles) {
            SysUserRole row = new SysUserRole();
            row.setUserId(userId);
            row.setRoleId(role.getId());
            row.setScopeType("SELF");
            row.setStartDate(startDate);
            row.setEndDate(endDate);
            row.setStatus("ACTIVE");
            row.setNote(note);
            row.setGrantedBy(grantedBy);
            userRoleMapper.insert(row);
        }
    }

    private Employee validateEmployeeLink(Long employeeId, Long excludeUserId) {
        if (employeeId == null) {
            return null;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null) {
            throw new BusinessException(40001, "员工档案不存在");
        }
        SysUser other = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .last("LIMIT 1"));
        if (other != null && !Objects.equals(other.getId(), excludeUserId)) {
            throw new BusinessException(40901, "该员工已有登录账号；每人只能有一个账号，岗位变动请调整角色");
        }
        return employee;
    }

    private List<UserDtos.RoleAssignment> normalizeAssignments(UserDtos.RoleAssignRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }
        if (request.getAssignments() != null && !request.getAssignments().isEmpty()) {
            return request.getAssignments();
        }
        if (request.getRoleCodes() == null || request.getRoleCodes().isEmpty()) {
            return Collections.emptyList();
        }
        List<UserDtos.RoleAssignment> assignments = new ArrayList<>();
        for (String code : request.getRoleCodes()) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            UserDtos.RoleAssignment assignment = new UserDtos.RoleAssignment();
            assignment.setRoleCode(code);
            assignments.add(assignment);
        }
        return assignments;
    }

    private boolean isRoleCode(UserDtos.RoleAssignment assignment, String code) {
        return assignment != null && code.equalsIgnoreCase(assignment.getRoleCode());
    }

    private boolean isEffective(SysUserRole row) {
        return isEffective(row, LocalDate.now());
    }

    private boolean isEffective(SysUserRole row, LocalDate today) {
        if (!"ACTIVE".equals(row.getStatus())) {
            return false;
        }
        return (row.getStartDate() == null || !row.getStartDate().isAfter(today))
                && (row.getEndDate() == null || !row.getEndDate().isBefore(today));
    }

    private SysUserRole findEffective(List<SysUserRole> rows, Long roleId, LocalDate today) {
        return rows.stream()
                .filter(row -> Objects.equals(row.getRoleId(), roleId) && isEffective(row, today))
                .findFirst()
                .orElse(null);
    }

    private String effectiveStatus(SysUserRole row, LocalDate today) {
        if ("REVOKED".equals(row.getStatus())) {
            return "REVOKED";
        }
        if (row.getStartDate() != null && row.getStartDate().isAfter(today)) {
            return "SCHEDULED";
        }
        if (row.getEndDate() != null && row.getEndDate().isBefore(today)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private void ensureNotLastAdmin(SysUser user) {
        List<SysRole> roles = roleMapper.selectByUserId(user.getId());
        boolean isAdmin = roles.stream().anyMatch(role -> "SYS_ADMIN".equals(role.getCode()));
        if (!isAdmin) {
            return;
        }
        ensureAnotherAdminExists(user);
    }

    private void ensureAnotherAdminExists(SysUser user) {
        SysRole adminRole = roleMapper.selectByCode("SYS_ADMIN");
        if (adminRole == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        List<SysUserRole> adminRows = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, adminRole.getId()));
        long activeOthers = adminRows.stream()
                .filter(row -> isEffective(row, today))
                .filter(row -> !Objects.equals(row.getUserId(), user.getId()))
                .map(row -> userMapper.selectById(row.getUserId()))
                .filter(Objects::nonNull)
                .filter(other -> "ACTIVE".equals(other.getStatus()))
                .count();
        if (activeOthers == 0) {
            throw new BusinessException(40901, "系统至少需要保留一个启用的系统管理员");
        }
    }

    private List<UserDtos.UserView> toViews(List<SysUser> users) {
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = users.stream().map(SysUser::getId).collect(Collectors.toSet());
        List<SysUserRole> rows = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, ids));
        LocalDate today = LocalDate.now();
        Set<Long> roleIds = rows.stream()
                .filter(row -> isEffective(row, today))
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toSet());
        Map<Long, SysRole> rolesById = roleIds.isEmpty() ? Collections.emptyMap()
                : roleMapper.selectBatchIds(roleIds).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        Map<Long, List<SysRole>> rolesByUser = new java.util.HashMap<>();
        for (SysUserRole row : rows) {
            if (!isEffective(row, today)) {
                continue;
            }
            SysRole role = rolesById.get(row.getRoleId());
            if (role != null) {
                rolesByUser.computeIfAbsent(row.getUserId(), key -> new ArrayList<>()).add(role);
            }
        }
        return users.stream()
                .map(user -> toView(user, rolesByUser.getOrDefault(user.getId(), Collections.emptyList())))
                .toList();
    }

    private UserDtos.UserView toView(SysUser user, List<SysRole> roles) {
        List<UserDtos.RoleView> roleViews = roles.stream()
                .sorted(Comparator.comparing(SysRole::getId))
                .map(role -> new UserDtos.RoleView(role.getId(), role.getCode(), role.getName()))
                .toList();
        Employee employee = user.getEmployeeId() == null ? null : employeeMapper.selectById(user.getEmployeeId());
        return new UserDtos.UserView(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getEmployeeId(),
                employee == null ? null : employee.getName(),
                employee == null ? null : employee.getEmployeeNo(),
                user.getStatus(), Boolean.TRUE.equals(user.getMfaEnabled()),
                user.getLastLoginAt(), user.getCreatedAt(), roleViews
        );
    }

    private Object safeView(SysUser user, List<SysRole> roles) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "employeeId", user.getEmployeeId() == null ? "" : user.getEmployeeId(),
                "status", user.getStatus(),
                "roles", roles.stream().map(SysRole::getCode).toList()
        );
    }

    private SysUser copy(SysUser source) {
        SysUser copy = new SysUser();
        copy.setId(source.getId());
        copy.setUsername(source.getUsername());
        copy.setDisplayName(source.getDisplayName());
        copy.setEmployeeId(source.getEmployeeId());
        copy.setStatus(source.getStatus());
        copy.setMfaEnabled(source.getMfaEnabled());
        copy.setLastLoginAt(source.getLastLoginAt());
        return copy;
    }
}
