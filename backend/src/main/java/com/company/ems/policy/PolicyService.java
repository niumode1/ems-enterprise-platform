package com.company.ems.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.ai.AiDocumentDtos;
import com.company.ems.ai.AiDocumentService;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.policy.entity.CompanyPolicy;
import com.company.ems.policy.mapper.CompanyPolicyMapper;
import com.company.ems.project.DocumentTextExtractor;
import com.company.ems.project.FileStorageService;
import com.company.ems.security.LoginUser;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Company-wide policies/regulations. Every employee with policy:read can list,
 * preview and download them; policy:manage controls upload/delete. The AI path
 * reuses the permission-filtered text retrieval from the document Q&A so a
 * policy answer is always grounded in uploaded policy text.
 */
@Service
public class PolicyService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int SNIPPET_LENGTH = 120;

    private final CompanyPolicyMapper policyMapper;
    private final EmployeeMapper employeeMapper;
    private final FileStorageService fileStorageService;
    private final DocumentTextExtractor textExtractor;
    private final ConfigService configService;
    private final AuditService auditService;
    private final AiDocumentService aiDocumentService;

    public PolicyService(CompanyPolicyMapper policyMapper,
                         EmployeeMapper employeeMapper,
                         FileStorageService fileStorageService,
                         DocumentTextExtractor textExtractor,
                         ConfigService configService,
                         AuditService auditService,
                         AiDocumentService aiDocumentService) {
        this.policyMapper = policyMapper;
        this.employeeMapper = employeeMapper;
        this.fileStorageService = fileStorageService;
        this.textExtractor = textExtractor;
        this.configService = configService;
        this.auditService = auditService;
        this.aiDocumentService = aiDocumentService;
    }

    public List<PolicyDtos.PolicyView> list(String keyword) {
        LambdaQueryWrapper<CompanyPolicy> wrapper = new LambdaQueryWrapper<CompanyPolicy>()
                .eq(CompanyPolicy::getStatus, STATUS_ACTIVE);
        if (StringUtils.hasText(keyword)) {
            String term = keyword.trim();
            wrapper.and(w -> w.like(CompanyPolicy::getTitle, term)
                    .or().like(CompanyPolicy::getOriginalName, term)
                    .or().like(CompanyPolicy::getContentText, term));
        }
        wrapper.orderByDesc(CompanyPolicy::getUpdatedAt)
                .last("LIMIT 500");
        List<CompanyPolicy> rows = policyMapper.selectList(wrapper);
        Map<Long, Employee> uploaders = employees(rows.stream()
                .map(CompanyPolicy::getUploaderId).toList());
        return rows.stream()
                .map(row -> view(row, uploaders.get(row.getUploaderId())))
                .toList();
    }

    public PolicyDtos.PolicyDetail detail(Long id) {
        CompanyPolicy policy = requirePolicy(id);
        auditService.access("POLICY", id, "VIEW", false);
        Employee uploader = policy.getUploaderId() == null
                ? null : employeeMapper.selectById(policy.getUploaderId());
        return new PolicyDtos.PolicyDetail(policy.getId(), displayTitle(policy),
                policy.getCategory(), policy.getOriginalName(), policy.getContentType(),
                policy.getSizeBytes(), policy.getUploaderId(),
                uploader == null ? null : uploader.getName(),
                StringUtils.hasText(policy.getContentText()), policy.getContentText(),
                policy.getCreatedAt(), policy.getUpdatedAt());
    }

    public Download download(Long id) {
        CompanyPolicy policy = requirePolicy(id);
        auditService.access("POLICY", id, "DOWNLOAD", false);
        return new Download(StringUtils.hasText(policy.getOriginalName())
                ? policy.getOriginalName() : displayTitle(policy),
                fileStorageService.load(policy.getStorageKey()), policy.getContentType());
    }

    @Transactional
    public PolicyDtos.PolicyView upload(String title, String category,
                                        MultipartFile file, LoginUser user) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(40001, "请填写制度名称");
        }
        String normalizedTitle = title.trim();
        if (normalizedTitle.length() > 200) {
            throw new BusinessException(40001, "制度名称不能超过 200 个字符");
        }
        String normalizedCategory = StringUtils.hasText(category) ? category.trim() : null;
        if (normalizedCategory != null && normalizedCategory.length() > 64) {
            throw new BusinessException(40001, "分类不能超过 64 个字符");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "请选择要上传的制度文件");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : normalizedTitle;
        String extension = StringUtils.getFilenameExtension(originalName);
        Set<String> allowed = allowedExtensions();
        if (!allowed.isEmpty()
                && (extension == null || !allowed.contains(extension.toLowerCase(Locale.ROOT)))) {
            throw new BusinessException(40001, "不支持的文件类型，允许：" + String.join("、", allowed));
        }
        long maxBytes = (long) configService.getInt("policy.file.maxMb", 50) * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessException(40001, "文件超过大小限制（" + maxBytes / 1024 / 1024 + "MB）");
        }
        String storageKey;
        try {
            storageKey = fileStorageService.store("policy", originalName, file.getInputStream());
        } catch (IOException ex) {
            throw new BusinessException(50001, "文件存储失败");
        }
        String contentText = textExtractor.extract(file);
        CompanyPolicy policy = new CompanyPolicy();
        policy.setTitle(normalizedTitle);
        policy.setCategory(normalizedCategory);
        policy.setOriginalName(originalName);
        policy.setStorageKey(storageKey);
        policy.setSizeBytes(file.getSize());
        policy.setContentType(file.getContentType());
        policy.setUploaderId(user.employeeId());
        policy.setStatus(STATUS_ACTIVE);
        policy.setContentText(contentText);
        policy.setContentIndexedAt(contentText == null ? null : LocalDateTime.now());
        try {
            policyMapper.insert(policy);
        } catch (RuntimeException ex) {
            fileStorageService.delete(storageKey);
            throw ex;
        }
        Employee uploader = user.employeeId() == null
                ? null : employeeMapper.selectById(user.employeeId());
        PolicyDtos.PolicyView view = view(policy, uploader);
        auditService.record("POLICY", "policy", "UPLOAD", "POLICY", policy.getId(),
                null, view, null, null);
        return view;
    }

    @Transactional
    public void delete(Long id) {
        CompanyPolicy policy = requirePolicy(id);
        Employee uploader = policy.getUploaderId() == null
                ? null : employeeMapper.selectById(policy.getUploaderId());
        PolicyDtos.PolicyView before = view(policy, uploader);
        policyMapper.deleteById(id);
        fileStorageService.delete(policy.getStorageKey());
        auditService.record("POLICY", "policy", "DELETE", "POLICY", id,
                before, null, null, null);
    }

    public List<PolicyDtos.PolicySearchResult> search(String keyword, Integer limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        int max = limit == null ? 20 : Math.max(1, Math.min(limit, 200));
        String term = keyword.trim();
        List<CompanyPolicy> rows = policyMapper.selectList(new LambdaQueryWrapper<CompanyPolicy>()
                .eq(CompanyPolicy::getStatus, STATUS_ACTIVE)
                .and(w -> w.like(CompanyPolicy::getTitle, term)
                        .or().like(CompanyPolicy::getOriginalName, term)
                        .or().like(CompanyPolicy::getContentText, term))
                .orderByDesc(CompanyPolicy::getUpdatedAt)
                .last("LIMIT " + max));
        return rows.stream()
                .map(row -> new PolicyDtos.PolicySearchResult(row.getId(), displayTitle(row),
                        row.getCategory(), snippet(row.getContentText(), term, SNIPPET_LENGTH),
                        row.getUpdatedAt()))
                .toList();
    }

    public AiDocumentDtos.PolicyAnswer ask(PolicyDtos.PolicyAskRequest request, LoginUser user) {
        if (!configService.getBoolean("policy.ai.enabled", true)) {
            throw new BusinessException(40001, "公司制度问答未启用");
        }
        List<CompanyPolicy> rows;
        if (request.getPolicyId() != null) {
            rows = List.of(requirePolicy(request.getPolicyId()));
        } else {
            int maxDocs = Math.max(1, configService.getInt("policy.ai.maxDocs", 50));
            rows = policyMapper.selectList(new LambdaQueryWrapper<CompanyPolicy>()
                    .eq(CompanyPolicy::getStatus, STATUS_ACTIVE)
                    .isNotNull(CompanyPolicy::getContentText)
                    .ne(CompanyPolicy::getContentText, "")
                    .orderByDesc(CompanyPolicy::getUpdatedAt)
                    .last("LIMIT " + maxDocs));
        }
        int maxContext = Math.max(1000, configService.getInt("policy.ai.maxContextChars", 30000));
        int used = 0;
        List<AiDocumentDtos.PolicySource> sources = new ArrayList<>();
        for (CompanyPolicy row : rows) {
            if (!StringUtils.hasText(row.getContentText())) {
                continue;
            }
            int remaining = maxContext - used;
            if (remaining <= 0) {
                break;
            }
            String content = row.getContentText();
            if (content.length() > remaining) {
                content = content.substring(0, remaining);
            }
            sources.add(new AiDocumentDtos.PolicySource(row.getId(), displayTitle(row), content));
            used += content.length();
            if (used >= maxContext) {
                break;
            }
        }
        if (sources.isEmpty()) {
            throw new BusinessException(40001,
                    "暂无可检索的制度正文，请上传 txt、docx 或 xlsx 等可解析文件");
        }
        AiDocumentDtos.PolicyAnswer answer = aiDocumentService.askPolicies(
                sources, request.getQuestion().trim(), user);
        Set<Long> cited = answer.citations().stream()
                .map(AiDocumentDtos.PolicyCitation::policyId)
                .collect(Collectors.toSet());
        for (Long policyId : cited) {
            auditService.access("POLICY", policyId, "AI_READ", false);
        }
        return answer;
    }

    public PolicyDtos.PolicyCapabilities capabilities(LoginUser user) {
        boolean aiEnabled = configService.getBoolean("policy.ai.enabled", true);
        return new PolicyDtos.PolicyCapabilities(
                configService.getInt("policy.file.maxMb", 50),
                new ArrayList<>(allowedExtensions()),
                aiEnabled,
                configService.getBoolean("search.policy.enabled", true),
                user.hasPermission("policy:manage"),
                aiEnabled && user.hasPermission("ai:use"));
    }

    private CompanyPolicy requirePolicy(Long id) {
        CompanyPolicy policy = id == null ? null : policyMapper.selectById(id);
        if (policy == null || !STATUS_ACTIVE.equals(policy.getStatus())) {
            throw new BusinessException(40401, "制度不存在");
        }
        return policy;
    }

    private PolicyDtos.PolicyView view(CompanyPolicy policy, Employee uploader) {
        return new PolicyDtos.PolicyView(policy.getId(), displayTitle(policy),
                policy.getCategory(), policy.getOriginalName(), policy.getContentType(),
                policy.getSizeBytes(), policy.getUploaderId(),
                uploader == null ? null : uploader.getName(),
                StringUtils.hasText(policy.getContentText()),
                policy.getCreatedAt(), policy.getUpdatedAt());
    }

    private String displayTitle(CompanyPolicy policy) {
        return StringUtils.hasText(policy.getTitle())
                ? policy.getTitle() : policy.getOriginalName();
    }

    private Map<Long, Employee> employees(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return employeeMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    private Set<String> allowedExtensions() {
        return Arrays.stream(configService.getString("policy.file.extensions",
                                "pdf,doc,docx,xls,xlsx,txt,md").split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private String snippet(String text, String term, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String singleLine = text.replaceAll("\\s+", " ").trim();
        int index = singleLine.toLowerCase(Locale.ROOT)
                .indexOf(term.toLowerCase(Locale.ROOT));
        int start = index < 0 ? 0 : Math.max(0, index - maxLength / 3);
        int end = Math.min(singleLine.length(), start + maxLength);
        if (end - start < maxLength && start > 0) {
            start = Math.max(0, end - maxLength);
        }
        String body = singleLine.substring(start, end);
        return (start > 0 ? "…" : "") + body + (end < singleLine.length() ? "…" : "");
    }

    public record Download(String filename, Resource resource, String contentType) {
    }
}
