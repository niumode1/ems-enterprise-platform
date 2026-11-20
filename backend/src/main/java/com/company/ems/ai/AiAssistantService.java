package com.company.ems.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.ai.entity.AiHelpIndex;
import com.company.ems.ai.entity.AiProviderConfig;
import com.company.ems.ai.entity.AiQueryLog;
import com.company.ems.ai.entity.AiQuickQuestion;
import com.company.ems.ai.mapper.AiHelpIndexMapper;
import com.company.ems.ai.mapper.AiQueryLogMapper;
import com.company.ems.ai.mapper.AiQuickQuestionMapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.entity.OrgUnit;
import com.company.ems.employee.entity.Position;
import com.company.ems.employee.entity.ReportingLine;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.employee.mapper.OrgUnitMapper;
import com.company.ems.employee.mapper.PositionMapper;
import com.company.ems.employee.mapper.ReportingLineMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.FieldPermissionService;
import com.company.ems.system.FieldPermissionService.FieldAccess;
import com.company.ems.system.FieldPermissionService.FieldPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic-first AI assistant. Operation guidance and people answers are computed
 * from local data and field permissions; the configured model only rewrites the final
 * short answer from the already filtered context. No record outside the caller's
 * field permissions ever reaches the model.
 */
@Service
public class AiAssistantService {

    private static final String ENTITY_TYPE = "EMPLOYEE";
    private static final int MAX_ANSWER_CHARS = 200;
    private static final int MAX_LOCATIONS = 3;
    private static final int MAX_ORG_NAMES = 12;

    private static final String PEOPLE_SYSTEM_PROMPT = """
            你是企业人事问答助手。只根据“资料”回答，最多 80 个汉字，只给结论。
            不得推测、不得补充资料中没有的信息；资料不足时回答“未找到相关信息”。
            不要输出 Markdown、表格或额外建议。
            """;

    private static final Map<String, List<String>> FIELD_KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, String> FIELD_LABELS = new LinkedHashMap<>();

    static {
        FIELD_KEYWORDS.put("phone", List.of("电话", "手机", "联系方式", "号码"));
        FIELD_KEYWORDS.put("email", List.of("邮箱", "邮件", "email"));
        FIELD_KEYWORDS.put("address", List.of("地址", "住址", "住在"));
        FIELD_KEYWORDS.put("idCard", List.of("身份证", "证件号"));
        FIELD_KEYWORDS.put("emergencyContact", List.of("紧急联系人", "联系人"));
        FIELD_KEYWORDS.put("birthDate", List.of("生日", "出生日期", "出生"));
        FIELD_KEYWORDS.put("gender", List.of("性别"));
        FIELD_KEYWORDS.put("hireDate", List.of("入职日期", "入职时间", "什么时候入职"));
        FIELD_KEYWORDS.put("employeeNo", List.of("工号", "员工号"));
        FIELD_KEYWORDS.put("org", List.of("部门", "所属部门"));
        FIELD_KEYWORDS.put("position", List.of("岗位", "职位", "职务"));
        FIELD_KEYWORDS.put("manager", List.of("上级", "领导", "汇报对象"));
        FIELD_KEYWORDS.put("name", List.of("姓名", "名字"));

        FIELD_LABELS.put("phone", "电话");
        FIELD_LABELS.put("email", "邮箱");
        FIELD_LABELS.put("address", "地址");
        FIELD_LABELS.put("idCard", "身份证");
        FIELD_LABELS.put("emergencyContact", "紧急联系人");
        FIELD_LABELS.put("birthDate", "出生日期");
        FIELD_LABELS.put("gender", "性别");
        FIELD_LABELS.put("hireDate", "入职日期");
        FIELD_LABELS.put("employeeNo", "工号");
        FIELD_LABELS.put("org", "部门");
        FIELD_LABELS.put("position", "岗位");
        FIELD_LABELS.put("manager", "直属上级");
        FIELD_LABELS.put("name", "姓名");
    }

    private final AiHelpIndexMapper helpIndexMapper;
    private final AiQuickQuestionMapper quickQuestionMapper;
    private final AiQueryLogMapper queryLogMapper;
    private final AiProviderService providerService;
    private final EmployeeMapper employeeMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final ReportingLineMapper reportingLineMapper;
    private final FieldPermissionService fieldPermissionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AiAssistantService(AiHelpIndexMapper helpIndexMapper,
                              AiQuickQuestionMapper quickQuestionMapper,
                              AiQueryLogMapper queryLogMapper,
                              AiProviderService providerService,
                              EmployeeMapper employeeMapper,
                              OrgUnitMapper orgUnitMapper,
                              PositionMapper positionMapper,
                              ReportingLineMapper reportingLineMapper,
                              FieldPermissionService fieldPermissionService,
                              AuditService auditService,
                              ObjectMapper objectMapper) {
        this.helpIndexMapper = helpIndexMapper;
        this.quickQuestionMapper = quickQuestionMapper;
        this.queryLogMapper = queryLogMapper;
        this.providerService = providerService;
        this.employeeMapper = employeeMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.positionMapper = positionMapper;
        this.reportingLineMapper = reportingLineMapper;
        this.fieldPermissionService = fieldPermissionService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- help locate

    public List<AiAssistantDtos.HelpHit> locate(String question, LoginUser user) {
        String q = normalize(question);
        List<AiHelpIndex> entries = helpIndexMapper.selectList(new LambdaQueryWrapper<AiHelpIndex>()
                .eq(AiHelpIndex::getEnabled, true)
                .orderByAsc(AiHelpIndex::getSort));
        List<ScoredHelp> scored = new ArrayList<>();
        for (AiHelpIndex entry : entries) {
            if (StringUtils.hasText(entry.getRequiredPermission())
                    && !user.hasPermission(entry.getRequiredPermission())) {
                continue;
            }
            List<String> matches = matchedKeywords(entry, q);
            int score = matches.size() * 10;
            if (q.contains(normalize(entry.getTitle()))) {
                score += 15;
            }
            scored.add(new ScoredHelp(entry, score, matches));
        }
        scored.sort(Comparator.comparingInt(ScoredHelp::score).reversed()
                .thenComparingInt(item -> item.entry().getSort() == null ? 0 : item.entry().getSort()));
        List<AiAssistantDtos.HelpHit> hits = new ArrayList<>();
        for (ScoredHelp item : scored) {
            if (hits.size() >= MAX_LOCATIONS) {
                break;
            }
            String reason = item.matches().isEmpty()
                    ? "常用入口"
                    : "匹配：" + String.join("、", item.matches());
            hits.add(new AiAssistantDtos.HelpHit(
                    item.entry().getTitle(),
                    item.entry().getRoute(),
                    item.entry().getDescription(),
                    reason));
        }
        logDeterministic(user, "HELP_LOCATE", question,
                hits.stream().map(AiAssistantDtos.HelpHit::route).toList().toString());
        return hits;
    }

    // ---------------------------------------------------------------- people Q&A

    public AiAssistantDtos.PeopleAnswer askPeople(AiAssistantDtos.AskRequest request, LoginUser user) {
        String question = request.getQuestion().trim();
        Employee employee = resolveEmployee(question, request.getEmployeeId(), user);
        if (employee != null) {
            return answerEmployee(employee, question, user);
        }
        OrgUnit org = resolveOrg(question);
        if (org != null) {
            return answerOrg(org, question, user);
        }
        if (asksForCount(question)) {
            long active = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getStatus, "ACTIVE"));
            String answer = "公司当前共有 " + active + " 名在职员工。";
            logDeterministic(user, "PEOPLE_QA", question, answer);
            return new AiAssistantDtos.PeopleAnswer(answer, List.of(), List.of(), false);
        }
        String fallback = "请说明要查询的员工姓名、工号或部门，例如“李四的电话是多少”。";
        logDeterministic(user, "PEOPLE_QA", question, fallback);
        return new AiAssistantDtos.PeopleAnswer(fallback, List.of(), List.of(), false);
    }

    public AiAssistantDtos.AskResult ask(AiAssistantDtos.AskRequest request, LoginUser user) {
        String question = request.getQuestion().trim();
        boolean peopleIntent = hasPeopleIntent(question);
        if (peopleIntent && user.hasPermission("ai:people")) {
            AiAssistantDtos.PeopleAnswer answer = askPeople(request, user);
            return new AiAssistantDtos.AskResult("PEOPLE", answer.answer(), List.of(),
                    answer.citations(), answer.maskedFields(), answer.aiUsed());
        }
        if (user.hasPermission("ai:help")) {
            List<AiAssistantDtos.HelpHit> hits = locate(question, user);
            String answer = hits.isEmpty()
                    ? "没有找到匹配的功能入口，可以换一个说法或联系管理员。"
                    : "你可能需要：" + hits.get(0).title() + "。";
            return new AiAssistantDtos.AskResult("HELP", answer, hits, List.of(), List.of(), false);
        }
        if (user.hasPermission("ai:people")) {
            AiAssistantDtos.PeopleAnswer answer = askPeople(request, user);
            return new AiAssistantDtos.AskResult("PEOPLE", answer.answer(), List.of(),
                    answer.citations(), answer.maskedFields(), answer.aiUsed());
        }
        throw new BusinessException(40301, "无可用 AI 助手权限");
    }

    private AiAssistantDtos.PeopleAnswer answerEmployee(Employee employee, String question,
                                                        LoginUser user) {
        FieldPolicy policy = fieldPermissionService.resolve(ENTITY_TYPE, user, employee.getId());
        Set<String> requested = requestedFields(question);
        List<String> maskedFields = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        List<String> denied = new ArrayList<>();

        String name = displayValue(employee, "name", policy, maskedFields);
        String subject = name == null ? "该员工" : name;

        Set<String> fields = requested.isEmpty()
                ? new LinkedHashSetBuilder("org", "position", "phone").build()
                : requested;
        for (String field : fields) {
            FieldAccess access = policy.access(field);
            if (!access.visible() || !access.aiReadable()) {
                denied.add(FIELD_LABELS.getOrDefault(field, field));
                continue;
            }
            String value = displayValue(employee, field, policy, maskedFields);
            if (value == null || value.isBlank()) {
                parts.add(FIELD_LABELS.getOrDefault(field, field) + "未填写");
            } else {
                parts.add(FIELD_LABELS.getOrDefault(field, field) + "是" + value);
            }
        }

        String fallback;
        if (parts.isEmpty()) {
            fallback = denied.isEmpty()
                    ? "没有找到可回答的信息。"
                    : "无权通过 AI 查看" + String.join("、", denied) + "。";
        } else {
            fallback = subject + "：" + String.join("，", parts) + "。";
            if (!denied.isEmpty()) {
                fallback += "（无权查看" + String.join("、", denied) + "）";
            }
        }

        String context = "员工：" + subject + "\n" + String.join("\n", parts)
                + (denied.isEmpty() ? "" : "\n无权查看：" + String.join("、", denied));
        AiText result = enhance(context, question, policy.canView("name"), fallback, user);
        String answer = limit(result.text());
        AiAssistantDtos.PersonCitation citation = new AiAssistantDtos.PersonCitation(
                employee.getId(),
                name == null ? "员工" : name,
                employee.getEmployeeNo());
        if (name == null) {
            citation = new AiAssistantDtos.PersonCitation(employee.getId(), "员工",
                    policy.access("employeeNo").visible() ? employee.getEmployeeNo() : null);
        }
        return new AiAssistantDtos.PeopleAnswer(answer, List.of(citation),
                maskedFields.stream().distinct().toList(), result.aiUsed());
    }

    private AiAssistantDtos.PeopleAnswer answerOrg(OrgUnit org, String question, LoginUser user) {
        List<Long> orgIds = new ArrayList<>();
        orgIds.add(org.getId());
        if (StringUtils.hasText(org.getPath())) {
            orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnit>()
                            .likeRight(OrgUnit::getPath, org.getPath()))
                    .forEach(child -> {
                        if (!orgIds.contains(child.getId())) {
                            orgIds.add(child.getId());
                        }
                    });
        }
        long total = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .in(Employee::getPrimaryOrgId, orgIds)
                .eq(Employee::getStatus, "ACTIVE"));
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getPrimaryOrgId, orgIds)
                .eq(Employee::getStatus, "ACTIVE")
                .orderByAsc(Employee::getEmployeeNo)
                .last("LIMIT 500"));
        List<String> names = new ArrayList<>();
        List<AiAssistantDtos.PersonCitation> citations = new ArrayList<>();
        for (Employee employee : employees) {
            if (names.size() >= MAX_ORG_NAMES) {
                break;
            }
            FieldPolicy policy = fieldPermissionService.resolve(ENTITY_TYPE, user, employee.getId());
            FieldAccess nameAccess = policy.access("name");
            if (!nameAccess.visible() || !nameAccess.aiReadable()) {
                continue;
            }
            String display = nameAccess.masked()
                    ? fieldPermissionService.mask(employee.getName(), nameAccess.maskRule())
                    : employee.getName();
            names.add(display);
            citations.add(new AiAssistantDtos.PersonCitation(
                    employee.getId(), display, employee.getEmployeeNo()));
        }
        String fallback = org.getName() + "共有 " + total + " 名在职员工";
        if (!names.isEmpty()) {
            fallback += "，包括：" + String.join("、", names);
            if (total > names.size()) {
                fallback += " 等";
            }
        }
        fallback += "。";
        String context = "部门：" + org.getName() + "\n在职人数：" + total
                + "\n可见姓名：" + (names.isEmpty() ? "无" : String.join("、", names));
        AiText result = enhance(context, question, true, fallback, user);
        return new AiAssistantDtos.PeopleAnswer(limit(result.text()), citations,
                List.of(), result.aiUsed());
    }

    private Employee resolveEmployee(String question, Long employeeId, LoginUser user) {
        if (employeeId != null) {
            Employee employee = employeeMapper.selectById(employeeId);
            if (employee == null) {
                throw new BusinessException(40401, "员工不存在");
            }
            return employee;
        }
        String q = normalize(question);
        if (user.employeeId() != null && q.contains("我的")) {
            Employee self = employeeMapper.selectById(user.employeeId());
            if (self != null) {
                return self;
            }
        }
        Employee byNo = null;
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStatus, "ACTIVE")
                .last("LIMIT 5000"));
        String bestName = null;
        for (Employee employee : employees) {
            if (employee.getEmployeeNo() != null
                    && q.contains(employee.getEmployeeNo().toLowerCase(Locale.ROOT))) {
                byNo = employee;
                break;
            }
            String name = normalize(employee.getName());
            if (name.length() >= 2 && q.contains(name)
                    && (bestName == null || name.length() > bestName.length())) {
                bestName = name;
                byNo = employee;
            }
        }
        return byNo;
    }

    private OrgUnit resolveOrg(String question) {
        String q = normalize(question);
        OrgUnit best = null;
        for (OrgUnit org : orgUnitMapper.selectList(new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getStatus, "ACTIVE"))) {
            String name = normalize(org.getName());
            if (name.length() >= 2 && q.contains(name)) {
                if (best == null || name.length() > normalize(best.getName()).length()) {
                    best = org;
                }
            }
        }
        return best;
    }

    private Set<String> requestedFields(String question) {
        String q = normalize(question);
        Set<String> fields = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : FIELD_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (q.contains(normalize(keyword))) {
                    fields.add(entry.getKey());
                    break;
                }
            }
        }
        return fields;
    }

    private boolean hasPeopleIntent(String question) {
        String q = normalize(question);
        if (!requestedFields(question).isEmpty()) {
            return true;
        }
        if (asksForCount(question) || q.contains("有哪些人") || q.contains("都有谁")) {
            return true;
        }
        if (resolveOrg(question) != null) {
            return true;
        }
        return userMentioned(question);
    }

    private boolean userMentioned(String question) {
        String q = normalize(question);
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getStatus, "ACTIVE")
                .last("LIMIT 5000"));
        for (Employee employee : employees) {
            String name = normalize(employee.getName());
            if (name.length() >= 2 && q.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean asksForCount(String question) {
        String q = normalize(question);
        return q.contains("多少人") || q.contains("几个人") || q.contains("人数")
                || q.contains("多少员工") || q.contains("多少名员工");
    }

    private String displayValue(Employee employee, String field, FieldPolicy policy,
                                List<String> maskedFields) {
        FieldAccess access = policy.access(field);
        if (!access.visible() || !access.aiReadable()) {
            return null;
        }
        String raw = switch (field) {
            case "name" -> employee.getName();
            case "employeeNo" -> employee.getEmployeeNo();
            case "gender" -> employee.getGender();
            case "birthDate" -> employee.getBirthDate() == null ? null : employee.getBirthDate().toString();
            case "phone" -> employee.getPhone();
            case "email" -> employee.getEmail();
            case "idCard" -> employee.getIdCardEnc();
            case "address" -> employee.getAddress();
            case "emergencyContact" -> employee.getEmergencyContact();
            case "hireDate" -> employee.getHireDate() == null ? null : employee.getHireDate().toString();
            case "org" -> orgName(employee.getPrimaryOrgId(), access);
            case "position" -> positionName(employee.getPrimaryPositionId(), access);
            case "manager" -> managerName(employee.getId(), access);
            default -> null;
        };
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if (access.masked()) {
            maskedFields.add(field);
            return fieldPermissionService.mask(raw, access.maskRule());
        }
        return raw;
    }

    private String orgName(Long orgId, FieldAccess access) {
        if (orgId == null || !access.visible() || !access.aiReadable()) {
            return null;
        }
        OrgUnit org = orgUnitMapper.selectById(orgId);
        return org == null ? null : org.getName();
    }

    private String positionName(Long positionId, FieldAccess access) {
        if (positionId == null || !access.visible() || !access.aiReadable()) {
            return null;
        }
        Position position = positionMapper.selectById(positionId);
        return position == null ? null : position.getName();
    }

    private String managerName(Long employeeId, FieldAccess access) {
        if (!access.visible() || !access.aiReadable()) {
            return null;
        }
        ReportingLine line = reportingLineMapper.selectOne(new LambdaQueryWrapper<ReportingLine>()
                .eq(ReportingLine::getEmployeeId, employeeId)
                .eq(ReportingLine::getIsPrimary, true)
                .and(w -> w.isNull(ReportingLine::getEndDate)
                        .or().ge(ReportingLine::getEndDate, java.time.LocalDate.now()))
                .orderByDesc(ReportingLine::getStartDate)
                .last("LIMIT 1"));
        if (line == null || line.getManagerId() == null) {
            return null;
        }
        Employee manager = employeeMapper.selectById(line.getManagerId());
        return manager == null ? null : manager.getName();
    }

    private AiText enhance(String context, String question, boolean withContext,
                           String fallback, LoginUser user) {
        AiProviderConfig provider = providerService.defaultProvider();
        if (provider == null || !withContext) {
            logDeterministic(user, "PEOPLE_QA", question, fallback);
            return new AiText(fallback, false);
        }
        try {
            String answer = providerService.chat(provider, PEOPLE_SYSTEM_PROMPT,
                    "资料：\n" + context + "\n问题：" + question, user, "PEOPLE_QA", question);
            if (StringUtils.hasText(answer)) {
                return new AiText(answer.trim(), true);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the deterministic answer when the provider is unreachable.
        }
        logDeterministic(user, "PEOPLE_QA", question, fallback);
        return new AiText(fallback, false);
    }

    // ---------------------------------------------------------------- quick questions & logs

    public List<AiAssistantDtos.QuickQuestion> quickQuestions(LoginUser user) {
        List<AiQuickQuestion> questions = quickQuestionMapper.selectList(
                new LambdaQueryWrapper<AiQuickQuestion>()
                        .eq(AiQuickQuestion::getEnabled, true)
                        .orderByAsc(AiQuickQuestion::getSort));
        List<AiAssistantDtos.QuickQuestion> result = new ArrayList<>();
        for (AiQuickQuestion question : questions) {
            if (StringUtils.hasText(question.getRequiredPermission())
                    && !user.hasPermission(question.getRequiredPermission())) {
                continue;
            }
            if (!rolesAllowed(question.getRolesJson(), user)) {
                continue;
            }
            result.add(new AiAssistantDtos.QuickQuestion(
                    question.getId(), question.getQuestion(), question.getFeature()));
        }
        return result;
    }

    public PageResult<AiAssistantDtos.QueryLogView> logs(long page, long size, String feature) {
        long current = Math.max(1, page);
        long pageSize = Math.min(Math.max(1, size), 200);
        LambdaQueryWrapper<AiQueryLog> wrapper = new LambdaQueryWrapper<AiQueryLog>()
                .eq(StringUtils.hasText(feature), AiQueryLog::getFeature, feature)
                .orderByDesc(AiQueryLog::getCreatedAt);
        Page<AiQueryLog> result = queryLogMapper.selectPage(new Page<>(current, pageSize), wrapper);
        List<AiAssistantDtos.QueryLogView> views = result.getRecords().stream()
                .map(log -> new AiAssistantDtos.QueryLogView(
                        log.getId(),
                        log.getFeature(),
                        log.getQuestionSummary(),
                        log.getModel(),
                        Boolean.TRUE.equals(log.getSuccess()),
                        log.getLatencyMs() == null ? 0 : log.getLatencyMs(),
                        log.getAnswerSummary(),
                        log.getCreatedAt()))
                .toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), views);
    }

    // ---------------------------------------------------------------- help index admin

    public List<AiAssistantDtos.HelpIndexView> listHelpIndex() {
        return helpIndexMapper.selectList(new LambdaQueryWrapper<AiHelpIndex>()
                        .orderByAsc(AiHelpIndex::getSort)
                        .orderByAsc(AiHelpIndex::getId))
                .stream().map(this::helpView).toList();
    }

    @Transactional
    public AiAssistantDtos.HelpIndexView saveHelpIndex(Long id,
                                                       AiAssistantDtos.HelpIndexRequest request,
                                                       LoginUser user) {
        validateRoute(request.getRoute());
        AiHelpIndex entity;
        Object before = null;
        if (id == null) {
            entity = new AiHelpIndex();
        } else {
            entity = helpIndexMapper.selectById(id);
            if (entity == null) {
                throw new BusinessException(40401, "操作指引入口不存在");
            }
            before = helpView(entity);
        }
        entity.setTitle(request.getTitle().trim());
        entity.setKeywords(request.getKeywords() == null ? "" : request.getKeywords().trim());
        entity.setRoute(request.getRoute().trim());
        entity.setDescription(request.getDescription());
        entity.setRequiredPermission(StringUtils.hasText(request.getRequiredPermission())
                ? request.getRequiredPermission().trim() : null);
        entity.setSort(request.getSort() == null ? 0 : request.getSort());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        if (id == null) {
            helpIndexMapper.insert(entity);
        } else {
            helpIndexMapper.updateById(entity);
        }
        AiAssistantDtos.HelpIndexView view = helpView(entity);
        auditService.record("CONFIG", "ai", id == null ? "CREATE" : "UPDATE",
                "AI_HELP_INDEX", entity.getId(), before, view, null, null);
        return view;
    }

    @Transactional
    public void deleteHelpIndex(Long id) {
        AiHelpIndex entity = helpIndexMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(40401, "操作指引入口不存在");
        }
        helpIndexMapper.deleteById(id);
        auditService.record("CONFIG", "ai", "DELETE", "AI_HELP_INDEX", id,
                helpView(entity), null, null, null);
    }

    // ---------------------------------------------------------------- helpers

    private List<String> matchedKeywords(AiHelpIndex entry, String question) {
        List<String> matches = new ArrayList<>();
        for (String keyword : splitKeywords(entry.getKeywords())) {
            if (question.contains(normalize(keyword))) {
                matches.add(keyword);
            }
        }
        return matches;
    }

    private List<String> splitKeywords(String keywords) {
        if (!StringUtils.hasText(keywords)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String token : keywords.split("[,，;；]")) {
            String trimmed = token.trim();
            if (StringUtils.hasText(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private boolean rolesAllowed(String rolesJson, LoginUser user) {
        if (!StringUtils.hasText(rolesJson)) {
            return true;
        }
        try {
            JsonNode node = objectMapper.readTree(rolesJson);
            if (!node.isArray()) {
                return true;
            }
            for (JsonNode role : node) {
                if (role.isTextual() && user.hasRole(role.asText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private void validateRoute(String route) {
        String value = route == null ? "" : route.trim();
        if (!value.startsWith("/") || value.contains("://") || value.contains("..")) {
            throw new BusinessException(40001, "入口路径必须是站内绝对路径");
        }
    }

    private void logDeterministic(LoginUser user, String feature, String question, String answer) {
        AiQueryLog log = new AiQueryLog();
        log.setUserId(user == null ? null : user.userId());
        log.setFeature(feature);
        log.setQuestionSummary(truncate(question, 500));
        log.setSuccess(true);
        log.setLatencyMs(0L);
        log.setAnswerSummary(truncate(answer, 500));
        queryLogMapper.insert(log);
    }

    private AiAssistantDtos.HelpIndexView helpView(AiHelpIndex entity) {
        return new AiAssistantDtos.HelpIndexView(
                entity.getId(),
                entity.getTitle(),
                entity.getKeywords(),
                entity.getRoute(),
                entity.getDescription(),
                entity.getRequiredPermission(),
                entity.getSort() == null ? 0 : entity.getSort(),
                Boolean.TRUE.equals(entity.getEnabled()));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private String limit(String value) {
        return truncate(value, MAX_ANSWER_CHARS);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private record ScoredHelp(AiHelpIndex entry, int score, List<String> matches) {
    }

    private record AiText(String text, boolean aiUsed) {
    }

    /**
     * Small helper to build an insertion-ordered unique set without pulling in a dependency.
     */
    private static final class LinkedHashSetBuilder {
        private final Map<String, Boolean> values = new LinkedHashMap<>();

        private LinkedHashSetBuilder(String... fields) {
            for (String field : fields) {
                values.put(field, Boolean.TRUE);
            }
        }

        private Set<String> build() {
            return values.keySet();
        }
    }
}
