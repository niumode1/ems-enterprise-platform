package com.company.ems.employee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.entity.OrgUnit;
import com.company.ems.employee.entity.Position;
import com.company.ems.employee.entity.ReportingLine;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.employee.mapper.OrgUnitMapper;
import com.company.ems.employee.mapper.PositionMapper;
import com.company.ems.employee.mapper.ReportingLineMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrgService {

    private final OrgUnitMapper orgUnitMapper;
    private final EmployeeMapper employeeMapper;
    private final PositionMapper positionMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final AuditService auditService;

    public OrgService(OrgUnitMapper orgUnitMapper,
                      EmployeeMapper employeeMapper,
                      PositionMapper positionMapper,
                      ReportingLineMapper reportingLineMapper,
                      AuditService auditService) {
        this.orgUnitMapper = orgUnitMapper;
        this.employeeMapper = employeeMapper;
        this.positionMapper = positionMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.auditService = auditService;
    }

    public List<OrgDtos.OrgNode> tree() {
        List<OrgUnit> units = orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnit>()
                .orderByAsc(OrgUnit::getSort)
                .orderByAsc(OrgUnit::getName));
        Map<Long, Long> counts = employeeCounts();
        Map<Long, OrgDtos.OrgNode> nodes = new LinkedHashMap<>();
        for (OrgUnit unit : units) {
            nodes.put(unit.getId(), new OrgDtos.OrgNode(
                    unit.getId(), unit.getParentId(), unit.getName(), unit.getCode(), unit.getType(),
                    unit.getLeaderEmployeeId(), unit.getPath(), unit.getSort(), unit.getStatus(),
                    counts.getOrDefault(unit.getId(), 0L), new ArrayList<>()
            ));
        }
        List<OrgDtos.OrgNode> roots = new ArrayList<>();
        for (OrgUnit unit : units) {
            OrgDtos.OrgNode node = nodes.get(unit.getId());
            OrgDtos.OrgNode parent = unit.getParentId() == null ? null : nodes.get(unit.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        sortNodes(roots);
        return roots;
    }

    /**
     * Active reporting-line relations for one employee. Only identity fields are
     * returned, so callers cannot use this endpoint to bypass field permissions.
     */
    public OrgDtos.EmployeeRelations relations(Long employeeId) {
        Employee target = employeeMapper.selectById(employeeId);
        if (target == null) {
            throw new BusinessException(40401, "员工不存在");
        }
        ReportingLine line = primaryLine(employeeId);
        Long managerId = line == null ? null : line.getManagerId();
        Employee manager = managerId == null ? null : employeeMapper.selectById(managerId);
        List<Employee> superiors = new ArrayList<>();
        Set<Long> seen = new java.util.LinkedHashSet<>();
        Long cursor = managerId;
        while (cursor != null && seen.add(cursor)) {
            Employee superior = employeeMapper.selectById(cursor);
            if (superior == null) {
                break;
            }
            superiors.add(superior);
            ReportingLine superiorLine = primaryLine(cursor);
            cursor = superiorLine == null ? null : superiorLine.getManagerId();
        }
        List<Employee> reports = directReports(employeeId);
        List<Employee> siblings = managerId == null
                ? Collections.emptyList()
                : directReports(managerId).stream()
                        .filter(employee -> !Objects.equals(employee.getId(), employeeId))
                        .toList();
        return new OrgDtos.EmployeeRelations(employeeId,
                relationship(manager),
                relationships(superiors),
                relationships(reports),
                relationships(siblings));
    }

    private ReportingLine primaryLine(Long employeeId) {
        LocalDate today = LocalDate.now();
        return reportingLineMapper.selectOne(new LambdaQueryWrapper<ReportingLine>()
                .eq(ReportingLine::getEmployeeId, employeeId)
                .eq(ReportingLine::getIsPrimary, true)
                .and(w -> w.isNull(ReportingLine::getStartDate)
                        .or().le(ReportingLine::getStartDate, today))
                .and(w -> w.isNull(ReportingLine::getEndDate)
                        .or().ge(ReportingLine::getEndDate, today))
                .orderByDesc(ReportingLine::getStartDate)
                .last("LIMIT 1"));
    }

    private List<Employee> directReports(Long managerId) {
        LocalDate today = LocalDate.now();
        List<Long> ids = reportingLineMapper.selectList(new LambdaQueryWrapper<ReportingLine>()
                        .eq(ReportingLine::getManagerId, managerId)
                        .eq(ReportingLine::getIsPrimary, true)
                        .and(w -> w.isNull(ReportingLine::getStartDate)
                                .or().le(ReportingLine::getStartDate, today))
                        .and(w -> w.isNull(ReportingLine::getEndDate)
                                .or().ge(ReportingLine::getEndDate, today)))
                .stream().map(ReportingLine::getEmployeeId).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return employeeMapper.selectBatchIds(ids).stream()
                .filter(employee -> "ACTIVE".equals(employee.getStatus()))
                .sorted(Comparator.comparing(Employee::getEmployeeNo,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private List<OrgDtos.RelationEmployee> relationships(List<Employee> employees) {
        return employees.stream().map(this::relationship).toList();
    }

    private OrgDtos.RelationEmployee relationship(Employee employee) {
        return employee == null ? null
                : new OrgDtos.RelationEmployee(employee.getId(), employee.getEmployeeNo(),
                        employee.getName());
    }

    @Transactional
    public OrgUnit create(OrgDtos.OrgRequest request) {
        if (StringUtils.hasText(request.getCode())) {
            Long exists = orgUnitMapper.selectCount(new LambdaQueryWrapper<OrgUnit>()
                    .eq(OrgUnit::getCode, request.getCode()));
            if (exists != null && exists > 0) {
                throw new BusinessException(40901, "组织编码已存在");
            }
        }
        OrgUnit parent = null;
        if (request.getParentId() != null) {
            parent = orgUnitMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BusinessException(40401, "上级组织不存在");
            }
        }
        OrgUnit unit = new OrgUnit();
        unit.setParentId(request.getParentId());
        unit.setName(request.getName());
        unit.setCode(request.getCode());
        unit.setType(StringUtils.hasText(request.getType()) ? request.getType() : "DEPARTMENT");
        unit.setLeaderEmployeeId(request.getLeaderEmployeeId());
        unit.setSort(request.getSort() == null ? 0 : request.getSort());
        unit.setStatus("ACTIVE");
        orgUnitMapper.insert(unit);
        String parentPath = parent == null || !StringUtils.hasText(parent.getPath())
                ? "/" : parent.getPath();
        unit.setPath(parentPath + unit.getId() + "/");
        orgUnitMapper.updateById(unit);
        auditService.record("ORGANIZATION", "org", "CREATE", "ORG_UNIT", unit.getId(),
                null, unit, null, null);
        return unit;
    }

    @Transactional
    public OrgUnit update(Long id, OrgDtos.OrgRequest request) {
        OrgUnit existing = requireOrg(id);
        String before = existing.toString();
        if (request.getParentId() != null && Objects.equals(request.getParentId(), id)) {
            throw new BusinessException(40001, "不能将组织设为自己的上级");
        }
        OrgUnit newParent = null;
        if (request.getParentId() != null) {
            newParent = orgUnitMapper.selectById(request.getParentId());
            if (newParent == null) {
                throw new BusinessException(40401, "上级组织不存在");
            }
            if (StringUtils.hasText(existing.getPath())
                    && StringUtils.hasText(newParent.getPath())
                    && newParent.getPath().startsWith(existing.getPath())) {
                throw new BusinessException(40001, "不能将组织移动到自己的下级");
            }
        }
        String oldPath = existing.getPath();
        Long oldParentId = existing.getParentId();
        existing.setParentId(request.getParentId());
        existing.setName(request.getName());
        if (StringUtils.hasText(request.getCode())) {
            existing.setCode(request.getCode());
        }
        if (StringUtils.hasText(request.getType())) {
            existing.setType(request.getType());
        }
        existing.setLeaderEmployeeId(request.getLeaderEmployeeId());
        if (request.getSort() != null) {
            existing.setSort(request.getSort());
        }
        if (!Objects.equals(oldParentId, request.getParentId())) {
            String parentPath = newParent == null || !StringUtils.hasText(newParent.getPath())
                    ? "/" : newParent.getPath();
            String newPath = parentPath + existing.getId() + "/";
            existing.setPath(newPath);
            updateDescendantPaths(oldPath, newPath);
        }
        orgUnitMapper.updateById(existing);
        auditService.record("ORGANIZATION", "org", "UPDATE", "ORG_UNIT", id,
                before, existing, null, null);
        return existing;
    }

    @Transactional
    public OrgUnit disable(Long id) {
        OrgUnit existing = requireOrg(id);
        Long activeChildren = orgUnitMapper.selectCount(new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getParentId, id)
                .eq(OrgUnit::getStatus, "ACTIVE"));
        if (activeChildren != null && activeChildren > 0) {
            throw new BusinessException(40901, "请先停用下级组织");
        }
        Long activeEmployees = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getPrimaryOrgId, id)
                .eq(Employee::getStatus, "ACTIVE"));
        if (activeEmployees != null && activeEmployees > 0) {
            throw new BusinessException(40901, "该组织下仍有在职员工，不能停用");
        }
        existing.setStatus("DISABLED");
        orgUnitMapper.updateById(existing);
        auditService.record("ORGANIZATION", "org", "DISABLE", "ORG_UNIT", id,
                "ACTIVE", "DISABLED", null, null);
        return existing;
    }

    public List<Position> positions() {
        return positionMapper.selectList(new LambdaQueryWrapper<Position>()
                .eq(Position::getStatus, "ACTIVE")
                .orderByAsc(Position::getCode));
    }

    @Transactional
    public Position createPosition(OrgDtos.PositionRequest request) {
        Long exists = positionMapper.selectCount(new LambdaQueryWrapper<Position>()
                .eq(Position::getCode, request.getCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(40901, "岗位编码已存在");
        }
        Position position = new Position();
        position.setCode(request.getCode());
        position.setName(request.getName());
        position.setLevel(request.getLevel());
        position.setStatus("ACTIVE");
        positionMapper.insert(position);
        auditService.record("ORGANIZATION", "position", "CREATE", "POSITION", position.getId(),
                null, position, null, null);
        return position;
    }

    public List<OrgDtos.ReportingNode> reportingTree() {
        LocalDate today = LocalDate.now();
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStatus, "ACTIVE")
                .orderByAsc(Employee::getEmployeeNo));
        List<ReportingLine> lines = reportingLineMapper.selectList(new LambdaQueryWrapper<ReportingLine>()
                .eq(ReportingLine::getIsPrimary, true)
                .and(w -> w.isNull(ReportingLine::getEndDate).or().ge(ReportingLine::getEndDate, today))
                .orderByDesc(ReportingLine::getStartDate));
        Map<Long, Long> managerByEmployee = new HashMap<>();
        for (ReportingLine line : lines) {
            managerByEmployee.putIfAbsent(line.getEmployeeId(), line.getManagerId());
        }
        Set<Long> positionIds = employees.stream().map(Employee::getPrimaryPositionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Position> positions = positionIds.isEmpty() ? Collections.emptyMap()
                : positionMapper.selectBatchIds(positionIds).stream()
                .collect(Collectors.toMap(Position::getId, Function.identity()));

        Map<Long, OrgDtos.ReportingNode> nodes = new LinkedHashMap<>();
        for (Employee employee : employees) {
            Position position = positions.get(employee.getPrimaryPositionId());
            nodes.put(employee.getId(), new OrgDtos.ReportingNode(
                    employee.getId(), employee.getName(), employee.getEmployeeNo(),
                    employee.getPrimaryPositionId(), position == null ? null : position.getName(),
                    new ArrayList<>()
            ));
        }
        List<OrgDtos.ReportingNode> roots = new ArrayList<>();
        for (Employee employee : employees) {
            OrgDtos.ReportingNode node = nodes.get(employee.getId());
            Long managerId = managerByEmployee.get(employee.getId());
            OrgDtos.ReportingNode manager = managerId == null ? null : nodes.get(managerId);
            if (manager == null) {
                roots.add(node);
            } else {
                manager.children().add(node);
            }
        }
        return roots;
    }

    private Map<Long, Long> employeeCounts() {
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStatus, "ACTIVE")
                .isNotNull(Employee::getPrimaryOrgId));
        Map<Long, Long> counts = new HashMap<>();
        for (Employee employee : employees) {
            counts.merge(employee.getPrimaryOrgId(), 1L, Long::sum);
        }
        return counts;
    }

    private void updateDescendantPaths(String oldPath, String newPath) {
        if (!StringUtils.hasText(oldPath)) {
            return;
        }
        List<OrgUnit> descendants = orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnit>()
                .likeRight(OrgUnit::getPath, oldPath));
        for (OrgUnit descendant : descendants) {
            if (descendant.getPath() != null && descendant.getPath().startsWith(oldPath)
                    && !descendant.getPath().equals(oldPath)) {
                descendant.setPath(newPath + descendant.getPath().substring(oldPath.length()));
                orgUnitMapper.updateById(descendant);
            }
        }
    }

    private void sortNodes(List<OrgDtos.OrgNode> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        nodes.sort((left, right) -> {
            int leftSort = left.sort() == null ? 0 : left.sort();
            int rightSort = right.sort() == null ? 0 : right.sort();
            if (leftSort != rightSort) {
                return Integer.compare(leftSort, rightSort);
            }
            return String.valueOf(left.name()).compareTo(String.valueOf(right.name()));
        });
        for (OrgDtos.OrgNode node : nodes) {
            sortNodes(node.children());
        }
    }

    private OrgUnit requireOrg(Long id) {
        OrgUnit unit = orgUnitMapper.selectById(id);
        if (unit == null) {
            throw new BusinessException(40401, "组织不存在");
        }
        return unit;
    }
}
