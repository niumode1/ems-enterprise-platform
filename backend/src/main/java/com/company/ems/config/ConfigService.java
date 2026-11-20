package com.company.ems.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.approval.ApprovalDtos;
import com.company.ems.approval.ApprovalService;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.entity.SysConfigVersion;
import com.company.ems.system.mapper.SysConfigMapper;
import com.company.ems.system.mapper.SysConfigVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConfigService {

    private static final Set<String> DEFAULT_PROTECTED_GROUPS = Set.of(
            "approval", "recovery", "audit", "boss", "security");
    private static final Set<String> DEFAULT_PROTECTED_KEYS = Set.of(
            "ai.mode", "ai.apiKey", "doc.externalShare.enabled");

    private final SysConfigMapper configMapper;
    private final SysConfigVersionMapper configVersionMapper;
    private final ApprovalService approvalService;
    private final AuditService auditService;

    public ConfigService(SysConfigMapper configMapper,
                         SysConfigVersionMapper configVersionMapper,
                         ApprovalService approvalService,
                         AuditService auditService) {
        this.configMapper = configMapper;
        this.configVersionMapper = configVersionMapper;
        this.approvalService = approvalService;
        this.auditService = auditService;
    }

    public List<SysConfig> list(String group, String keyword) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(group), SysConfig::getConfigGroup, group)
                .like(StringUtils.hasText(keyword), SysConfig::getConfigKey, keyword)
                .orderByAsc(SysConfig::getConfigGroup)
                .orderByAsc(SysConfig::getConfigKey);
        List<SysConfig> list = configMapper.selectList(wrapper);
        list.forEach(this::maskSensitive);
        return list;
    }

    public SysConfig get(String key) {
        SysConfig config = configMapper.selectByKey(key);
        if (config == null) {
            throw new BusinessException(40401, "配置项不存在");
        }
        maskSensitive(config);
        return config;
    }

    public List<SysConfigVersion> history(String key) {
        List<SysConfigVersion> versions = configVersionMapper.selectList(
                new LambdaQueryWrapper<SysConfigVersion>()
                        .eq(SysConfigVersion::getConfigKey, key)
                        .orderByDesc(SysConfigVersion::getCreatedAt));
        for (SysConfigVersion version : versions) {
            version.setOldValue(maskValue(key, version.getOldValue()));
            version.setNewValue(maskValue(key, version.getNewValue()));
        }
        return versions;
    }

    public String getString(String key, String defaultValue) {
        SysConfig config = configMapper.selectByKey(key);
        return config == null || config.getConfigValue() == null ? defaultValue : config.getConfigValue();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        try {
            return new BigDecimal(getString(key, defaultValue.toPlainString()));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Transactional
    public ConfigDtos.UpdateResult requestUpdate(String key, ConfigDtos.UpdateRequest request, LoginUser user) {
        SysConfig existing = configMapper.selectByKey(key);
        if (existing == null && !StringUtils.hasText(request.getGroup())) {
            throw new BusinessException(40001, "新增配置项必须指定分组");
        }
        if (isProtected(key, existing)) {
            ApprovalDtos.SubmitRequest submit = new ApprovalDtos.SubmitRequest();
            submit.setPolicyCode("CONFIG_CHANGE");
            submit.setEntityType("CONFIG");
            submit.setPayload(Map.of("key", key, "value", request.getValue()));
            submit.setReason(request.getReason());
            ApprovalDtos.ApprovalView approval = approvalService.submit(submit, user);
            return new ConfigDtos.UpdateResult("PENDING_APPROVAL", approval.id(),
                    "该配置项受保护，已提交审批", null);
        }
        SysConfig applied = applyChange(key, request.getValue(), request.getGroup(),
                request.getDescription(), request.getValueType(), user.userId(), null);
        return new ConfigDtos.UpdateResult("APPLIED", null, "配置已更新", applied.getConfigValue());
    }

    @Transactional
    public SysConfig applyApprovedChange(String key, String value, Long requesterId, Long approvalId) {
        SysConfig existing = configMapper.selectByKey(key);
        String group = existing == null ? "custom" : existing.getConfigGroup();
        String description = existing == null ? null : existing.getDescription();
        String type = existing == null ? null : existing.getValueType();
        return applyChange(key, value, group, description, type, requesterId, approvalId);
    }

    @Transactional
    public SysConfig applyChange(String key, String value, String group, String description,
                                 String valueType, Long actorId, Long approvalId) {
        SysConfig existing = configMapper.selectByKey(key);
        String oldValue = existing == null ? null : existing.getConfigValue();
        SysConfig saved;
        if (existing == null) {
            saved = new SysConfig();
            saved.setConfigKey(key);
            saved.setConfigValue(value);
            saved.setValueType(StringUtils.hasText(valueType) ? valueType : inferType(value));
            saved.setConfigGroup(StringUtils.hasText(group) ? group : "custom");
            saved.setDescription(description);
            saved.setVersion(1);
            saved.setUpdatedBy(actorId);
            configMapper.insert(saved);
        } else {
            saved = existing;
            saved.setConfigValue(value);
            saved.setVersion((existing.getVersion() == null ? 0 : existing.getVersion()) + 1);
            saved.setUpdatedBy(actorId);
            if (StringUtils.hasText(valueType)) {
                saved.setValueType(valueType);
            }
            if (StringUtils.hasText(group)) {
                saved.setConfigGroup(group);
            }
            if (StringUtils.hasText(description)) {
                saved.setDescription(description);
            }
            configMapper.updateById(saved);
        }

        SysConfigVersion version = new SysConfigVersion();
        version.setConfigKey(key);
        version.setOldValue(oldValue);
        version.setNewValue(value);
        version.setActorId(actorId);
        version.setApprovalId(approvalId);
        configVersionMapper.insert(version);

        auditService.record("CONFIG", "config", "UPDATE", "CONFIG", saved.getId(),
                Map.of("key", key, "oldValue", maskValue(key, oldValue) == null ? "" : maskValue(key, oldValue)),
                Map.of("key", key, "newValue", maskValue(key, value) == null ? "" : maskValue(key, value)),
                approvalId == null ? null : "审批通过后生效", approvalId);
        saved.setConfigValue(maskValue(key, saved.getConfigValue()));
        return saved;
    }

    public boolean isProtected(String key, SysConfig existing) {
        String group = existing == null ? "custom" : existing.getConfigGroup();
        Set<String> groups = readTokenSet("approval.protectedConfigGroups", DEFAULT_PROTECTED_GROUPS);
        Set<String> keys = readTokenSet("approval.protectedConfigKeys", DEFAULT_PROTECTED_KEYS);
        return groups.contains(group.toLowerCase()) || keys.contains(key.toLowerCase());
    }

    private Set<String> readTokenSet(String configKey, Set<String> defaults) {
        SysConfig config = configMapper.selectByKey(configKey);
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return defaults;
        }
        return java.util.Arrays.stream(config.getConfigValue().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(token -> token.toLowerCase())
                .collect(Collectors.toSet());
    }

    private void maskSensitive(SysConfig config) {
        config.setConfigValue(maskValue(config.getConfigKey(), config.getConfigValue()));
    }

    public String maskValue(String key, String value) {
        if (value == null) {
            return null;
        }
        String lower = key.toLowerCase();
        if (lower.contains("apikey") || lower.contains("secret") || lower.contains("password")) {
            return value.length() <= 4 ? "****" : "****" + value.substring(value.length() - 4);
        }
        return value;
    }

    private String inferType(String value) {
        if (value == null) {
            return "STRING";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "BOOLEAN";
        }
        if (value.matches("-?\\d+")) {
            return "INT";
        }
        if (value.matches("-?\\d+\\.\\d+")) {
            return "DECIMAL";
        }
        return "STRING";
    }
}
