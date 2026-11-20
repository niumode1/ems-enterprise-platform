CREATE TABLE notification_preference (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    muted TINYINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_notification_preference (user_id, notification_type),
    KEY idx_notification_preference_type (notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3220, 'notification.preferences.enabled', 'true', 'BOOLEAN', 'notification', '是否允许员工自行设置通知偏好；关闭后所有通知照常送达'),
(3221, 'notification.preferences.types', 'APPROVAL,TASK,IMPORT,CHAT,RETENTION', 'STRING', 'notification', '允许员工自行开关的通知类型，逗号分隔；未列出的类型（如 SECURITY）始终送达'),
(3222, 'notification.preferences.labels', 'APPROVAL:审批,TASK:任务,IMPORT:花名册,CHAT:聊天,RETENTION:保留期', 'STRING', 'notification', '通知类型显示名称，格式 类型:名称，逗号分隔；未配置时显示类型代码'),
(3223, 'notification.preferences.defaultMuted', '', 'STRING', 'notification', '新员工默认关闭的通知类型，逗号分隔；留空表示默认全部开启'),
(3224, 'notification.preferences.behavior', 'SILENT', 'STRING', 'notification', '关闭某类通知后的行为：SILENT=仍写入站内记录但不实时推送；SUPPRESS=不再生成该类通知');
