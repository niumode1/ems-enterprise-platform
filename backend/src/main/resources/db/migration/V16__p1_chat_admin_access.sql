-- P1 chat admin read access: approval-gated, time-bound, read-only grants.
CREATE TABLE chat_access_request (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    reason VARCHAR(500),
    approval_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    duration_minutes INT NOT NULL,
    starts_at DATETIME(6) NULL,
    expires_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    revoked_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_chat_access_user (requester_id, status),
    KEY idx_chat_access_conversation (conversation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3180, 'chat.adminAccess.mode', 'REQUEST', 'STRING', 'chat', '管理员查看聊天模式：REQUEST/DIRECT/DISABLED'),
(3181, 'chat.adminAccess.durationMinutes', '60', 'INT', 'chat', '管理员查看时长（分钟）'),
(3182, 'chat.adminAccess.maxDurationMinutes', '1440', 'INT', 'chat', '管理员查看最长时长（分钟）'),
(3183, 'chat.adminAccess.requireReason', 'true', 'BOOLEAN', 'chat', '管理员查看必须填写用途');

INSERT INTO sys_approval_policy
(id, policy_code, name, entity_type, field_codes, quorum, applicant_included, change_quorum, enabled)
VALUES
(5005, 'CHAT_ACCESS', '聊天内容查看', 'CHAT_CONVERSATION', '*', 2, 0, 2, 1);

-- Boss and system admin can raise/review requests; permission assignment stays configurable.
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119850 + ROW_NUMBER() OVER (ORDER BY p.id), 1, p.id
FROM sys_permission p
WHERE p.code IN ('chat:access:request', 'chat:access:approve');

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119860 + ROW_NUMBER() OVER (ORDER BY p.id), 2, p.id
FROM sys_permission p
WHERE p.code IN ('chat:access:request', 'chat:access:approve');
