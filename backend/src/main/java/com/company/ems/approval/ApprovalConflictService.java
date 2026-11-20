package com.company.ems.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.entity.ReportingLine;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.employee.mapper.ReportingLineMapper;
import com.company.ems.system.entity.SysApprovalRequest;
import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.mapper.SysApprovalRequestMapper;
import com.company.ems.system.mapper.SysConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Configurable conflict detection for approval requests. Companies decide which
 * policies participate, whether a duplicate pending request blocks submission,
 * and what happens when the underlying data changed while an approval was open.
 * Nothing here is hardcoded per company.
 */
@Service
public class ApprovalConflictService {

    private static final Set<String> EMPLOYEE_FIELDS = Set.of(
            "name", "gender", "birthDate", "phone", "email", "idCard", "address",
            "emergencyContact", "hireDate", "org", "position", "manager");

    private final SysConfigMapper configMapper;
    private final SysApprovalRequestMapper requestMapper;
    private final EmployeeMapper employeeMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final ObjectMapper objectMapper;

    public ApprovalConflictService(SysConfigMapper configMapper,
                                   SysApprovalRequestMapper requestMapper,
                                   EmployeeMapper employeeMapper,
                                   ReportingLineMapper reportingLineMapper,
                                   ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.requestMapper = requestMapper;
        this.employeeMapper = employeeMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.objectMapper = objectMapper;
    }

    public boolean watches(String policyCode) {
        if (!boolConfig("approval.conflict.enabled", true) || !StringUtils.hasText(policyCode)) {
            return false;
        }
        return listConfig("approval.conflict.policies", "KEY_INFO,ENTITY_ROLLBACK")
                .contains(policyCode.toUpperCase(Locale.ROOT));
    }

    /**
     * Other open requests for the same object in any participating policy.
     * Different policies can touch the same employee (key info vs rollback), so
     * rollback requests are resolved through their payload target first.
     */
    public List<Long> pendingConflicts(String policyCode, Map<String, Object> payload,
                                       String entityType, Long entityId, Long excludeId) {
        if (!watches(policyCode)) {
            return Collections.emptyList();
        }
        ConflictKey key = targetKey(policyCode, payload, entityType, entityId);
        if (key == null) {
            return Collections.emptyList();
        }
        Set<String> policies = listConfig("approval.conflict.policies", "KEY_INFO,ENTITY_ROLLBACK");
        if (policies.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (SysApprovalRequest row : requestMapper.selectList(new LambdaQueryWrapper<SysApprovalRequest>()
                .eq(SysApprovalRequest::getStatus, "PENDING")
                .in(SysApprovalRequest::getPolicyCode, policies)
                .ne(excludeId != null, SysApprovalRequest::getId, excludeId))) {
            ConflictKey other = targetKey(row.getPolicyCode(), parsePayload(row.getPayloadJson()),
                    row.getEntityType(), row.getEntityId());
            if (key.equals(other)) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    private ConflictKey targetKey(String policyCode, Map<String, Object> payload,
                                  String entityType, Long entityId) {
        if ("ENTITY_ROLLBACK".equalsIgnoreCase(policyCode) && payload != null) {
            Object targetType = payload.get("entityType");
            Object targetId = payload.get("entityId");
            if (targetType != null && targetId != null) {
                try {
                    return new ConflictKey(String.valueOf(targetType),
                            Long.valueOf(String.valueOf(targetId)));
                } catch (NumberFormatException ignored) {
                    // fall through to the request-level entity
                }
            }
        }
        if (entityId == null || !StringUtils.hasText(entityType)) {
            return null;
        }
        return new ConflictKey(entityType, entityId);
    }

    private record ConflictKey(String entityType, Long entityId) {
    }

    /** WARN keeps the request open with a visible hint; BLOCK refuses the submission. */
    public String pendingAction() {
        return choiceConfig("approval.conflict.pendingAction", Set.of("WARN", "BLOCK"), "WARN");
    }

    /** BLOCK refuses the approval; NOTIFY records and warns, then lets the approval proceed. */
    public String staleAction() {
        return choiceConfig("approval.conflict.staleAction", Set.of("BLOCK", "NOTIFY"), "BLOCK");
    }

    /**
     * Fields whose current value no longer matches the baseline captured when the
     * request was submitted, and where the pending change would overwrite someone
     * else's newer value. Only configured employee key fields are compared.
     */
    public List<String> staleFields(SysApprovalRequest request) {
        if (request == null || !watches(request.getPolicyCode())
                || !boolConfig("approval.conflict.staleCheck", true)
                || !"EMPLOYEE".equalsIgnoreCase(request.getEntityType())
                || request.getEntityId() == null) {
            return Collections.emptyList();
        }
        Map<String, Object> payload = parsePayload(request.getPayloadJson());
        if (!(payload.get("baseline") instanceof Map<?, ?> rawBaseline)
                || !(payload.get("changes") instanceof Map<?, ?> rawChanges)) {
            return Collections.emptyList();
        }
        Employee employee = employeeMapper.selectById(request.getEntityId());
        if (employee == null) {
            return Collections.emptyList();
        }
        List<String> stale = new ArrayList<>();
        for (Map.Entry<?, ?> entry : rawBaseline.entrySet()) {
            String field = String.valueOf(entry.getKey());
            if (!EMPLOYEE_FIELDS.contains(field) || !rawChanges.containsKey(field)) {
                continue;
            }
            Object baseline = entry.getValue();
            Object current = currentValue(employee, field);
            Object target = rawChanges.get(field);
            if (!sameValue(baseline, current) && !sameValue(target, current)) {
                stale.add(field);
            }
        }
        return stale;
    }

    private Object currentValue(Employee employee, String field) {
        return switch (field) {
            case "name" -> employee.getName();
            case "gender" -> employee.getGender();
            case "birthDate" -> employee.getBirthDate();
            case "phone" -> employee.getPhone();
            case "email" -> employee.getEmail();
            case "idCard" -> employee.getIdCardEnc();
            case "address" -> employee.getAddress();
            case "emergencyContact" -> employee.getEmergencyContact();
            case "hireDate" -> employee.getHireDate();
            case "org" -> employee.getPrimaryOrgId();
            case "position" -> employee.getPrimaryPositionId();
            case "manager" -> currentManagerId(employee.getId());
            default -> null;
        };
    }

    private Long currentManagerId(Long employeeId) {
        ReportingLine line = reportingLineMapper.selectOne(new LambdaQueryWrapper<ReportingLine>()
                .eq(ReportingLine::getEmployeeId, employeeId)
                .eq(ReportingLine::getIsPrimary, true)
                .and(w -> w.isNull(ReportingLine::getEndDate)
                        .or().ge(ReportingLine::getEndDate, LocalDate.now()))
                .orderByDesc(ReportingLine::getStartDate)
                .last("LIMIT 1"));
        return line == null ? null : line.getManagerId();
    }

    private boolean sameValue(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Objects.toString(left, "").equals(Objects.toString(right, ""));
    }

    private Map<String, Object> parsePayload(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private Set<String> listConfig(String key, String defaultValue) {
        SysConfig config = configMapper.selectByKey(key);
        String raw = config == null || !StringUtils.hasText(config.getConfigValue())
                ? defaultValue : config.getConfigValue();
        Set<String> values = new LinkedHashSet<>();
        if (!StringUtils.hasText(raw)) {
            return values;
        }
        for (String part : raw.split(",")) {
            if (StringUtils.hasText(part)) {
                values.add(part.trim().toUpperCase(Locale.ROOT));
            }
        }
        return values;
    }

    private String choiceConfig(String key, Set<String> allowed, String defaultValue) {
        SysConfig config = configMapper.selectByKey(key);
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return defaultValue;
        }
        String value = config.getConfigValue().trim().toUpperCase(Locale.ROOT);
        return allowed.contains(value) ? value : defaultValue;
    }

    private boolean boolConfig(String key, boolean defaultValue) {
        SysConfig config = configMapper.selectByKey(key);
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return defaultValue;
        }
        String value = config.getConfigValue().trim();
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return defaultValue;
    }
}
