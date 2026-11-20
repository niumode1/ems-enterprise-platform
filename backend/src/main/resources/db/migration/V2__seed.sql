-- ============================================================
-- Seed roles, permissions, default configs, field policies
-- ============================================================

INSERT INTO sys_role (id, code, name, built_in) VALUES
(1, 'BOSS', '老板', 1),
(2, 'SYS_ADMIN', '系统管理员', 1),
(3, 'HR_ADMIN', 'HR管理员', 1),
(4, 'DEPT_LEADER', '行政组长', 1),
(5, 'PROJECT_LEADER', '项目组长', 1),
(6, 'EMPLOYEE', '普通员工', 1),
(7, 'AUDITOR', '审计员', 1);

INSERT INTO sys_permission (id, code, name, module, action) VALUES
(1001, 'user:read', '账号查看', 'user', 'read'),
(1002, 'user:create', '账号创建', 'user', 'create'),
(1003, 'user:update', '账号修改', 'user', 'update'),
(1004, 'user:disable', '账号禁用', 'user', 'disable'),
(1005, 'user:role:assign', '角色分配', 'user', 'role:assign'),
(1006, 'role:read', '角色查看', 'role', 'read'),
(1007, 'permission:read', '权限点查看', 'permission', 'read'),
(1008, 'field:read', '字段权限查看', 'field', 'read'),
(1009, 'field:update', '字段权限修改', 'field', 'update'),

(1101, 'employee:read', '员工查看', 'employee', 'read'),
(1102, 'employee:create', '员工新增', 'employee', 'create'),
(1103, 'employee:update', '员工修改', 'employee', 'update'),
(1104, 'employee:archive', '员工归档', 'employee', 'archive'),
(1105, 'employee:position:update', '岗位维护', 'employee', 'position:update'),
(1106, 'employee:reporting:update', '汇报关系维护', 'employee', 'reporting:update'),
(1107, 'employee:search', '员工搜索', 'employee', 'search'),
(1108, 'employee:sensitive:request', '敏感信息申请', 'employee', 'sensitive:request'),
(1109, 'employee:sensitive:read', '敏感信息查看', 'employee', 'sensitive:read'),

(1201, 'org:read', '组织查看', 'org', 'read'),
(1202, 'org:create', '组织新增', 'org', 'create'),
(1203, 'org:update', '组织修改', 'org', 'update'),
(1204, 'org:disable', '组织停用', 'org', 'disable'),

(1301, 'attr:read', '属性定义查看', 'attr', 'read'),
(1302, 'attr:create', '属性定义新增', 'attr', 'create'),
(1303, 'attr:update', '属性定义修改', 'attr', 'update'),
(1304, 'attr:disable', '属性定义停用', 'attr', 'disable'),
(1305, 'attr:value:read', '属性值查看', 'attr', 'value:read'),
(1306, 'attr:value:update', '属性值修改', 'attr', 'value:update'),

(1401, 'project:read', '项目查看', 'project', 'read'),
(1402, 'project:create', '项目创建', 'project', 'create'),
(1403, 'project:update', '项目修改', 'project', 'update'),
(1404, 'project:delete', '项目删除', 'project', 'delete'),
(1405, 'project:member:manage', '项目成员管理', 'project', 'member:manage'),

(1501, 'task:read', '任务查看', 'task', 'read'),
(1502, 'task:create', '任务创建', 'task', 'create'),
(1503, 'task:update', '任务修改', 'task', 'update'),
(1504, 'task:delete', '任务删除', 'task', 'delete'),
(1505, 'task:assign', '任务分配', 'task', 'assign'),
(1506, 'task:comment', '任务评论', 'task', 'comment'),

(1601, 'milestone:read', '里程碑查看', 'milestone', 'read'),
(1602, 'milestone:create', '里程碑创建', 'milestone', 'create'),
(1603, 'milestone:update', '里程碑修改', 'milestone', 'update'),
(1604, 'milestone:delete', '里程碑删除', 'milestone', 'delete'),
(1605, 'project:progress:update', '项目进度更新', 'project', 'progress:update'),

(1701, 'doc:read', '文档查看', 'doc', 'read'),
(1702, 'doc:upload', '文档上传', 'doc', 'upload'),
(1703, 'doc:download', '文档下载', 'doc', 'download'),
(1704, 'doc:version:upload', '文档新版本', 'doc', 'version:upload'),
(1705, 'doc:acl:manage', '文档授权', 'doc', 'acl:manage'),
(1706, 'doc:delete', '文档删除', 'doc', 'delete'),
(1707, 'doc:search', '文档搜索', 'doc', 'search'),

(1801, 'chat:use', '聊天使用', 'chat', 'use'),
(1802, 'chat:member', '会话成员', 'chat', 'member'),
(1803, 'chat:member:manage', '会话成员管理', 'chat', 'member:manage'),
(1804, 'chat:group:manage', '群聊管理', 'chat', 'group:manage'),
(1805, 'chat:access:request', '聊天内容查看申请', 'chat', 'access:request'),
(1806, 'chat:access:approve', '聊天内容查看审批', 'chat', 'access:approve'),

(1901, 'ai:use', 'AI使用', 'ai', 'use'),
(1902, 'ai:roster', '花名册解析', 'ai', 'roster'),
(1903, 'ai:doc', '文档问答', 'ai', 'doc'),
(1904, 'ai:people', '人员问答', 'ai', 'people'),
(1905, 'ai:help', '操作指引', 'ai', 'help'),
(1906, 'ai:config:read', 'AI配置查看', 'ai', 'config:read'),
(1907, 'ai:config:update', 'AI配置修改', 'ai', 'config:update'),
(1908, 'ai:log:read', 'AI日志查看', 'ai', 'log:read'),

(2001, 'audit:read', '审计日志查看', 'audit', 'read'),
(2002, 'audit:sensitive:read', '敏感访问日志查看', 'audit', 'sensitive:read'),
(2003, 'audit:version:read', '版本查看', 'audit', 'version:read'),
(2004, 'audit:rollback', '版本回滚', 'audit', 'rollback'),

(2101, 'config:read', '配置查看', 'config', 'read'),
(2102, 'config:update', '配置修改', 'config', 'update'),
(2103, 'config:approval:update', '审批配置修改', 'config', 'approval:update'),
(2104, 'config:recovery', '紧急恢复', 'config', 'recovery'),

(2201, 'import:upload', '导入上传', 'import', 'upload'),
(2202, 'import:read', '导入查看', 'import', 'read'),
(2203, 'import:parse', '导入解析', 'import', 'parse'),
(2204, 'import:review', '导入校对', 'import', 'review'),
(2205, 'import:push', '导入推送', 'import', 'push'),
(2206, 'import:confirm', '导入确认', 'import', 'confirm'),
(2207, 'import:commit', '导入提交', 'import', 'commit'),
(2208, 'import:rollback', '导入回滚', 'import', 'rollback'),

(2301, 'approval:read', '审批查看', 'approval', 'read'),
(2302, 'approval:approve', '审批操作', 'approval', 'approve'),
(2303, 'notification:read', '通知查看', 'notification', 'read');

-- BOSS
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 100000 + ROW_NUMBER() OVER (ORDER BY p.id), 1, p.id
FROM sys_permission p
WHERE p.code IN (
    'employee:read','employee:search','employee:sensitive:request','org:read',
    'project:read','task:read','milestone:read','doc:read','doc:download','doc:search',
    'chat:use','ai:use','ai:doc','ai:people','ai:help',
    'audit:read','audit:sensitive:read','audit:version:read',
    'approval:read','approval:approve','config:read','notification:read','import:read'
);

-- SYS_ADMIN
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 110000 + ROW_NUMBER() OVER (ORDER BY p.id), 2, p.id
FROM sys_permission p
WHERE p.code IN (
    'user:read','user:create','user:update','user:disable','user:role:assign',
    'role:read','permission:read','field:read','field:update',
    'config:read','config:update','config:approval:update','config:recovery',
    'ai:config:read','ai:config:update','ai:log:read',
    'audit:read','audit:version:read','approval:read','approval:approve',
    'employee:read','org:read','project:read','doc:read','notification:read','import:read'
);

-- HR_ADMIN
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 120000 + ROW_NUMBER() OVER (ORDER BY p.id), 3, p.id
FROM sys_permission p
WHERE p.code IN (
    'employee:read','employee:create','employee:update','employee:archive',
    'employee:position:update','employee:reporting:update','employee:search','employee:sensitive:read',
    'org:read','org:create','org:update','org:disable',
    'attr:read','attr:create','attr:update','attr:disable','attr:value:read','attr:value:update',
    'import:upload','import:read','import:parse','import:review','import:push','import:confirm','import:commit','import:rollback',
    'approval:read','approval:approve','audit:read','project:read','doc:read',
    'chat:use','ai:use','ai:doc','ai:people','ai:help','config:read','notification:read'
);

-- DEPT_LEADER
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 130000 + ROW_NUMBER() OVER (ORDER BY p.id), 4, p.id
FROM sys_permission p
WHERE p.code IN (
    'employee:read','employee:search','employee:update','org:read',
    'project:read','task:read','doc:read','doc:download','chat:use',
    'ai:use','ai:people','ai:help','approval:read','notification:read'
);

-- PROJECT_LEADER
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 140000 + ROW_NUMBER() OVER (ORDER BY p.id), 5, p.id
FROM sys_permission p
WHERE p.code IN (
    'project:read','project:create','project:update','project:delete','project:member:manage',
    'task:read','task:create','task:update','task:delete','task:assign','task:comment',
    'milestone:read','milestone:create','milestone:update','milestone:delete',
    'project:progress:update','doc:read','doc:upload','doc:download','doc:version:upload',
    'doc:acl:manage','doc:delete','doc:search','chat:use','chat:member','chat:member:manage',
    'chat:group:manage','chat:access:request','ai:use','ai:doc','ai:help',
    'employee:read','org:read','notification:read','approval:read'
);

-- EMPLOYEE
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 150000 + ROW_NUMBER() OVER (ORDER BY p.id), 6, p.id
FROM sys_permission p
WHERE p.code IN (
    'employee:read','employee:search','org:read','project:read','task:read','task:update','task:comment',
    'milestone:read','doc:read','doc:download','doc:search','chat:use','chat:member','chat:access:request',
    'ai:use','ai:doc','ai:people','ai:help','approval:read','notification:read','import:confirm'
);

-- AUDITOR
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 160000 + ROW_NUMBER() OVER (ORDER BY p.id), 7, p.id
FROM sys_permission p
WHERE p.code IN (
    'audit:read','audit:sensitive:read','audit:version:read','approval:read',
    'employee:read','org:read','project:read','doc:read','notification:read'
);

-- Default configs
INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3001, 'init.completed', 'false', 'BOOLEAN', 'init', '是否完成初始化'),
(3002, 'company.name', '示例公司', 'STRING', 'init', '公司名称'),
(3003, 'security.password.minLength', '8', 'INT', 'security', '密码最小长度'),
(3004, 'security.login.maxAttempts', '5', 'INT', 'security', '登录失败锁定次数'),
(3005, 'security.login.lockMinutes', '30', 'INT', 'security', '锁定时长'),
(3006, 'security.session.timeoutMinutes', '120', 'INT', 'security', '会话超时'),
(3007, 'approval.keyFields', 'idCard,position,manager,org,projectMembership,permission', 'STRING', 'approval', '关键字段'),
(3008, 'approval.quorum', '2', 'INT', 'approval', '关键信息通过人数'),
(3009, 'approval.applicantIncluded', 'false', 'BOOLEAN', 'approval', '申请人是否计入通过人数'),
(3010, 'approval.configChangeQuorum', '2', 'INT', 'approval', '修改审批配置所需人数'),
(3011, 'approval.timeoutHours', '24', 'INT', 'approval', '审批提醒时长'),
(3012, 'approval.expireHours', '72', 'INT', 'approval', '审批超时关闭时长'),
(3013, 'audit.retentionDays', '365', 'INT', 'audit', '日志保留天数'),
(3014, 'audit.autoClean', 'true', 'BOOLEAN', 'audit', '到期自动清理'),
(3015, 'audit.realtimeAlert', 'false', 'BOOLEAN', 'audit', '敏感访问实时告警'),
(3016, 'audit.readLogEnabled', 'true', 'BOOLEAN', 'audit', '敏感读日志'),
(3017, 'boss.access.mode', 'REQUEST', 'STRING', 'boss', '老板敏感访问模式'),
(3018, 'boss.access.approverCount', '1', 'INT', 'boss', '老板敏感访问审批人数'),
(3019, 'boss.access.durationMinutes', '30', 'INT', 'boss', '老板敏感访问时长'),
(3020, 'boss.access.requireReason', 'true', 'BOOLEAN', 'boss', '必须填写用途'),
(3021, 'ai.enabled', 'true', 'BOOLEAN', 'ai', '启用AI'),
(3022, 'ai.mode', 'EXTERNAL', 'STRING', 'ai', 'AI模式'),
(3023, 'ai.docQa.requireCitations', 'true', 'BOOLEAN', 'ai', '文档问答必须引用'),
(3024, 'ai.lowConfidenceThreshold', '0.6', 'DECIMAL', 'ai', '低置信阈值'),
(3025, 'chat.enabled', 'true', 'BOOLEAN', 'chat', '启用聊天'),
(3026, 'chat.retentionDays', '365', 'INT', 'chat', '聊天保留天数'),
(3027, 'chat.file.maxMb', '100', 'INT', 'chat', '聊天文件大小'),
(3028, 'chat.group.maxMembers', '200', 'INT', 'chat', '群人数上限'),
(3029, 'chat.adminAccess.enabled', 'true', 'BOOLEAN', 'chat', '管理员可申请查看'),
(3030, 'chat.adminAccess.quorum', '2', 'INT', 'chat', '管理员查看审批人数'),
(3031, 'doc.externalShare.enabled', 'false', 'BOOLEAN', 'doc', '外链分享'),
(3032, 'doc.onlineEdit.enabled', 'false', 'BOOLEAN', 'doc', '在线编辑'),
(3033, 'doc.delete.mode', 'HARD', 'STRING', 'doc', '删除模式'),
(3034, 'doc.retentionDays.default', '3650', 'INT', 'doc', '默认保留期'),
(3035, 'doc.file.maxMb', '200', 'INT', 'doc', '文档大小'),
(3036, 'project.retentionDays.default', '3650', 'INT', 'project', '项目默认保留期'),
(3037, 'project.progress.mode', 'MANUAL', 'STRING', 'project', '进度模式'),
(3038, 'import.formats', 'xlsx,docx,pdf', 'STRING', 'import', '导入格式'),
(3039, 'import.maxRows', '5000', 'INT', 'import', '导入最大行数'),
(3040, 'import.confidenceThreshold', '0.8', 'DECIMAL', 'import', '低置信阈值'),
(3041, 'import.commitRequiresApproval', 'true', 'BOOLEAN', 'import', '提交走审批'),
(3042, 'recovery.enabled', 'true', 'BOOLEAN', 'recovery', '紧急恢复'),
(3043, 'recovery.mode', 'DUAL_SIGN', 'STRING', 'recovery', '恢复模式'),
(3044, 'search.engine', 'mysql', 'STRING', 'search', '搜索引擎');

-- Default field permissions (role_id = 0 means default for all roles)
INSERT INTO sys_field_permission
(id, entity_type, field_code, role_id, scope_type, visibility, mask_rule, self_edit, exportable, ai_readable, is_sensitive, key_field)
VALUES
(4001, 'EMPLOYEE', 'name', 0, 'ALL', 'ALL', 'NONE', 0, 1, 1, 0, 0),
(4002, 'EMPLOYEE', 'employeeNo', 0, 'ALL', 'ALL', 'NONE', 0, 1, 1, 0, 0),
(4003, 'EMPLOYEE', 'phone', 0, 'ALL', 'SELF,DIRECT_REPORTS,HR_ADMIN,PROJECT_MEMBER,BOSS', 'PHONE', 1, 1, 1, 1, 0),
(4004, 'EMPLOYEE', 'email', 0, 'ALL', 'SELF,DIRECT_REPORTS,HR_ADMIN,PROJECT_MEMBER,BOSS', 'EMAIL', 1, 1, 1, 1, 0),
(4005, 'EMPLOYEE', 'idCard', 0, 'ALL', 'SELF,HR_ADMIN,BOSS', 'ID_CARD', 0, 0, 0, 1, 1),
(4006, 'EMPLOYEE', 'address', 0, 'ALL', 'SELF,HR_ADMIN', 'ADDRESS', 1, 0, 0, 1, 0),
(4007, 'EMPLOYEE', 'emergencyContact', 0, 'ALL', 'SELF,HR_ADMIN', 'CONTACT', 1, 0, 0, 1, 0),
(4008, 'EMPLOYEE', 'position', 0, 'ALL', 'ALL', 'NONE', 0, 1, 1, 0, 1),
(4009, 'EMPLOYEE', 'manager', 0, 'ALL', 'ALL', 'NONE', 0, 1, 1, 0, 1),
(4010, 'EMPLOYEE', 'org', 0, 'ALL', 'ALL', 'NONE', 0, 1, 1, 0, 1);

-- Approval policies
INSERT INTO sys_approval_policy
(id, policy_code, name, entity_type, field_codes, quorum, applicant_included, change_quorum, enabled)
VALUES
(5001, 'KEY_INFO', '关键信息修改', 'EMPLOYEE', 'idCard,position,manager,org,projectMembership,permission', 2, 0, 2, 1),
(5002, 'CONFIG_CHANGE', '关键配置修改', 'CONFIG', '*', 2, 0, 2, 1);
