-- ============================================================
-- P1: project tasks, milestones and progress snapshots
-- ============================================================

ALTER TABLE project
    ADD COLUMN progress INT NOT NULL DEFAULT 0,
    ADD COLUMN progress_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL';

CREATE TABLE task (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    assignee_employee_id BIGINT NULL,
    creator_employee_id BIGINT NULL,
    start_date DATE NULL,
    due_date DATE NULL,
    progress INT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_project (project_id, status, due_date),
    KEY idx_task_assignee (assignee_employee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_participant (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_participant (task_id, employee_id),
    KEY idx_task_participant_employee (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_dependency (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    depends_on_task_id BIGINT NOT NULL,
    dependency_type VARCHAR(24) NOT NULL DEFAULT 'FINISH_TO_START',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_dependency (task_id, depends_on_task_id),
    KEY idx_task_dependency_target (depends_on_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_comment (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    author_employee_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_task_comment_task (task_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE milestone (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NULL,
    due_date DATE NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    reached_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_milestone_project (project_id, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_progress_snapshot (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    task_total INT NOT NULL DEFAULT 0,
    task_done INT NOT NULL DEFAULT 0,
    note VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_progress_snapshot_project (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_config (id, config_key, config_value, value_type, config_group, description) VALUES
(3060, 'project.task.createRoles', 'OWNER,ADMIN,MEMBER', 'STRING', 'project', '可创建任务的项目角色'),
(3061, 'project.task.editRoles', 'OWNER,ADMIN,ASSIGNEE', 'STRING', 'project', '可编辑任务的项目角色'),
(3062, 'project.task.deleteRoles', 'OWNER,ADMIN', 'STRING', 'project', '可删除任务的项目角色'),
(3063, 'project.milestone.manageRoles', 'OWNER,ADMIN', 'STRING', 'project', '可管理里程碑的项目角色'),
(3064, 'project.progress.autoFromTasks', 'false', 'BOOLEAN', 'project', '是否按任务完成率自动计算进度'),
(3065, 'project.progress.manageRoles', 'OWNER,ADMIN', 'STRING', 'project', '可更新项目进度的项目角色');
