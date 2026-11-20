-- P1: configurable retention cleanup.
ALTER TABLE project ADD COLUMN retention_notified_at DATETIME(6) NULL COMMENT '保留期提醒时间';
ALTER TABLE document ADD COLUMN retention_notified_at DATETIME(6) NULL COMMENT '保留期提醒时间';

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3160, 'notification.retentionDays', '180', 'INT', 'notification', '站内通知保留天数，0=永久'),
(3161, 'ai.log.retentionDays', '180', 'INT', 'ai', 'AI 查询日志保留天数，0=永久'),
(3162, 'audit.cleanLogRetentionDays', '365', 'INT', 'audit', '清理动作审计日志保留天数，0=永久'),
(3163, 'project.retention.action', 'ARCHIVE', 'STRING', 'project', '项目到期动作：ARCHIVE/NOTIFY/PURGE/NONE'),
(3164, 'project.retention.autoPurge', 'false', 'BOOLEAN', 'project', '是否允许定时任务真正删除到期项目'),
(3165, 'doc.retention.action', 'ARCHIVE', 'STRING', 'doc', '文档到期动作：ARCHIVE/NOTIFY/PURGE/NONE'),
(3166, 'doc.retention.autoPurge', 'false', 'BOOLEAN', 'doc', '是否允许定时任务真正删除到期文档'),
(3167, 'retention.job.enabled', 'true', 'BOOLEAN', 'retention', '是否启用每日保留期清理任务');
