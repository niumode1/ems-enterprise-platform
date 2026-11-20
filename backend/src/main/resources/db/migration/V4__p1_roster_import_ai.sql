-- ============================================================
-- P1: AI roster import, employee confirmation, AI provider config
-- ============================================================

CREATE TABLE import_batch (
    id BIGINT PRIMARY KEY,
    batch_no VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    format VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'UPLOADED',
    total_rows INT NOT NULL DEFAULT 0,
    parsed_rows INT NOT NULL DEFAULT 0,
    low_confidence_rows INT NOT NULL DEFAULT 0,
    pending_confirm_rows INT NOT NULL DEFAULT 0,
    confirmed_rows INT NOT NULL DEFAULT 0,
    rejected_rows INT NOT NULL DEFAULT 0,
    committed_rows INT NOT NULL DEFAULT 0,
    ai_used TINYINT NOT NULL DEFAULT 0,
    ai_model VARCHAR(128) NULL,
    error_message VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    committed_at DATETIME NULL,
    UNIQUE KEY uk_import_batch_no (batch_no),
    KEY idx_import_batch_created (created_by, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE import_row (
    id BIGINT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    raw_json TEXT NULL,
    parsed_json TEXT NULL,
    final_json TEXT NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    status VARCHAR(24) NOT NULL DEFAULT 'PARSED',
    matched_employee_id BIGINT NULL,
    confirm_user_id BIGINT NULL,
    reject_reason VARCHAR(500) NULL,
    committed_employee_id BIGINT NULL,
    before_json TEXT NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_import_row_batch (batch_id, row_no),
    KEY idx_import_row_confirm (confirm_user_id, status),
    KEY idx_import_row_employee (matched_employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_provider_config (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    provider_type VARCHAR(32) NOT NULL DEFAULT 'OPENAI_COMPATIBLE',
    base_url VARCHAR(500) NOT NULL,
    api_key_enc TEXT NULL,
    model VARCHAR(128) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    is_default TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_query_log (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NULL,
    feature VARCHAR(32) NOT NULL,
    question_summary VARCHAR(500) NULL,
    model VARCHAR(128) NULL,
    success TINYINT NOT NULL DEFAULT 1,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    answer_summary VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_query_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3050, 'import.employeeEditableFields', 'name,phone,email,address,emergencyContact', 'STRING', 'import', '员工确认时可编辑字段'),
(3051, 'import.requireEmployeeConfirm', 'true', 'BOOLEAN', 'import', '已有员工数据是否需本人确认'),
(3052, 'import.roster.useAi', 'true', 'BOOLEAN', 'import', '花名册解析是否调用已配置的AI补全列映射');

INSERT INTO sys_approval_policy
(id, policy_code, name, entity_type, field_codes, quorum, applicant_included, change_quorum, enabled)
VALUES
(5003, 'IMPORT_COMMIT', '花名册提交', 'IMPORT', '*', 2, 0, 2, 1);
