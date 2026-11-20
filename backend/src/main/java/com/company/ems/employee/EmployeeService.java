package com.company.ems.employee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.approval.ApprovalDtos;
import com.company.ems.approval.ApprovalService;
import com.company.ems.audit.AuditService;
import com.company.ems.audit.EntityVersionService;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.entity.EmployeePosition;
import com.company.ems.employee.entity.OrgUnit;
import com.company.ems.employee.entity.Position;
import com.company.ems.employee.entity.ReportingLine;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.employee.mapper.EmployeePositionMapper;
import com.company.ems.employee.mapper.OrgUnitMapper;
import com.company.ems.employee.mapper.PositionMapper;
import com.company.ems.employee.mapper.ReportingLineMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.FieldPermissionService;
import com.company.ems.system.FieldPermissionService.FieldAccess;
import com.company.ems.system.FieldPermissionService.FieldPolicy;
import com.company.ems.system.entity.SysApprovalPolicy;
import com.company.ems.system.mapper.SysApprovalPolicyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private static final String ENTITY_TYPE = "EMPLOYEE";
    private static final String KEY_INFO_POLICY = "KEY_INFO";

    private final EmployeeMapper employeeMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final EmployeePositionMapper employeePositionMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final SysApprovalPolicyMapper approvalPolicyMapper;
    private final FieldPermissionService fieldPermissionService;
    private final ApprovalService approvalService;
    private final AuditService auditService;
    private final EntityVersionService entityVersionService;
    private final EmployeeVersionSnapshotService versionSnapshotService;

    public EmployeeService(EmployeeMapper employeeMapper,
                           OrgUnitMapper orgUnitMapper,
                           PositionMapper positionMapper,
                           EmployeePositionMapper employeePositionMapper,
                           ReportingLineMapper reportingLineMapper,
                           SysApprovalPolicyMapper approvalPolicyMapper,
                           FieldPermissionService fieldPermissionService,
                           ApprovalService approvalService,
                           AuditService auditService,
                           EntityVersionService entityVersionService,
                           EmployeeVersionSnapshotService versionSnapshotService) {
        this.employeeMapper = employeeMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.positionMapper = positionMapper;
        this.employeePositionMapper = employeePositionMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.approvalPolicyMapper = approvalPolicyMapper;
        this.fieldPermissionService = fieldPermissionService;
        this.approvalService = approvalService;
        this.auditService = auditService;
        this.entityVersionService = entityVersionService;
        this.versionSnapshotService = versionSnapshotService;
    }

    public PageResult<EmployeeDtos.EmployeeView> page(long page, long size,
                                                      String keyword,
                                                      Long orgId,
                                                      String status,
                                                      LoginUser user) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Employee::getName, keyword)
                    .or().like(Employee::getEmployeeNo, keyword)
                    .or().like(Employee::getPhone, keyword)
                    .or().like(Employee::getEmail, keyword));
        }
        if (orgId != null) {
            List<Long> orgIds = orgSubtreeIds(orgId);
            if (orgIds.isEmpty()) {
                return new PageResult<>(0, page, size, Collections.emptyList());
            }
            wrapper.in(Employee::getPrimaryOrgId, orgIds);
        }
        wrapper.eq(StringUtils.hasText(status), Employee::getStatus, status);
        wrapper.orderByAsc(Employee::getEmployeeNo);

        Page<Employee> result = employeeMapper.selectPage(new Page<>(page, size), wrapper);
        List<EmployeeDtos.EmployeeView> views = toViews(result.getRecords(), user);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), views);
    }

    public EmployeeDtos.EmployeeView get(Long id, LoginUser user) {
        Employee employee = requireEmployee(id);
        FieldPolicy policy = fieldPermissionService.resolve(ENTITY_TYPE, user, id);
        boolean sensitive = policy.fields().values().stream()
                .anyMatch(access -> access.sensitive() && access.visible());
        auditService.access(ENTITY_TYPE, id, "READ", sensitive);
        return toViews(List.of(employee), user).get(0);
    }

    @Transactional
    public EmployeeDtos.EmployeeView create(EmployeeDtos.CreateRequest request, LoginUser user) {
        Long exists = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmployeeNo, request.getEmployeeNo()));
        if (exists != null && exists > 0) {
            throw new BusinessException(40901, "工号已存在");
        }
        if (request.getPrimaryPositionId() != null && request.getPrimaryOrgId() == null) {
            throw new BusinessException(40001, "指定岗位时必须指定部门");
        }
        validateOrganizationRefs(request.getPrimaryOrgId(), request.getPrimaryPositionId(),
                request.getManagerId(), null);
        Employee employee = new Employee();
        employee.setEmployeeNo(request.getEmployeeNo());
        employee.setName(request.getName());
        employee.setGender(request.getGender());
        employee.setBirthDate(request.getBirthDate());
        employee.setPhone(request.getPhone());
        employee.setEmail(request.getEmail());
        employee.setIdCardEnc(request.getIdCard());
        employee.setAddress(request.getAddress());
        employee.setEmergencyContact(request.getEmergencyContact());
        employee.setHireDate(request.getHireDate());
        employee.setStatus("ACTIVE");
        employee.setPrimaryOrgId(request.getPrimaryOrgId());
        employee.setPrimaryPositionId(request.getPrimaryPositionId());
        employeeMapper.insert(employee);

        if (request.getPrimaryOrgId() != null && request.getPrimaryPositionId() != null) {
            changePosition(employee, request.getPrimaryOrgId(), request.getPrimaryPositionId());
        }
        if (request.getManagerId() != null) {
            changeManager(employee, request.getManagerId());
        }
        auditService.record("PERSONNEL", "employee", "CREATE", ENTITY_TYPE, employee.getId(),
                null, employee, null, null);
        entityVersionService.snapshot(ENTITY_TYPE, employee.getId(), "CREATE",
                versionSnapshotService.snapshot(employee), user.userId(), null);
        return toViews(List.of(employee), user).get(0);
    }

    @Transactional
    public EmployeeDtos.UpdateResult update(Long id, EmployeeDtos.UpdateRequest request, LoginUser user) {
        Employee employee = requireEmployee(id);
        Map<String, Object> directChanges = new LinkedHashMap<>();
        Map<String, Object> keyChanges = new LinkedHashMap<>();
        Set<String> keyFields = keyFieldCodes();
        boolean canEditAll = user.hasPermission("employee:update");
        boolean self = Objects.equals(user.employeeId(), id);
        FieldPolicy selfPolicy = fieldPermissionService.resolve(ENTITY_TYPE, user, id);

        collect(request.getName(), "name", employee.getName(), directChanges, keyChanges, keyFields);
        collect(request.getGender(), "gender", employee.getGender(), directChanges, keyChanges, keyFields);
        collect(request.getBirthDate(), "birthDate", employee.getBirthDate(), directChanges, keyChanges, keyFields);
        collect(request.getPhone(), "phone", employee.getPhone(), directChanges, keyChanges, keyFields);
        collect(request.getEmail(), "email", employee.getEmail(), directChanges, keyChanges, keyFields);
        collect(request.getIdCard(), "idCard", employee.getIdCardEnc(), directChanges, keyChanges, keyFields);
        collect(request.getAddress(), "address", employee.getAddress(), directChanges, keyChanges, keyFields);
        collect(request.getEmergencyContact(), "emergencyContact", employee.getEmergencyContact(),
                directChanges, keyChanges, keyFields);
        collect(request.getHireDate(), "hireDate", employee.getHireDate(), directChanges, keyChanges, keyFields);
        collect(request.getPrimaryOrgId(), "org", employee.getPrimaryOrgId(), directChanges, keyChanges, keyFields);
        collect(request.getPrimaryPositionId(), "position", employee.getPrimaryPositionId(),
                directChanges, keyChanges, keyFields);
        collect(request.getManagerId(), "manager", currentManagerId(id),
                directChanges, keyChanges, keyFields);

        if (!canEditAll) {
            if (!self) {
                throw new BusinessException(40301, "无权限修改该员工信息");
            }
            for (String field : directChanges.keySet()) {
                if (!selfPolicy.canSelfEdit(field)) {
                    throw new BusinessException(40301, "字段 " + field + " 不允许本人修改");
                }
            }
            if (!keyChanges.isEmpty()) {
                throw new BusinessException(40301, "关键信息需由人事提交修改申请");
            }
        }

        if (directChanges.isEmpty() && keyChanges.isEmpty()) {
            return new EmployeeDtos.UpdateResult("NO_CHANGE", null, "没有需要修改的内容", view(employee, user));
        }

        validateOrganizationRefs(request.getPrimaryOrgId(), request.getPrimaryPositionId(),
                request.getManagerId(), id);

        if (!directChanges.isEmpty()) {
            Employee before = copy(employee);
            applyChanges(employee, directChanges, null);
            employeeMapper.updateById(employee);
            auditService.record("PERSONNEL", "employee", "UPDATE", ENTITY_TYPE, id,
                    before, employee, request.getReason(), null);
            entityVersionService.snapshot(ENTITY_TYPE, id, "UPDATE",
                    versionSnapshotService.snapshot(requireEmployee(id)), user.userId(), null);
        }

        Long approvalId = null;
        boolean conflict = false;
        if (!keyChanges.isEmpty()) {
            Map<String, Object> baseline = new LinkedHashMap<>();
            for (String field : keyChanges.keySet()) {
                baseline.put(field, currentFieldValue(employee, field));
            }
            ApprovalDtos.SubmitRequest submit = new ApprovalDtos.SubmitRequest();
            submit.setPolicyCode(KEY_INFO_POLICY);
            submit.setEntityType(ENTITY_TYPE);
            submit.setEntityId(id);
            submit.setPayload(Map.of("employeeId", id, "changes", keyChanges, "baseline", baseline));
            submit.setReason(request.getReason());
            ApprovalDtos.ApprovalView approval = approvalService.submit(submit, user);
            approvalId = approval.id();
            conflict = approval.conflict();
        }

        String status = keyChanges.isEmpty() ? "APPLIED"
                : (directChanges.isEmpty() ? "PENDING_APPROVAL" : "PARTIAL_APPLIED");
        String message = keyChanges.isEmpty()
                ? "修改已生效"
                : (directChanges.isEmpty() ? "关键信息已提交审批" : "普通字段已生效，关键信息已提交审批");
        if (conflict) {
            message += "（提示：该员工已有其他待审批申请，请协调处理）";
        }
        return new EmployeeDtos.UpdateResult(status, approvalId, message, view(requireEmployee(id), user));
    }

    @Transactional
    public void applyApprovedChanges(Long employeeId, Map<String, Object> changes, Long approvalId,
                                     String reason, Long operatorId) {
        Employee employee = requireEmployee(employeeId);
        Employee before = copy(employee);
        Map<String, Object> normalized = new LinkedHashMap<>(changes);
        Long orgId = asLong(normalized.get("org"));
        Long positionId = asLong(normalized.get("position"));
        Long managerId = asLong(normalized.get("manager"));

        if (orgId != null || positionId != null) {
            Long targetOrg = orgId != null ? orgId : employee.getPrimaryOrgId();
            Long targetPosition = positionId != null ? positionId : employee.getPrimaryPositionId();
            if (targetOrg == null || targetPosition == null) {
                throw new BusinessException(40001, "部门和岗位必须同时设置");
            }
            changePosition(employee, targetOrg, targetPosition);
        }
        if (managerId != null) {
            changeManager(employee, managerId);
        }
        applyChanges(employee, normalized, null);
        employeeMapper.updateById(employee);
        auditService.record("PERSONNEL", "employee", "UPDATE_APPROVED", ENTITY_TYPE, employeeId,
                before, employee, reason, approvalId);
        entityVersionService.snapshot(ENTITY_TYPE, employeeId, "UPDATE_APPROVED",
                versionSnapshotService.snapshot(requireEmployee(employeeId)), operatorId, approvalId);
    }

    /**
     * Restores the configured subset of an employee version. Org/position/manager
     * changes keep the existing history tables accurate; scalar fields (including
     * nulls) are written explicitly because updateById skips null values.
     */
    @Transactional
    public Long applyVersionRollback(Long employeeId, Map<String, Object> fields,
                                     Long approvalId, String reason, Long operatorId) {
        Employee employee = requireEmployee(employeeId);
        Employee before = copy(employee);
        Map<String, Object> changes = fields == null ? Map.of() : fields;
        if (changes.isEmpty()) {
            throw new BusinessException(40001, "没有可回滚字段");
        }

        boolean hasOrg = changes.containsKey("org");
        boolean hasPosition = changes.containsKey("position");
        if (hasOrg || hasPosition) {
            Long targetOrg = asLong(changes.get("org"));
            Long targetPosition = asLong(changes.get("position"));
            if (targetOrg == null && targetPosition == null) {
                endPrimaryPosition(employeeId);
                employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                        .eq(Employee::getId, employeeId)
                        .set(Employee::getPrimaryOrgId, null)
                        .set(Employee::getPrimaryPositionId, null));
                employee.setPrimaryOrgId(null);
                employee.setPrimaryPositionId(null);
            } else if (targetOrg == null || targetPosition == null) {
                throw new BusinessException(40001, "版本中的部门和岗位不完整，无法回滚");
            } else if (!Objects.equals(targetOrg, employee.getPrimaryOrgId())
                    || !Objects.equals(targetPosition, employee.getPrimaryPositionId())) {
                OrgUnit org = orgUnitMapper.selectById(targetOrg);
                Position position = positionMapper.selectById(targetPosition);
                if (org == null || !"ACTIVE".equals(org.getStatus())) {
                    throw new BusinessException(40001, "版本中的部门已不存在或停用，无法回滚");
                }
                if (position == null || !"ACTIVE".equals(position.getStatus())) {
                    throw new BusinessException(40001, "版本中的岗位已不存在或停用，无法回滚");
                }
                changePosition(employee, targetOrg, targetPosition);
            }
        }

        if (changes.containsKey("manager")) {
            Long targetManager = asLong(changes.get("manager"));
            if (targetManager == null) {
                endPrimaryManager(employeeId);
            } else if (!Objects.equals(targetManager, currentManagerId(employeeId))) {
                if (employeeMapper.selectById(targetManager) == null) {
                    throw new BusinessException(40001, "版本中的直属上级已不存在，无法回滚");
                }
                changeManager(employee, targetManager);
            }
        }

        LambdaUpdateWrapper<Employee> update = new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, employeeId);
        boolean scalar = false;
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            switch (entry.getKey()) {
                case "name" -> {
                    String value = asString(entry.getValue());
                    if (!StringUtils.hasText(value)) {
                        throw new BusinessException(40001, "版本中的姓名为空，无法回滚");
                    }
                    update.set(Employee::getName, value);
                    scalar = true;
                }
                case "gender" -> {
                    update.set(Employee::getGender, asString(entry.getValue()));
                    scalar = true;
                }
                case "birthDate" -> {
                    update.set(Employee::getBirthDate, asDate(entry.getValue()));
                    scalar = true;
                }
                case "phone" -> {
                    update.set(Employee::getPhone, asString(entry.getValue()));
                    scalar = true;
                }
                case "email" -> {
                    update.set(Employee::getEmail, asString(entry.getValue()));
                    scalar = true;
                }
                case "address" -> {
                    update.set(Employee::getAddress, asString(entry.getValue()));
                    scalar = true;
                }
                case "emergencyContact" -> {
                    update.set(Employee::getEmergencyContact, asString(entry.getValue()));
                    scalar = true;
                }
                case "hireDate" -> {
                    update.set(Employee::getHireDate, asDate(entry.getValue()));
                    scalar = true;
                }
                case "status" -> {
                    String value = asString(entry.getValue());
                    if (!StringUtils.hasText(value)) {
                        throw new BusinessException(40001, "版本中的员工状态为空，无法回滚");
                    }
                    update.set(Employee::getStatus, value);
                    scalar = true;
                }
                default -> {
                }
            }
        }
        if (scalar) {
            employeeMapper.update(null, update);
        }

        Employee after = requireEmployee(employeeId);
        auditService.record("PERSONNEL", "employee", "VERSION_ROLLBACK", ENTITY_TYPE, employeeId,
                before, after, reason, approvalId);
        com.company.ems.audit.entity.EntityVersion version = entityVersionService.snapshot(
                ENTITY_TYPE, employeeId, "ROLLBACK", versionSnapshotService.snapshot(after),
                operatorId, approvalId);
        return version == null ? null : version.getId();
    }

    private void endPrimaryPosition(Long employeeId) {
        LocalDate today = LocalDate.now();
        List<EmployeePosition> actives = employeePositionMapper.selectList(
                new LambdaQueryWrapper<EmployeePosition>()
                        .eq(EmployeePosition::getEmployeeId, employeeId)
                        .eq(EmployeePosition::getIsPrimary, true)
                        .eq(EmployeePosition::getStatus, "ACTIVE")
                        .and(w -> w.isNull(EmployeePosition::getEndDate)
                                .or().ge(EmployeePosition::getEndDate, today)));
        for (EmployeePosition active : actives) {
            active.setEndDate(today.minusDays(1));
            active.setStatus("ENDED");
            employeePositionMapper.updateById(active);
        }
    }

    private void endPrimaryManager(Long employeeId) {
        LocalDate today = LocalDate.now();
        List<ReportingLine> actives = reportingLineMapper.selectList(
                new LambdaQueryWrapper<ReportingLine>()
                        .eq(ReportingLine::getEmployeeId, employeeId)
                        .eq(ReportingLine::getIsPrimary, true)
                        .and(w -> w.isNull(ReportingLine::getEndDate)
                                .or().ge(ReportingLine::getEndDate, today)));
        for (ReportingLine active : actives) {
            active.setEndDate(today.minusDays(1));
            reportingLineMapper.updateById(active);
        }
    }

    private void collect(Object value, String field, Object current,
                         Map<String, Object> direct, Map<String, Object> key, Set<String> keyFields) {
        if (value == null) {
            return;
        }
        if (Objects.equals(String.valueOf(value), String.valueOf(current))) {
            return;
        }
        if (keyFields.contains(field)) {
            key.put(field, value);
        } else {
            direct.put(field, value);
        }
    }

    private void applyChanges(Employee employee, Map<String, Object> changes, Long ignored) {
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            switch (entry.getKey()) {
                case "name" -> employee.setName(asString(entry.getValue()));
                case "gender" -> employee.setGender(asString(entry.getValue()));
                case "birthDate" -> employee.setBirthDate(asDate(entry.getValue()));
                case "phone" -> employee.setPhone(asString(entry.getValue()));
                case "email" -> employee.setEmail(asString(entry.getValue()));
                case "idCard" -> employee.setIdCardEnc(asString(entry.getValue()));
                case "address" -> employee.setAddress(asString(entry.getValue()));
                case "emergencyContact" -> employee.setEmergencyContact(asString(entry.getValue()));
                case "hireDate" -> employee.setHireDate(asDate(entry.getValue()));
                case "org" -> {
                    Long orgId = asLong(entry.getValue());
                    if (orgId != null) {
                        employee.setPrimaryOrgId(orgId);
                    }
                }
                case "position" -> {
                    Long positionId = asLong(entry.getValue());
                    if (positionId != null) {
                        employee.setPrimaryPositionId(positionId);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void validateOrganizationRefs(Long orgId, Long positionId, Long managerId, Long selfId) {
        if (orgId != null) {
            OrgUnit org = orgUnitMapper.selectById(orgId);
            if (org == null || !"ACTIVE".equals(org.getStatus())) {
                throw new BusinessException(40001, "部门不存在或已停用");
            }
        }
        if (positionId != null) {
            Position position = positionMapper.selectById(positionId);
            if (position == null || !"ACTIVE".equals(position.getStatus())) {
                throw new BusinessException(40001, "岗位不存在或已停用");
            }
        }
        if (managerId != null) {
            if (Objects.equals(selfId, managerId)) {
                throw new BusinessException(40001, "不能设置自己为直属上级");
            }
            Employee manager = employeeMapper.selectById(managerId);
            if (manager == null || !"ACTIVE".equals(manager.getStatus())) {
                throw new BusinessException(40001, "直属上级不存在或已离职");
            }
            ensureNoManagerCycle(selfId, managerId);
        }
    }

    private void ensureNoManagerCycle(Long employeeId, Long managerId) {
        if (employeeId == null) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        Long cursor = managerId;
        while (cursor != null) {
            if (Objects.equals(cursor, employeeId)) {
                throw new BusinessException(40001, "汇报关系不能形成环");
            }
            if (!seen.add(cursor)) {
                return;
            }
            ReportingLine line = reportingLineMapper.selectOne(
                    new LambdaQueryWrapper<ReportingLine>()
                            .eq(ReportingLine::getEmployeeId, cursor)
                            .eq(ReportingLine::getIsPrimary, true)
                            .and(w -> w.isNull(ReportingLine::getEndDate)
                                    .or().ge(ReportingLine::getEndDate, LocalDate.now()))
                            .orderByDesc(ReportingLine::getStartDate)
                            .last("LIMIT 1"));
            cursor = line == null ? null : line.getManagerId();
        }
    }

    private void changePosition(Employee employee, Long orgId, Long positionId) {
        OrgUnit org = orgUnitMapper.selectById(orgId);
        if (org == null || !"ACTIVE".equals(org.getStatus())) {
            throw new BusinessException(40001, "部门不存在或已停用");
        }
        Position position = positionMapper.selectById(positionId);
        if (position == null || !"ACTIVE".equals(position.getStatus())) {
            throw new BusinessException(40001, "岗位不存在或已停用");
        }
        LocalDate today = LocalDate.now();
        List<EmployeePosition> actives = employeePositionMapper.selectList(
                new LambdaQueryWrapper<EmployeePosition>()
                        .eq(EmployeePosition::getEmployeeId, employee.getId())
                        .eq(EmployeePosition::getIsPrimary, true)
                        .eq(EmployeePosition::getStatus, "ACTIVE")
                        .and(w -> w.isNull(EmployeePosition::getEndDate).or().ge(EmployeePosition::getEndDate, today)));
        for (EmployeePosition active : actives) {
            active.setEndDate(today.minusDays(1));
            active.setStatus("ENDED");
            employeePositionMapper.updateById(active);
        }
        EmployeePosition assignment = new EmployeePosition();
        assignment.setEmployeeId(employee.getId());
        assignment.setOrgId(orgId);
        assignment.setPositionId(positionId);
        assignment.setIsPrimary(true);
        assignment.setStartDate(today);
        assignment.setStatus("ACTIVE");
        employeePositionMapper.insert(assignment);
        employee.setPrimaryOrgId(orgId);
        employee.setPrimaryPositionId(positionId);
    }

    private void changeManager(Employee employee, Long managerId) {
        if (Objects.equals(employee.getId(), managerId)) {
            throw new BusinessException(40001, "不能设置自己为直属上级");
        }
        Employee manager = employeeMapper.selectById(managerId);
        if (manager == null || !"ACTIVE".equals(manager.getStatus())) {
            throw new BusinessException(40001, "直属上级不存在或已离职");
        }
        ensureNoManagerCycle(employee.getId(), managerId);
        LocalDate today = LocalDate.now();
        List<ReportingLine> actives = reportingLineMapper.selectList(
                new LambdaQueryWrapper<ReportingLine>()
                        .eq(ReportingLine::getEmployeeId, employee.getId())
                        .eq(ReportingLine::getIsPrimary, true)
                        .and(w -> w.isNull(ReportingLine::getEndDate).or().ge(ReportingLine::getEndDate, today)));
        for (ReportingLine active : actives) {
            active.setEndDate(today.minusDays(1));
            reportingLineMapper.updateById(active);
        }
        ReportingLine line = new ReportingLine();
        line.setEmployeeId(employee.getId());
        line.setManagerId(managerId);
        line.setType("SOLID");
        line.setIsPrimary(true);
        line.setStartDate(today);
        reportingLineMapper.insert(line);
    }

    private Object currentFieldValue(Employee employee, String field) {
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
                .and(w -> w.isNull(ReportingLine::getEndDate).or().ge(ReportingLine::getEndDate, LocalDate.now()))
                .orderByDesc(ReportingLine::getStartDate)
                .last("LIMIT 1"));
        return line == null ? null : line.getManagerId();
    }

    private Set<String> keyFieldCodes() {
        SysApprovalPolicy policy = approvalPolicyMapper.selectByCode(KEY_INFO_POLICY);
        if (policy == null || !StringUtils.hasText(policy.getFieldCodes())) {
            return Set.of("idCard", "org", "position", "manager");
        }
        return java.util.Arrays.stream(policy.getFieldCodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private List<Long> orgSubtreeIds(Long orgId) {
        OrgUnit org = orgUnitMapper.selectById(orgId);
        if (org == null) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        ids.add(orgId);
        if (StringUtils.hasText(org.getPath())) {
            List<OrgUnit> descendants = orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnit>()
                    .likeRight(OrgUnit::getPath, org.getPath()));
            descendants.stream().map(OrgUnit::getId).filter(id -> !ids.contains(id)).forEach(ids::add);
        }
        return ids;
    }

    private List<EmployeeDtos.EmployeeView> toViews(List<Employee> employees, LoginUser user) {
        if (employees.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> employeeIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());
        Set<Long> orgIds = employees.stream().map(Employee::getPrimaryOrgId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> positionIds = employees.stream().map(Employee::getPrimaryPositionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, OrgUnit> orgs = orgIds.isEmpty() ? Collections.emptyMap()
                : orgUnitMapper.selectBatchIds(orgIds).stream()
                .collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        Map<Long, Position> positions = positionIds.isEmpty() ? Collections.emptyMap()
                : positionMapper.selectBatchIds(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Function.identity()));

        List<ReportingLine> lines = reportingLineMapper.selectList(new LambdaQueryWrapper<ReportingLine>()
                .in(ReportingLine::getEmployeeId, employeeIds)
                .eq(ReportingLine::getIsPrimary, true)
                .and(w -> w.isNull(ReportingLine::getEndDate).or().ge(ReportingLine::getEndDate, LocalDate.now()))
                .orderByDesc(ReportingLine::getStartDate));
        Map<Long, Long> managerByEmployee = new HashMap<>();
        for (ReportingLine line : lines) {
            managerByEmployee.putIfAbsent(line.getEmployeeId(), line.getManagerId());
        }
        Set<Long> managerIds = new HashSet<>(managerByEmployee.values());
        Map<Long, Employee> managers = managerIds.isEmpty() ? Collections.emptyMap()
                : employeeMapper.selectBatchIds(managerIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));

        List<EmployeeDtos.EmployeeView> views = new ArrayList<>();
        for (Employee employee : employees) {
            FieldPolicy policy = fieldPermissionService.resolve(ENTITY_TYPE, user, employee.getId());
            Set<String> masked = new HashSet<>();
            OrgUnit org = orgs.get(employee.getPrimaryOrgId());
            Position position = positions.get(employee.getPrimaryPositionId());
            Long managerId = managerByEmployee.get(employee.getId());
            Employee manager = managerId == null ? null : managers.get(managerId);

            views.add(new EmployeeDtos.EmployeeView(
                    employee.getId(),
                    employee.getEmployeeNo(),
                    value(policy, "name", employee.getName(), masked),
                    value(policy, "gender", employee.getGender(), masked),
                    employee.getBirthDate(),
                    value(policy, "phone", employee.getPhone(), masked),
                    value(policy, "email", employee.getEmail(), masked),
                    value(policy, "idCard", employee.getIdCardEnc(), masked),
                    value(policy, "address", employee.getAddress(), masked),
                    value(policy, "emergencyContact", employee.getEmergencyContact(), masked),
                    employee.getHireDate(),
                    employee.getStatus(),
                    policy.canView("org") ? employee.getPrimaryOrgId() : null,
                    policy.canView("org") && org != null ? org.getName() : null,
                    policy.canView("position") ? employee.getPrimaryPositionId() : null,
                    policy.canView("position") && position != null ? position.getName() : null,
                    policy.canView("manager") ? managerId : null,
                    policy.canView("manager") && manager != null ? manager.getName() : null,
                    masked
            ));
        }
        return views;
    }

    private EmployeeDtos.EmployeeView view(Employee employee, LoginUser user) {
        return toViews(List.of(employee), user).get(0);
    }

    private String value(FieldPolicy policy, String field, String raw, Set<String> masked) {
        if (!policy.canView(field)) {
            return null;
        }
        if (raw == null) {
            return null;
        }
        FieldAccess access = policy.access(field);
        if (access.masked()) {
            masked.add(field);
            return fieldPermissionService.mask(raw, access.maskRule());
        }
        return raw;
    }

    private Employee requireEmployee(Long id) {
        Employee employee = employeeMapper.selectById(id);
        if (employee == null) {
            throw new BusinessException(40401, "员工不存在");
        }
        return employee;
    }

    private Employee copy(Employee source) {
        Employee copy = new Employee();
        copy.setId(source.getId());
        copy.setEmployeeNo(source.getEmployeeNo());
        copy.setName(source.getName());
        copy.setGender(source.getGender());
        copy.setBirthDate(source.getBirthDate());
        copy.setPhone(source.getPhone());
        copy.setEmail(source.getEmail());
        copy.setIdCardEnc(source.getIdCardEnc());
        copy.setAddress(source.getAddress());
        copy.setEmergencyContact(source.getEmergencyContact());
        copy.setAvatarFileId(source.getAvatarFileId());
        copy.setHireDate(source.getHireDate());
        copy.setStatus(source.getStatus());
        copy.setPrimaryOrgId(source.getPrimaryOrgId());
        copy.setPrimaryPositionId(source.getPrimaryPositionId());
        return copy;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    private LocalDate asDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : LocalDate.parse(text);
    }
}
