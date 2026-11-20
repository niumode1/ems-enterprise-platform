package com.company.ems.employee;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.entity.OrgUnit;
import com.company.ems.employee.entity.Position;
import com.company.ems.employee.entity.ReportingLine;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.employee.mapper.OrgUnitMapper;
import com.company.ems.employee.mapper.PositionMapper;
import com.company.ems.employee.mapper.ReportingLineMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the restorable, human-readable employee snapshot used by entity versions.
 * ID-card ciphertext is deliberately never included.
 */
@Service
public class EmployeeVersionSnapshotService {

    private final EmployeeMapper employeeMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final ReportingLineMapper reportingLineMapper;

    public EmployeeVersionSnapshotService(EmployeeMapper employeeMapper,
                                          OrgUnitMapper orgUnitMapper,
                                          PositionMapper positionMapper,
                                          ReportingLineMapper reportingLineMapper) {
        this.employeeMapper = employeeMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.positionMapper = positionMapper;
        this.reportingLineMapper = reportingLineMapper;
    }

    public Map<String, Object> snapshot(Employee employee) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeNo", employee.getEmployeeNo());
        snapshot.put("name", employee.getName());
        snapshot.put("gender", employee.getGender());
        snapshot.put("birthDate", employee.getBirthDate());
        snapshot.put("phone", employee.getPhone());
        snapshot.put("email", employee.getEmail());
        snapshot.put("address", employee.getAddress());
        snapshot.put("emergencyContact", employee.getEmergencyContact());
        snapshot.put("hireDate", employee.getHireDate());
        snapshot.put("status", employee.getStatus());
        snapshot.put("org", employee.getPrimaryOrgId());
        snapshot.put("position", employee.getPrimaryPositionId());
        OrgUnit org = employee.getPrimaryOrgId() == null ? null
                : orgUnitMapper.selectById(employee.getPrimaryOrgId());
        Position position = employee.getPrimaryPositionId() == null ? null
                : positionMapper.selectById(employee.getPrimaryPositionId());
        snapshot.put("orgName", org == null ? null : org.getName());
        snapshot.put("positionName", position == null ? null : position.getName());
        Long managerId = currentManagerId(employee.getId());
        snapshot.put("manager", managerId);
        snapshot.put("managerName", managerId == null ? null
                : nameOf(managerId));
        return snapshot;
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

    private String nameOf(Long employeeId) {
        Employee employee = employeeMapper.selectById(employeeId);
        return employee == null ? null : employee.getName();
    }
}
