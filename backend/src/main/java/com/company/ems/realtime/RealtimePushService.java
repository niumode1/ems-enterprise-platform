package com.company.ems.realtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysConfigMapper;
import com.company.ems.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Central realtime entry point. Policies are read from the config center on each
 * push so a company can switch channels off without a restart or code change.
 * Events are emitted only after the surrounding transaction commits, so clients
 * never see a message whose database write was rolled back.
 */
@Service
public class RealtimePushService {

    private static final String TYPE_NOTIFICATION = "NOTIFICATION";
    private static final String CHAT_PREFIX = "CHAT_";

    private final RealtimeWebSocketHandler handler;
    private final SysConfigMapper configMapper;
    private final SysUserMapper userMapper;

    public RealtimePushService(RealtimeWebSocketHandler handler,
                               SysConfigMapper configMapper,
                               SysUserMapper userMapper) {
        this.handler = handler;
        this.configMapper = configMapper;
        this.userMapper = userMapper;
    }

    public RealtimeCapabilities capabilities() {
        boolean enabled = boolConfig("realtime.enabled", true);
        return new RealtimeCapabilities(
                enabled,
                enabled && boolConfig("realtime.chat.enabled", true),
                enabled && boolConfig("realtime.notification.enabled", true),
                Math.max(0, intConfig("realtime.heartbeatSeconds", 25)),
                Math.max(0, intConfig("realtime.maxConnectionsPerUser", 5)));
    }

    public void pushToUser(Long userId, String type, Object data) {
        if (userId == null) {
            return;
        }
        pushToUsers(List.of(userId), type, data);
    }

    public void pushToUsers(Collection<Long> userIds, String type, Object data) {
        if (userIds == null || userIds.isEmpty() || !enabledFor(type)) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>();
        userIds.stream().filter(Objects::nonNull).forEach(distinct::add);
        if (distinct.isEmpty()) {
            return;
        }
        RealtimeEvent event = RealtimeEvent.of(type, data);
        afterCommit(() -> distinct.forEach(userId -> handler.send(userId, event)));
    }

    /**
     * Chat members are stored by employee id; resolve them to active accounts so a
     * disabled account never receives realtime data.
     */
    public void pushToEmployees(Collection<Long> employeeIds, String type, Object data) {
        if (employeeIds == null || employeeIds.isEmpty() || !enabledFor(type)) {
            return;
        }
        List<Long> distinct = employeeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return;
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getEmployeeId, distinct)
                .eq(SysUser::getStatus, "ACTIVE"));
        pushToUsers(users.stream().map(SysUser::getId).toList(), type, data);
    }

    public boolean isConnected(Long userId) {
        return handler.connectionCount(userId) > 0;
    }

    private boolean enabledFor(String type) {
        if (type == null || !boolConfig("realtime.enabled", true)) {
            return false;
        }
        if (type.startsWith(CHAT_PREFIX)) {
            return boolConfig("realtime.chat.enabled", true);
        }
        if (TYPE_NOTIFICATION.equals(type)) {
            return boolConfig("realtime.notification.enabled", true);
        }
        return true;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private boolean boolConfig(String key, boolean defaultValue) {
        String value = stringConfig(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private int intConfig(String key, int defaultValue) {
        String value = stringConfig(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String stringConfig(String key) {
        SysConfig config = configMapper.selectByKey(key);
        return config == null ? null : config.getConfigValue();
    }
}
