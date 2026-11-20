-- P1 employee-controlled field visibility (privacy toggles for ordinary colleagues).
CREATE TABLE employee_field_visibility (
    id BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    visible TINYINT NOT NULL DEFAULT 1,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_employee_field_visibility (employee_id, field_code),
    KEY idx_employee_field_visibility (employee_id, visible)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3200, 'employee.selfVisibility.enabled', 'true', 'BOOLEAN', 'employee', '员工可控制本人字段对普通同事可见性'),
(3201, 'employee.selfVisibility.fields', 'phone,email,address,emergencyContact', 'STRING', 'employee', '员工可隐藏的字段（逗号分隔）'),
(3202, 'employee.selfVisibility.exemptPermissions', 'employee:sensitive:read', 'STRING', 'employee', '不受本人隐藏设置影响的权限（逗号分隔）');
