package com.company.ems.ai;

import com.company.ems.ai.entity.AiProviderConfig;
import com.company.ems.ai.entity.AiQueryLog;
import com.company.ems.ai.mapper.AiQueryLogMapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.project.DocumentService;
import com.company.ems.project.TaskDtos;
import com.company.ems.project.TaskService;
import com.company.ems.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Document Q&A and task extraction. Retrieval and the deterministic answer run
 * locally over the document text that DocumentService has already authorized for
 * this user; the configured model is only an optional rewriter/extractor over the
 * retrieved fragments, never a source of new facts.
 */
@Service
public class AiDocumentService {

    private static final int CHUNK_SIZE = 700;
    private static final int MAX_CHUNKS = 800;
    private static final int SNIPPET_LENGTH = 160;
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

    private static final Set<String> SINGLE_CHAR_TERMS = Set.of(
            "天", "年", "月", "日", "元", "次", "人", "个", "岁", "时");
    private static final List<String> STOP_PHRASES = List.of(
            "文档中提到的", "文档中提到", "中提到的", "提到的", "请问", "帮我", "查一下",
            "是什么", "是多少", "做什么", "有什么", "什么时候", "谁负责", "怎么样", "为什么",
            "文档中", "文档里", "里面", "哪些", "哪个", "什么", "多少", "怎么", "如何",
            "是否", "有没有", "何时", "关于", "相关", "内容", "这个", "那个", "一下",
            "可以", "能否", "告诉", "我们", "你们", "他们", "以及", "还有", "分别",
            "或者", "还是"
    );

    private static final List<String> STRONG_ACTION_WORDS = List.of(
            "负责", "完成", "提交", "跟进", "交付", "编写", "整理", "审核", "测试",
            "修复", "对接", "提供", "安排", "汇总", "更新", "归档", "截止", "todo", "action");

    private static final List<String> ACTION_WORDS = List.of(
            "负责", "完成", "提交", "跟进", "交付", "编写", "整理", "确认", "审核",
            "准备", "上线", "发布", "测试", "修复", "对接", "提供", "安排", "组织",
            "汇总", "更新", "归档", "检查", "评估", "制定", "推进", "落实", "截止",
            "里程碑", "todo", "action");

    private static final Pattern DUE_FULL = Pattern.compile(
            "(\\d{4})\\s*[-/年]\\s*(\\d{1,2})\\s*[-/月]\\s*(\\d{1,2})\\s*日?");
    private static final Pattern DUE_MONTH_DAY = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[-*+]\\s*");
    private static final Pattern CHECKBOX_PREFIX = Pattern.compile("^[-*+]\\s*\\[[ xX]?\\]\\s*");
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^\\d+[.)、]\\s*");
    private static final Pattern TODO_PREFIX = Pattern.compile("^(todo|任务|行动项)\\s*[:：]\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+");
    private static final Pattern CITATION_MARK = Pattern.compile("\\[\\d+]");

    private final DocumentService documentService;
    private final TaskService taskService;
    private final AiProviderService providerService;
    private final AiQueryLogMapper queryLogMapper;
    private final ConfigService configService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AiDocumentService(DocumentService documentService,
                             TaskService taskService,
                             AiProviderService providerService,
                             AiQueryLogMapper queryLogMapper,
                             ConfigService configService,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.documentService = documentService;
        this.taskService = taskService;
        this.providerService = providerService;
        this.queryLogMapper = queryLogMapper;
        this.configService = configService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- Q&A

    public AiDocumentDtos.DocAnswer ask(AiDocumentDtos.DocAskRequest request, LoginUser user) {
        if (!configService.getBoolean("ai.docQa.enabled", true)) {
            throw new BusinessException(40001, "文档问答功能未启用");
        }
        DocumentService.AiDocumentContext context =
                documentService.aiContext(request.getDocumentId(), user);
        String content = limit(context.content(), configService.getInt("ai.docQa.maxContextChars", 12000));
        int minChars = configService.getInt("ai.docQa.minContentChars", 20);
        if (!StringUtils.hasText(content) || content.strip().length() < minChars) {
            throw new BusinessException(40001, "该文档还没有可检索的正文（可能是 PDF 或旧文档）");
        }

        String question = request.getQuestion().trim();
        int maxAnswer = Math.max(20, configService.getInt("ai.docQa.maxAnswerChars", 300));
        int maxCitations = Math.min(10, Math.max(1, configService.getInt("ai.docQa.maxCitations", 3)));
        boolean citationsRequired = configService.getBoolean("ai.docQa.requireCitations", true);
        double threshold = configService.getDecimal("ai.lowConfidenceThreshold",
                new BigDecimal("0.6")).doubleValue();

        List<String> terms = queryTerms(question);
        List<Chunk> chunks = chunks(content);
        List<ScoredChunk> ranked = new ArrayList<>();
        for (Chunk chunk : chunks) {
            ScoredChunk scored = score(chunk, terms);
            if (scored.score() > 0) {
                ranked.add(scored);
            }
        }
        ranked.sort(Comparator.comparingInt(ScoredChunk::score).reversed()
                .thenComparing(Comparator.comparingDouble(ScoredChunk::confidence).reversed())
                .thenComparingInt(item -> item.chunk().index()));
        List<ScoredChunk> top = ranked.stream().limit(maxCitations).toList();
        double confidence = top.isEmpty() ? 0 : top.get(0).confidence();
        boolean lowConfidence = top.isEmpty() || confidence < threshold;

        List<AiDocumentDtos.DocCitation> citations = top.stream()
                .map(item -> new AiDocumentDtos.DocCitation(context.id(), context.name(),
                        context.versionNo(), item.chunk().index(),
                        snippet(item.chunk().text(), terms, SNIPPET_LENGTH)))
                .toList();

        String answer = top.isEmpty()
                ? "文档中未找到明确答案，请换一种问法或直接查看原文。"
                : bestSentence(top.get(0).chunk().text(), terms, maxAnswer);
        boolean aiUsed = false;

        AiProviderConfig provider = configService.getBoolean("ai.enabled", true)
                ? providerService.defaultProvider() : null;
        if (provider != null && !top.isEmpty()) {
            try {
                String modelAnswer = providerService.chat(provider, qaSystemPrompt(maxAnswer),
                        qaUserPrompt(context, top), user, "DOC_QA",
                        "文档问答：" + context.name());
                if (StringUtils.hasText(modelAnswer)) {
                    String candidate = limit(modelAnswer.strip(), maxAnswer);
                    if (!citationsRequired || CITATION_MARK.matcher(candidate).find()) {
                        answer = candidate;
                        aiUsed = true;
                        if (candidate.contains("未找到")) {
                            lowConfidence = true;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // Provider failure: keep the deterministic extractive answer.
            }
        }
        if (!aiUsed) {
            logDeterministic(user, "DOC_QA", question, answer);
        }
        return new AiDocumentDtos.DocAnswer(context.name(), context.versionNo(), answer, citations,
                round(confidence), lowConfidence, aiUsed, citationsRequired);
    }

    private String qaSystemPrompt(int maxAnswer) {
        return """
                你是企业项目文档问答助手。只根据“文档片段”回答，不使用外部知识，不推测。
                回答不超过 %d 个汉字，只给结论；必须用 [1][2] 这样的编号引用片段。
                资料不足时只回答“文档中未找到明确答案”。
                不要输出 Markdown、表格或额外建议。
                """.formatted(maxAnswer);
    }

    private String qaUserPrompt(DocumentService.AiDocumentContext context, List<ScoredChunk> top) {
        StringBuilder prompt = new StringBuilder()
                .append("文档：").append(context.name())
                .append("（v").append(context.versionNo()).append("）\n片段：\n");
        for (int i = 0; i < top.size(); i++) {
            prompt.append('[').append(i + 1).append("] ")
                    .append(top.get(i).chunk().text()).append('\n');
        }
        return prompt.toString();
    }

    /**
     * Answers a question across one or more company policy documents. The same
     * permission-filtered text path is used as document Q&A: retrieval and the
     * deterministic answer happen locally; the configured model may only rewrite
     * the retrieved fragments.
     */
    public AiDocumentDtos.PolicyAnswer askPolicies(List<AiDocumentDtos.PolicySource> sources,
                                                   String question,
                                                   LoginUser user) {
        int maxAnswer = Math.max(20, configService.getInt("ai.docQa.maxAnswerChars", 300));
        int maxCitations = Math.min(10, Math.max(1, configService.getInt("policy.ai.maxCitations", 5)));
        boolean citationsRequired = configService.getBoolean("ai.docQa.requireCitations", true);
        double threshold = configService.getDecimal("ai.lowConfidenceThreshold",
                new BigDecimal("0.6")).doubleValue();

        List<String> terms = queryTerms(question);
        List<ScoredPolicyChunk> ranked = new ArrayList<>();
        for (AiDocumentDtos.PolicySource source : sources) {
            if (source == null || !StringUtils.hasText(source.content())) {
                continue;
            }
            for (Chunk chunk : chunks(source.content())) {
                ScoredChunk scored = score(chunk, terms);
                if (scored.score() > 0) {
                    ranked.add(new ScoredPolicyChunk(source.policyId(), source.title(),
                            chunk, scored.score(), scored.confidence()));
                }
            }
        }
        ranked.sort(Comparator.comparingInt(ScoredPolicyChunk::score).reversed()
                .thenComparing(Comparator.comparingDouble(ScoredPolicyChunk::confidence).reversed())
                .thenComparingInt(item -> item.chunk().index()));

        // Keep the answer grounded in more than one policy when several match.
        List<ScoredPolicyChunk> top = new ArrayList<>();
        Map<Long, Integer> perPolicy = new HashMap<>();
        for (ScoredPolicyChunk item : ranked) {
            int used = perPolicy.getOrDefault(item.policyId(), 0);
            if (used >= 2) {
                continue;
            }
            top.add(item);
            perPolicy.put(item.policyId(), used + 1);
            if (top.size() >= maxCitations) {
                break;
            }
        }

        double confidence = top.isEmpty() ? 0 : top.get(0).confidence();
        boolean lowConfidence = top.isEmpty() || confidence < threshold;
        List<AiDocumentDtos.PolicyCitation> citations = top.stream()
                .map(item -> new AiDocumentDtos.PolicyCitation(item.policyId(), item.title(),
                        item.chunk().index(), snippet(item.chunk().text(), terms, SNIPPET_LENGTH)))
                .toList();

        String answer = top.isEmpty()
                ? "制度中未找到明确答案，请换一种问法或直接查看制度原文。"
                : bestSentence(top.get(0).chunk().text(), terms, maxAnswer);
        boolean aiUsed = false;

        AiProviderConfig provider = configService.getBoolean("ai.enabled", true)
                ? providerService.defaultProvider() : null;
        if (provider != null && !top.isEmpty()) {
            try {
                String modelAnswer = providerService.chat(provider, policySystemPrompt(maxAnswer),
                        policyUserPrompt(top), user, "POLICY_QA",
                        "公司制度问答：" + question);
                if (StringUtils.hasText(modelAnswer)) {
                    String candidate = limit(modelAnswer.strip(), maxAnswer);
                    if (!citationsRequired || CITATION_MARK.matcher(candidate).find()) {
                        answer = candidate;
                        aiUsed = true;
                        if (candidate.contains("未找到")) {
                            lowConfidence = true;
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // Provider failure: keep the deterministic extractive answer.
            }
        }
        if (!aiUsed) {
            logDeterministic(user, "POLICY_QA", question, answer);
        }
        return new AiDocumentDtos.PolicyAnswer(answer, citations, round(confidence),
                lowConfidence, aiUsed, citationsRequired);
    }

    private String policySystemPrompt(int maxAnswer) {
        return """
                你是企业制度问答助手。只根据“制度片段”回答，不使用外部知识，不推测。
                回答不超过 %d 个汉字，只给结论；必须用 [1][2] 这样的编号引用片段。
                资料不足时只回答“制度中未找到明确答案”。
                不要输出 Markdown、表格或额外建议。
                """.formatted(maxAnswer);
    }

    private String policyUserPrompt(List<ScoredPolicyChunk> top) {
        StringBuilder prompt = new StringBuilder("制度片段：\n");
        for (int i = 0; i < top.size(); i++) {
            prompt.append('[').append(i + 1).append("] 制度：")
                    .append(top.get(i).title()).append('\n')
                    .append(top.get(i).chunk().text()).append('\n');
        }
        return prompt.toString();
    }

    // ---------------------------------------------------------------- task extraction

    public AiDocumentDtos.DocTaskExtractResult extractTasks(Long documentId, Integer max,
                                                            LoginUser user) {
        if (!configService.getBoolean("ai.docTasks.enabled", true)) {
            throw new BusinessException(40001, "文档任务提取功能未启用");
        }
        DocumentService.AiDocumentContext context = documentService.aiContext(documentId, user);
        String content = limit(context.content(), configService.getInt("ai.docTasks.maxContextChars", 12000));
        int defaultMax = configService.getInt("ai.docTasks.maxSuggestions", 20);
        int limit = Math.max(1, Math.min(max == null ? defaultMax : max, 50));
        double threshold = configService.getDecimal("ai.docTasks.lowConfidenceThreshold",
                new BigDecimal("0.6")).doubleValue();
        String defaultPriority = normalizePriority(
                configService.getString("ai.docTasks.defaultPriority", "NORMAL"), "NORMAL");

        List<AiDocumentDtos.TaskSuggestion> suggestions = localTasks(content, limit,
                threshold, defaultPriority);
        boolean aiUsed = false;

        AiProviderConfig provider = configService.getBoolean("ai.enabled", true)
                ? providerService.defaultProvider() : null;
        if (provider != null && StringUtils.hasText(content)) {
            try {
                String modelAnswer = providerService.chat(provider, taskSystemPrompt(limit),
                        taskUserPrompt(context, content), user, "DOC_TASK_EXTRACT",
                        "文档任务提取：" + context.name());
                List<AiDocumentDtos.TaskSuggestion> parsed = parseModelTasks(modelAnswer, limit,
                        threshold, defaultPriority);
                if (!parsed.isEmpty()) {
                    suggestions = parsed;
                    aiUsed = true;
                }
            } catch (RuntimeException ignored) {
                // Keep local extraction when the model is unavailable or malformed.
            }
        }
        if (!aiUsed) {
            logDeterministic(user, "DOC_TASK_EXTRACT",
                    "文档任务提取：" + context.name(), "共 " + suggestions.size() + " 条建议");
        }
        String note = suggestions.isEmpty()
                ? "没有从文档中识别到明确的任务或待办，请人工补充。"
                : "共提取 " + suggestions.size() + " 条建议，需人工确认后再创建任务。";
        return new AiDocumentDtos.DocTaskExtractResult(context.id(), context.name(),
                context.versionNo(), suggestions, aiUsed, note);
    }

    private String taskSystemPrompt(int limit) {
        return """
                你是企业项目文档任务提取助手。只输出 JSON 数组，不要输出解释或 Markdown。
                每个元素格式：
                {"title":"任务标题，必填且不超过200字","description":"补充说明","dueDate":"YYYY-MM-DD或null","priority":"LOW/NORMAL/HIGH/URGENT"}
                只提取文档中明确写出的行动项、待办或交付物，不要编造，不要补充文档没有的信息。
                最多输出 %d 条；没有可提取内容时输出 []。
                """.formatted(limit);
    }

    private String taskUserPrompt(DocumentService.AiDocumentContext context, String content) {
        return "文档：" + context.name() + "\n正文：\n" + content;
    }

    private List<AiDocumentDtos.TaskSuggestion> parseModelTasks(String content, int limit,
                                                                double threshold,
                                                                String defaultPriority) {
        try {
            JsonNode root = objectMapper.readTree(stripFences(content));
            if (!root.isArray()) {
                return List.of();
            }
            List<AiDocumentDtos.TaskSuggestion> result = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode node : root) {
                if (result.size() >= limit) {
                    break;
                }
                String title = node.path("title").asText("").strip();
                if (!StringUtils.hasText(title)) {
                    continue;
                }
                title = limit(title, 200);
                String key = title.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    continue;
                }
                String description = node.path("description").asText("");
                LocalDate dueDate = parseDate(node.path("dueDate").asText(""));
                String priority = normalizePriority(node.path("priority").asText(""), defaultPriority);
                double confidence = 0.8;
                result.add(new AiDocumentDtos.TaskSuggestion(title,
                        description == null ? null : limit(description, 2000),
                        dueDate, priority, "", confidence, confidence < threshold));
            }
            return result;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<AiDocumentDtos.TaskSuggestion> localTasks(String content, int limit,
                                                            double threshold,
                                                            String defaultPriority) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        List<AiDocumentDtos.TaskSuggestion> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String heading = null;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (HEADING.matcher(line).find()) {
                heading = line.replaceFirst("^#{1,6}\\s+", "").strip();
                continue;
            }
            boolean checkbox = CHECKBOX_PREFIX.matcher(line).find();
            boolean todo = line.toLowerCase(Locale.ROOT).startsWith("todo")
                    || line.startsWith("任务：") || line.startsWith("任务:")
                    || line.startsWith("行动项：") || line.startsWith("行动项:");
            String candidate = cleanTaskLine(line);
            if (!StringUtils.hasText(candidate) || candidate.length() < 4) {
                continue;
            }
            candidate = limit(candidate, 200);
            String lower = candidate.toLowerCase(Locale.ROOT);
            LocalDate dueDate = parseDate(candidate);
            boolean strong = checkbox || todo || dueDate != null || containsAny(lower, STRONG_ACTION_WORDS);
            if (!strong) {
                continue;
            }
            boolean actionable = containsAny(lower, ACTION_WORDS);
            if (!actionable && !checkbox && !todo) {
                continue;
            }
            String key = candidate.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            double confidence = checkbox || todo ? 0.9 : dueDate != null ? 0.85 : 0.75;
            String description = line;
            if (StringUtils.hasText(heading)) {
                description = description + "\n来源：" + heading;
            }
            result.add(new AiDocumentDtos.TaskSuggestion(candidate, limit(description, 2000),
                    dueDate, inferPriority(lower, defaultPriority), candidate,
                    confidence, confidence < threshold));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    @Transactional
    public List<TaskDtos.TaskView> applyTasks(Long documentId,
                                              AiDocumentDtos.DocTaskApplyRequest request,
                                              LoginUser user) {
        if (!configService.getBoolean("ai.docTasks.enabled", true)) {
            throw new BusinessException(40001, "文档任务提取功能未启用");
        }
        DocumentService.AiDocumentContext context = documentService.aiContext(documentId, user);
        int maxApply = Math.max(1, configService.getInt("ai.docTasks.maxApply", 20));
        if (request.getTasks() == null || request.getTasks().isEmpty()) {
            throw new BusinessException(40001, "请选择要创建的任务");
        }
        if (request.getTasks().size() > maxApply) {
            throw new BusinessException(40001, "一次最多创建 " + maxApply + " 个任务");
        }
        List<Long> taskIds = new ArrayList<>();
        for (AiDocumentDtos.SuggestedTask suggestion : request.getTasks()) {
            TaskDtos.TaskRequest task = new TaskDtos.TaskRequest();
            task.setTitle(suggestion.getTitle().trim());
            task.setDescription(suggestion.getDescription());
            task.setDueDate(suggestion.getDueDate());
            task.setPriority(normalizePriority(suggestion.getPriority(),
                    configService.getString("ai.docTasks.defaultPriority", "NORMAL")));
            task.setAssigneeEmployeeId(request.getAssigneeEmployeeId());
            TaskDtos.TaskView created = taskService.create(context.projectId(), task, user);
            taskIds.add(created.id());
        }
        auditService.record("AI", "doc", "APPLY_TASKS", "DOCUMENT", documentId, null,
                Map.of("taskIds", taskIds), "AI 文档任务建议经人工确认后创建", null);
        List<TaskDtos.TaskView> views = new ArrayList<>();
        for (Long taskId : taskIds) {
            views.add(taskService.get(context.projectId(), taskId, user));
        }
        return views;
    }

    // ---------------------------------------------------------------- retrieval helpers

    private List<Chunk> chunks(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            if (line.length() > CHUNK_SIZE) {
                addChunk(chunks, current.toString());
                current.setLength(0);
                for (int start = 0; start < line.length(); start += CHUNK_SIZE) {
                    addChunk(chunks, line.substring(start, Math.min(line.length(), start + CHUNK_SIZE)));
                }
                continue;
            }
            if (current.length() + line.length() + 1 > CHUNK_SIZE) {
                addChunk(chunks, current.toString());
                current.setLength(0);
            }
            current.append(line).append('\n');
        }
        addChunk(chunks, current.toString());
        return chunks;
    }

    private void addChunk(List<Chunk> chunks, String text) {
        if (chunks.size() >= MAX_CHUNKS) {
            return;
        }
        String stripped = text.strip();
        if (!stripped.isEmpty()) {
            chunks.add(new Chunk(chunks.size() + 1, stripped));
        }
    }

    private List<String> queryTerms(String question) {
        String q = question.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
        for (String phrase : STOP_PHRASES) {
            q = q.replace(phrase, " ");
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String segment : q.split("\\s+")) {
            if (segment.length() == 1) {
                if (SINGLE_CHAR_TERMS.contains(segment)) {
                    terms.add(segment);
                }
                continue;
            }
            if (segment.length() < 2) {
                continue;
            }
            boolean han = segment.codePoints()
                    .allMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
            if (!han) {
                terms.add(segment);
                continue;
            }
            if (segment.length() <= 4) {
                terms.add(segment);
            }
            for (int i = 0; i + 2 <= segment.length(); i++) {
                terms.add(segment.substring(i, i + 2));
            }
        }
        return new ArrayList<>(terms);
    }

    private ScoredChunk score(Chunk chunk, List<String> terms) {
        if (terms.isEmpty()) {
            return new ScoredChunk(chunk, 0, 0);
        }
        String lower = chunk.text().toLowerCase(Locale.ROOT);
        int totalWeight = 0;
        int matchedWeight = 0;
        int score = 0;
        for (String term : terms) {
            int weight = term.length();
            totalWeight += weight;
            int count = countOccurrences(lower, term);
            if (count > 0) {
                matchedWeight += weight;
                score += weight * Math.min(count, 3);
            }
        }
        double confidence = totalWeight == 0 ? 0 : (double) matchedWeight / totalWeight;
        return new ScoredChunk(chunk, score, confidence);
    }

    private int countOccurrences(String text, String term) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = text.indexOf(term, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + term.length();
        }
    }

    private String bestSentence(String text, List<String> terms, int maxChars) {
        String[] sentences = text.split("(?<=[。！？!?；;\\n])");
        String best = null;
        int bestScore = -1;
        for (String sentence : sentences) {
            String trimmed = sentence.strip();
            if (trimmed.length() < 2) {
                continue;
            }
            int score = sentenceScore(trimmed, terms);
            if (score > bestScore) {
                bestScore = score;
                best = trimmed;
            }
        }
        if (best == null) {
            best = text.strip();
        }
        return limit(best, maxChars);
    }

    /**
     * A title line can match the question as well as the clause that answers it.
     * Prefer more matched terms, then repeated term hits, then a sentence that
     * actually ends with punctuation, then a longer (more informative) sentence.
     */
    private int sentenceScore(String sentence, List<String> terms) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        int matches = 0;
        int occurrences = 0;
        for (String term : terms) {
            int count = countOccurrences(lower, term);
            if (count > 0) {
                matches++;
                occurrences += Math.min(count, 3);
            }
        }
        boolean terminal = sentence.matches(".*[。！？!?；;]$");
        return matches * 1000 + occurrences * 100 + (terminal ? 50 : 0)
                + Math.min(sentence.length(), 49);
    }

    private String snippet(String text, List<String> terms, int maxChars) {
        String lower = text.toLowerCase(Locale.ROOT);
        int first = -1;
        for (String term : terms) {
            int index = lower.indexOf(term);
            if (index >= 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        if (first < 0) {
            return limit(text.replaceAll("\\s+", " "), maxChars);
        }
        int start = Math.max(0, first - maxChars / 3);
        int end = Math.min(text.length(), start + maxChars);
        return (start > 0 ? "…" : "")
                + text.substring(start, end).replaceAll("\\s+", " ").strip()
                + (end < text.length() ? "…" : "");
    }

    private int matchedTerms(String sentence, List<String> terms) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : terms) {
            if (lower.contains(term)) {
                matched++;
            }
        }
        return matched;
    }

    // ---------------------------------------------------------------- task helpers

    private String cleanTaskLine(String line) {
        String candidate = line;
        if (CHECKBOX_PREFIX.matcher(candidate).find()) {
            candidate = CHECKBOX_PREFIX.matcher(candidate).replaceFirst("");
        } else {
            candidate = BULLET_PREFIX.matcher(candidate).replaceFirst("");
            candidate = NUMBER_PREFIX.matcher(candidate).replaceFirst("");
        }
        candidate = TODO_PREFIX.matcher(candidate).replaceFirst("");
        return candidate.strip();
    }

    private boolean containsAny(String value, List<String> words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String inferPriority(String lower, String defaultPriority) {
        if (lower.contains("最高优先级") || lower.contains("非常紧急")) {
            return "URGENT";
        }
        if (lower.contains("紧急") || lower.contains("立即") || lower.contains("尽快")
                || lower.contains("务必")) {
            return "HIGH";
        }
        if (lower.contains("低优先级") || lower.contains("不着急")) {
            return "LOW";
        }
        return defaultPriority;
    }

    private String normalizePriority(String value, String defaultPriority) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return PRIORITIES.contains(normalized) ? normalized
                : normalizePriority(defaultPriority, "NORMAL");
    }

    private LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher full = DUE_FULL.matcher(text);
        if (full.find()) {
            try {
                return LocalDate.of(Integer.parseInt(full.group(1)),
                        Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        Matcher monthDay = DUE_MONTH_DAY.matcher(text);
        if (monthDay.find()) {
            try {
                return LocalDate.of(LocalDate.now().getYear(),
                        Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- misc helpers

    private String stripFences(String content) {
        String value = content == null ? "" : content.strip();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            if (firstNewline >= 0) {
                value = value.substring(firstNewline + 1);
            }
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3);
            }
        }
        return value.strip();
    }

    private String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        if (max <= 0) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void logDeterministic(LoginUser user, String feature, String question, String answer) {
        AiQueryLog log = new AiQueryLog();
        log.setUserId(user == null ? null : user.userId());
        log.setFeature(feature);
        log.setQuestionSummary(limit(question, 500));
        log.setSuccess(true);
        log.setLatencyMs(0L);
        log.setAnswerSummary(limit(answer, 500));
        queryLogMapper.insert(log);
    }

    private record Chunk(int index, String text) {
    }

    private record ScoredChunk(Chunk chunk, int score, double confidence) {
    }

    private record ScoredPolicyChunk(Long policyId, String title, Chunk chunk,
                                     int score, double confidence) {
    }
}
