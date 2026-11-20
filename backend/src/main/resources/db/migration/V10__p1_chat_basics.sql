-- ============================================================
-- P1: chat basics (direct + group, read state, recall/burn policy)
-- ============================================================

CREATE TABLE chat_conversation (
    id BIGINT PRIMARY KEY,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(200) NULL,
    owner_id BIGINT NOT NULL,
    burn_after_read TINYINT NOT NULL DEFAULT 0,
    anti_recall TINYINT NOT NULL DEFAULT 1,
    retention_days INT NULL,
    max_members INT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_message_at DATETIME(6) NULL,
    last_message_preview VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_chat_conversation_activity (status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_member (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    member_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    muted_until DATETIME(6) NULL,
    last_read_at DATETIME(6) NULL,
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_chat_member (conversation_id, employee_id),
    KEY idx_chat_member_employee (employee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    content TEXT NOT NULL,
    burn_after_read TINYINT NOT NULL DEFAULT 0,
    burn_at DATETIME(6) NULL,
    recalled TINYINT NOT NULL DEFAULT 0,
    recalled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_chat_message_conversation (conversation_id, id),
    KEY idx_chat_message_burn (conversation_id, burn_after_read, burn_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3090, 'chat.direct.enabled', 'true', 'BOOLEAN', 'chat', '是否允许单聊'),
(3091, 'chat.group.enabled', 'true', 'BOOLEAN', 'chat', '是否允许群聊'),
(3092, 'chat.group.manageRoles', 'OWNER', 'STRING', 'chat', '可管理群成员与群设置的角色（OWNER/MEMBER）'),
(3093, 'chat.memberManageRoles', 'OWNER', 'STRING', 'chat', '可添加/移除群成员的角色（OWNER/MEMBER）'),
(3094, 'chat.message.maxLength', '2000', 'INT', 'chat', '单条消息最大字符数'),
(3095, 'chat.recall.windowMinutes', '2', 'INT', 'chat', '允许撤回的时间窗口（分钟，0=不限；防撤回开启时不可撤回）'),
(3096, 'chat.burnAfterRead.default', 'false', 'BOOLEAN', 'chat', '新建会话默认阅后即焚'),
(3097, 'chat.antiRecall.default', 'true', 'BOOLEAN', 'chat', '新建会话默认防撤回'),
(3098, 'chat.history.defaultSize', '30', 'INT', 'chat', '消息分页默认条数'),
(3099, 'chat.notify.membership', 'true', 'BOOLEAN', 'chat', '群成员变更是否发送站内通知'),
(3100, 'chat.history.visibleBeforeJoin', 'true', 'BOOLEAN', 'chat', '新成员是否可查看加入前的历史消息');
