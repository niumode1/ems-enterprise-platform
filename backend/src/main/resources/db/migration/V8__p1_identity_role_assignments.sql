-- ============================================================
-- P1: person-first identity and time-bound role assignments
-- One person (employee) has at most one login account; system roles can be
-- combined and are effective only within their configured date range.
-- ============================================================

-- allow multiple historical assignment rows for the same role
ALTER TABLE sys_user_role
    DROP INDEX uk_sys_user_role,
    ADD KEY idx_sys_user_role_scope (user_id, role_id, scope_type, scope_id);

ALTER TABLE sys_user_role
    ADD COLUMN start_date DATE NULL AFTER scope_id,
    ADD COLUMN end_date DATE NULL AFTER start_date,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER end_date,
    ADD COLUMN note VARCHAR(200) NULL AFTER status,
    ADD COLUMN granted_by BIGINT NULL AFTER note,
    ADD COLUMN revoked_at DATETIME NULL AFTER granted_by,
    ADD KEY idx_sys_user_role_effective (user_id, status, start_date, end_date);

UPDATE sys_user_role SET start_date = DATE(created_at) WHERE start_date IS NULL;

-- one account per employee; NULL is reserved for deployment/recovery accounts
ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_employee (employee_id);

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3066, 'account.requireEmployeeLink', 'true', 'BOOLEAN', 'security', '新建账号是否必须关联员工档案（紧急恢复/部署账号可临时关闭）');
