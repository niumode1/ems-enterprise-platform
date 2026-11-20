-- P1 global search: configurable scopes and limits.
INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3170, 'search.enabled', 'true', 'BOOLEAN', 'search', '全局搜索开关'),
(3171, 'search.maxResults', '20', 'INT', 'search', '默认每页条数'),
(3172, 'search.maxPageSize', '50', 'INT', 'search', '单页最大条数'),
(3173, 'search.employee.enabled', 'true', 'BOOLEAN', 'search', '员工搜索'),
(3174, 'search.project.enabled', 'true', 'BOOLEAN', 'search', '项目搜索'),
(3175, 'search.task.enabled', 'true', 'BOOLEAN', 'search', '任务搜索'),
(3176, 'search.document.enabled', 'true', 'BOOLEAN', 'search', '文档搜索'),
(3177, 'search.snippetLength', '120', 'INT', 'search', '摘要长度'),
(3178, 'search.minKeywordLength', '1', 'INT', 'search', '关键词最短长度');
