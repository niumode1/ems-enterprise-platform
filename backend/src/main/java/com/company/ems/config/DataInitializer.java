package com.company.ems.config;

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
import com.company.ems.system.entity.SysRole;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.entity.SysUserRole;
import com.company.ems.system.mapper.SysRoleMapper;
import com.company.ems.system.mapper.SysUserMapper;
import com.company.ems.system.mapper.SysUserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Creates a usable first-run dataset: org tree, positions, a few employees and
 * one account per built-in role. Companies can delete/rename this sample data
 * after initializing through the UI; nothing here is a business rule.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String DEFAULT_PASSWORD = "admin123";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final EmployeeMapper employeeMapper;
    private final EmployeePositionMapper employeePositionMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper userMapper,
                           SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper,
                           OrgUnitMapper orgUnitMapper,
                           PositionMapper positionMapper,
                           EmployeeMapper employeeMapper,
                           EmployeePositionMapper employeePositionMapper,
                           ReportingLineMapper reportingLineMapper,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.positionMapper = positionMapper;
        this.employeeMapper = employeeMapper;
        this.employeePositionMapper = employeePositionMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long userCount = userMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            return;
        }
        log.info("Initializing sample organization and accounts");

        OrgUnit company = createOrg(null, "示例公司", "COMPANY-ROOT", "COMPANY", 0);
        OrgUnit tech = createOrg(company.getId(), "技术部", "TECH", "DEPARTMENT", 10);
        OrgUnit hrDept = createOrg(company.getId(), "人力资源部", "HR", "DEPARTMENT", 20);

        Position chairman = createPosition("P001", "董事长", "M1");
        Position manager = createPosition("P002", "总经理", "M2");
        Position techLead = createPosition("P003", "技术主管", "M3");
        Position engineer = createPosition("P004", "工程师", "P2");
        Position hrSpecialist = createPosition("P005", "人事专员", "P2");
        Position auditor = createPosition("P006", "审计专员", "P2");
        Position projectManager = createPosition("P007", "项目经理", "M3");

        Employee boss = createEmployee("E001", "王老板", company.getId(), chairman.getId(), null, "13800000001", "boss@example.com");
        Employee admin = createEmployee("E002", "张三", company.getId(), manager.getId(), boss.getId(), "13800000002", "admin@example.com");
        Employee admin2 = createEmployee("E003", "赵六", tech.getId(), techLead.getId(), admin.getId(), "13800000003", "admin2@example.com");
        Employee employee = createEmployee("E004", "李四", tech.getId(), engineer.getId(), admin2.getId(), "13800000004", "employee@example.com");
        Employee hr = createEmployee("E005", "王五", hrDept.getId(), hrSpecialist.getId(), admin.getId(), "13800000005", "hr@example.com");
        Employee audit = createEmployee("E006", "陈审计", company.getId(), auditor.getId(), boss.getId(), "13800000006", "auditor@example.com");
        Employee pm = createEmployee("E007", "周项目", tech.getId(), projectManager.getId(), admin.getId(), "13800000007", "pm@example.com");

        company.setLeaderEmployeeId(boss.getId());
        tech.setLeaderEmployeeId(admin2.getId());
        hrDept.setLeaderEmployeeId(hr.getId());
        orgUnitMapper.updateById(company);
        orgUnitMapper.updateById(tech);
        orgUnitMapper.updateById(hrDept);

        createUser("boss", "王老板", boss.getId(), List.of("BOSS", "EMPLOYEE"));
        createUser("admin", "张三", admin.getId(), List.of("SYS_ADMIN", "EMPLOYEE"));
        createUser("admin2", "赵六", admin2.getId(), List.of("SYS_ADMIN", "EMPLOYEE"));
        createUser("employee", "李四", employee.getId(), List.of("EMPLOYEE"));
        createUser("hr", "王五", hr.getId(), List.of("HR_ADMIN", "EMPLOYEE"));
        createUser("auditor", "陈审计", audit.getId(), List.of("AUDITOR", "EMPLOYEE"));
        createUser("pm", "周项目", pm.getId(), List.of("PROJECT_LEADER", "EMPLOYEE"));

        log.warn("Sample accounts created. Default password: {} — change it immediately after first login.", DEFAULT_PASSWORD);
    }

    private OrgUnit createOrg(Long parentId, String name, String code, String type, int sort) {
        OrgUnit unit = new OrgUnit();
        unit.setParentId(parentId);
        unit.setName(name);
        unit.setCode(code);
        unit.setType(type);
        unit.setSort(sort);
        unit.setStatus("ACTIVE");
        orgUnitMapper.insert(unit);
        OrgUnit parent = parentId == null ? null : orgUnitMapper.selectById(parentId);
        String parentPath = parent == null || parent.getPath() == null ? "/" : parent.getPath();
        unit.setPath(parentPath + unit.getId() + "/");
        orgUnitMapper.updateById(unit);
        return unit;
    }

    private Position createPosition(String code, String name, String level) {
        Position position = new Position();
        position.setCode(code);
        position.setName(name);
        position.setLevel(level);
        position.setStatus("ACTIVE");
        positionMapper.insert(position);
        return position;
    }

    private Employee createEmployee(String no, String name, Long orgId, Long positionId,
                                    Long managerId, String phone, String email) {
        Employee employee = new Employee();
        employee.setEmployeeNo(no);
        employee.setName(name);
        employee.setGender("MALE");
        employee.setPhone(phone);
        employee.setEmail(email);
        employee.setHireDate(LocalDate.now().minusYears(1));
        employee.setStatus("ACTIVE");
        employee.setPrimaryOrgId(orgId);
        employee.setPrimaryPositionId(positionId);
        employeeMapper.insert(employee);

        EmployeePosition assignment = new EmployeePosition();
        assignment.setEmployeeId(employee.getId());
        assignment.setOrgId(orgId);
        assignment.setPositionId(positionId);
        assignment.setIsPrimary(true);
        assignment.setStartDate(employee.getHireDate());
        assignment.setStatus("ACTIVE");
        employeePositionMapper.insert(assignment);

        if (managerId != null) {
            ReportingLine line = new ReportingLine();
            line.setEmployeeId(employee.getId());
            line.setManagerId(managerId);
            line.setType("SOLID");
            line.setIsPrimary(true);
            line.setStartDate(employee.getHireDate());
            reportingLineMapper.insert(line);
        }
        return employee;
    }

    private void createUser(String username, String displayName, Long employeeId, List<String> roleCodes) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setDisplayName(displayName);
        user.setEmployeeId(employeeId);
        user.setStatus("ACTIVE");
        user.setMfaEnabled(false);
        userMapper.insert(user);
        for (String code : roleCodes) {
            SysRole role = roleMapper.selectByCode(code);
            if (role == null) {
                continue;
            }
            SysUserRole row = new SysUserRole();
            row.setUserId(user.getId());
            row.setRoleId(role.getId());
            row.setScopeType("SELF");
            userRoleMapper.insert(row);
        }
    }
}
