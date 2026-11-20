package com.company.ems.employee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.EmployeeFieldVisibility;
import com.company.ems.employee.mapper.EmployeeFieldVisibilityMapper;
import com.company.ems.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-employee privacy switches. Companies choose which fields may be hidden
 * and which permissions still bypass the switch.
 */
@Service
public class EmployeeVisibilityService {

    private static final Map<String, String> LABELS = Map.of(
            "phone", "手机",
            "email", "邮箱",
            "address", "住址",
            "emergencyContact", "紧急联系人",
            "birthDate", "出生日期",
            "hireDate", "入职日期",
            "gender", "性别"
    );

    private final EmployeeFieldVisibilityMapper visibilityMapper;
    private final ConfigService configService;
    private final AuditService auditService;

    public EmployeeVisibilityService(EmployeeFieldVisibilityMapper visibilityMapper,
                                     ConfigService configService,
                                     AuditService auditService) {
        this.visibilityMapper = visibilityMapper;
        this.configService = configService;
        this.auditService = auditService;
    }

    public boolean enabled() {
        return configService.getBoolean("employee.selfVisibility.enabled", true);
    }

    public Set<String> allowedFields() {
        String csv = configService.getString("employee.selfVisibility.fields",
                "phone,email,address,emergencyContact");
        return split(csv, false);
    }

    public List<EmployeeDtos.VisibilityView> myVisibility(LoginUser user) {
        Long employeeId = requireEmployee(user);
        List<EmployeeDtos.VisibilityView> views = new ArrayList<>();
        boolean enabled = enabled();
        for (String field : allowedFields()) {
            EmployeeFieldVisibility row = find(employeeId, field);
            boolean visible = row == null || !Boolean.FALSE.equals(row.getVisible());
            views.add(new EmployeeDtos.VisibilityView(field, label(field), visible, enabled));
        }
        return views;
    }

    @Transactional
    public EmployeeDtos.VisibilityView updateVisibility(LoginUser user,
                                                        EmployeeDtos.VisibilityUpdateRequest request) {
        Long employeeId = requireEmployee(user);
        if (!enabled()) {
            throw new BusinessException(40901, "公司未启用本人字段可见性设置");
        }
        String field = request.getFieldCode() == null ? ""
                : request.getFieldCode().trim();
        if (!allowedFields().contains(field)) {
            throw new BusinessException(40001, "该字段不允许本人设置可见性");
        }
        boolean visible = Boolean.TRUE.equals(request.getVisible());
        EmployeeFieldVisibility row = find(employeeId, field);
        Boolean before = row == null ? true : row.getVisible();
        if (row == null) {
            row = new EmployeeFieldVisibility();
            row.setEmployeeId(employeeId);
            row.setFieldCode(field);
            row.setVisible(visible);
            row.setUpdatedAt(LocalDateTime.now());
            visibilityMapper.insert(row);
        } else {
            row.setVisible(visible);
            row.setUpdatedAt(LocalDateTime.now());
            visibilityMapper.updateById(row);
        }
        auditService.record("PERSONAL", "employee",
                visible ? "SHOW_FIELD" : "HIDE_FIELD", "EMPLOYEE", employeeId,
                Map.of("fieldCode", field, "visible", before),
                Map.of("fieldCode", field, "visible", visible), null, null);
        return new EmployeeDtos.VisibilityView(field, label(field), visible, true);
    }

    /**
     * Fields the viewer must not see because the target employee hid them.
     * The employee themself and configured exempt permissions always see them.
     */
    public Set<String> hiddenFields(LoginUser viewer, Long targetEmployeeId) {
        if (!enabled() || viewer == null || targetEmployeeId == null) {
            return Collections.emptySet();
        }
        if (viewer.employeeId() != null && viewer.employeeId().equals(targetEmployeeId)) {
            return Collections.emptySet();
        }
        if (exempt(viewer)) {
            return Collections.emptySet();
        }
        Set<String> allowed = allowedFields();
        if (allowed.isEmpty()) {
            return Collections.emptySet();
        }
        List<EmployeeFieldVisibility> rows = visibilityMapper.selectList(
                new LambdaQueryWrapper<EmployeeFieldVisibility>()
                        .eq(EmployeeFieldVisibility::getEmployeeId, targetEmployeeId)
                        .eq(EmployeeFieldVisibility::getVisible, false));
        return rows.stream().map(EmployeeFieldVisibility::getFieldCode)
                .filter(allowed::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String label(String field) {
        return LABELS.getOrDefault(field, field);
    }

    private boolean exempt(LoginUser viewer) {
        for (String permission : split(configService.getString(
                "employee.selfVisibility.exemptPermissions", "employee:sensitive:read"), false)) {
            if (viewer.hasPermission(permission.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private Long requireEmployee(LoginUser user) {
        if (user == null || user.employeeId() == null) {
            throw new BusinessException(40401, "当前账号未关联员工档案");
        }
        return user.employeeId();
    }

    private EmployeeFieldVisibility find(Long employeeId, String field) {
        return visibilityMapper.selectOne(new LambdaQueryWrapper<EmployeeFieldVisibility>()
                .eq(EmployeeFieldVisibility::getEmployeeId, employeeId)
                .eq(EmployeeFieldVisibility::getFieldCode, field)
                .last("LIMIT 1"));
    }

    private Set<String> split(String csv, boolean upper) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptySet();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> upper ? value.toUpperCase() : value)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
