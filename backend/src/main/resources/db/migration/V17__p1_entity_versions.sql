-- P1 entity version snapshots + approval-gated rollback (employee first).
CREATE TABLE entity_version (
    id BIGINT PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    diff_json LONGTEXT NULL,
    operator_id BIGINT NULL,
    approval_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_entity_version (entity_type, entity_id, version_no),
    KEY idx_entity_version_lookup (entity_type, entity_id, created_at),
    KEY idx_entity_version_operator (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3190, 'audit.version.enabled', 'true', 'BOOLEAN', 'audit', '实体版本快照总开关'),
(3191, 'audit.version.entities', 'EMPLOYEE', 'STRING', 'audit', '启用版本快照的实体类型（逗号分隔）'),
(3192, 'audit.version.maxKept', '0', 'INT', 'audit', '每实体保留版本数（0=不限）'),
(3193, 'audit.version.rollbackApproval', 'true', 'BOOLEAN', 'audit', '版本回滚是否需要审批'),
(3194, 'audit.version.rollbackApproverCount', '2', 'INT', 'audit', '版本回滚所需同意人数'),
(3195, 'audit.version.sensitiveFields', 'phone,email,birthDate,address,emergencyContact', 'STRING', 'audit', '版本详情默认脱敏字段'),
(3196, 'audit.version.employee.rollbackFields', 'name,gender,birthDate,phone,email,address,emergencyContact,hireDate,status,org,position,manager', 'STRING', 'audit', '员工版本可回滚字段'),
(3197, 'audit.version.rollbackRequireReason', 'true', 'BOOLEAN', 'audit', '版本回滚必须填写原因');

INSERT INTO sys_approval_policy
(id, policy_code, name, entity_type, field_codes, quorum, applicant_included, change_quorum, enabled)
VALUES
(5006, 'ENTITY_ROLLBACK', '实体版本回滚', 'ENTITY_VERSION', '*', 2, 0, 2, 1);

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119870, 1, id FROM sys_permission WHERE code = 'audit:rollback';

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119880, 2, id FROM sys_permission WHERE code = 'audit:rollback';

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119890, 3, id FROM sys_permission WHERE code = 'audit:rollback';

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119891, 3, id FROM sys_permission WHERE code = 'audit:version:read';
