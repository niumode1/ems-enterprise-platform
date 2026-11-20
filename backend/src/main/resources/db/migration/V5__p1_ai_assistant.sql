-- ============================================================
-- P1: AI assistant (operation guidance, people Q&A, quick questions)
-- ============================================================

CREATE TABLE ai_help_index (
    id BIGINT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    keywords VARCHAR(500) NOT NULL DEFAULT '',
    route VARCHAR(200) NOT NULL,
    description VARCHAR(500) NULL,
    required_permission VARCHAR(64) NULL,
    sort INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_ai_help_enabled (enabled, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_quick_question (
    id BIGINT PRIMARY KEY,
    question VARCHAR(200) NOT NULL,
    feature VARCHAR(24) NOT NULL DEFAULT 'HELP',
    required_permission VARCHAR(64) NULL,
    roles_json VARCHAR(300) NULL,
    sort INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_ai_quick_enabled (enabled, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO ai_help_index (id, title, keywords, route, description, required_permission, sort, enabled) VALUES
(1, '项目管理', '项目,文档,成员,上传,下载,文件夹', '/projects', '进入项目查看文件夹、文档与成员权限', 'project:read', 10, 1),
(2, '员工管理', '员工,档案,手机号,电话,邮箱,地址,身份证,入职,资料', '/employees', '查询员工档案；可见字段与脱敏由字段权限决定', 'employee:read', 20, 1),
(3, '组织架构', '部门,组织,架构,汇报,上级,领导,树', '/org', '查看行政组织树与业务汇报树', 'org:read', 30, 1),
(4, '自定义属性', '属性,字段,时间线,工资,调薪,扩展', '/custom-fields', '维护不改表的扩展属性与时间线历史', 'attr:read', 40, 1),
(5, '花名册导入', '导入,花名册,excel,表格,批量,上传员工', '/imports', '上传花名册、解析校对、推送员工确认与提交审批', 'import:read', 50, 1),
(6, '我的确认', '确认,本人,我的档案,核对,驳回', '/imports/my-confirmations', '确认或修改管理员推送的本人档案', 'import:confirm', 60, 1),
(7, 'AI 设置', 'AI,模型,厂商,密钥,apikey,接口地址', '/ai-settings', '管理员配置外部或本地 OpenAI 兼容模型', 'ai:config:read', 70, 1),
(8, '通知中心', '通知,消息,提醒,未读', '/notifications', '查看站内通知与审批提醒', 'notification:read', 80, 1),
(9, '审批中心', '审批,待办,通过,驳回,申请', '/approvals', '处理待我审批或查看我发起的审批', 'approval:read', 90, 1),
(10, '审计日志', '日志,审计,操作记录,追溯,敏感访问', '/audit', '按模块、动作、敏感级别筛选审计日志', 'audit:read', 100, 1),
(11, '系统配置', '配置,策略,初始化,保留期,规则', '/config', '公司的审批、审计、文档与 AI 策略配置', 'config:read', 110, 1),
(12, '字段权限', '字段权限,脱敏,可见,本人编辑,AI可读', '/field-permissions', '按角色配置字段可见性、脱敏与本人编辑', 'field:read', 120, 1),
(13, '账号权限', '账号,用户,角色,权限,禁用', '/users', '创建账号、分配角色与禁用账号', 'user:read', 130, 1),
(14, '我的资料', '我的,本人,资料,密码,修改密码,头像', '/profile', '查看和修改允许本人维护的信息', NULL, 140, 1);

INSERT INTO ai_quick_question (id, question, feature, required_permission, roles_json, sort, enabled) VALUES
(1, '员工档案在哪里修改？', 'HELP', 'employee:read', NULL, 10, 1),
(2, '如何导入花名册？', 'HELP', 'import:read', NULL, 20, 1),
(3, '审批中心在哪里？', 'HELP', 'approval:read', NULL, 30, 1),
(4, '技术部有哪些人？', 'PEOPLE', 'employee:read', NULL, 40, 1),
(5, '李四的电话是多少？', 'PEOPLE', 'employee:read', NULL, 50, 1),
(6, '我的资料在哪里修改？', 'HELP', NULL, NULL, 60, 1);
