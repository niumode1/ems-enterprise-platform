-- P1: AI document Q&A and task extraction.
-- Retrieval always runs against the current document text after the caller passes
-- the normal project-membership + per-document ACL checks. The configured model is
-- only an optional answer writer / extractor over retrieved fragments.
INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3110, 'ai.docQa.enabled', 'true', 'BOOLEAN', 'ai', '是否启用文档问答'),
(3111, 'ai.docQa.maxContextChars', '12000', 'INT', 'ai', '文档问答最多检索的正文长度'),
(3112, 'ai.docQa.maxAnswerChars', '300', 'INT', 'ai', '文档问答回答最大字符数'),
(3113, 'ai.docQa.maxCitations', '3', 'INT', 'ai', '文档问答最多返回的引用片段数'),
(3114, 'ai.docQa.minContentChars', '20', 'INT', 'ai', '少于该正文长度时提示无可用内容'),
(3115, 'ai.docTasks.enabled', 'true', 'BOOLEAN', 'ai', '是否启用文档任务提取'),
(3116, 'ai.docTasks.maxSuggestions', '20', 'INT', 'ai', '单次任务提取最多建议数'),
(3117, 'ai.docTasks.maxApply', '20', 'INT', 'ai', '单次确认创建任务的最大数量'),
(3118, 'ai.docTasks.maxContextChars', '12000', 'INT', 'ai', '任务提取最多读取的正文长度'),
(3119, 'ai.docTasks.defaultPriority', 'NORMAL', 'STRING', 'ai', '未识别优先级时的默认任务优先级'),
(3120, 'ai.docTasks.lowConfidenceThreshold', '0.6', 'DECIMAL', 'ai', '任务建议低置信阈值');
