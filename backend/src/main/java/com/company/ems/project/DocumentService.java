package com.company.ems.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.project.entity.DocFolder;
import com.company.ems.project.entity.Document;
import com.company.ems.project.entity.DocumentPermission;
import com.company.ems.project.entity.DocumentVersion;
import com.company.ems.project.entity.Project;
import com.company.ems.project.entity.ProjectMember;
import com.company.ems.project.mapper.DocFolderMapper;
import com.company.ems.project.mapper.DocumentMapper;
import com.company.ems.project.mapper.DocumentPermissionMapper;
import com.company.ems.project.mapper.DocumentVersionMapper;
import com.company.ems.project.mapper.ProjectMapper;
import com.company.ems.project.mapper.ProjectMemberMapper;
import com.company.ems.security.LoginUser;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final java.time.format.DateTimeFormatter PRINT_TIME_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DocumentMapper documentMapper;
    private final DocumentPermissionMapper permissionMapper;
    private final DocumentVersionMapper versionMapper;
    private final DocFolderMapper folderMapper;
    private final EmployeeMapper employeeMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMapper projectMapper;
    private final ProjectService projectService;
    private final FileStorageService fileStorageService;
    private final DocumentTextExtractor textExtractor;
    private final ConfigService configService;
    private final AuditService auditService;

    public DocumentService(DocumentMapper documentMapper,
                           DocumentPermissionMapper permissionMapper,
                           DocumentVersionMapper versionMapper,
                           DocFolderMapper folderMapper,
                           EmployeeMapper employeeMapper,
                           ProjectMemberMapper projectMemberMapper,
                           ProjectMapper projectMapper,
                           ProjectService projectService,
                           FileStorageService fileStorageService,
                           DocumentTextExtractor textExtractor,
                           ConfigService configService,
                           AuditService auditService) {
        this.documentMapper = documentMapper;
        this.permissionMapper = permissionMapper;
        this.versionMapper = versionMapper;
        this.folderMapper = folderMapper;
        this.employeeMapper = employeeMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.projectMapper = projectMapper;
        this.projectService = projectService;
        this.fileStorageService = fileStorageService;
        this.textExtractor = textExtractor;
        this.configService = configService;
        this.auditService = auditService;
    }

    public List<ProjectDtos.DocumentView> list(Long projectId, Long folderId, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getProjectId, projectId)
                .eq(folderId != null, Document::getFolderId, folderId)
                .orderByDesc(Document::getCreatedAt);
        List<Document> documents = documentMapper.selectList(wrapper);
        Project project = projectService.requireProject(projectId);
        Map<Long, DocumentPermission> overrides = overrides(documents, user.employeeId());
        Map<Long, Employee> uploaders = employees(documents.stream().map(Document::getUploaderId).toList());
        List<ProjectDtos.DocumentView> views = new ArrayList<>();
        for (Document document : documents) {
            DocumentPermission override = overrides.get(document.getId());
            if (!canView(document, member, override, user.employeeId())) {
                continue;
            }
            Employee uploader = uploaders.get(document.getUploaderId());
            views.add(new ProjectDtos.DocumentView(document.getId(), document.getProjectId(),
                    document.getFolderId(), document.getName(), document.getOriginalName(),
                    document.getSizeBytes(), document.getContentType(), document.getUploaderId(),
                    uploader == null ? null : uploader.getName(),
                    document.getCurrentVersion() == null ? 1 : document.getCurrentVersion(),
                    document.getCreatedAt(),
                    canDownload(document, project, member, override, user.employeeId()),
                    canPrint(document, project, member, override, user.employeeId()),
                    canDelete(document, member, override, user.employeeId())));
        }
        return views;
    }

    @Transactional
    public ProjectDtos.DocumentView upload(Long projectId, Long folderId, String name,
                                           MultipartFile file, LoginUser user) {
        ProjectMember member = projectService.requireMember(projectId, user);
        if (!projectService.canUpload(member)) {
            throw new BusinessException(40301, "当前项目角色不能上传文档");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "请选择要上传的文件");
        }
        long maxBytes = (long) configService.getInt("doc.file.maxMb", 200) * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(40001, "文件超过大小限制");
        }
        if (folderId != null) {
            DocFolder folder = folderMapper.selectById(folderId);
            if (folder == null || !Objects.equals(folder.getProjectId(), projectId)) {
                throw new BusinessException(40001, "文件夹不存在");
            }
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : (StringUtils.hasText(name) ? name : "file");
        String storageKey;
        try {
            storageKey = fileStorageService.store(projectId, originalName, file.getInputStream());
        } catch (IOException ex) {
            throw new BusinessException(50001, "文件存储失败");
        }
        String contentText = textExtractor.extract(file);
        Document document = new Document();
        document.setProjectId(projectId);
        document.setFolderId(folderId);
        document.setName(StringUtils.hasText(name) ? name : originalName);
        document.setOriginalName(originalName);
        document.setStorageKey(storageKey);
        document.setSizeBytes(file.getSize());
        document.setContentType(file.getContentType());
        document.setUploaderId(user.employeeId());
        document.setStatus("ACTIVE");
        document.setCurrentVersion(1);
        document.setContentText(contentText);
        document.setContentIndexedAt(contentText == null ? null : LocalDateTime.now());
        documentMapper.insert(document);
        insertVersion(document, 1, storageKey, document.getName(), originalName, file.getSize(),
                file.getContentType(), contentText, user.employeeId(), "初始版本", "CURRENT");
        auditService.record("DOCUMENT", "document", "UPLOAD", "DOCUMENT", document.getId(),
                null, document, null, null);
        Project project = projectService.requireProject(projectId);
        return new ProjectDtos.DocumentView(document.getId(), projectId, folderId, document.getName(),
                originalName, document.getSizeBytes(), document.getContentType(),
                document.getUploaderId(), user.displayName(), 1, document.getCreatedAt(),
                canDownload(document, project, member, null, user.employeeId()),
                canPrint(document, project, member, null, user.employeeId()),
                canDelete(document, member, null, user.employeeId()));
    }

    public Download download(Long documentId, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        Project project = projectService.requireProject(document.getProjectId());
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canView(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档");
        }
        if (!canDownload(document, project, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权下载该文档");
        }
        Resource resource = fileStorageService.load(document.getStorageKey());
        auditService.access("DOCUMENT", documentId, "DOWNLOAD", false);
        String filename = StringUtils.hasText(document.getOriginalName())
                ? document.getOriginalName() : document.getName();
        return new Download(filename, resource, document.getContentType());
    }

    /**
     * Print/preview entry point. Binary formats cannot be rendered by the server,
     * so the response only carries indexed text when available; the browser then
     * prints the text preview with the configured watermark. Permission is still
     * enforced here and every print is recorded in the access log.
     */
    public ProjectDtos.DocumentPrint print(Long documentId, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        Project project = projectService.requireProject(document.getProjectId());
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canView(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档");
        }
        if (!canPrint(document, project, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权打印该文档");
        }
        auditService.access("DOCUMENT", documentId, "PRINT", false);
        String content = document.getContentText();
        boolean watermarkEnabled = configService.getBoolean("doc.watermark.enabled", false);
        String watermarkText = watermarkEnabled ? watermark(document, project, user) : null;
        return new ProjectDtos.DocumentPrint(document.getId(), document.getName(),
                document.getOriginalName(), document.getContentType(),
                StringUtils.hasText(content), content, watermarkEnabled, watermarkText);
    }

    private String watermark(Document document, Project project, LoginUser user) {
        String template = configService.getString("doc.watermark.text", "{name} {time}");
        Employee employee = user.employeeId() == null ? null : employeeMapper.selectById(user.employeeId());
        String name = StringUtils.hasText(user.displayName()) ? user.displayName() : user.username();
        return template
                .replace("{name}", name == null ? "" : name)
                .replace("{employeeNo}", employee == null || employee.getEmployeeNo() == null
                        ? "" : employee.getEmployeeNo())
                .replace("{project}", project == null || project.getName() == null ? "" : project.getName())
                .replace("{time}", LocalDateTime.now().format(PRINT_TIME_FORMAT));
    }

    @Transactional
    public void delete(Long documentId, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canDelete(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权删除该文档");
        }
        List<DocumentVersion> versions = versionMapper.selectList(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId));
        permissionMapper.delete(new LambdaQueryWrapper<DocumentPermission>()
                .eq(DocumentPermission::getDocumentId, documentId));
        versionMapper.delete(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId));
        documentMapper.deleteById(documentId);
        Set<String> storageKeys = versions.stream().map(DocumentVersion::getStorageKey)
                .collect(Collectors.toSet());
        storageKeys.add(document.getStorageKey());
        storageKeys.stream().filter(StringUtils::hasText).forEach(fileStorageService::delete);
        auditService.record("DOCUMENT", "document", "DELETE", "DOCUMENT", documentId,
                document, null, null, null);
    }

    public List<ProjectDtos.DocumentPermissionView> permissions(Long documentId, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        projectService.requireManager(member);
        List<DocumentPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<DocumentPermission>()
                        .eq(DocumentPermission::getDocumentId, documentId)
                        .orderByAsc(DocumentPermission::getCreatedAt));
        Map<Long, Employee> employees = employees(
                permissions.stream().map(DocumentPermission::getEmployeeId).toList());
        return permissions.stream().map(permission -> {
            Employee employee = employees.get(permission.getEmployeeId());
            return new ProjectDtos.DocumentPermissionView(permission.getId(), documentId,
                    permission.getEmployeeId(), employee == null ? null : employee.getName(),
                    Boolean.TRUE.equals(permission.getCanView()),
                    Boolean.TRUE.equals(permission.getCanDownload()),
                    Boolean.TRUE.equals(permission.getCanPrint()),
                    Boolean.TRUE.equals(permission.getCanDelete()));
        }).toList();
    }

    @Transactional
    public ProjectDtos.DocumentPermissionView setPermission(Long documentId,
                                                            ProjectDtos.DocumentPermissionRequest request,
                                                            LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        projectService.requireManager(member);
        if (!projectService.isMember(document.getProjectId(), request.getEmployeeId())) {
            throw new BusinessException(40001, "文档权限只能授予项目成员");
        }
        DocumentPermission permission = override(documentId, request.getEmployeeId());
        boolean created = permission == null;
        if (created) {
            permission = new DocumentPermission();
            permission.setDocumentId(documentId);
            permission.setEmployeeId(request.getEmployeeId());
        }
        if (request.getCanView() != null) {
            permission.setCanView(request.getCanView());
        } else if (created) {
            permission.setCanView(true);
        }
        if (request.getCanDownload() != null) {
            permission.setCanDownload(request.getCanDownload());
        } else if (created) {
            permission.setCanDownload(false);
        }
        if (request.getCanPrint() != null) {
            permission.setCanPrint(request.getCanPrint());
        } else if (created) {
            permission.setCanPrint(false);
        }
        if (request.getCanDelete() != null) {
            permission.setCanDelete(request.getCanDelete());
        } else if (created) {
            permission.setCanDelete(false);
        }
        if (created) {
            permissionMapper.insert(permission);
        } else {
            permissionMapper.updateById(permission);
        }
        auditService.record("DOCUMENT", "document", "ACL_UPDATE", "DOCUMENT", documentId,
                null, permission, null, null);
        Employee employee = employeeMapper.selectById(request.getEmployeeId());
        return new ProjectDtos.DocumentPermissionView(permission.getId(), documentId,
                permission.getEmployeeId(), employee == null ? null : employee.getName(),
                Boolean.TRUE.equals(permission.getCanView()),
                Boolean.TRUE.equals(permission.getCanDownload()),
                Boolean.TRUE.equals(permission.getCanPrint()),
                Boolean.TRUE.equals(permission.getCanDelete()));
    }

    public List<ProjectDtos.DocumentVersionView> versions(Long documentId, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canView(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档");
        }
        if (!hasDocRole(configService.getString("doc.version.viewRoles",
                        "OWNER,ADMIN,UPLOADER,MEMBER,VIEWER"), member, document, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档的版本历史");
        }
        List<DocumentVersion> rows = versionMapper.selectList(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId)
                .orderByDesc(DocumentVersion::getVersionNo));
        Map<Long, Employee> uploaders = employees(rows.stream().map(DocumentVersion::getUploaderId).toList());
        return rows.stream().map(row -> toVersionView(row, uploaders.get(row.getUploaderId()))).toList();
    }

    @Transactional
    public ProjectDtos.DocumentVersionView uploadVersion(Long documentId, String name, String note,
                                                         MultipartFile file, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canView(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档");
        }
        requireVersionEnabled();
        if (!hasDocRole(configService.getString("doc.version.uploadRoles", "OWNER,ADMIN,UPLOADER"),
                member, document, user.employeeId())) {
            throw new BusinessException(40301, "当前项目角色不能上传文档新版本");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "请选择要上传的文件");
        }
        long maxBytes = (long) configService.getInt("doc.file.maxMb", 200) * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(40001, "文件超过大小限制");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : (StringUtils.hasText(name) ? name : document.getOriginalName());
        String storageKey;
        try {
            storageKey = fileStorageService.store(document.getProjectId(), originalName, file.getInputStream());
        } catch (IOException ex) {
            throw new BusinessException(50001, "文件存储失败");
        }
        String contentText = textExtractor.extract(file);
        int current = document.getCurrentVersion() == null ? 1 : document.getCurrentVersion();
        int next = current + 1;
        archiveCurrentVersions(documentId);
        DocumentVersion version = insertVersion(document, next, storageKey,
                StringUtils.hasText(name) ? name : document.getName(), originalName,
                file.getSize(), file.getContentType(), contentText, user.employeeId(),
                StringUtils.hasText(note) ? note : "上传新版本", "CURRENT");

        String newName = StringUtils.hasText(name) ? name : document.getName();
        document.setName(newName);
        document.setOriginalName(originalName);
        document.setStorageKey(storageKey);
        document.setSizeBytes(file.getSize());
        document.setContentType(file.getContentType());
        document.setCurrentVersion(next);
        document.setContentText(contentText);
        document.setContentIndexedAt(contentText == null ? null : LocalDateTime.now());
        updateDocumentContent(document);
        pruneVersions(documentId, storageKey);
        auditService.record("DOCUMENT", "document", "VERSION_UPLOAD", "DOCUMENT", documentId,
                Map.of("version", current), Map.of("version", next), note, null);
        return toVersionView(version, employeeMapper.selectById(user.employeeId()));
    }

    @Transactional
    public ProjectDtos.DocumentVersionView rollback(Long documentId, int versionNo, String note,
                                                    LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canView(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档");
        }
        requireVersionEnabled();
        if (!hasDocRole(configService.getString("doc.version.rollbackRoles", "OWNER,ADMIN,UPLOADER"),
                member, document, user.employeeId())) {
            throw new BusinessException(40301, "当前项目角色不能回滚文档版本");
        }
        DocumentVersion target = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId)
                .eq(DocumentVersion::getVersionNo, versionNo)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException(40401, "版本不存在");
        }
        int current = document.getCurrentVersion() == null ? 1 : document.getCurrentVersion();
        if (target.getVersionNo() == current) {
            throw new BusinessException(40001, "该版本已是当前版本");
        }
        String storageKey;
        try (InputStream input = fileStorageService.load(target.getStorageKey()).getInputStream()) {
            storageKey = fileStorageService.store(document.getProjectId(), target.getOriginalName(), input);
        } catch (IOException ex) {
            throw new BusinessException(50001, "版本文件读取失败");
        }
        int next = current + 1;
        String rollbackNote = StringUtils.hasText(note) ? note : "回滚至 v" + versionNo;
        archiveCurrentVersions(documentId);
        DocumentVersion version = insertVersion(document, next, storageKey, target.getName(),
                target.getOriginalName(), target.getSizeBytes(), target.getContentType(),
                target.getContentText(), user.employeeId(), rollbackNote, "CURRENT");

        document.setName(target.getName());
        document.setOriginalName(target.getOriginalName());
        document.setStorageKey(storageKey);
        document.setSizeBytes(target.getSizeBytes());
        document.setContentType(target.getContentType());
        document.setCurrentVersion(next);
        document.setContentText(target.getContentText());
        document.setContentIndexedAt(target.getContentText() == null ? null : LocalDateTime.now());
        updateDocumentContent(document);
        pruneVersions(documentId, storageKey);
        auditService.record("DOCUMENT", "document", "VERSION_ROLLBACK", "DOCUMENT", documentId,
                Map.of("version", current), Map.of("version", next, "from", versionNo),
                rollbackNote, null);
        return toVersionView(version, employeeMapper.selectById(user.employeeId()));
    }

    public Download downloadVersion(Long documentId, int versionNo, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        Project project = projectService.requireProject(document.getProjectId());
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canView(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档");
        }
        if (!canDownload(document, project, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权下载该文档");
        }
        DocumentVersion version = versionMapper.selectOne(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId)
                .eq(DocumentVersion::getVersionNo, versionNo)
                .last("LIMIT 1"));
        if (version == null) {
            throw new BusinessException(40401, "版本不存在");
        }
        Resource resource = fileStorageService.load(version.getStorageKey());
        auditService.access("DOCUMENT", documentId, "VERSION_DOWNLOAD", false);
        String filename = StringUtils.hasText(version.getOriginalName())
                ? version.getOriginalName() : version.getName();
        return new Download(filename, resource, version.getContentType());
    }

    /**
     * Returns the current document text only after the same project-membership and
     * per-document ACL checks used by download. AI features must never bypass them.
     */
    public AiDocumentContext aiContext(Long documentId, LoginUser user) {
        Document document = requireDocument(documentId);
        ProjectMember member = projectService.requireMember(document.getProjectId(), user);
        DocumentPermission override = override(documentId, user.employeeId());
        if (!canView(document, member, override, user.employeeId())) {
            throw new BusinessException(40301, "无权查看该文档");
        }
        Project project = projectMapper.selectById(document.getProjectId());
        auditService.access("DOCUMENT", documentId, "AI_READ", false);
        String name = StringUtils.hasText(document.getName())
                ? document.getName() : document.getOriginalName();
        return new AiDocumentContext(document.getId(), name, document.getProjectId(),
                project == null ? null : project.getName(),
                document.getCurrentVersion() == null ? 1 : document.getCurrentVersion(),
                document.getContentText(), document.getUpdatedAt());
    }

    public List<ProjectDtos.DocumentSearchResult> search(String keyword, Long projectId,
                                                         Integer limit, LoginUser user) {
        if (!configService.getBoolean("doc.search.enabled", true)) {
            throw new BusinessException(40001, "文档搜索未启用");
        }
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(40001, "请输入搜索关键词");
        }
        if (user.employeeId() == null) {
            return Collections.emptyList();
        }
        List<ProjectMember> memberships = projectMemberMapper.selectList(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getEmployeeId, user.employeeId()));
        Map<Long, ProjectMember> membersByProject = memberships.stream()
                .collect(Collectors.toMap(ProjectMember::getProjectId, Function.identity(), (a, b) -> a));
        Set<Long> projectIds = membersByProject.keySet();
        if (projectId != null) {
            if (!projectIds.contains(projectId)) {
                throw new BusinessException(40301, "您不是该项目成员");
            }
            projectIds = Set.of(projectId);
        }
        if (projectIds.isEmpty()) {
            return Collections.emptyList();
        }
        int maxResults = limit == null ? configService.getInt("doc.search.maxResults", 50)
                : Math.max(1, Math.min(limit, 200));
        String term = keyword.trim();
        List<Document> matches = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .in(Document::getProjectId, projectIds)
                .eq(Document::getStatus, "ACTIVE")
                .and(wrapper -> wrapper.like(Document::getName, term)
                        .or().like(Document::getOriginalName, term)
                        .or().like(Document::getContentText, term))
                .orderByDesc(Document::getUpdatedAt)
                .last("LIMIT " + Math.min(maxResults * 3, 500)));
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Project> projects = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<Long, DocumentPermission> overrides = overrides(matches, user.employeeId());
        Map<Long, Employee> uploaders = employees(matches.stream().map(Document::getUploaderId).toList());
        String lower = term.toLowerCase(Locale.ROOT);
        List<ProjectDtos.DocumentSearchResult> results = new ArrayList<>();
        for (Document document : matches) {
            ProjectMember member = membersByProject.get(document.getProjectId());
            DocumentPermission override = overrides.get(document.getId());
            if (member == null || !canView(document, member, override, user.employeeId())) {
                continue;
            }
            Project project = projects.get(document.getProjectId());
            Employee uploader = uploaders.get(document.getUploaderId());
            results.add(new ProjectDtos.DocumentSearchResult(document.getId(), document.getProjectId(),
                    project == null ? null : project.getName(), document.getFolderId(),
                    document.getName(), document.getOriginalName(), document.getContentType(),
                    document.getSizeBytes(),
                    document.getCurrentVersion() == null ? 1 : document.getCurrentVersion(),
                    document.getUploaderId(), uploader == null ? null : uploader.getName(),
                    document.getUpdatedAt(), canDownload(document, project, member, override,
                            user.employeeId()), snippet(document.getContentText(), lower)));
        }
        results.sort(Comparator
                .comparingInt((ProjectDtos.DocumentSearchResult result) -> relevance(result, lower))
                .reversed()
                .thenComparing(ProjectDtos.DocumentSearchResult::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return results.size() > maxResults ? results.subList(0, maxResults) : results;
    }

    private int relevance(ProjectDtos.DocumentSearchResult result, String lowerKeyword) {
        if (result.name() != null && result.name().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            return 3;
        }
        if (result.originalName() != null
                && result.originalName().toLowerCase(Locale.ROOT).contains(lowerKeyword)) {
            return 2;
        }
        return result.snippet() == null ? 0 : 1;
    }

    private String snippet(String content, String lowerKeyword) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(lowerKeyword);
        if (index < 0) {
            return null;
        }
        int radius = Math.max(40, configService.getInt("doc.search.snippetLength", 120));
        int start = Math.max(0, index - radius / 2);
        int end = Math.min(content.length(), index + lowerKeyword.length() + radius / 2);
        String piece = content.substring(start, end).replaceAll("\\s+", " ").trim();
        return (start > 0 ? "…" : "") + piece + (end < content.length() ? "…" : "");
    }

    private void requireVersionEnabled() {
        if (!configService.getBoolean("doc.version.enabled", true)) {
            throw new BusinessException(40001, "文档版本功能未启用");
        }
    }

    private boolean hasDocRole(String config, ProjectMember member, Document document, Long employeeId) {
        if (!StringUtils.hasText(config) || member == null) {
            return false;
        }
        for (String raw : config.split(",")) {
            String token = raw.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if ("UPLOADER".equals(token)) {
                if (Objects.equals(document.getUploaderId(), employeeId)) {
                    return true;
                }
            } else if (token.equals(member.getMemberRole())) {
                return true;
            }
        }
        return false;
    }

    private void archiveCurrentVersions(Long documentId) {
        versionMapper.update(null, new LambdaUpdateWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId)
                .set(DocumentVersion::getStatus, "ARCHIVED"));
    }

    private DocumentVersion insertVersion(Document document, int versionNo, String storageKey,
                                          String name, String originalName, long sizeBytes,
                                          String contentType, String contentText, Long uploaderId,
                                          String note, String status) {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNo(versionNo);
        version.setStorageKey(storageKey);
        version.setName(name);
        version.setOriginalName(originalName);
        version.setSizeBytes(sizeBytes);
        version.setContentType(contentType);
        version.setContentText(contentText);
        version.setUploaderId(uploaderId);
        version.setNote(note);
        version.setStatus(status);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);
        return version;
    }

    private void updateDocumentContent(Document document) {
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, document.getId())
                .set(Document::getName, document.getName())
                .set(Document::getOriginalName, document.getOriginalName())
                .set(Document::getStorageKey, document.getStorageKey())
                .set(Document::getSizeBytes, document.getSizeBytes())
                .set(Document::getContentType, document.getContentType())
                .set(Document::getCurrentVersion, document.getCurrentVersion())
                .set(Document::getContentText, document.getContentText())
                .set(Document::getContentIndexedAt, document.getContentIndexedAt())
                .set(Document::getUpdatedAt, LocalDateTime.now()));
    }

    private void pruneVersions(Long documentId, String currentStorageKey) {
        int maxKept = configService.getInt("doc.version.maxKept", 0);
        if (maxKept <= 0) {
            return;
        }
        List<DocumentVersion> rows = versionMapper.selectList(new LambdaQueryWrapper<DocumentVersion>()
                .eq(DocumentVersion::getDocumentId, documentId)
                .orderByAsc(DocumentVersion::getVersionNo));
        int toDelete = rows.size() - maxKept;
        for (DocumentVersion row : rows) {
            if (toDelete <= 0) {
                return;
            }
            if ("CURRENT".equals(row.getStatus())
                    || Objects.equals(row.getStorageKey(), currentStorageKey)) {
                continue;
            }
            versionMapper.deleteById(row.getId());
            fileStorageService.delete(row.getStorageKey());
            toDelete--;
        }
    }

    private ProjectDtos.DocumentVersionView toVersionView(DocumentVersion version, Employee uploader) {
        return new ProjectDtos.DocumentVersionView(version.getId(), version.getDocumentId(),
                version.getVersionNo() == null ? 1 : version.getVersionNo(), version.getName(),
                version.getOriginalName(), version.getSizeBytes() == null ? 0 : version.getSizeBytes(),
                version.getContentType(), version.getUploaderId(),
                uploader == null ? null : uploader.getName(), version.getNote(), version.getStatus(),
                version.getCreatedAt());
    }

    private boolean canView(Document document, ProjectMember member, DocumentPermission override,
                            Long employeeId) {
        if (isManagerOrUploader(document, member, employeeId)) {
            return true;
        }
        return override == null || Boolean.TRUE.equals(override.getCanView());
    }

    private boolean canDownload(Document document, Project project, ProjectMember member,
                                DocumentPermission override, Long employeeId) {
        if (isManagerOrUploader(document, member, employeeId)) {
            return true;
        }
        if (override != null) {
            return Boolean.TRUE.equals(override.getCanDownload());
        }
        return project != null && "ALL".equals(project.getDownloadPolicy());
    }

    private boolean canPrint(Document document, Project project, ProjectMember member,
                             DocumentPermission override, Long employeeId) {
        if (!configService.getBoolean("doc.print.enabled", true)) {
            return false;
        }
        if (isManagerOrUploader(document, member, employeeId)) {
            return true;
        }
        if (override != null) {
            return Boolean.TRUE.equals(override.getCanPrint());
        }
        return "ALL".equalsIgnoreCase(configService.getString("doc.print.default", "ROLE_BASED"));
    }

    private boolean canDelete(Document document, ProjectMember member, DocumentPermission override,
                              Long employeeId) {
        if (isManagerOrUploader(document, member, employeeId)) {
            return true;
        }
        return override != null && Boolean.TRUE.equals(override.getCanDelete());
    }

    private boolean isManagerOrUploader(Document document, ProjectMember member, Long employeeId) {
        return projectService.isManager(member) || Objects.equals(document.getUploaderId(), employeeId);
    }

    private Map<Long, DocumentPermission> overrides(List<Document> documents, Long employeeId) {
        if (documents.isEmpty() || employeeId == null) {
            return Collections.emptyMap();
        }
        List<Long> ids = documents.stream().map(Document::getId).toList();
        return permissionMapper.selectList(new LambdaQueryWrapper<DocumentPermission>()
                        .in(DocumentPermission::getDocumentId, ids)
                        .eq(DocumentPermission::getEmployeeId, employeeId))
                .stream().collect(Collectors.toMap(DocumentPermission::getDocumentId, Function.identity()));
    }

    private DocumentPermission override(Long documentId, Long employeeId) {
        if (employeeId == null) {
            return null;
        }
        return permissionMapper.selectOne(new LambdaQueryWrapper<DocumentPermission>()
                .eq(DocumentPermission::getDocumentId, documentId)
                .eq(DocumentPermission::getEmployeeId, employeeId)
                .last("LIMIT 1"));
    }

    private Document requireDocument(Long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(40401, "文档不存在");
        }
        return document;
    }

    private Map<Long, Employee> employees(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return employeeMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    public record Download(String filename, Resource resource, String contentType) {
    }

    public record AiDocumentContext(Long id,
                                    String name,
                                    Long projectId,
                                    String projectName,
                                    int versionNo,
                                    String content,
                                    LocalDateTime updatedAt) {
    }
}
