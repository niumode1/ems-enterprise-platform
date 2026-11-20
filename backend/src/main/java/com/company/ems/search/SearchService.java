package com.company.ems.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.policy.PolicyDtos;
import com.company.ems.policy.PolicyService;
import com.company.ems.project.DocumentService;
import com.company.ems.project.ProjectDtos;
import com.company.ems.project.entity.Project;
import com.company.ems.project.entity.ProjectMember;
import com.company.ems.project.entity.Task;
import com.company.ems.project.mapper.ProjectMapper;
import com.company.ems.project.mapper.ProjectMemberMapper;
import com.company.ems.project.mapper.TaskMapper;
import com.company.ems.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Permission-aware global search. Results never widen visibility: employees
 * need employee:read, projects/tasks need membership plus the read permission,
 * documents reuse the existing project membership + per-document ACL search.
 */
@Service
public class SearchService {

    private static final List<String> ALL_TYPES = List.of(
            "EMPLOYEE", "PROJECT", "TASK", "DOCUMENT", "POLICY");
    private static final int MAX_FETCH = 200;

    private final ConfigService configService;
    private final EmployeeMapper employeeMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final TaskMapper taskMapper;
    private final DocumentService documentService;
    private final PolicyService policyService;

    public SearchService(ConfigService configService,
                         EmployeeMapper employeeMapper,
                         ProjectMapper projectMapper,
                         ProjectMemberMapper projectMemberMapper,
                         TaskMapper taskMapper,
                         DocumentService documentService,
                         PolicyService policyService) {
        this.configService = configService;
        this.employeeMapper = employeeMapper;
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.taskMapper = taskMapper;
        this.documentService = documentService;
        this.policyService = policyService;
    }

    public SearchDtos.Response search(String keyword, String typesParam,
                                      Long pageParam, Long sizeParam, LoginUser user) {
        if (!configService.getBoolean("search.enabled", true)) {
            throw new BusinessException(40001, "全局搜索未启用");
        }
        String term = keyword == null ? "" : keyword.trim();
        int minLength = Math.max(1, configService.getInt("search.minKeywordLength", 1));
        if (term.length() < minLength) {
            throw new BusinessException(40001, "搜索关键词至少 " + minLength + " 个字符");
        }
        long page = pageParam == null ? 1 : Math.max(1, pageParam);
        int maxPageSize = Math.max(1, configService.getInt("search.maxPageSize", 50));
        long size = sizeParam == null ? configService.getInt("search.maxResults", 20)
                : Math.max(1, Math.min(sizeParam, maxPageSize));
        int snippetLength = Math.max(20, configService.getInt("search.snippetLength", 120));

        List<String> requested = parseTypes(typesParam);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String type : ALL_TYPES) {
            counts.put(type, 0);
        }
        List<SearchDtos.Item> items = new ArrayList<>();
        List<Ranked> ranked = new ArrayList<>();
        if (user.employeeId() == null) {
            return response(term, page, size, requested, counts, List.of());
        }
        int fetchLimit = (int) Math.min(Math.max(page * size, size), MAX_FETCH);

        if (requested.contains("EMPLOYEE") && enabled("search.employee.enabled")
                && user.hasPermission("employee:read")) {
            searchEmployees(term, fetchLimit, snippetLength, ranked, counts);
        }
        List<Long> projectIds = memberProjectIds(user);
        if (requested.contains("PROJECT") && enabled("search.project.enabled")
                && user.hasPermission("project:read")) {
            searchProjects(term, projectIds, fetchLimit, snippetLength, ranked, counts);
        }
        if (requested.contains("TASK") && enabled("search.task.enabled")
                && user.hasPermission("task:read")) {
            searchTasks(term, projectIds, fetchLimit, snippetLength, ranked, counts);
        }
        if (requested.contains("DOCUMENT") && enabled("search.document.enabled")
                && configService.getBoolean("doc.search.enabled", true)
                && user.hasPermission("doc:search")) {
            searchDocuments(term, fetchLimit, snippetLength, ranked, counts, user);
        }
        if (requested.contains("POLICY") && enabled("search.policy.enabled")
                && user.hasPermission("policy:read")) {
            searchPolicies(term, fetchLimit, snippetLength, ranked, counts);
        }

        ranked.sort(Comparator
                .comparingInt(Ranked::rank).reversed()
                .thenComparing(Ranked::time,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        long offset = (page - 1) * size;
        for (int i = (int) offset; i < ranked.size() && items.size() < size; i++) {
            items.add(ranked.get(i).item());
        }
        long total = counts.values().stream().mapToLong(Integer::longValue).sum();
        return response(term, page, size, requested, counts, items);
    }

    private void searchEmployees(String term, int limit, int snippetLength,
                                 List<Ranked> ranked, Map<String, Integer> counts) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .and(w -> w.like(Employee::getName, term)
                        .or().like(Employee::getEmployeeNo, term));
        Long count = employeeMapper.selectCount(wrapper);
        counts.put("EMPLOYEE", n(count));
        if (n(count) == 0) {
            return;
        }
        List<Employee> employees = employeeMapper.selectList(wrapper
                .orderByAsc(Employee::getEmployeeNo)
                .last("LIMIT " + limit));
        for (Employee employee : employees) {
            String title = employee.getName();
            String subtitle = employee.getEmployeeNo();
            String snippet = null;
            ranked.add(new Ranked(new SearchDtos.Item("EMPLOYEE", employee.getId(),
                    title, subtitle, snippet, "/employees?focus=" + employee.getId(),
                    highlight(title, term), null),
                    rank(title, subtitle, snippet, term), employee.getUpdatedAt()));
        }
    }

    private void searchProjects(String term, List<Long> projectIds, int limit, int snippetLength,
                                List<Ranked> ranked, Map<String, Integer> counts) {
        if (projectIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .in(Project::getId, projectIds)
                .and(w -> w.like(Project::getName, term)
                        .or().like(Project::getCode, term)
                        .or().like(Project::getDescription, term));
        Long count = projectMapper.selectCount(wrapper);
        counts.put("PROJECT", n(count));
        if (n(count) == 0) {
            return;
        }
        List<Project> projects = projectMapper.selectList(wrapper
                .orderByDesc(Project::getUpdatedAt)
                .last("LIMIT " + limit));
        for (Project project : projects) {
            String title = project.getName();
            String subtitle = String.join(" · ", nonBlank(project.getCode(), project.getStatus()));
            String snippet = snippet(project.getDescription(), term, snippetLength);
            ranked.add(new Ranked(new SearchDtos.Item("PROJECT", project.getId(),
                    title, subtitle, snippet, "/projects/" + project.getId(),
                    highlight(title, term), highlight(snippet, term)),
                    rank(title, subtitle, snippet, term), project.getUpdatedAt()));
        }
    }

    private void searchTasks(String term, List<Long> projectIds, int limit, int snippetLength,
                             List<Ranked> ranked, Map<String, Integer> counts) {
        if (projectIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<Task>()
                .in(Task::getProjectId, projectIds)
                .and(w -> w.like(Task::getTitle, term)
                        .or().like(Task::getDescription, term));
        Long count = taskMapper.selectCount(wrapper);
        counts.put("TASK", n(count));
        if (n(count) == 0) {
            return;
        }
        List<Task> tasks = taskMapper.selectList(wrapper
                .orderByDesc(Task::getUpdatedAt)
                .last("LIMIT " + limit));
        Map<Long, Project> projects = projectMapper.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        for (Task task : tasks) {
            Project project = projects.get(task.getProjectId());
            String title = task.getTitle();
            String subtitle = String.join(" · ", nonBlank(
                    project == null ? null : project.getName(), task.getStatus()));
            String snippet = snippet(task.getDescription(), term, snippetLength);
            String link = "/projects/" + task.getProjectId()
                    + "?tab=tasks&taskId=" + task.getId();
            ranked.add(new Ranked(new SearchDtos.Item("TASK", task.getId(),
                    title, subtitle, snippet, link,
                    highlight(title, term), highlight(snippet, term)),
                    rank(title, subtitle, snippet, term), task.getUpdatedAt()));
        }
    }

    private void searchDocuments(String term, int limit, int snippetLength,
                                 List<Ranked> ranked, Map<String, Integer> counts, LoginUser user) {
        List<ProjectDtos.DocumentSearchResult> documents =
                documentService.search(term, null, limit, user);
        counts.put("DOCUMENT", documents.size());
        for (ProjectDtos.DocumentSearchResult document : documents) {
            String title = StringUtils.hasText(document.name())
                    ? document.name() : document.originalName();
            String subtitle = document.projectName();
            String snippet = snippet(document.snippet(), term, snippetLength);
            String link = "/projects/" + document.projectId()
                    + "?tab=documents&documentId=" + document.documentId();
            ranked.add(new Ranked(new SearchDtos.Item("DOCUMENT", document.documentId(),
                    title, subtitle, snippet, link,
                    highlight(title, term), highlight(snippet, term)),
                    rank(title, subtitle, snippet, term), document.updatedAt()));
        }
    }

    private void searchPolicies(String term, int limit, int snippetLength,
                                List<Ranked> ranked, Map<String, Integer> counts) {
        List<PolicyDtos.PolicySearchResult> policies = policyService.search(term, limit);
        counts.put("POLICY", policies.size());
        for (PolicyDtos.PolicySearchResult policy : policies) {
            String title = policy.title();
            String subtitle = policy.category();
            String snippet = snippet(policy.snippet(), term, snippetLength);
            String link = "/policies?focus=" + policy.id();
            ranked.add(new Ranked(new SearchDtos.Item("POLICY", policy.id(),
                    title, subtitle, snippet, link,
                    highlight(title, term), highlight(snippet, term)),
                    rank(title, subtitle, snippet, term), policy.updatedAt()));
        }
    }

    private List<Long> memberProjectIds(LoginUser user) {
        return projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getEmployeeId, user.employeeId()))
                .stream()
                .map(ProjectMember::getProjectId)
                .distinct()
                .toList();
    }

    private List<String> parseTypes(String typesParam) {
        if (!StringUtils.hasText(typesParam)) {
            return ALL_TYPES;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (String raw : typesParam.split(",")) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String type = raw.trim().toUpperCase(Locale.ROOT);
            if (!ALL_TYPES.contains(type)) {
                throw new BusinessException(40001, "不支持的搜索类型：" + raw);
            }
            selected.add(type);
        }
        return selected.isEmpty() ? ALL_TYPES : new ArrayList<>(selected);
    }

    private boolean enabled(String key) {
        return configService.getBoolean(key, true);
    }

    private SearchDtos.Response response(String keyword, long page, long size,
                                         List<String> types, Map<String, Integer> counts,
                                         List<SearchDtos.Item> items) {
        long total = counts.values().stream().mapToLong(Integer::longValue).sum();
        return new SearchDtos.Response(keyword, total, page, size, types, counts, items);
    }

    private int rank(String title, String subtitle, String snippet, String term) {
        int rank = 0;
        String lowerTerm = term.toLowerCase(Locale.ROOT);
        if (title != null) {
            String lower = title.toLowerCase(Locale.ROOT);
            if (lower.equals(lowerTerm)) {
                rank += 6;
            } else if (lower.startsWith(lowerTerm)) {
                rank += 4;
            } else if (lower.contains(lowerTerm)) {
                rank += 3;
            }
        }
        if (subtitle != null && subtitle.toLowerCase(Locale.ROOT).contains(lowerTerm)) {
            rank += 1;
        }
        if (snippet != null && snippet.toLowerCase(Locale.ROOT).contains(lowerTerm)) {
            rank += 1;
        }
        return rank;
    }

    private String snippet(String text, String term, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String singleLine = text.replaceAll("\\s+", " ").trim();
        int index = singleLine.toLowerCase(Locale.ROOT).indexOf(term.toLowerCase(Locale.ROOT));
        int start = index < 0 ? 0 : Math.max(0, index - maxLength / 3);
        int end = Math.min(singleLine.length(), start + maxLength);
        if (end - start < maxLength && start > 0) {
            start = Math.max(0, end - maxLength);
        }
        String body = singleLine.substring(start, end);
        return (start > 0 ? "…" : "") + body + (end < singleLine.length() ? "…" : "");
    }

    private String highlight(String text, String term) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile(Pattern.quote(term),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text);
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            result.append(HtmlUtils.htmlEscape(text.substring(cursor, matcher.start())));
            result.append("<mark>")
                    .append(HtmlUtils.htmlEscape(matcher.group()))
                    .append("</mark>");
            cursor = matcher.end();
        }
        result.append(HtmlUtils.htmlEscape(text.substring(cursor)));
        return result.toString();
    }

    private List<String> nonBlank(String... values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private int n(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private record Ranked(SearchDtos.Item item, int rank, LocalDateTime time) {
    }
}
