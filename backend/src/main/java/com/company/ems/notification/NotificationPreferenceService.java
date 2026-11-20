package com.company.ems.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.notification.entity.NotificationPreference;
import com.company.ems.notification.mapper.NotificationPreferenceMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-user notification preferences. The company decides which notification types
 * may be muted, what they are called, which are muted by default, and whether a
 * muted notification is still stored (SILENT) or not created at all (SUPPRESS).
 * Types outside the configured list (for example SECURITY) are always delivered.
 */
@Service
public class NotificationPreferenceService {

    public static final String BEHAVIOR_SILENT = "SILENT";
    public static final String BEHAVIOR_SUPPRESS = "SUPPRESS";

    private final NotificationPreferenceMapper preferenceMapper;
    private final SysConfigMapper configMapper;
    private final AuditService auditService;

    public NotificationPreferenceService(NotificationPreferenceMapper preferenceMapper,
                                         SysConfigMapper configMapper,
                                         AuditService auditService) {
        this.preferenceMapper = preferenceMapper;
        this.configMapper = configMapper;
        this.auditService = auditService;
    }

    /**
     * @param suppress skip creating the notification row entirely
     * @param realtime send the notification over WebSocket
     */
    public record DeliveryDecision(boolean suppress, boolean realtime) {
        public static DeliveryDecision deliver() {
            return new DeliveryDecision(false, true);
        }

        public static DeliveryDecision silent() {
            return new DeliveryDecision(false, false);
        }

        public static DeliveryDecision suppressAll() {
            return new DeliveryDecision(true, false);
        }
    }

    public DeliveryDecision decide(Long userId, String notificationType) {
        if (userId == null || !enabled()) {
            return DeliveryDecision.deliver();
        }
        String type = normalize(notificationType);
        Set<String> mutableTypes = mutableTypes();
        if (type == null || !mutableTypes.contains(type)) {
            return DeliveryDecision.deliver();
        }
        if (!effectiveMuted(userId, type)) {
            return DeliveryDecision.deliver();
        }
        return BEHAVIOR_SUPPRESS.equals(behavior())
                ? DeliveryDecision.suppressAll()
                : DeliveryDecision.silent();
    }

    public NotificationPreferenceDtos.PreferencesView view(LoginUser user) {
        List<NotificationPreferenceDtos.PreferenceItem> items = new ArrayList<>();
        Map<String, String> labels = labels();
        for (String type : mutableTypes()) {
            items.add(new NotificationPreferenceDtos.PreferenceItem(
                    type, labels.getOrDefault(type, type), effectiveMuted(user.userId(), type)));
        }
        return new NotificationPreferenceDtos.PreferencesView(
                enabled(), behavior(), items);
    }

    @Transactional
    public NotificationPreferenceDtos.PreferencesView update(
            NotificationPreferenceDtos.UpdateRequest request, LoginUser user) {
        if (request == null || request.getPreferences() == null) {
            throw new BusinessException(40001, "请提交通知偏好");
        }
        Set<String> mutableTypes = mutableTypes();
        Map<String, Boolean> before = new LinkedHashMap<>();
        for (NotificationPreferenceDtos.PreferenceItem item : view(user).preferences()) {
            before.put(item.type(), item.muted());
        }
        for (NotificationPreferenceDtos.Setting setting : request.getPreferences()) {
            if (setting == null || !StringUtils.hasText(setting.getType())
                    || setting.getMuted() == null) {
                throw new BusinessException(40001, "通知偏好参数不完整");
            }
            String type = normalize(setting.getType());
            if (!mutableTypes.contains(type)) {
                throw new BusinessException(40001, "通知类型 " + setting.getType() + " 不支持偏好设置");
            }
            upsert(user.userId(), type, setting.getMuted(), LocalDateTime.now());
        }
        NotificationPreferenceDtos.PreferencesView after = view(user);
        Map<String, Boolean> afterMap = new LinkedHashMap<>();
        for (NotificationPreferenceDtos.PreferenceItem item : after.preferences()) {
            afterMap.put(item.type(), item.muted());
        }
        auditService.record("PERSONAL", "notification", "PREFERENCE_UPDATE",
                "NOTIFICATION_PREFERENCE", user.userId(), before, afterMap, null, null);
        return after;
    }

    public boolean enabled() {
        return boolConfig("notification.preferences.enabled", true);
    }

    public String behavior() {
        String value = stringConfig("notification.preferences.behavior", BEHAVIOR_SILENT);
        return BEHAVIOR_SUPPRESS.equalsIgnoreCase(value) ? BEHAVIOR_SUPPRESS : BEHAVIOR_SILENT;
    }

    private boolean effectiveMuted(Long userId, String type) {
        NotificationPreference preference = preferenceMapper.selectOne(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId)
                        .eq(NotificationPreference::getNotificationType, type)
                        .last("LIMIT 1"));
        if (preference != null) {
            return Boolean.TRUE.equals(preference.getMuted());
        }
        return defaultMuted().contains(type);
    }

    private void upsert(Long userId, String type, boolean muted, LocalDateTime now) {
        NotificationPreference preference = preferenceMapper.selectOne(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId)
                        .eq(NotificationPreference::getNotificationType, type)
                        .last("LIMIT 1"));
        if (preference == null) {
            preference = new NotificationPreference();
            preference.setUserId(userId);
            preference.setNotificationType(type);
            preference.setMuted(muted);
            preference.setUpdatedAt(now);
            preferenceMapper.insert(preference);
        } else {
            preference.setMuted(muted);
            preference.setUpdatedAt(now);
            preferenceMapper.updateById(preference);
        }
    }

    private Set<String> mutableTypes() {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String value : split(stringConfig("notification.preferences.types", ""))) {
            String type = normalize(value);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private Set<String> defaultMuted() {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String value : split(stringConfig("notification.preferences.defaultMuted", ""))) {
            String type = normalize(value);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (String entry : split(stringConfig("notification.preferences.labels", ""))) {
            String[] parts = entry.split(":", 2);
            String type = normalize(parts[0]);
            if (type != null) {
                labels.put(type, parts.length > 1 && StringUtils.hasText(parts[1])
                        ? parts[1].trim() : type);
            }
        }
        return labels;
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private boolean boolConfig(String key, boolean defaultValue) {
        String value = stringConfig(key, null);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private String stringConfig(String key, String defaultValue) {
        SysConfig config = configMapper.selectByKey(key);
        return config == null || config.getConfigValue() == null
                ? defaultValue : config.getConfigValue();
    }
}
