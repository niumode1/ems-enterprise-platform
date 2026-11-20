-- Document print control / watermark and approval timeout reminders.
ALTER TABLE document_permission
    ADD COLUMN can_print TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE sys_approval_request
    ADD COLUMN last_remind_at DATETIME NULL;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3240, 'doc.print.enabled', 'true', 'BOOLEAN', 'doc', '是否允许打印/打印预览文档'),
(3241, 'doc.print.default', 'ROLE_BASED', 'STRING', 'doc', '未单独授权时的打印权限：ROLE_BASED=仅负责人/项目管理员/上传人；ALL=项目成员都可；NONE=都不可'),
(3242, 'doc.watermark.enabled', 'false', 'BOOLEAN', 'doc', '打印预览是否叠加水印'),
(3243, 'doc.watermark.text', '{name} {time}', 'STRING', 'doc', '水印文字模板，可用 {name}=当前用户、{employeeNo}=工号、{time}=当前时间、{project}=项目名'),
(3244, 'approval.reminder.enabled', 'true', 'BOOLEAN', 'approval', '是否对待审批超时申请向审批人发送提醒'),
(3245, 'approval.reminder.maxPerRun', '100', 'INT', 'approval', '单次提醒任务最多处理多少条待审批申请');
