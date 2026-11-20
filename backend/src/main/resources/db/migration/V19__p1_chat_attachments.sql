-- ============================================================
-- P1: chat image/file messages
-- Attachments are uploaded first, then bound to exactly one message.
-- Policies (enablement, per-conversation-type switches, size, extension
-- whitelist, burn-after-read compatibility, orphan retention) are all
-- configuration-driven so each company can set its own rules.
-- ============================================================

ALTER TABLE chat_message ADD COLUMN attachment_id BIGINT NULL AFTER content;

CREATE TABLE chat_attachment (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    message_id BIGINT NULL,
    kind VARCHAR(16) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes BIGINT NOT NULL DEFAULT 0,
    storage_key VARCHAR(255) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_chat_attachment_conversation (conversation_id, id),
    KEY idx_chat_attachment_message (message_id),
    KEY idx_chat_attachment_orphan (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3210, 'chat.attachment.enabled', 'true', 'BOOLEAN', 'chat', '是否允许发送图片/文件消息'),
(3211, 'chat.attachment.direct.enabled', 'true', 'BOOLEAN', 'chat', '单聊是否允许图片/文件消息'),
(3212, 'chat.attachment.group.enabled', 'true', 'BOOLEAN', 'chat', '群聊是否允许图片/文件消息'),
(3213, 'chat.attachment.burnAfterRead.enabled', 'true', 'BOOLEAN', 'chat', '阅后即焚会话是否允许发送附件'),
(3214, 'chat.file.imageMaxMb', '10', 'INT', 'chat', '聊天图片最大体积（MB，0=不限制；文件上限复用 chat.file.maxMb）'),
(3215, 'chat.attachment.allowedExtensions', 'pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,csv,json,xml,zip,rar,7z,png,jpg,jpeg,gif,webp,bmp', 'STRING', 'chat', '允许的文件扩展名，逗号分隔；* 表示不限制'),
(3216, 'chat.attachment.imageExtensions', 'png,jpg,jpeg,gif,webp,bmp', 'STRING', 'chat', '按图片展示与预览的扩展名，逗号分隔'),
(3217, 'chat.attachment.orphanHours', '24', 'INT', 'chat', '未发送附件的保留小时数，过期由保留期清理任务删除');
