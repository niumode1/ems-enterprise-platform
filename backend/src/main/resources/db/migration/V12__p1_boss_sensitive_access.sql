-- P1: boss sensitive-field access by work need + approval.
-- A grant never expands which fields a role may see; it only removes masking for
-- fields the role can already view, until the configured duration expires.
CREATE TABLE boss_access_grant (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    employee_id BIGINT NULL COMMENT 'NULL=全部员工（按配置允许时）',
    reason VARCHAR(500) NULL,
    approval_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED/REPLACED',
    starts_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoked_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_boss_grant_user (user_id, status, expires_at),
    KEY idx_boss_grant_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing keys (boss.access.mode / approverCount / durationMinutes / requireReason)
-- are seeded in V2 and stay company-editable; only add what is new.
INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3128, 'boss.access.enabled', 'true', 'BOOLEAN', 'boss', '是否启用老板敏感信息访问机制'),
(3130, 'boss.access.allowAllEmployees', 'false', 'BOOLEAN', 'boss', '是否允许老板一次申请全部员工的敏感字段'),
(3131, 'boss.access.maxDurationMinutes', '240', 'INT', 'boss', '单次敏感访问最长有效分钟数');

-- Default to multi-approval; each company can change this value later.
UPDATE sys_config SET config_value = '2' WHERE config_key = 'boss.access.approverCount';

INSERT INTO sys_approval_policy
(id, policy_code, name, entity_type, field_codes, quorum, applicant_included, change_quorum, enabled)
VALUES
(5004, 'BOSS_ACCESS', '老板敏感信息访问', 'EMPLOYEE', '', 1, 0, 1, 1);
