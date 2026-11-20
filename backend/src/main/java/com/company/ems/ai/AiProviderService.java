package com.company.ems.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.ai.entity.AiProviderConfig;
import com.company.ems.ai.entity.AiQueryLog;
import com.company.ems.ai.mapper.AiProviderConfigMapper;
import com.company.ems.ai.mapper.AiQueryLogMapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AiProviderService {

    private static final String SYSTEM_MAPPING_PROMPT = """
            你是企业花名册列映射助手。只输出 JSON，不要输出解释或 Markdown。
            把用户给出的表头映射到下列标准字段（能确定才映射，不确定就不要包含）：
            employeeNo(工号), name(姓名), gender(性别), birthDate(出生日期), phone(手机),
            email(邮箱), idCard(身份证号), address(地址), emergencyContact(紧急联系人),
            hireDate(入职日期), orgName(部门), positionName(岗位)。
            输出格式：{"mapping":{"employeeNo":"表头原文","name":"表头原文"}}。
            """;

    private final AiProviderConfigMapper providerMapper;
    private final AiQueryLogMapper queryLogMapper;
    private final CryptoService cryptoService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AiProviderService(AiProviderConfigMapper providerMapper,
                             AiQueryLogMapper queryLogMapper,
                             CryptoService cryptoService,
                             AuditService auditService,
                             ObjectMapper objectMapper,
                             RestClient.Builder restClientBuilder) {
        this.providerMapper = providerMapper;
        this.queryLogMapper = queryLogMapper;
        this.cryptoService = cryptoService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public List<AiDtos.ProviderView> list() {
        return providerMapper.selectList(new LambdaQueryWrapper<AiProviderConfig>()
                        .orderByDesc(AiProviderConfig::getIsDefault)
                        .orderByAsc(AiProviderConfig::getName))
                .stream().map(this::toView).toList();
    }

    @Transactional
    public AiDtos.ProviderView save(AiDtos.ProviderRequest request, LoginUser user) {
        AiProviderConfig entity;
        Map<String, Object> before = null;
        if (request.getId() != null) {
            entity = providerMapper.selectById(request.getId());
            if (entity == null) {
                throw new BusinessException(40401, "AI 厂商配置不存在");
            }
            before = new LinkedHashMap<>();
            before.put("name", entity.getName());
            before.put("baseUrl", entity.getBaseUrl());
            before.put("model", entity.getModel());
            before.put("enabled", entity.getEnabled());
            before.put("isDefault", entity.getIsDefault());
        } else {
            if (providerMapper.selectCount(null) >= 10) {
                throw new BusinessException(40001, "最多配置 10 个 AI 厂商");
            }
            entity = new AiProviderConfig();
            entity.setCreatedBy(user.userId());
        }
        entity.setName(request.getName().trim());
        entity.setProviderType(StringUtils.hasText(request.getProviderType())
                ? request.getProviderType().trim().toUpperCase() : "OPENAI_COMPATIBLE");
        entity.setBaseUrl(request.getBaseUrl().trim());
        entity.setModel(request.getModel().trim());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setIsDefault(Boolean.TRUE.equals(request.getIsDefault()));
        if (StringUtils.hasText(request.getApiKey())) {
            entity.setApiKeyEnc(cryptoService.encrypt(request.getApiKey().trim()));
        }
        if (request.getId() == null) {
            providerMapper.insert(entity);
        } else {
            providerMapper.updateById(entity);
        }
        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            clearOtherDefaults(entity.getId());
        } else if (providerMapper.selectCount(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getIsDefault, true)) == 0) {
            entity.setIsDefault(true);
            providerMapper.updateById(entity);
        }
        auditService.record("CONFIG", "ai", request.getId() == null ? "CREATE" : "UPDATE",
                "AI_PROVIDER", entity.getId(), before, toView(entity), null, null);
        return toView(entity);
    }

    public AiDtos.TestResult test(Long providerId, LoginUser user) {
        AiProviderConfig provider = providerId == null ? defaultProvider()
                : providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException(40401, "请先配置 AI 厂商");
        }
        long started = System.currentTimeMillis();
        try {
            String content = chat(provider, "你是连通性测试助手。", "只回复 OK。", user, "PROVIDER_TEST");
            long elapsed = System.currentTimeMillis() - started;
            return new AiDtos.TestResult(true, "连接成功：" + trim(content, 80), elapsed);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - started;
            return new AiDtos.TestResult(false, "连接失败：" + trim(ex.getMessage(), 300), elapsed);
        }
    }

    /**
     * Ask the configured model to map roster headers to standard field codes.
     * Returns an empty map when no provider is configured or the call fails;
     * callers fall back to the deterministic header dictionary.
     */
    public Map<String, String> suggestColumnMapping(List<String> headers, LoginUser user) {
        AiProviderConfig provider = defaultProvider();
        if (provider == null) {
            return Map.of();
        }
        try {
            String request = objectMapper.writeValueAsString(Map.of("headers", headers));
            String content = chat(provider, SYSTEM_MAPPING_PROMPT, request, user, "ROSTER_MAPPING");
            JsonNode root = objectMapper.readTree(stripFences(content));
            JsonNode mapping = root.path("mapping");
            if (!mapping.isObject()) {
                return Map.of();
            }
            Map<String, String> result = new LinkedHashMap<>();
            mapping.fields().forEachRemaining(entry -> {
                if (entry.getValue().isTextual() && headers.contains(entry.getValue().asText())) {
                    result.put(entry.getKey(), entry.getValue().asText());
                }
            });
            return result;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    public String chat(AiProviderConfig provider, String systemPrompt, String userPrompt,
                       LoginUser user, String feature) {
        return chat(provider, systemPrompt, userPrompt, user, feature, userPrompt);
    }

    /**
     * Same as {@link #chat(AiProviderConfig, String, String, LoginUser, String)} but
     * lets callers keep sensitive data out of the persisted question summary.
     */
    public String chat(AiProviderConfig provider, String systemPrompt, String userPrompt,
                       LoginUser user, String feature, String logSummary) {
        long started = System.currentTimeMillis();
        boolean success = false;
        String answer = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", provider.getModel());
            body.put("temperature", 0);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            String response = restClient.post()
                    .uri(chatEndpoint(provider.getBaseUrl()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + cryptoService.decrypt(provider.getApiKeyEnc()))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                throw new IllegalStateException("AI 返回格式不符合 OpenAI 兼容格式");
            }
            answer = content.asText();
            success = true;
            return answer;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(50201, "AI 调用失败：" + trim(ex.getMessage(), 300));
        } finally {
            AiQueryLog log = new AiQueryLog();
            log.setUserId(user == null ? null : user.userId());
            log.setFeature(feature);
            log.setQuestionSummary(trim(logSummary, 500));
            log.setModel(provider.getModel());
            log.setSuccess(success);
            log.setLatencyMs(System.currentTimeMillis() - started);
            log.setAnswerSummary(trim(answer, 500));
            queryLogMapper.insert(log);
        }
    }

    public AiProviderConfig defaultProvider() {
        AiProviderConfig provider = providerMapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getEnabled, true)
                .eq(AiProviderConfig::getIsDefault, true)
                .last("LIMIT 1"));
        if (provider != null) {
            return provider;
        }
        return providerMapper.selectOne(new LambdaQueryWrapper<AiProviderConfig>()
                .eq(AiProviderConfig::getEnabled, true)
                .orderByAsc(AiProviderConfig::getName)
                .last("LIMIT 1"));
    }

    private void clearOtherDefaults(Long keepId) {
        List<AiProviderConfig> defaults = providerMapper.selectList(
                new LambdaQueryWrapper<AiProviderConfig>()
                        .eq(AiProviderConfig::getIsDefault, true)
                        .ne(AiProviderConfig::getId, keepId));
        for (AiProviderConfig other : defaults) {
            other.setIsDefault(false);
            providerMapper.updateById(other);
        }
    }

    private String chatEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    private AiDtos.ProviderView toView(AiProviderConfig entity) {
        String plain = null;
        try {
            plain = cryptoService.decrypt(entity.getApiKeyEnc());
        } catch (Exception ignored) {
            // A changed APP_CRYPTO_KEY should not break listing; re-save the key to fix.
        }
        return new AiDtos.ProviderView(
                entity.getId(),
                entity.getName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getModel(),
                Boolean.TRUE.equals(entity.getEnabled()),
                Boolean.TRUE.equals(entity.getIsDefault()),
                StringUtils.hasText(entity.getApiKeyEnc()),
                cryptoService.mask(plain),
                entity.getUpdatedAt()
        );
    }

    private String stripFences(String content) {
        if (content == null) {
            return "{}";
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    private String trim(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
