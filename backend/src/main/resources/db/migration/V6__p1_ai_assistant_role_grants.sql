-- ============================================================
-- P1: system administrators may use the AI assistant.
-- Field visibility and masking are still enforced per employee/role,
-- so this does not grant access to sensitive fields.
-- ============================================================

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119901, 2, id FROM sys_permission WHERE code = 'ai:use';

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119902, 2, id FROM sys_permission WHERE code = 'ai:help';

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 119903, 2, id FROM sys_permission WHERE code = 'ai:people';
