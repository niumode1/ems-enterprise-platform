# API 清单 v1.1

> 状态标记：**✅ 已实现**、**⬜ 规划中（P1/P2）**。本节记录当前代码真实路由；未标记为 ✅ 的接口尚未实现。

## 1. 通用约定

- Base path：`/api/v1`
- 认证：`Authorization: Bearer <access_token>`
- 请求追踪：`X-Request-Id`
- 幂等：创建/提交类接口支持 `Idempotency-Key`
- 分页：`page`（从 1 开始）、`size`、`sort`
- 时间：ISO 8601，服务端时区 `Asia/Shanghai`
- 响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "..."
}
```

- 错误：`code != 0`，HTTP 状态码表达语义；权限不足返回 403，未登录 401。
- 所有写操作记录审计；敏感读操作记录访问日志。
- 权限列写“权限点/数据范围”，实际由 RBAC + 字段权限判断。

## 2. 认证

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` | 用户名密码登录 | 公开 |
| POST | `/auth/refresh` | 刷新令牌 | 刷新令牌 |
| POST | `/auth/logout` | 登出 | 登录 |
| GET | `/auth/me` | 当前用户、角色、权限、数据范围 | 登录 |
| POST | `/auth/password` | 修改密码 | 登录 |

> 连续登录失败达到 `security.login.maxAttempts`（默认 5）后账号锁定 `security.login.lockMinutes`（默认 30）分钟，
> 锁定期间即使密码正确也返回 `40301`；成功登录会清零失败次数并解除已到期锁定。管理员可通过紧急恢复 `UNLOCK` 解锁。

## 3. 账号、角色、权限

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/users` | 账号列表 | `user:read` |
| POST | `/users` | 创建账号 | `user:create` |
| PUT | `/users/{id}` | 修改账号（显示名、关联员工、状态） | `user:update` |
| POST | `/users/{id}/disable` | 禁用账号 | `user:disable` |
| POST | `/users/{id}/reset-password` | 重置密码 | `user:update` |
| PUT | `/users/{id}/roles` | 分配角色（支持多角色、生效/失效日期、备注） | `user:role:assign` |
| GET | `/users/{id}/roles` | 角色分配历史（含待生效/已到期/已撤销） | `user:read` |
| GET | `/roles` | 角色列表 | `role:read` |
| GET | `/permissions` | 权限点列表 | `permission:read` |
| GET | `/field-permissions` | 字段权限配置 | `field:read` |
| PUT | `/field-permissions` | 修改字段权限（关键配置走审批） | `field:update` |

> 身份模型：一个人一个账号，账号通过 `employee_id` 唯一关联员工档案。同一个人可在同一账号下同时拥有多个系统角色（如 `HR_ADMIN` + `AUDITOR`），也可为角色设置生效/失效日期。登录与鉴权只加载“状态 ACTIVE 且当前日期在有效期内”的角色与权限；到期自动失效，历史保留为 `SCHEDULED`/`EXPIRED`/`REVOKED`。项目组长/成员属于项目成员角色（`OWNER/ADMIN/MEMBER/VIEWER`），与系统角色相互独立。`PUT /users/{id}/roles` 请求体示例：`{"assignments":[{"roleCode":"AUDITOR","startDate":"2026-12-01","endDate":"2027-12-31","note":"兼任审计"}]}`；仍兼容旧的 `{"roleCodes":["EMPLOYEE"]}` 写法。新建账号默认必须关联员工档案，部署/紧急恢复账号可由受保护配置 `account.requireEmployeeLink` 关闭该限制。

## 4. 员工与组织

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/employees` | 员工列表/筛选（`keyword`/`orgId`/`status`） | `employee:read` + 数据范围 | ✅ |
| POST | `/employees` | 新增员工 | `employee:create` | ✅ |
| GET | `/employees/me` | 我的员工档案 | 登录 | ✅ |
| PUT | `/employees/me` | 本人可编辑字段 | 登录 | ✅ |
| GET | `/employees/me/visibility` | 本人字段可见性开关（`fieldCode`/`label`/`visible`/`canHide`） | 登录 | ✅ |
| PUT | `/employees/me/visibility` | 设置本人字段是否对普通同事可见 | 登录 | ✅ |
| GET | `/employees/{id}` | 员工详情（字段脱敏 + 多岗位 + 汇报关系） | `employee:read` | ✅ |
| PUT | `/employees/{id}` | 修改员工（关键字段走审批） | `employee:update` 或本人可编辑字段 | ✅ |
| GET | `/employees/{id}/positions` | 岗位/部门履历 | `employee:read` | ⬜ 详情内已含岗位列表 |
| POST | `/employees/{id}/positions` | 新增岗位任职 | `employee:position:update` | ⬜ |
| GET | `/employees/{id}/reporting-lines` | 汇报关系 | `employee:read` | ⬜ 详情内已含 |
| GET | `/employees/{id}/history` | 档案变更历史 | `employee:read` | ⬜ 审计日志可查 |
| GET | `/employees/search` | 按姓名/工号/手机等搜索 | `employee:search` | ⬜ 列表 `keyword` 已支持 |
| POST | `/boss-access/requests` | 老板申请查看敏感字段（见 4.1） | `employee:sensitive:request` | ✅ |
| DELETE | `/employees/{id}` | 离职归档 | `employee:archive` | ⬜ |
| GET | `/org/tree` | 行政组织树 | `org:read` | ✅ |
| GET | `/org/reporting-tree` | 业务汇报树 | `org:read` | ✅ |
| GET | `/org/employees/{id}/relations` | 员工直属上级、汇报链、直接下属与同级同事（仅工号/姓名） | `org:read` | ✅ |
| GET | `/org/positions` | 岗位字典 | `org:read` | ✅ |
| POST | `/org` | 新增组织 | `org:create` | ✅ |
| PUT | `/org/{id}` | 修改组织 | `org:update` | ✅ |
| POST | `/org/{id}/disable` | 停用组织 | `org:disable` | ✅ |
| POST | `/org/positions` | 新增岗位 | `employee:position:update` | ✅ |

`PUT /employees/me/visibility` 请求体为 `{"fieldCode":"phone","visible":false}`。字段白名单由
`employee.selfVisibility.fields` 决定；关闭后普通同事在列表和详情中都拿不到该字段（返回 `null`，
不进入 `maskedFields`），本人与 `employee.selfVisibility.exemptPermissions` 配置的权限仍可见。
总开关 `employee.selfVisibility.enabled=false` 时列表仍返回字段但 `canHide=false`，写接口返回 `40901`。
每次显示/隐藏都会写入 `PERSONAL/employee` 审计。

### 4.1 老板敏感访问

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/boss-access/status` | 当前模式、审批人数、时长、本人有效授权 | `employee:read` | ✅ |
| POST | `/boss-access/requests` | 申请指定员工或全部员工的敏感访问 | `employee:sensitive:request` | ✅ |
| GET | `/boss-access/grants?all=true` | 授权记录；`all=true` 需 `audit:read` 或 `config:read` | `employee:read` | ✅ |
| POST | `/boss-access/grants/{id}/revoke` | 撤销授权（本人或审计/配置管理员） | `employee:read` | ✅ |

- 授权**不扩大字段可见范围**：只对当前角色本来就能看到的字段取消脱敏；看不到的字段授权后仍不可见。
- `REQUEST` 模式走 `BOSS_ACCESS` 审批（`boss.access.approverCount` 人通过，默认 2），
  `DIRECT` 模式直接授予，`DISABLED` 关闭；`boss.access.enabled=false` 时全部停止。
- 单次时长取 `boss.access.durationMinutes`，并受 `boss.access.maxDurationMinutes` 上限约束；
  到期自动失效。全部员工授权需 `boss.access.allowAllEmployees=true`。
- 同一员工/全部范围的旧授权会被新授权替换；不同员工的授权互不影响。
- 授予、撤销写 `SECURITY/boss` 审计；被授权人的员工敏感读取继续写访问日志。

## 5. 自定义属性

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/custom-fields/defs?entityType=EMPLOYEE` | 属性定义列表（不返回敏感定义给无权者） | `attr:read` 或本人 | ✅ |
| POST | `/custom-fields/defs` | 新增属性（不改表） | `attr:create` | ✅ |
| PUT | `/custom-fields/defs/{id}` | 修改属性 | `attr:update` | ✅ |
| POST | `/custom-fields/defs/{id}/disable` | 停用属性 | `attr:disable` | ✅ |
| GET | `/custom-fields/values?entityType=&entityId=` | 查询实体属性值（按可见性过滤，不可见字段不返回或标记 `visible=false`） | 登录 | ✅ |
| PUT | `/custom-fields/values` | 批量设置属性值；时间线属性按 `effectiveDate` 关闭旧版本 | `attr:value:update` 或本人可编辑 | ✅ |
| GET | `/custom-fields/values/history?entityType=&entityId=` | 时间线历史 | 登录 | ✅ |

> 时间线属性通过 `timeline=true` 定义；每次设置值会关闭上一条记录的 `endDate = effectiveDate - 1`，保留完整历史（如工资调整）。

## 6. 初始化导入

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| POST | `/imports` | 上传花名册（multipart：csv/xlsx/xls） | `import:upload` | ✅ |
| GET | `/imports` | 批次列表 | `import:read` | ✅ |
| GET | `/imports/{id}` | 批次详情（含进度计数） | `import:read` | ✅ |
| POST | `/imports/{id}/parse` | 表头映射解析/重新解析（可选 AI 补全，仅发送表头） | `import:parse` | ✅ |
| GET | `/imports/{id}/rows` | 草稿行列表（原值/解析值/最终值/匹配合并） | `import:read` | ✅ |
| PUT | `/imports/{id}/rows/{rowId}` | 管理员校对单行，可代员工确认 | `import:review` | ✅ |
| POST | `/imports/{id}/push` | 推送员工确认（匹配到账号的员工收到通知） | `import:push` | ✅ |
| GET | `/imports/my-confirmations` | 我的待确认行 | `import:confirm` | ✅ |
| GET | `/imports/my-confirmations/fields` | 我的可编辑字段（读取 `import.employeeEditableFields`） | `import:confirm` | ✅ |
| PUT | `/imports/rows/{rowId}/confirm` | 员工编辑后确认（仅允许字段，越权 403） | `import:confirm` | ✅ |
| POST | `/imports/rows/{rowId}/reject` | 员工驳回并填写原因 | `import:confirm` | ✅ |
| POST | `/imports/{id}/commit` | 提交落库；审批策略 `IMPORT_COMMIT` 启用时进入审批 | `import:commit` | ✅ |
| POST | `/imports/{id}/rollback` | 批次回滚（恢复快照/停用新建员工，值已变更的行跳过） | `import:rollback` | ✅ |

> 批量校对 `/imports/{id}/review`、独立进度 `/imports/{id}/status` 未单独提供：批次详情已返回 `totalRows/parsedRows/pendingConfirmRows/confirmedRows/rejectedRows/committedRows`。
> 批次状态：`UPLOADED → PARSED → PENDING_CONFIRM → PENDING_APPROVAL → COMMITTED / ROLLED_BACK / FAILED`。

## 7. 项目、任务、里程碑

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/projects` | 项目列表（仅本人参与的项目） | `project:read` | ✅ |
| POST | `/projects` | 创建项目（创建人自动 OWNER） | `project:create` | ✅ |
| GET | `/projects/{id}` | 项目详情 | 项目成员 | ✅ |
| PUT | `/projects/{id}` | 修改项目（含下载策略/保留天数） | OWNER/ADMIN | ✅ |
| DELETE | `/projects/{id}` | 硬删除项目 | OWNER | ✅ |
| GET | `/projects/{id}/members` | 项目成员 | 项目成员 | ✅ |
| POST | `/projects/{id}/members` | 添加成员 | OWNER/ADMIN | ✅ |
| PUT | `/projects/{id}/members/{memberId}` | 调整成员角色 | OWNER/ADMIN | ⬜ |
| DELETE | `/projects/{id}/members/{memberId}` | 移除成员 | OWNER/ADMIN | ✅ |
| GET | `/projects/{id}/folders` | 文件夹列表 | 项目成员 | ✅ |
| POST | `/projects/{id}/folders` | 新建文件夹 | OWNER/ADMIN/MEMBER | ✅ |
| GET | `/projects/{id}/tasks` | 任务列表（可按状态/负责人/标题筛选） | `task:read` + 项目成员 | ✅ |
| POST | `/projects/{id}/tasks` | 创建任务 | `task:create` + `project.task.createRoles` | ✅ |
| GET | `/projects/{id}/tasks/{taskId}` | 任务详情 | `task:read` + 项目成员 | ✅ |
| PUT | `/projects/{id}/tasks/{taskId}` | 修改任务（含参与人/前置依赖） | `task:update` + `project.task.editRoles` | ✅ |
| PUT | `/projects/{id}/tasks/{taskId}/status` | 更新任务状态/进度 | `task:update` + `project.task.editRoles` | ✅ |
| DELETE | `/projects/{id}/tasks/{taskId}` | 删除任务 | `task:delete` + `project.task.deleteRoles` | ✅ |
| GET | `/projects/{id}/tasks/{taskId}/comments` | 任务评论列表 | `task:read` + 项目成员 | ✅ |
| POST | `/projects/{id}/tasks/{taskId}/comments` | 发表评论 | `task:comment` + 项目成员 | ✅ |
| GET | `/projects/{id}/milestones` | 里程碑列表 | `milestone:read` + 项目成员 | ✅ |
| POST | `/projects/{id}/milestones` | 创建里程碑 | `milestone:create` + `project.milestone.manageRoles` | ✅ |
| PUT | `/projects/{id}/milestones/{milestoneId}` | 修改里程碑（`REACHED` 自动记录达成时间） | `milestone:update` + `project.milestone.manageRoles` | ✅ |
| DELETE | `/projects/{id}/milestones/{milestoneId}` | 删除里程碑 | `milestone:delete` + `project.milestone.manageRoles` | ✅ |
| GET | `/projects/{id}/progress` | 项目进度与任务完成统计 | `task:read` + 项目成员 | ✅ |
| PUT | `/projects/{id}/progress` | 更新项目进度（自动/手动由配置决定） | `project:progress:update` + `project.progress.manageRoles` | ✅ |
| GET | `/projects/{id}/progress/snapshots` | 进度变更记录（最近 100 条） | `task:read` + 项目成员 | ✅ |

> 项目角色：`OWNER`（负责人，唯一）、`ADMIN`、`MEMBER`、`VIEWER`。非成员访问项目一律 403，不暴露项目是否存在。
> 任务权限是“系统权限 + 项目角色配置”双重校验：`project.task.createRoles`/`editRoles`/`deleteRoles` 支持 `OWNER/ADMIN/MEMBER/VIEWER/ASSIGNEE/CREATOR` 令牌，由管理员按公司规则配置。任务负责人必须是项目成员，依赖不得形成环。任务响应带 `canEdit`/`canDelete`，按当前用户与同一套角色配置计算，前端据此显示操作。
> 项目进度 `progressMode` 为 `MANUAL` 时由有权限的人手工维护并写入 `project_progress_snapshot`；配置 `project.progress.autoFromTasks=true` 或项目模式为 `AUTO` 时按“已完成任务 / 未取消任务”自动计算。任务完成会通知负责人。

## 8. 文档

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/projects/{projectId}/documents` | 文档列表（带 `canDownload`/`canPrint`/`canDelete`） | 项目成员 | ✅ |
| POST | `/projects/{projectId}/documents` | 上传文档（multipart：`file`、`folderId`、`name`） | OWNER/ADMIN/MEMBER | ✅ |
| GET | `/documents/{id}/download` | 直接下载文件流（`Content-Disposition` UTF-8） | 项目成员 + 下载策略/文档 ACL | ✅ |
| GET | `/documents/{id}/print` | 打印预览：返回已索引正文、`watermarkEnabled`/`watermarkText`；无正文时 `previewable=false` | 项目成员 + 打印策略/文档 ACL | ✅ P1 |
| DELETE | `/documents/{id}` | 硬删除（同时删除物理文件） | OWNER/ADMIN 或文档 ACL 授权 | ✅ |
| GET | `/documents/{id}/permissions` | 单文档 ACL 列表 | OWNER/ADMIN | ✅ |
| PUT | `/documents/{id}/permissions` | 单文档 ACL 覆盖（`employeeId`/`canView`/`canDownload`/`canPrint`/`canDelete`） | OWNER/ADMIN | ✅ |
| GET | `/documents/search` | 文档搜索（文件名/原名/正文，权限过滤，返回摘要） | 项目成员 + `doc:search` | ✅ P1 |
| GET | `/documents/{id}/versions` | 版本列表（当前/历史，含上传人/备注） | 项目成员 + 项目角色配置 | ✅ P1 |
| POST | `/documents/{id}/versions` | 上传新版本（不覆盖旧文件） | `doc:version:upload` + 项目角色配置 | ✅ P1 |
| GET | `/documents/{id}/versions/{versionNo}/download` | 下载指定版本 | 项目成员 + 下载策略/文档 ACL | ✅ P1 |
| POST | `/documents/{id}/versions/{versionNo}/rollback` | 回滚到指定版本（生成新版本，保留历史） | `doc:version:upload` + 项目角色配置 | ✅ P1 |
| GET | `/documents/{id}` | 文档详情 | 项目成员 | ⬜ |
| GET | `/documents/{id}/preview` | 预览 URL/转换 | 项目成员 | ⬜ P1 |

> 外链分享接口不提供。下载优先看单文档 ACL；无 ACL 时按项目 `downloadPolicy`（`ALL`/`ADMINS`/`NONE`）判断。删除直接硬删除，不保留回收站；保留期由项目 `retentionDays` 配置，清理任务 P1 实现。
>
> 文档版本：首次上传生成 v1；上传新版本或回滚都追加一条新版本，旧版本标记 `ARCHIVED`、文件保留，可下载。回滚不会删除历史，而是以旧版本文件生成新的当前版本。可上传/回滚/查看版本的项目角色由 `doc.version.uploadRoles`/`rollbackRoles`/`viewRoles` 配置，支持 `UPLOADER`（文档当前上传人）令牌；`doc.version.maxKept` 限制保留版本数（0 不限）。搜索在用户所属项目范围内，按项目成员关系 + 单文档 ACL 过滤，文件名/原名/正文匹配，正文提取支持 txt/md/csv/json/xml/html/log/docx/xlsx（PDF 暂不提取），扩展名列表由 `doc.search.contentExtensions` 配置。
>
> 打印：`doc.print.enabled` 是总开关；未单独授权时按 `doc.print.default`（`ROLE_BASED`/`ALL`/`NONE`）判断，负责人、项目管理员与上传人始终可打印。`doc.watermark.enabled` 与 `doc.watermark.text` 控制打印预览水印。服务端只对可提取正文的格式返回文本预览，其他格式提示下载后打印；每次打开打印预览写 `DOCUMENT/PRINT` 访问日志。受浏览器限制，水印只覆盖应用内打印预览，不改变原文件。

## 9. 聊天

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/chat/capabilities` | 当前公司的附件开关、大小上限与扩展名白名单 | `chat:use` | ✅ P1 |
| GET | `/chat/conversations` | 我的会话列表（含未读数、最后消息） | `chat:use` | ✅ P1 |
| POST | `/chat/conversations` | 创建单聊/群聊 | `chat:use` | ✅ P1 |
| GET | `/chat/conversations/{id}` | 会话详情与成员 | `chat:use` + 会话成员 | ✅ P1 |
| PUT | `/chat/conversations/{id}/settings` | 会话设置（名称/阅后即焚/防撤回/保留期） | `chat:use` + 角色配置 | ✅ P1 |
| POST | `/chat/conversations/{id}/members` | 添加成员 | `chat:use` + `chat.memberManageRoles` | ✅ P1 |
| DELETE | `/chat/conversations/{id}/members/{employeeId}` | 移除成员/主动退群 | `chat:use` + 角色配置 | ✅ P1 |
| GET | `/chat/conversations/{id}/messages?page=&size=` | 历史消息（分页，默认 `chat.history.defaultSize`） | `chat:use` + 会话成员 | ✅ P1 |
| POST | `/chat/conversations/{id}/messages` | 发送文字或附件消息（`content` 可作附件说明，`attachmentId` 绑定一次） | `chat:use` + 会话成员 | ✅ P1 |
| POST | `/chat/conversations/{id}/attachments` | 上传待发送附件（multipart `file`） | `chat:use` + 会话成员 + 公司附件策略 | ✅ P1 |
| GET | `/chat/attachments/{id}/download` | 下载已发送附件；非成员需有效管理员查看授权 | `chat:use`/`chat:access:request` + 成员或授权 | ✅ P1 |
| POST | `/chat/conversations/{id}/read` | 会话标记已读（推进 `last_read_at`） | `chat:use` + 会话成员 | ✅ P1 |
| POST | `/chat/messages/{id}/read` | 消息标记已读（等价于会话已读） | `chat:use` + 会话成员 | ✅ P1 |
| POST | `/chat/messages/{id}/recall` | 撤回自己的消息 | `chat:use` + 会话成员 | ✅ P1 |
| GET | `/chat/admin-access/status` | 查看模式、可申请状态、时长上限、我的待审/有效授权 | 登录 | ✅ P1 |
| POST | `/chat/admin-access/requests` | 申请查看非本人会话（时长自动裁剪） | `chat:access:request` | ✅ P1 |
| GET | `/chat/admin-access/requests?all=` | 我的申请；`all=true` 需审批/审计/配置查看权 | 登录 | ✅ P1 |
| POST | `/chat/admin-access/requests/{id}/revoke` | 撤回待审申请或撤销有效授权 | 本人或管理角色 | ✅ P1 |
| GET | `/chat/admin-access/conversations/{id}/messages?page=&size=` | 授权后只读查看完整历史 | 有效授权 + `chat:access:request/approve` | ✅ P1 |

> 聊天以员工（`employee_id`）为成员，一人一账号；单聊幂等，重复发起返回同一会话。群聊创建必须填名称，创建人为 `OWNER`，`chat.group.maxMembers` 控制上限。未读数基于成员 `last_read_at`，拉取历史不会自动已读，需显式调用已读接口。
>
> 会话列表与详情额外返回 `canManageSettings`、`canManageMembers`、`canLeave`，由 `chat.group.manageRoles` / `chat.memberManageRoles` 和当前成员角色实时计算；前端据此显示设置、成员管理与退群入口，避免把公司策略写死在前端。
>
> 撤回：会话 `antiRecall=true` 时禁止撤回（默认开启）；否则发送人可在 `chat.recall.windowMinutes` 内撤回（0=不限），被撤回消息对所有人显示“消息已撤回”，且会话预览回退到上一条未撤回消息。
>
> 阅后即焚：会话开启 `burnAfterRead` 后，新消息标记为焚毁消息；接收人读取后内容立即从数据库清空，所有成员只看到“消息已焚毁”，会话预览同步替换。新成员是否能看到入群前历史由 `chat.history.visibleBeforeJoin` 控制；成员变更通知由 `chat.notify.membership` 控制，并写入通知中心。

> 图片/文件消息：先上传得到 `PENDING` 附件，再发送消息绑定；同一附件只能绑定一次。附件是否可用、单聊/群聊是否可用、阅后即焚会话是否可用、文件/图片大小上限、扩展名白名单、图片后缀、未发送附件清理时长全部由 `chat.attachment.*`、`chat.file.maxMb`、`chat.file.imageMaxMb` 配置。下载时重新校验会话成员关系或有效管理员查看授权，并写入访问日志；消息撤回或阅后即焚触发时，附件文件同步删除且元数据不再返回。禁用上传不影响历史附件的下载。
>
> 管理员查看：`chat.adminAccess.mode` 支持 `REQUEST`（走 `CHAT_ACCESS` 审批，人数取 `chat.adminAccess.quorum`）、`DIRECT`（直接授权）和 `DISABLED`；`chat.adminAccess.enabled` 是总开关。申请人是会话成员时拒绝；时长取 `chat.adminAccess.durationMinutes`，并按 `maxDurationMinutes` 裁剪。授权按时到期，可随时撤销，只读完整历史，不会让查看人成为成员、不能收发消息，也不改变任何成员的已读/焚毁状态；每次读取写入 `audit_log`（`SECURITY/chat/ACCESS_READ`）和 `access_log`。审批通过/撤销/读取均可在审计中追溯。

## 10. 通知

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/notifications?page=&size=&unreadOnly=` | 我的通知列表 | `notification:read` | ✅ |
| GET | `/notifications/unread-count` | 未读数（`{count}`，数值不是字符串） | `notification:read` | ✅ |
| POST | `/notifications/{id}/read` | 标记已读（仅本人） | `notification:read` | ✅ |
| POST | `/notifications/read-all` | 全部已读 | `notification:read` | ✅ |
| GET | `/notifications/preferences` | 我的通知偏好（公司允许的类型、显示名、当前开关、关闭行为） | `notification:read` | ✅ P1 |
| PUT | `/notifications/preferences` | 修改我的通知偏好（仅本人） | `notification:read` | ✅ P1 |

> 审批提交时通知具备 `approval:approve` 权限的用户（排除申请人）；审批进度/通过/驳回通知申请人。

> 通知偏好由公司配置决定可设置范围：`notification.preferences.types` 列出允许员工自行开关的类型，未列出的类型（默认如 `SECURITY`）始终送达；`notification.preferences.labels` 配置显示名，`notification.preferences.defaultMuted` 设置新用户默认关闭项。员工关闭某类通知后，`notification.preferences.behavior=SILENT` 仍写入站内记录但不实时推送，`SUPPRESS` 则不再生成该类通知。`notification.preferences.enabled` 是总开关，关闭后所有通知按原规则送达。偏好按用户隔离，修改写 `PERSONAL/notification/PREFERENCE_UPDATE` 审计。

## 11. AI

| 方法 | 路径 | 说明 | 权限 | 状态 |
|---|---|---|---|---|
| GET | `/ai/config` | 厂商/模型列表，API Key 仅返回掩码（如 `sk-t****7890`） | `ai:config:read` | ✅ |
| PUT | `/ai/config` | 新增/修改厂商；不传 `apiKey` 保留原 Key；AES-GCM 加密存储 | `ai:config:update` | ✅ |
| POST | `/ai/providers/test` | 测试 OpenAI 兼容连接，返回耗时 | `ai:config:update` | ✅ |
| POST | `/ai/ask` | 智能路由：操作指引或人员问答 | `ai:use` | ✅ |
| POST | `/ai/help/locate` | 操作入口推荐，最多 3 个，按权限过滤 | `ai:help` | ✅ |
| POST | `/ai/people/ask` | 人员问答，字段权限 + AI 可读标记 + 脱敏 | `ai:people` | ✅ |
| GET | `/ai/quick-questions` | 快速问按钮，按所需权限过滤 | `ai:use` | ✅ |
| GET | `/ai/logs` | AI 调用日志分页（功能/问题摘要/模型/耗时/成功） | `ai:log:read` | ✅ |
| GET | `/ai/help-index` | 操作指引入口列表（管理员） | `ai:config:read` | ✅ |
| POST | `/ai/help-index` | 新增入口 | `ai:config:update` | ✅ |
| PUT | `/ai/help-index/{id}` | 修改入口 | `ai:config:update` | ✅ |
| DELETE | `/ai/help-index/{id}` | 删除入口 | `ai:config:update` | ✅ |
| POST | `/ai/documents/ask` | 文档问答（项目成员 + 单文档 ACL 过滤、引用片段、低置信标记） | `ai:doc` | ✅ |
| POST | `/ai/documents/{id}/extract-tasks` | 文档任务提取建议（不直接建任务） | `ai:doc` | ✅ |
| POST | `/ai/documents/{id}/apply-tasks` | 人工确认后批量创建项目任务（走任务创建角色校验） | `ai:doc` + 任务角色 | ✅ |

> 花名册解析只把**表头**发给模型换取列映射，员工数据不出内网；`import.roster.useAi=false` 时使用内置中英文字典。
> AI 助手采用“本地规则优先”：操作入口和人员字段先在本地按权限过滤，再可选交给模型把资料改写成不超过 80 字的回答；模型不可用时自动回退本地答案。
> `/ai/people/ask` 只使用字段权限中 `visible=true && ai_readable=true` 的字段，脱敏规则照常生效；资料之外的信息不进入提示词。
> 所有调用写 `ai_query_log`（功能、模型、耗时、成功标记；人员问答只记录问题与回答摘要，不落完整资料）；密钥以 `APP_CRYPTO_KEY` 派生 AES-GCM 加密，响应永不带明文。
> `/ai/help-index` 的入口由管理员维护；`required_permission` 为空表示登录即可，用户缺少对应权限时该入口不会出现在推荐中。
> 文档问答与任务提取都必须先通过文档的**项目成员 + 单文档 ACL**校验（复用下载权限边界，并写 `AI_READ` 访问日志）；检索只针对当前版本已索引的正文，PDF 等无文本文档返回“无可检索正文”。
> 问答先在本地按问题词检索片段并给出可定位的引用（文档名、版本号、片段序号、摘要），置信度不足或无匹配时返回 `lowConfidence=true` 与“未找到”提示；配置了模型时仅允许模型改写已检索片段，回答必须带 `[n]` 引用（`ai.docQa.requireCitations`），失败自动回退本地答案。
> 任务提取默认在本地按勾选项、待办标记、行动词与截止日期识别建议，返回标题/截止日期/优先级/来源片段/置信度，**不会自动建任务**；`apply-tasks` 必须由用户确认清单后调用，逐条走 `project.task.createRoles` 校验与审计 `AI/APPLY_TASKS`。
> 文档问答与任务提取的开关、上下文长度、回答长度、引用数、建议数、单次创建上限、默认优先级与低置信阈值均可在配置中心调整。

## 12. 审计与版本

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/audit/logs` | 审计日志（按敏感/日常筛选） | `audit:read` |
| GET | `/audit/access-logs` | 敏感访问日志 | `audit:sensitive:read` |
| GET | `/audit/versions` | 版本快照列表 | `audit:version:read` |
| GET | `/audit/versions/{id}` | 版本详情/diff | `audit:version:read` |
| POST | `/audit/rollback` | 发起回滚（走审批） | `audit:rollback` |
| GET | `/audit/approvals` | 审批记录 | `audit:read` |

实体版本为通用不可变快照：`audit.version.enabled` 控制总开关，`audit.version.entities` 指定启用快照的实体类型（当前内置 `EMPLOYEE`，导入创建/更新/回滚也会写入版本链）。`audit.version.maxKept` 控制每个实体保留的版本数（0 表示不限），超出后自动删除最旧版本。版本详情按 `audit.version.sensitiveFields` 对快照与 diff 的 before/after 同时脱敏，拥有 `audit:sensitive:read` 或 `employee:sensitive:read` 的用户可看原值；身份证密文从不写入快照。

`POST /audit/rollback` 只接受配置允许回滚的字段（员工默认 `audit.version.employee.rollbackFields`），回滚动作本身也会生成新的 `ROLLBACK` 版本。`audit.version.rollbackApproval=true` 时走 `ENTITY_ROLLBACK` 审批，同意人数取 `audit.version.rollbackApproverCount`；设为 `false` 则直接生效。`audit.version.rollbackRequireReason` 控制是否必须填写原因。员工回滚会同时维护岗位与汇报关系履历，部门/岗位/上级已不存在或停用时拒绝回滚。

## 13. 配置中心

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/config` | 配置列表 | `config:read` |
| PUT | `/config` | 修改配置（关键配置走审批） | `config:update` |
| GET | `/config/approval-policies` | 审批策略 | `config:read` |
| PUT | `/config/approval-policies` | 修改审批策略（走审批） | `config:approval:update` |
| GET | `/config/chat-policy` | 聊天策略 | `config:read` |
| PUT | `/config/chat-policy` | 修改聊天策略 | `config:update` |
| GET | `/config/doc-policy` | 文档策略 | `config:read` |
| PUT | `/config/doc-policy` | 修改文档策略 | `config:update` |
| GET | `/config/ai-policy` | AI 策略 | `config:read` |
| PUT | `/config/ai-policy` | 修改 AI 策略 | `config:update` |

## 14. 审批

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/approvals` | 我的审批/待办 | 登录 |
| GET | `/approvals/{id}` | 审批详情 | `approval:read` |
| POST | `/approvals/{id}/approve` | 通过 | `approval:approve` |
| POST | `/approvals/{id}/reject` | 拒绝 | `approval:approve` |
| POST | `/approvals/{id}/cancel` | 撤回申请 | 申请人 |
| POST | `/approvals/reminders/run` | 立即检查超时审批并提醒审批人 | `approval:approve` |

审批列表/详情额外返回冲突提示字段（均由公司配置决定是否启用与如何处理）：

- `conflict`：同一对象上是否还有本策略或其他参与策略的待审批申请；
- `conflictingRequestIds`：冲突的待审批申请 ID；
- `staleFields`：申请提交后底层关键信息是否已被他人修改（字段代码，如 `manager`）。

`approval.conflict.policies` 决定哪些策略参与检测（默认 `KEY_INFO,ENTITY_ROLLBACK`）；
`approval.conflict.pendingAction=WARN` 允许重复提交并提示，`BLOCK` 直接拒绝（`40901`）。
通过审批前会按基线核对关键信息：`staleAction=BLOCK` 返回 `40902` 并要求驳回后重新提交，
`NOTIFY` 则提示申请人、写 `CONFLICT_DETECTED` 审计后继续。`approval.conflict.enabled` 是总开关，
`approval.conflict.staleCheck` 可单独关闭数据变更核对。员工更新接口返回的 `message` 也会带重复申请提示。

审批超时提醒：每日按小时检查（`approval.reminder.enabled` 总开关），等待超过
`approval.timeoutHours` 的待审批申请会通知具备 `approval:approve` 的用户（排除申请人），
写 `APPROVAL/REMIND` 审计；同一申请在每个超时窗口内最多提醒一次，超过 `approval.expireHours`
的申请自动标记为 `EXPIRED`。`approval.reminder.maxPerRun` 限制单次处理条数。

## 15. 搜索与系统

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/search` | 全局搜索（员工/项目/任务/文档/公司制度） | 登录 |
| GET | `/health` | 健康检查 | 公开 |
| GET | `/actuator/health` | 运维健康检查 | 内网 |

`GET /search?keyword=&types=EMPLOYEE,PROJECT,TASK,DOCUMENT,POLICY&page=1&size=20`

- 每个范围都先做权限过滤：员工需要 `employee:read`；项目/任务需要 `project:read`/`task:read`
  且只搜索本人参与的项目；文档复用项目成员 + 单文档 ACL + `doc:search`；公司制度需要
  `policy:read`。因此非成员、无文档权限或无制度权限的内容不会出现在结果、计数或摘要中。
- `types` 可省略（使用已启用范围）；未知类型返回 `40001`。`search.employee.enabled`、
  `search.project.enabled`、`search.task.enabled`、`search.document.enabled`、
  `search.policy.enabled` 可分别关闭范围，`search.enabled=false` 时整个接口不可用。
- 返回 `counts`（各范围匹配数）、`total`、`page`、`size` 与 `items`；`size` 受
  `search.maxResults`（默认 20）和 `search.maxPageSize`（默认 50）约束。标题/摘要同时返回
  纯文本和已做 HTML 转义的 `highlightedTitle`/`highlightedSnippet`（命中词包裹 `<mark>`），
  前端可直接渲染高亮而不会引入数据中的 HTML。
- 员工只按姓名/工号检索（不检索可能不可见的手机号/邮箱）；项目按名称/编码/描述，任务按标题/描述，
  文档按名称/原名/正文，公司制度按标题/原文件名/正文。关键词最短长度由
  `search.minKeywordLength` 控制，摘要长度由 `search.snippetLength` 控制。

## 16. 错误码建议

| code | 含义 |
|---|---|
| 0 | 成功 |
| 40001 | 参数错误 |
| 40101 | 未登录/令牌过期 |
| 40301 | 无权限 |
| 40302 | 字段不可见 |
| 40401 | 资源不存在 |
| 40501 | 请求方法不支持 |
| 40901 | 并发冲突/版本冲突/重复数据 |
| 41501 | 请求类型不支持 |
| 40902 | 审批未通过 |
| 42201 | 业务校验失败 |
| 42901 | 限流/配额超限 |
| 50001 | 系统错误 |
| 50002 | AI 服务不可用 |

请求体 JSON 非法或查询参数类型错误统一返回 `40001`；唯一键并发冲突返回 `40901`；不支持的请求方法/内容类型返回 `40501`/`41501`。
员工创建与修改会校验部门、岗位、直属上级必须存在且在职，汇报关系不能成环。

## 17. 紧急恢复

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/recovery/status` | 模式、签署人数/角色、可用操作、本人是否为签署人 | 登录 |
| GET | `/recovery/requests` | 恢复申请列表（管理员/审计看全部，签署人看待签，其他人看本人） | 登录 |
| GET | `/recovery/requests/{id}` | 恢复申请详情（含签署记录） | 申请人/签署人/`audit:read`/`config:recovery` |
| POST | `/recovery/requests` | 发起恢复申请（重置密码/解锁/授予管理员） | `config:recovery` |
| POST | `/recovery/requests/{id}/sign` | 签署；达到配置人数后立即执行 | 配置的签署角色 |
| POST | `/recovery/requests/{id}/reject` | 驳回 | 配置的签署角色 |
| POST | `/recovery/requests/{id}/cancel` | 撤回 | 申请人 |

- 默认 `recovery.mode=DUAL_SIGN`：申请需 `recovery.requiredSigners`（默认 2）名、
  且持有 `recovery.signers`（默认 `BOSS,AUDITOR`）中任一角色的不同用户签署；申请人不能签署自己的申请。
- `recovery.mode=DIRECT` 时立即执行；部署级恢复账号（`recovery.deploymentAccount.usernames`，
  且 `recovery.deploymentAccount.enabled=true`）也直接执行，无需签署。
- 操作白名单 `recovery.allowedActions`：`RESET_PASSWORD` / `UNLOCK` / `GRANT_ADMIN`。
  新密码在受理时即转成 BCrypt 哈希，明文不落库、任何接口都不返回；`GRANT_ADMIN` 的授权天数由
  `recovery.adminGrantDays` 控制（0=长期），角色备注为“紧急恢复通道”。
- 申请有效期 `recovery.expireHours`（默认 24 小时），过期自动置为 `EXPIRED`；
  所有请求/签署/执行/驳回/撤回写 `SECURITY/recovery` 审计。恢复通道不能删除或修改审计日志。

## 18. 保留期清理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/retention/status` | 各清理范围的开关、天数、到期动作 | `config:read` 或 `config:recovery` |
| POST | `/retention/run` | 手动执行清理；`dryRun` 默认 `true`，`scopes` 可选 | `config:recovery` |

- 支持范围：`AUDIT`、`CHAT`、`NOTIFICATION`、`AI`、`RECOVERY`、`PROJECT`、`DOCUMENT`；
  天数配置为 `0` 表示永久保留，预演只统计不删除。每日 03:30 自动执行，受
  `retention.job.enabled` 控制，自动与手动运行都写 `SECURITY/retention/RUN` 审计。
- `AUDIT` 同时清理审计日志、访问日志、登录日志；默认排除 `retention` 模块自身，
  清理日志再按 `audit.cleanLogRetentionDays` 单独过期，因此“谁清理了什么”始终可追溯。
- 项目保留期取项目 `retentionDays`（未设置用 `project.retentionDays.default`），
  起点为结束日期（无结束日期用创建日期）；文档跟随所属项目。
- 到期动作 `project.retention.action` / `doc.retention.action`：
  `ARCHIVE` 归档、`NOTIFY` 仅通知一次（记录 `retention_notified_at`）、`PURGE` 删除、
  `NONE` 关闭。`PURGE` 还必须显式开启 `project.retention.autoPurge` /
  `doc.retention.autoPurge`，避免误删。
- 项目删除会级联删除文档（含版本文件、单文档权限）、文件夹、任务（含评论/参与人/依赖）、
  里程碑、进度快照、成员，并写 `retention/PURGE` 审计；文档删除同样清理全部版本文件。
- 聊天按会话 `retentionDays` 覆盖全局 `chat.retentionDays`，删除后重算会话最后消息时间与预览；
  通知、AI 查询日志、恢复申请分别使用 `notification.retentionDays`、`ai.log.retentionDays`、
  `recovery.logRetentionDays`。审计日志没有手工删除接口，只能通过本清理任务按配置过期。

## 19. 实时推送（WebSocket）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/realtime/capabilities` | 公司实时推送开关、心跳间隔与单账号连接上限 | 登录 |
| WS | `/api/v1/ws?token={accessToken}` | 实时事件通道；仅接受访问令牌，刷新令牌无效 | 有效登录 |

- 握手在升级前校验 JWT 与账号状态，任一不满足返回 `401`；`realtime.enabled=false`
  时返回 `503`。浏览器无法为 WebSocket 设置 Authorization 头，因此使用查询参数
  `token`；非浏览器客户端也可以改用 `Authorization: Bearer ...`。令牌到期后服务端
  在下次推送时关闭该连接。
- 客户端每 `realtime.heartbeatSeconds` 秒发送 `{"type":"PING"}`（或纯文本 `PING`），
  服务端回复 `PONG`。每个账号的连接数受 `realtime.maxConnectionsPerUser` 限制，
  超限的新连接先收到 `TOO_MANY_CONNECTIONS` 再被关闭。
- 服务端推送统一为 `{type, data, at}`：
  - `CONNECTED`：`{userId, heartbeatSeconds}`
  - `CHAT_MESSAGE`：`{conversationId, messageId, senderEmployeeId, senderName, messageType, preview}`，仅推给除发送人外的会话成员
  - `CHAT_RECALL`：`{conversationId, messageId}`，推给全体会话成员
  - `NOTIFICATION`：完整通知对象，仅推给收件人
  - `TOO_MANY_CONNECTIONS`：`{maxConnections}`
- 事件在数据库事务提交后才发送，回滚的写操作不会推到客户端。`realtime.chat.enabled`
  与 `realtime.notification.enabled` 可分别关闭聊天/通知推送；关闭后前端自动回退到
  轮询，业务接口不受影响。

> 当前实现将会话保存在单实例内存中，适合单机/单容器部署；若横向扩容到多实例，需要引入
> Redis pub/sub 或网关粘性会话，否则用户只会收到落在同一实例上的事件。

## 20. 公司制度

公司级规章制度与项目文档分开存储：项目文档受项目成员与单文档 ACL 限制，公司制度由
`policy:read` 统一控制，所有员工可查看；`policy:manage` 控制上传与删除。制度不新增左侧
功能大类，通过工作台常用入口、全局搜索 `POLICY` 结果和 `/policies?focus=id` 深链进入。

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/policies` | 制度列表，支持 `keyword` 搜索标题/原名/正文 | `policy:read` |
| GET | `/policies/capabilities` | 当前公司大小上限、扩展名、AI/搜索开关与本人管理能力 | `policy:read` |
| GET | `/policies/{id}` | 制度详情，返回已提取正文用于在线预览 | `policy:read` |
| GET | `/policies/{id}/download` | 下载原文件（`Content-Disposition` UTF-8） | `policy:read` |
| POST | `/policies` | 上传制度（multipart：`title`、可选 `category`、`file`） | `policy:manage` |
| DELETE | `/policies/{id}` | 删除制度与物理文件 | `policy:manage` |
| POST | `/policies/ask` | 制度 AI 问答（`question`、可选 `policyId`） | `policy:read` + `ai:use` |

- 上传校验：标题必填且不超过 200 字、分类不超过 64 字、文件非空；扩展名白名单来自
  `policy.file.extensions`，大小上限来自 `policy.file.maxMb`。未提供文件或类型/大小不合法返回 `400`。
- 正文提取复用文档提取器：txt/md/docx/xlsx 等可被预览和 AI 检索；PDF/旧版 doc/xls 仍可上传下载，
  但详情返回 `contentText=null`、`searchable=false`，前端提示下载查看。提取字符上限沿用
  `doc.search.extractMaxChars`，超限文件不阻塞上传。
- AI 问答：只检索启用的制度，单次最多 `policy.ai.maxDocs` 份、合计
  `policy.ai.maxContextChars` 字符；回答按片段排序并最多返回 `policy.ai.maxCitations` 条引用。
  模型不可用时使用本地抽取式答案，仍返回引用、置信度与低置信标记；模型改写必须先出现 `[n]`
  引用（受 `ai.docQa.requireCitations` 控制）。`policy.ai.enabled=false` 时问答返回 `400`，
  查看/下载不受影响。
- 访问审计：查看、下载、AI 引用读取分别写 `access_log`（`POLICY/VIEW`、`POLICY/DOWNLOAD`、
  `POLICY/AI_READ`）；上传、删除写不可手工删除的 `POLICY/policy/UPLOAD|DELETE` 审计。
- 全局搜索：`search.policy.enabled` 控制是否包含制度；结果链接为 `/policies?focus={id}`。
