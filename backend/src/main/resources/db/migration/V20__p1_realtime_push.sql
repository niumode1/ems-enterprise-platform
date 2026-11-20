-- WebSocket realtime push. Every switch is company-configurable; when disabled the
-- frontend falls back to polling without any code change.
INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3300, 'realtime.enabled', 'true', 'BOOLEAN', 'realtime', '是否启用 WebSocket 实时推送；关闭后前端自动退回轮询'),
(3301, 'realtime.chat.enabled', 'true', 'BOOLEAN', 'realtime', '是否实时推送聊天消息与撤回事件'),
(3302, 'realtime.notification.enabled', 'true', 'BOOLEAN', 'realtime', '是否实时推送站内通知'),
(3303, 'realtime.heartbeatSeconds', '25', 'INT', 'realtime', '客户端心跳间隔（秒），0=不发送心跳'),
(3304, 'realtime.maxConnectionsPerUser', '5', 'INT', 'realtime', '每个账号允许的最大实时连接数，0=不限制');
