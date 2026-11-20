-- ============================================================
-- P0: custom attributes (no DDL), projects/members, documents,
--     document-level ACL, in-app notifications
-- ============================================================

CREATE TABLE custom_field_def (
    id BIGINT PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    field_type VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    options_json TEXT NULL,
    timeline TINYINT NOT NULL DEFAULT 0,
    visibility VARCHAR(255) NOT NULL DEFAULT 'ALL',
    mask_rule VARCHAR(32) NOT NULL DEFAULT 'NONE',
    is_sensitive TINYINT NOT NULL DEFAULT 0,
    self_editable TINYINT NOT NULL DEFAULT 0,
    required TINYINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_custom_field_def (entity_type, field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE custom_field_value (
    id BIGINT PRIMARY KEY,
    def_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id BIGINT NOT NULL,
    value_text TEXT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_custom_value_entity (entity_type, entity_id, def_id, start_date),
    KEY idx_custom_value_def (def_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    code VARCHAR(64) NULL,
    description TEXT NULL,
    owner_id BIGINT NOT NULL,
    org_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    start_date DATE NULL,
    end_date DATE NULL,
    retention_days INT NULL,
    download_policy VARCHAR(16) NOT NULL DEFAULT 'ALL',
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_code (code),
    KEY idx_project_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_member (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    member_role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_member (project_id, employee_id),
    KEY idx_project_member_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE doc_folder (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    name VARCHAR(128) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_doc_folder_project (project_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    folder_id BIGINT NULL,
    name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NULL,
    storage_key VARCHAR(500) NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    content_type VARCHAR(128) NULL,
    uploader_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_document_project (project_id, folder_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE document_permission (
    id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    can_view TINYINT NOT NULL DEFAULT 1,
    can_download TINYINT NOT NULL DEFAULT 0,
    can_delete TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_permission (document_id, employee_id),
    KEY idx_document_permission_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NULL,
    link VARCHAR(255) NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notification_user (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
