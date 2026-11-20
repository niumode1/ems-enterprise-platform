-- P1: emergency recovery (dual-sign or deployment account) + login lock state.
ALTER TABLE sys_user ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数';
ALTER TABLE sys_user ADD COLUMN locked_until DATETIME(6) NULL COMMENT '锁定截止时间';

CREATE TABLE recovery_request (
    id BIGINT PRIMARY KEY,
    action VARCHAR(32) NOT NULL COMMENT 'RESET_PASSWORD/UNLOCK/GRANT_ADMIN',
    target_user_id BIGINT NOT NULL,
    payload_json TEXT NULL COMMENT '仅保存执行所需数据（密码仅存BCrypt哈希）',
    reason VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPLIED/REJECTED/CANCELLED/EXPIRED',
    requester_id BIGINT NOT NULL,
    required_signers INT NOT NULL DEFAULT 2,
    expires_at DATETIME(6) NULL,
    applied_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_recovery_status (status, expires_at),
    KEY idx_recovery_requester (requester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE recovery_signature (
    id BIGINT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    signer_id BIGINT NOT NULL,
    signer_role VARCHAR(64) NULL,
    comment VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_recovery_signature (request_id, signer_id),
    KEY idx_recovery_signature_signer (signer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3140, 'recovery.requiredSigners', '2', 'INT', 'recovery', '紧急恢复需要几名签署人'),
(3141, 'recovery.signers', 'BOSS,AUDITOR', 'STRING', 'recovery', '有资格签署恢复申请的角色代码，逗号分隔'),
(3142, 'recovery.allowedActions', 'RESET_PASSWORD,UNLOCK,GRANT_ADMIN', 'STRING', 'recovery', '允许的恢复操作'),
(3143, 'recovery.deploymentAccount.enabled', 'true', 'BOOLEAN', 'recovery', '是否允许部署级恢复账号直接执行'),
(3144, 'recovery.deploymentAccount.usernames', 'deploy', 'STRING', 'recovery', '部署级恢复账号用户名，逗号分隔'),
(3145, 'recovery.adminGrantDays', '1', 'INT', 'recovery', '恢复授予管理员的有效天数，0=长期'),
(3146, 'recovery.expireHours', '24', 'INT', 'recovery', '恢复申请有效小时数'),
(3147, 'recovery.logRetentionDays', '0', 'INT', 'recovery', '恢复记录保留天数，0=永久');
