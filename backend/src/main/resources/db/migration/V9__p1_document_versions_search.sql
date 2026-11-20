-- ============================================================
-- P1: document versions (history, rollback) and content search
-- ============================================================

ALTER TABLE document
    ADD COLUMN current_version INT NOT NULL DEFAULT 1 AFTER status,
    ADD COLUMN content_text MEDIUMTEXT NULL AFTER current_version,
    ADD COLUMN content_indexed_at DATETIME NULL AFTER content_text;

CREATE TABLE document_version (
    id BIGINT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    content_type VARCHAR(128) NULL,
    content_text MEDIUMTEXT NULL,
    uploader_id BIGINT NOT NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ARCHIVED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_version (document_id, version_no),
    KEY idx_document_version_doc (document_id, status, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing documents become version 1 so history and rollback work retroactively.
INSERT INTO document_version (id, document_id, version_no, storage_key, name, original_name,
                              size_bytes, content_type, uploader_id, note, status, created_at)
SELECT -d.id, d.id, 1, d.storage_key, d.name, d.original_name,
       d.size_bytes, d.content_type, d.uploader_id, '历史文档补录', 'CURRENT', d.created_at
FROM document d
WHERE NOT EXISTS (SELECT 1 FROM document_version v WHERE v.document_id = d.id);

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3070, 'doc.version.enabled', 'true', 'BOOLEAN', 'doc', '是否启用文档版本历史'),
(3071, 'doc.version.uploadRoles', 'OWNER,ADMIN,UPLOADER', 'STRING', 'doc', '可上传文档新版本的项目角色（UPLOADER=当前文件上传人）'),
(3072, 'doc.version.rollbackRoles', 'OWNER,ADMIN,UPLOADER', 'STRING', 'doc', '可回滚文档版本的项目角色（UPLOADER=当前文件上传人）'),
(3073, 'doc.version.viewRoles', 'OWNER,ADMIN,UPLOADER,MEMBER,VIEWER', 'STRING', 'doc', '可查看版本历史的项目角色'),
(3074, 'doc.version.maxKept', '0', 'INT', 'doc', '每个文档保留的版本数上限（0=不限）'),
(3075, 'doc.search.enabled', 'true', 'BOOLEAN', 'doc', '是否启用文档搜索'),
(3076, 'doc.search.maxResults', '50', 'INT', 'doc', '文档搜索默认返回条数'),
(3077, 'doc.search.snippetLength', '120', 'INT', 'doc', '搜索结果摘要长度'),
(3078, 'doc.search.contentExtensions', 'txt,md,csv,json,xml,html,htm,log,docx,xlsx', 'STRING', 'doc', '可提取正文的扩展名（逗号分隔，PDF 暂不支持）'),
(3079, 'doc.search.extractMaxChars', '200000', 'INT', 'doc', '单文档正文索引最大字符数'),
(3080, 'doc.search.extractMaxMb', '20', 'INT', 'doc', '超过该大小的文件不提取正文（MB）');
