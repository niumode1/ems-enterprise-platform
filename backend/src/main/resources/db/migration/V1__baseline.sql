-- ============================================================
-- P0 baseline schema
-- ============================================================

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    employee_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled TINYINT NOT NULL DEFAULT 0,
    last_login_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_role (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    built_in TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_permission (
    id BIGINT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    module VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_permission_code (code),
    KEY idx_sys_permission_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'SELF',
    scope_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_role (user_id, role_id, scope_type, scope_id),
    KEY idx_sys_user_role_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_role_permission (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id),
    KEY idx_sys_role_permission_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_field_permission (
    id BIGINT PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    role_id BIGINT NOT NULL DEFAULT 0,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL',
    visibility VARCHAR(255) NOT NULL DEFAULT 'ALL',
    mask_rule VARCHAR(32) NOT NULL DEFAULT 'NONE',
    self_edit TINYINT NOT NULL DEFAULT 0,
    exportable TINYINT NOT NULL DEFAULT 0,
    ai_readable TINYINT NOT NULL DEFAULT 1,
    is_sensitive TINYINT NOT NULL DEFAULT 0,
    key_field TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_field_permission (entity_type, field_code, role_id, scope_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_config (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NULL,
    value_type VARCHAR(16) NOT NULL DEFAULT 'STRING',
    config_group VARCHAR(64) NOT NULL DEFAULT 'general',
    description VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 1,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_config_version (
    id BIGINT PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    actor_id BIGINT NULL,
    approval_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_config_version_key (config_key, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_approval_policy (
    id BIGINT PRIMARY KEY,
    policy_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    field_codes TEXT NULL,
    quorum INT NOT NULL DEFAULT 2,
    applicant_included TINYINT NOT NULL DEFAULT 0,
    change_quorum INT NOT NULL DEFAULT 2,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_policy_code (policy_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_approval_request (
    id BIGINT PRIMARY KEY,
    policy_code VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NULL,
    payload_json LONGTEXT NOT NULL,
    reason VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    requester_id BIGINT NOT NULL,
    current_approvals INT NOT NULL DEFAULT 0,
    required_approvals INT NOT NULL DEFAULT 2,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_approval_request_status (status, created_at),
    KEY idx_approval_request_requester (requester_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_approval_action (
    id BIGINT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_approval_action (request_id, approver_id),
    KEY idx_approval_action_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_login_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NULL,
    username VARCHAR(64) NULL,
    result VARCHAR(16) NOT NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_login_log_created (created_at),
    KEY idx_login_log_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employee (
    id BIGINT PRIMARY KEY,
    employee_no VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    gender VARCHAR(16) NULL,
    birth_date DATE NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    id_card_enc VARCHAR(512) NULL,
    address VARCHAR(255) NULL,
    emergency_contact VARCHAR(128) NULL,
    avatar_file_id BIGINT NULL,
    hire_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    primary_org_id BIGINT NULL,
    primary_position_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_employee_no (employee_no),
    KEY idx_employee_name (name),
    KEY idx_employee_phone (phone),
    KEY idx_employee_email (email),
    KEY idx_employee_org (primary_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE org_unit (
    id BIGINT PRIMARY KEY,
    parent_id BIGINT NULL,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NULL,
    type VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT',
    leader_employee_id BIGINT NULL,
    path VARCHAR(500) NULL,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_org_unit_code (code),
    KEY idx_org_unit_parent (parent_id),
    KEY idx_org_unit_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE position (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    level VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_position_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employee_position (
    id BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    org_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    is_primary TINYINT NOT NULL DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_emp_position_emp (employee_id, start_date, end_date),
    KEY idx_emp_position_org (org_id, position_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE reporting_line (
    id BIGINT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    manager_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL DEFAULT 'SOLID',
    is_primary TINYINT NOT NULL DEFAULT 1,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reporting_emp (employee_id),
    KEY idx_reporting_manager (manager_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
    id BIGINT PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    module VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NULL,
    entity_id BIGINT NULL,
    actor_id BIGINT NULL,
    actor_name VARCHAR(64) NULL,
    ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    before_json LONGTEXT NULL,
    after_json LONGTEXT NULL,
    diff_json LONGTEXT NULL,
    reason VARCHAR(500) NULL,
    approval_id BIGINT NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_created (created_at),
    KEY idx_audit_actor (actor_id, created_at),
    KEY idx_audit_entity (entity_type, entity_id, created_at),
    KEY idx_audit_category (category, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE access_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NULL,
    action VARCHAR(32) NOT NULL,
    is_sensitive TINYINT NOT NULL DEFAULT 0,
    ip VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_access_log_user (user_id, created_at),
    KEY idx_access_log_resource (resource_type, resource_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
