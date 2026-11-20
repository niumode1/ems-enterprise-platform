package com.company.ems.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.project.entity.DocFolder;
import com.company.ems.project.entity.Document;
import com.company.ems.project.entity.DocumentPermission;
import com.company.ems.project.entity.Project;
import com.company.ems.project.entity.ProjectMember;
import com.company.ems.project.mapper.DocumentMapper;
import com.company.ems.project.mapper.DocumentPermissionMapper;
import com.company.ems.project.mapper.DocFolderMapper;
import com.company.ems.project.mapper.ProjectMapper;
import com.company.ems.project.mapper.ProjectMemberMapper;
import com.company.ems.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    private static final Set<String> ROLES = Set.of("OWNER", "ADMIN", "MEMBER", "VIEWER");
    private static final Set<String> DOWNLOAD_POLICIES = Set.of("ALL", "ADMINS", "NONE");

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final DocumentMapper documentMapper;
    private final DocumentPermissionMapper documentPermissionMapper;
    private final DocFolderMapper folderMapper;
    private final EmployeeMapper employeeMapper;
    private final ConfigService configService;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;

    public ProjectService(ProjectMapper projectMapper,
                          ProjectMemberMapper memberMapper,
                          DocumentMapper documentMapper,
                          DocumentPermissionMapper documentPermissionMapper,
                          DocFolderMapper folderMapper,
                          EmployeeMapper employeeMapper,
                          ConfigService configService,
                          AuditService auditService,
                          FileStorageService fileStorageService) {
        this.projectMapper = projectMapper;
        this.memberMapper = memberMapper;
        this.documentMapper = documentMapper;
        this.documentPermissionMapper = documentPermissionMapper;
        this.folderMapper = folderMapper;
        this.employeeMapper = employeeMapper;
        this.configService = configService;
        this.auditService = auditService;
        this.fileStorageService = fileStorageService;
    }

    public PageResult<ProjectDtos.ProjectView> page(long page, long size, String keyword, String status,
                                                    LoginUser user) {
        if (user.employeeId() == null) {
            return new PageResult<>(0, page, size, Collections.emptyList());
        }
        List<Long> projectIds = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getEmployeeId, user.employeeId()))
                .stream().map(ProjectMember::getProjectId).toList();
        if (projectIds.isEmpty()) {
            return new PageResult<>(0, page, size, Collections.emptyList());
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .in(Project::getId, projectIds)
                .like(StringUtils.hasText(keyword), Project::getName, keyword)
                .eq(StringUtils.hasText(status), Project::getStatus, status)
                .orderByDesc(Project::getCreatedAt);
        Page<Project> result = projectMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(),
                views(result.getRecords(), user));
    }

    public ProjectDtos.ProjectView get(Long id, LoginUser user) {
        Project project = requireProject(id);
        requireMember(id, user);
        return views(List.of(project), user).get(0);
    }

    @Transactional
    public ProjectDtos.ProjectView create(ProjectDtos.ProjectRequest request, LoginUser user) {
        Long employeeId = requireEmployeeId(user);
        if (StringUtils.hasText(request.getCode())) {
            Long exists = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                    .eq(Project::getCode, request.getCode().trim()));
            if (exists != null && exists > 0) {
                throw new BusinessException(40901, "项目编码已存在");
            }
        }
        Project project = new Project();
        project.setName(request.getName());
        project.setCode(request.getCode());
        project.setDescription(request.getDescription());
        project.setOwnerId(employeeId);
        project.setOrgId(request.getOrgId());
        project.setStatus("ACTIVE");
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        project.setRetentionDays(request.getRetentionDays() != null
                ? request.getRetentionDays()
                : configService.getInt("project.retentionDays.default", 3650));
        project.setDownloadPolicy(normalizeDownloadPolicy(request.getDownloadPolicy()));
        project.setCreatedBy(user.userId());
        projectMapper.insert(project);

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(project.getId());
        owner.setEmployeeId(employeeId);
        owner.setMemberRole("OWNER");
        memberMapper.insert(owner);

        auditService.record("PROJECT", "project", "CREATE", "PROJECT", project.getId(),
                null, project, null, null);
        return views(List.of(project), user).get(0);
    }

    @Transactional
    public ProjectDtos.ProjectView update(Long id, ProjectDtos.ProjectRequest request, LoginUser user) {
        Project project = requireProject(id);
        ProjectMember member = requireMember(id, user);
        requireManager(member);
        if (StringUtils.hasText(request.getCode()) && !Objects.equals(request.getCode(), project.getCode())) {
            Long exists = projectMapper.selectCount(new LambdaQueryWrapper<Project>()
                    .eq(Project::getCode, request.getCode().trim())
                    .ne(Project::getId, id));
            if (exists != null && exists > 0) {
                throw new BusinessException(40901, "项目编码已存在");
            }
        }
        String before = project.toString();
        project.setName(request.getName());
        project.setCode(request.getCode());
        project.setDescription(request.getDescription());
        project.setOrgId(request.getOrgId());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        if (request.getRetentionDays() != null) {
            project.setRetentionDays(request.getRetentionDays());
        }
        if (StringUtils.hasText(request.getDownloadPolicy())) {
            project.setDownloadPolicy(normalizeDownloadPolicy(request.getDownloadPolicy()));
        }
        projectMapper.updateById(project);
        auditService.record("PROJECT", "project", "UPDATE", "PROJECT", id,
                before, project, null, null);
        return views(List.of(project), user).get(0);
    }

    @Transactional
    public void delete(Long id, LoginUser user) {
        Project project = requireProject(id);
        ProjectMember member = requireMember(id, user);
        if (!"OWNER".equals(member.getMemberRole())) {
            throw new BusinessException(40301, "只有项目负责人可以删除项目");
        }
        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .eq(Document::getProjectId, id));
        for (Document document : documents) {
            fileStorageService.delete(document.getStorageKey());
            documentPermissionMapper.delete(new LambdaQueryWrapper<DocumentPermission>()
                    .eq(DocumentPermission::getDocumentId, document.getId()));
        }
        documentMapper.delete(new LambdaQueryWrapper<Document>().eq(Document::getProjectId, id));
        folderMapper.delete(new LambdaQueryWrapper<DocFolder>().eq(DocFolder::getProjectId, id));
        memberMapper.delete(new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getProjectId, id));
        projectMapper.deleteById(id);
        auditService.record("PROJECT", "project", "DELETE", "PROJECT", id,
                project, null, null, null);
    }

    public List<ProjectDtos.MemberView> members(Long projectId, LoginUser user) {
        requireMember(projectId, user);
        List<ProjectMember> members = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .orderByAsc(ProjectMember::getMemberRole));
        Map<Long, Employee> employees = employees(members.stream().map(ProjectMember::getEmployeeId).toList());
        return members.stream().map(member -> {
            Employee employee = employees.get(member.getEmployeeId());
            return new ProjectDtos.MemberView(member.getId(), projectId, member.getEmployeeId(),
                    employee == null ? null : employee.getName(),
                    employee == null ? null : employee.getEmployeeNo(),
                    member.getMemberRole());
        }).toList();
    }

    @Transactional
    public ProjectDtos.MemberView addMember(Long projectId, ProjectDtos.MemberRequest request, LoginUser user) {
        ProjectMember actor = requireMember(projectId, user);
        requireManager(actor);
        String role = normalizeRole(request.getMemberRole());
        if ("OWNER".equals(role)) {
            throw new BusinessException(40001, "负责人角色不可通过成员接口设置");
        }
        ProjectMember existing = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getEmployeeId, request.getEmployeeId())
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setMemberRole(role);
            memberMapper.updateById(existing);
        } else {
            existing = new ProjectMember();
            existing.setProjectId(projectId);
            existing.setEmployeeId(request.getEmployeeId());
            existing.setMemberRole(role);
            memberMapper.insert(existing);
        }
        ProjectMember saved = existing;
        auditService.record("PROJECT", "member", "ASSIGN", "PROJECT_MEMBER", saved.getId(),
                null, saved, null, null);
        return members(projectId, user).stream()
                .filter(m -> m.id().equals(saved.getId()))
                .findFirst()
                .orElseThrow();
    }

    @Transactional
    public void removeMember(Long projectId, Long memberId, LoginUser user) {
        ProjectMember actor = requireMember(projectId, user);
        requireManager(actor);
        ProjectMember member = memberMapper.selectById(memberId);
        if (member == null || !Objects.equals(member.getProjectId(), projectId)) {
            throw new BusinessException(40401, "项目成员不存在");
        }
        if ("OWNER".equals(member.getMemberRole())) {
            throw new BusinessException(40001, "不能移除项目负责人");
        }
        memberMapper.deleteById(memberId);
        auditService.record("PROJECT", "member", "REMOVE", "PROJECT_MEMBER", memberId,
                member, null, null, null);
    }

    public List<ProjectDtos.FolderView> folders(Long projectId, LoginUser user) {
        requireMember(projectId, user);
        return folderMapper.selectList(new LambdaQueryWrapper<DocFolder>()
                        .eq(DocFolder::getProjectId, projectId)
                        .orderByAsc(DocFolder::getName))
                .stream().map(folder -> new ProjectDtos.FolderView(folder.getId(), projectId,
                        folder.getParentId(), folder.getName()))
                .toList();
    }

    @Transactional
    public ProjectDtos.FolderView createFolder(Long projectId, ProjectDtos.FolderRequest request, LoginUser user) {
        ProjectMember member = requireMember(projectId, user);
        if (!canUpload(member)) {
            throw new BusinessException(40301, "当前角色不能创建文件夹");
        }
        if (request.getParentId() != null) {
            DocFolder parent = folderMapper.selectById(request.getParentId());
            if (parent == null || !Objects.equals(parent.getProjectId(), projectId)) {
                throw new BusinessException(40001, "上级文件夹不存在");
            }
        }
        DocFolder folder = new DocFolder();
        folder.setProjectId(projectId);
        folder.setParentId(request.getParentId());
        folder.setName(request.getName());
        folder.setCreatedBy(user.userId());
        folderMapper.insert(folder);
        return new ProjectDtos.FolderView(folder.getId(), projectId, folder.getParentId(), folder.getName());
    }

    public Project requireProject(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(40401, "项目不存在");
        }
        return project;
    }

    public ProjectMember requireMember(Long projectId, LoginUser user) {
        if (user.employeeId() == null) {
            throw new BusinessException(40301, "当前账号未关联员工，不能访问项目");
        }
        ProjectMember member = memberMapper.selectOne(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getEmployeeId, user.employeeId())
                .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException(40302, "不是该项目成员");
        }
        return member;
    }

    public boolean isMember(Long projectId, Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                .eq(ProjectMember::getProjectId, projectId)
                .eq(ProjectMember::getEmployeeId, employeeId));
        return count != null && count > 0;
    }

    public boolean canUpload(ProjectMember member) {
        return "OWNER".equals(member.getMemberRole())
                || "ADMIN".equals(member.getMemberRole())
                || "MEMBER".equals(member.getMemberRole());
    }

    public boolean isManager(ProjectMember member) {
        return "OWNER".equals(member.getMemberRole()) || "ADMIN".equals(member.getMemberRole());
    }

    public void requireManager(ProjectMember member) {
        if (!isManager(member)) {
            throw new BusinessException(40301, "需要项目负责人或项目管理员权限");
        }
    }

    private List<ProjectDtos.ProjectView> views(List<Project> projects, LoginUser user) {
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, String> roles = memberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getEmployeeId, user.employeeId())
                        .in(ProjectMember::getProjectId, projectIds))
                .stream().collect(Collectors.toMap(ProjectMember::getProjectId, ProjectMember::getMemberRole));
        Map<Long, Employee> owners = employees(projects.stream().map(Project::getOwnerId).toList());
        List<ProjectDtos.ProjectView> views = new ArrayList<>();
        for (Project project : projects) {
            Employee owner = owners.get(project.getOwnerId());
            long members = memberMapper.selectCount(new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, project.getId()));
            long documents = documentMapper.selectCount(new LambdaQueryWrapper<Document>()
                    .eq(Document::getProjectId, project.getId()));
            views.add(new ProjectDtos.ProjectView(project.getId(), project.getName(), project.getCode(),
                    project.getDescription(), project.getOwnerId(), owner == null ? null : owner.getName(),
                    project.getOrgId(), project.getStatus(), project.getStartDate(), project.getEndDate(),
                    project.getRetentionDays(), project.getDownloadPolicy(),
                    project.getProgress() == null ? 0 : project.getProgress(),
                    project.getProgressMode() == null ? "MANUAL" : project.getProgressMode(),
                    roles.get(project.getId()),
                    members, documents, project.getCreatedAt()));
        }
        return views;
    }

    private Map<Long, Employee> employees(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return employeeMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    private Long requireEmployeeId(LoginUser user) {
        if (user.employeeId() == null) {
            throw new BusinessException(40001, "当前账号未关联员工档案，不能创建项目");
        }
        return user.employeeId();
    }

    private String normalizeRole(String role) {
        String value = role == null ? "MEMBER" : role.toUpperCase();
        if (!ROLES.contains(value)) {
            throw new BusinessException(40001, "项目角色必须是 OWNER/ADMIN/MEMBER/VIEWER");
        }
        return value;
    }

    private String normalizeDownloadPolicy(String policy) {
        String value = StringUtils.hasText(policy) ? policy.toUpperCase() : "ALL";
        if (!DOWNLOAD_POLICIES.contains(value)) {
            throw new BusinessException(40001, "下载策略必须是 ALL/ADMINS/NONE");
        }
        return value;
    }
}
