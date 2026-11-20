INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3230, 'approval.conflict.enabled', 'true', 'BOOLEAN', 'approval', '是否启用关键信息审批冲突检测'),
(3231, 'approval.conflict.policies', 'KEY_INFO,ENTITY_ROLLBACK', 'STRING', 'approval', '参与冲突检测的审批策略，逗号分隔；同一对象上这些策略的待审批申请会互相提示'),
(3232, 'approval.conflict.pendingAction', 'WARN', 'STRING', 'approval', '同一对象已有待审批申请时：WARN=允许提交并提示；BLOCK=拒绝提交'),
(3233, 'approval.conflict.staleCheck', 'true', 'BOOLEAN', 'approval', '审批通过前是否核对申请提交后底层关键信息是否被他人修改'),
(3234, 'approval.conflict.staleAction', 'BLOCK', 'STRING', 'approval', '发现审批期间数据已变更时：BLOCK=阻止通过并要求驳回重提；NOTIFY=提示后继续通过');
