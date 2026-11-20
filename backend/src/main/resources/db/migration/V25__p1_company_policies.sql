-- Company-wide policies/regulations: upload, view, download and AI Q&A.
CREATE TABLE company_policy (
    id BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    category VARCHAR(64) NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    size_bytes BIGINT NULL,
    content_type VARCHAR(128) NULL,
    uploader_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    content_text MEDIUMTEXT NULL,
    content_indexed_at DATETIME NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_company_policy_status (status),
    KEY idx_company_policy_category (category),
    KEY idx_company_policy_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (id, code, name, module, action) VALUES
(2401, 'policy:read', '公司制度查看', 'policy', 'read'),
(2402, 'policy:manage', '公司制度管理', 'policy', 'manage');

-- Every employee-facing role can read company policies.
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 170000 + ROW_NUMBER() OVER (ORDER BY r.id), r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code = 'policy:read'
WHERE r.id IN (1, 2, 3, 4, 5, 6, 7);

-- HR and system administrators maintain the policy library.
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 171000 + ROW_NUMBER() OVER (ORDER BY r.id), r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code = 'policy:manage'
WHERE r.id IN (2, 3);

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3250, 'policy.file.maxMb', '50', 'INT', 'policy', '公司制度文件大小上限（MB）'),
(3251, 'policy.file.extensions', 'pdf,doc,docx,xls,xlsx,txt,md', 'STRING', 'policy', '公司制度允许上传的扩展名，逗号分隔'),
(3252, 'policy.ai.enabled', 'true', 'BOOLEAN', 'policy', '是否允许对公司制度进行AI问答'),
(3253, 'policy.ai.maxDocs', '50', 'INT', 'policy', '单次制度问答最多检索多少份制度'),
(3254, 'policy.ai.maxContextChars', '30000', 'INT', 'policy', '单次制度问答最多送入多少正文字符'),
(3255, 'policy.ai.maxCitations', '5', 'INT', 'policy', '制度问答最多返回几条引用'),
(3256, 'search.policy.enabled', 'true', 'BOOLEAN', 'search', '全局搜索是否包含公司制度');
