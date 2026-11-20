# 配置项清单

> 所有配置由每家公司初始化时设置，后续按审批规则修改。配置带版本和变更审计。  
> “审批”列表示修改该配置是否需要走审批；“是*”表示按初始化时设定的配置审批规则执行。

## 1. 初始化

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `init.completed` | 是否完成初始化 | false | 是* |
| `company.name` | 公司名称 | - | 否 |
| `company.code` | 公司编码 | - | 否 |
| `init.admin.username` | 初始管理员账号 | - | 是* |
| `init.timezone` | 时区 | Asia/Shanghai | 否 |
| `init.locale` | 语言 | zh-CN | 否 |

## 2. 安全

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `security.password.minLength` | 密码最小长度 | 8 | 否 |
| `security.password.requireComplex` | 复杂度要求 | true | 否 |
| `security.login.maxAttempts` | 失败锁定次数 | 5 | 否 |
| `security.login.lockMinutes` | 锁定时长（分钟） | 30 | 否 |

> 连续失败达到 `maxAttempts` 后锁定 `lockMinutes` 分钟；成功登录清零。管理员可通过紧急恢复 `UNLOCK` 解锁。
| `security.session.timeoutMinutes` | 会话超时 | 120 | 否 |
| `security.mfa.enabled` | 是否启用 MFA | false | 是* |
| `security.mfa.requiredRoles` | 必须 MFA 的角色 | SYS_ADMIN | 是* |
| `security.ip.allowlist` | 管理后台 IP 白名单 | - | 是* |
| `account.requireEmployeeLink` | 新建账号是否必须关联员工档案（一人一账号）；仅部署/紧急恢复账号可临时关闭 | true | 是* |

## 3. 审批

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `approval.keyFields` | 关键字段列表 | salary,idCard,bankAccount,position,manager,org,projectMembership,permission | 是* |
| `approval.quorum` | 关键信息通过人数 | 2 | 是* |
| `approval.applicantIncluded` | 申请人是否计入通过人数 | false | 是* |
| `approval.configChangeQuorum` | 修改审批配置所需人数 | 2 | 是* |
| `approval.timeoutHours` | 审批等待超过多少小时触发提醒 | 24 | 是* |
| `approval.expireHours` | 审批超时关闭 | 72 | 是* |
| `approval.reminder.enabled` | 是否启用审批超时提醒任务 | true | 是* |
| `approval.reminder.maxPerRun` | 单次提醒任务最多处理条数 | 100 | 是* |
| `approval.allowSelfApprove` | 是否允许自批 | false | 是* |
| `approval.conflict.enabled` | 关键信息审批冲突检测总开关 | true | 是* |
| `approval.conflict.policies` | 参与冲突检测的审批策略，逗号分隔 | KEY_INFO,ENTITY_ROLLBACK | 是* |
| `approval.conflict.pendingAction` | 同一对象已有待审批申请时：`WARN` 允许提交并提示；`BLOCK` 拒绝提交 | WARN | 是* |
| `approval.conflict.staleCheck` | 通过前核对申请提交后关键信息是否被他人修改 | true | 是* |
| `approval.conflict.staleAction` | 发现数据已变更时：`BLOCK` 阻止通过并要求驳回重提；`NOTIFY` 提示后继续 | BLOCK | 是* |

> 冲突提示按对象（`entityType` + `entityId`）计算，跨策略生效，例如同一员工的 `KEY_INFO`
> 与 `ENTITY_ROLLBACK` 申请会互相提示。`WARN` 只在列表/详情显示“重复申请”和
> `conflictingRequestIds`；`BLOCK` 在提交时返回 `40901`。`staleFields` 来自申请提交时保存的
> 基线，仅比较策略改写的关键字段，不会暴露基线原文；被他人改成与本次目标相同的值不算冲突。
> `NOTIFY` 会给申请人发送“审批冲突提示”通知并写 `CONFLICT_DETECTED` 审计。基线核对目前适用于
> 提交时保存了关键字段基线的申请（关键信息修改）；回滚等未保存基线的策略只参与重复申请提示。

## 4. 审计

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `audit.enabled` | 是否启用审计 | true | 是* |
| `audit.retentionDays` | 日志保留天数 | 365 | 是* |
| `audit.autoClean` | 到期自动清理 | true | 是* |
| `audit.cleanLogRetentionDays` | 清理动作日志保留天数 | 365 | 是* |
| `retention.job.enabled` | 保留期每日清理任务开关 | true | 是* |
| `audit.realtimeAlert` | 敏感访问实时告警 | false | 否 |
| `audit.auditorUserIds` | 审计员用户 | - | 是* |
| `audit.auditorRoleCodes` | 审计员角色 | AUDITOR | 是* |
| `audit.readLogEnabled` | 敏感读日志 | true | 是* |
| `audit.exportLogEnabled` | 导出日志 | true | 是* |
| `audit.version.enabled` | 实体版本快照总开关 | true | 是* |
| `audit.version.entities` | 启用版本快照的实体类型（逗号分隔） | EMPLOYEE | 是* |
| `audit.version.maxKept` | 每个实体保留的版本数（0=不限） | 0 | 是* |
| `audit.version.rollbackApproval` | 版本回滚是否需要审批 | true | 是* |
| `audit.version.rollbackApproverCount` | 版本回滚所需同意人数 | 2 | 是* |
| `audit.version.rollbackRequireReason` | 版本回滚必须填写原因 | true | 是* |
| `audit.version.sensitiveFields` | 版本快照与 diff 默认脱敏字段 | phone,email,birthDate,address,emergencyContact | 是* |
| `audit.version.employee.rollbackFields` | 员工版本允许回滚的字段 | name,gender,birthDate,phone,email,address,emergencyContact,hireDate,status,org,position,manager | 是* |

## 5. 老板敏感访问

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `boss.access.enabled` | 是否启用敏感访问机制 | true | 是* |
| `boss.access.mode` | 访问模式：REQUEST / DIRECT / DISABLED | REQUEST | 是* |
| `boss.access.approverCount` | 审批通过人数 | 2 | 是* |
| `boss.access.durationMinutes` | 默认单次查看时长（分钟） | 30 | 是* |
| `boss.access.maxDurationMinutes` | 单次查看时长上限（分钟） | 240 | 是* |
| `boss.access.requireReason` | 必须填写用途 | true | 是* |
| `boss.access.allowAllEmployees` | 允许申请全部员工 | false | 是* |

审批策略代码 `BOSS_ACCESS`（策略 ID 5004）。授权只取消当前角色可见字段的脱敏，
不扩大字段可见范围；授予/撤销写 `SECURITY/boss` 审计。

## 6. 字段权限

字段权限不是固定 Key，按 `entity_type + field_code` 动态配置：

| 配置项 | 说明 | 示例 |
|---|---|---|
| `visibility` | 可见角色/数据范围 | `SELF,DIRECT_REPORTS,HR_ADMIN` |
| `maskRule` | 脱敏规则 | `PHONE`、`ID_CARD`、`SALARY_RANGE` |
| `selfEdit` | 本人是否可编辑 | false |
| `exportable` | 是否可导出 | false |
| `aiReadable` | AI 是否可读 | false |
| `sensitive` | 是否敏感字段 | true |
| `keyField` | 是否关键字段 | true |

### 6.1 本人字段可见性

员工“我的资料”里的小眼睛开关不是固定字段，由公司配置：

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `employee.selfVisibility.enabled` | 是否允许员工控制本人字段对普通同事可见 | true | 是* |
| `employee.selfVisibility.fields` | 允许员工隐藏的字段（逗号分隔，区分大小写） | phone,email,address,emergencyContact | 是* |
| `employee.selfVisibility.exemptPermissions` | 不受本人隐藏设置影响的权限（逗号分隔） | employee:sensitive:read | 是* |

隐藏优先于角色字段权限，但不会突破字段权限本来就不允许看的范围；本人始终可见。隐藏后普通同事
拿到的值为 `null`，不会以脱敏形式出现。

## 7. 组织

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `org.tree.visibility` | 组织树可见范围 | SELF_CHAIN | 是* |
| `org.reporting.multipleManagers` | 是否允许多上级 | true | 是* |
| `org.reporting.dottedLine` | 是否允许虚线汇报 | true | 是* |
| `org.employee.multiplePositions` | 是否允许多岗位 | true | 是* |
| `org.employee.multipleDepartments` | 是否允许多部门 | true | 是* |
| `org.leader.canEditSubtree` | 组长是否可编辑子树 | true | 是* |

## 8. 自定义属性

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `attr.maxPerEntity` | 单实体最大属性数 | 200 | 否 |
| `attr.searchable.syncEs` | 可搜索属性同步 ES | true | 否 |
| `attr.timeline.enabled` | 启用时间线属性 | true | 否 |
| `attr.delete.mode` | 删除模式 | SOFT | 是* |
| `attr.typeChange.migrationRequired` | 改类型必须迁移 | true | 是* |

## 9. 导入

| Key | 说明 | 默认 | 审批 | 状态 |
|---|---|---|---|---|
| `import.formats` | 支持格式（当前实现 csv/xlsx/xls） | csv,xlsx,xls | 是* | ✅ |
| `import.employeeEditableFields` | 员工确认可编辑字段 | name,phone,email,address,emergencyContact | 是* | ✅ |
| `import.requireEmployeeConfirm` | 已有员工数据是否需本人确认 | true | 是* | ✅ |
| `import.roster.useAi` | 解析时是否调用已配置 AI 补全列映射（仅发送表头） | true | 是* | ✅ |
| `import.commitRequiresApproval` | 提交是否走审批 | true | 是* | ✅ |
| `import.maxFileMb` | 单文件大小 | 50 | 否 | ⬜ |
| `import.maxRows` | 最大行数 | 5000 | 否 | ⬜ |
| `import.confidenceThreshold` | 低置信阈值 | 0.8 | 否 | ⬜（当前行级置信度已计算） |
| `import.matchKeys` | 老员工匹配键顺序 | employeeNo | 是* | ⬜（当前按工号匹配） |
| `import.ocr.enabled` | 扫描件 OCR | false | 是* | ⬜ |

## 10. 项目与任务

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `project.visibility.default` | 默认可见范围 | MEMBER | 是* |
| `project.retentionDays.default` | 默认保留期 | 3650 | 是* |
| `project.retention.action` | 到期动作：`ARCHIVE`/`NOTIFY`/`PURGE`/`NONE` | ARCHIVE | 是* |
| `project.retention.autoPurge` | 允许自动删除到期项目（`PURGE` 的第二道开关） | false | 是* |
| `project.task.createRoles` | 可创建任务角色 | OWNER,ADMIN,MEMBER | 是* |
| `project.task.editRoles` | 可编辑任务角色 | OWNER,ADMIN,ASSIGNEE | 是* |
| `project.task.deleteRoles` | 可删除任务角色 | OWNER,ADMIN | 是* |
| `project.milestone.manageRoles` | 里程碑管理角色 | OWNER,ADMIN | 是* |
| `project.progress.manageRoles` | 可更新项目进度的项目角色 | OWNER,ADMIN | 是* |
| `project.progress.autoFromTasks` | 按任务完成率自动算 | false | 是* |
| `project.gantt.enabled` | 甘特图 | false | 否 |
| `project.kanban.enabled` | 看板 | false | 否 |

> 任务角色配置支持项目角色 `OWNER/ADMIN/MEMBER/VIEWER`，以及任务级令牌 `ASSIGNEE`（负责人）和 `CREATOR`（创建人）。项目进度模式存放在 `project.progress_mode`（`MANUAL`/`AUTO`），也可用全局配置 `project.progress.autoFromTasks` 统一开启。

## 11. 文档

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `doc.externalShare.enabled` | 外链分享 | false | 是* |
| `doc.onlineEdit.enabled` | 在线编辑 | false | 是* |
| `doc.delete.mode` | 删除模式 | HARD | 是* |
| `doc.retentionDays.default` | 默认保留期 | 3650 | 是* |
| `doc.retention.action` | 到期动作：`ARCHIVE`/`NOTIFY`/`PURGE`/`NONE` | ARCHIVE | 是* |
| `doc.retention.autoPurge` | 允许自动删除到期文档（`PURGE` 的第二道开关） | false | 是* |
| `doc.version.enabled` | 是否启用文档版本历史 | true | 否 |
| `doc.version.uploadRoles` | 可上传新版本的项目角色（`UPLOADER`=当前文件上传人） | OWNER,ADMIN,UPLOADER | 否 |
| `doc.version.rollbackRoles` | 可回滚的项目角色 | OWNER,ADMIN,UPLOADER | 否 |
| `doc.version.viewRoles` | 可查看版本历史的项目角色 | OWNER,ADMIN,UPLOADER,MEMBER,VIEWER | 否 |
| `doc.version.maxKept` | 每文档保留版本数上限（0=不限） | 0 | 否 |
| `doc.search.enabled` | 是否启用文档搜索 | true | 否 |
| `doc.search.maxResults` | 搜索默认返回条数 | 50 | 否 |
| `doc.search.snippetLength` | 搜索摘要长度（字符） | 120 | 否 |
| `doc.search.contentExtensions` | 可提取正文的扩展名 | txt,md,csv,json,xml,html,htm,log,docx,xlsx | 否 |
| `doc.search.extractMaxChars` | 单文档索引最大字符数 | 200000 | 否 |
| `doc.search.extractMaxMb` | 超过该大小不提取正文（MB） | 20 | 否 |
| `doc.download.default` | 默认下载权限 | ROLE_BASED | 是* |
| `doc.print.enabled` | 是否允许打印/打印预览 | true | 是* |
| `doc.print.default` | 未单独授权时的打印权限：`ROLE_BASED`=仅负责人/项目管理员/上传人；`ALL`=项目成员都可；`NONE`=都不可 | ROLE_BASED | 是* |
| `doc.watermark.enabled` | 打印预览是否叠加水印 | false | 否 |
| `doc.watermark.text` | 水印模板，可用 `{name}`/`{employeeNo}`/`{time}`/`{project}` | `{name} {time}` | 否 |
| `doc.file.maxMb` | 单文件大小 | 200 | 否 |
| `doc.virusScan.enabled` | 病毒扫描 | true | 否 |
| `doc.preview.provider` | 预览实现 | kkfileview | 否 |
| `doc.uploadRoles` | 可上传角色 | OWNER,ADMIN,MEMBER | 是* |
| `doc.deleteRoles` | 可删除角色 | OWNER | 是* |

## 12. 聊天

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `chat.enabled` | 聊天总开关 | true | 否 |
| `chat.retentionDays` | 消息默认保留天数（0=不限） | 365 | 否 |
| `chat.direct.enabled` | 是否允许新建单聊 | true | 否 |
| `chat.group.enabled` | 是否允许新建群聊 | true | 否 |
| `chat.group.maxMembers` | 群人数上限（修改后立即约束已有群） | 200 | 否 |
| `chat.group.manageRoles` | 可改群设置的会话角色 | OWNER | 否 |
| `chat.memberManageRoles` | 可增删群成员的会话角色 | OWNER | 否 |
| `chat.message.maxLength` | 单条消息最大字符数 | 2000 | 否 |
| `chat.recall.windowMinutes` | 允许撤回时间窗（分钟，0=不限） | 2 | 否 |
| `chat.burnAfterRead.default` | 新建会话默认阅后即焚 | false | 否 |
| `chat.antiRecall.default` | 新建会话默认防撤回 | true | 否 |
| `chat.history.defaultSize` | 消息分页默认条数 | 30 | 否 |
| `chat.history.visibleBeforeJoin` | 新成员可看入群前历史 | true | 否 |
| `chat.notify.membership` | 成员变更写入站内通知 | true | 否 |
| `chat.file.maxMb` | 聊天文件大小上限（MB，0=不限制） | 100 | 否 |
| `chat.file.imageMaxMb` | 聊天图片大小上限（MB，0=不限制） | 10 | 否 |
| `chat.attachment.enabled` | 图片/文件消息总开关（关闭只影响新上传，历史附件仍可下载） | true | 否 |
| `chat.attachment.direct.enabled` | 单聊是否允许图片/文件消息 | true | 否 |
| `chat.attachment.group.enabled` | 群聊是否允许图片/文件消息 | true | 否 |
| `chat.attachment.burnAfterRead.enabled` | 阅后即焚会话是否允许发送附件 | true | 否 |
| `chat.attachment.allowedExtensions` | 允许上传的扩展名，逗号分隔；`*` 表示不限制 | pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,csv,json,xml,zip,rar,7z,png,jpg,jpeg,gif,webp,bmp | 否 |
| `chat.attachment.imageExtensions` | 按图片展示、预览的扩展名，逗号分隔 | png,jpg,jpeg,gif,webp,bmp | 否 |
| `chat.attachment.orphanHours` | 已上传未发送附件的保留小时数，过期由保留期清理任务删除 | 24 | 否 |
| `chat.adminAccess.enabled` | 管理员查看聊天总开关 | true | 否 |
| `chat.adminAccess.mode` | 查看模式：`REQUEST`（审批）/`DIRECT`（直接）/`DISABLED`（关闭） | REQUEST | 否 |
| `chat.adminAccess.quorum` | 审批模式下所需同意人数 | 2 | 否 |
| `chat.adminAccess.durationMinutes` | 默认授权时长（分钟） | 60 | 否 |
| `chat.adminAccess.maxDurationMinutes` | 单次授权最长时长（分钟） | 1440 | 否 |
| `chat.adminAccess.requireReason` | 申请必须填写用途 | true | 否 |

> `chat.burnAfterRead.default`/`chat.antiRecall.default` 只决定新会话的初值，成员可在会话设置里逐会话调整。管理员查看使用审批策略 `CHAT_ACCESS`；`chat.adminAccess.quorum` 覆盖策略表人数。`chat.adminAccess.enabled`/`mode` 与审批策略均可由各公司自行调整，无需改代码。附件上传与下载每次重新读取上述配置；已发送附件在消息撤回或阅后即焚时删除文件，未发送附件按 `chat.attachment.orphanHours` 清理。焚毁倒计时、已读回执偏好等尚未实现，未列入上表。

## 13. 通知

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `notification.channels` | 渠道 | in-app | 是* |
| `notification.retentionDays` | 通知保留 | 180 | 是* |
| `notification.digest.enabled` | 聚合通知 | false | 否 |
| `notification.email.enabled` | 邮件 | false | 是* |
| `notification.wecom.enabled` | 企业微信 | false | 是* |
| `notification.dingtalk.enabled` | 钉钉 | false | 是* |
| `notification.preferences.enabled` | 员工自助通知偏好总开关 | true | 否 |
| `notification.preferences.types` | 允许员工自行开关的通知类型（逗号分隔，未列出的类型始终送达） | APPROVAL,TASK,IMPORT,CHAT,RETENTION | 否 |
| `notification.preferences.labels` | 偏好显示名，格式 `类型:名称`，逗号分隔 | APPROVAL:审批,TASK:任务,IMPORT:花名册,CHAT:聊天,RETENTION:保留期 | 否 |
| `notification.preferences.defaultMuted` | 新用户默认关闭的类型（逗号分隔，可留空） | 空 | 否 |
| `notification.preferences.behavior` | 关闭后的行为：`SILENT` 仍写入站内记录但不实时推送；`SUPPRESS` 不生成该类通知 | SILENT | 否 |

> 通知偏好存于 `notification_preference`（按用户 + 类型唯一），每家公司用 `types`/`labels`/`defaultMuted` 决定员工能看到和调整的范围，`behavior` 决定关闭后的处理方式；`enabled=false` 时所有偏好失效，通知按原规则送达。安全类等未列入 `types` 的通知不会被个人关闭。修改偏好写 `PERSONAL/notification/PREFERENCE_UPDATE` 审计。

## 14. AI

> 已落地：厂商、地址、模型与 API Key 存放在 `ai_provider_config` 表（管理端 `/ai/config` 维护），Key 使用 AES-GCM 加密、接口只回显掩码；花名册解析开关为 `import.roster.useAi`；操作指引入口存 `ai_help_index`、快速问存 `ai_quick_question`，均由管理员维护；AI 可读字段直接复用 `sys_field_permission.ai_readable`。文档问答与任务提取的开关、上下文/回答/引用长度、建议数、单次创建上限、默认优先级与低置信阈值已落地（V11），检索始终先过项目成员与单文档 ACL；下表其余按功能选模型、配额、日志保留等仍为后续规划。

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `ai.enabled` | 是否启用 AI | true | 是* |
| `ai.mode` | 模式 | EXTERNAL | 是* |
| `ai.provider` | 厂商 | openai-compatible | 是* |
| `ai.baseUrl` | API 地址 | - | 是* |
| `ai.apiKey` | API Key（加密） | - | 是* |
| `ai.model.default` | 默认模型 | - | 是* |
| `ai.model.roster` | 花名册解析模型 | - | 是* |
| `ai.model.docQa` | 文档问答模型 | - | 是* |
| `ai.model.peopleQa` | 人员问答模型 | - | 是* |
| `ai.model.help` | 操作指引模型 | - | 是* |
| `ai.embedding.provider` | 向量模型厂商 | - | 是* |
| `ai.embedding.model` | 向量模型 | - | 是* |
| `ai.temperature` | 温度 | 0.2 | 否 |
| `ai.maxTokens` | 最大输出 | 800 | 否 |
| `ai.docQa.enabled` | 是否启用文档问答 | true | 否 |
| `ai.docQa.maxContextChars` | 文档问答最多检索正文长度 | 12000 | 否 |
| `ai.docQa.maxAnswerChars` | 文档问答最大字数 | 300 | 否 |
| `ai.docQa.maxCitations` | 文档问答最多引用片段数 | 3 | 否 |
| `ai.docQa.minContentChars` | 低于该正文长度提示无可用内容 | 20 | 否 |
| `ai.docTasks.enabled` | 是否启用文档任务提取 | true | 否 |
| `ai.docTasks.maxSuggestions` | 单次任务提取最多建议数 | 20 | 否 |
| `ai.docTasks.maxApply` | 单次确认创建任务上限 | 20 | 否 |
| `ai.docTasks.maxContextChars` | 任务提取最多读取正文长度 | 12000 | 否 |
| `ai.docTasks.defaultPriority` | 未识别优先级时的默认值 | NORMAL | 否 |
| `ai.docTasks.lowConfidenceThreshold` | 任务建议低置信阈值 | 0.6 | 否 |
| `ai.peopleQa.maxAnswerChars` | 人员问答最大字数 | 300 | 否 |
| `ai.docQa.requireCitations` | 必须引用 | true | 是* |
| `ai.lowConfidenceThreshold` | 低置信阈值 | 0.6 | 否 |
| `ai.allowedFields` | AI 可读字段 | 按字段权限 | 是* |
| `ai.quickQuestions` | 快速问 | 系统预置 | 否 |
| `ai.quota.perUserPerDay` | 每用户每日配额 | 50 | 否 |
| `ai.quota.perDeptPerDay` | 每部门每日配额 | 500 | 否 |
| `ai.log.retentionDays` | AI 日志保留 | 180 | 是* |
| `ai.external.consentConfirmed` | 外部模型风险确认 | false | 是* |
| `ai.local.enabled` | 本地模型适配 | false | 是* |
| `ai.local.baseUrl` | 本地模型地址 | - | 是* |

## 15. 搜索

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `search.engine` | 引擎 | mysql | 否 |
| `search.es.enabled` | 启用 ES | false | 否 |
| `search.es.endpoint` | ES 地址 | - | 否 |
| `search.ik.enabled` | IK 分词 | true | 否 |
| `search.page.maxSize` | 最大分页 | 100 | 否 |
| `search.enabled` | 全局搜索开关 | true | 是* |
| `search.employee.enabled` | 员工范围 | true | 是* |
| `search.project.enabled` | 项目范围 | true | 是* |
| `search.task.enabled` | 任务范围 | true | 是* |
| `search.document.enabled` | 文档范围 | true | 是* |
| `search.maxResults` | 默认每页条数 | 20 | 是* |
| `search.maxPageSize` | 单页最大条数 | 50 | 是* |
| `search.snippetLength` | 摘要长度 | 120 | 是* |
| `search.minKeywordLength` | 关键词最短长度 | 1 | 是* |

## 16. 紧急恢复

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `recovery.enabled` | 启用紧急恢复 | true | 是* |
| `recovery.mode` | 模式：DUAL_SIGN / DIRECT | DUAL_SIGN | 是* |
| `recovery.requiredSigners` | 需要的签署人数 | 2 | 是* |
| `recovery.signers` | 有签署资格的角色代码 | BOSS,AUDITOR | 是* |
| `recovery.allowedActions` | 允许的恢复操作 | RESET_PASSWORD,UNLOCK,GRANT_ADMIN | 是* |
| `recovery.deploymentAccount.enabled` | 启用部署级恢复账号 | true | 是* |
| `recovery.deploymentAccount.usernames` | 部署级恢复账号用户名 | deploy | 是* |
| `recovery.adminGrantDays` | 恢复授予管理员的天数，0=长期 | 1 | 是* |
| `recovery.expireHours` | 恢复申请有效小时数 | 24 | 是* |
| `recovery.logRetentionDays` | 恢复记录保留天数，0=永久 | 0 | 是* |

## 17. 实时推送

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `realtime.enabled` | 是否启用 WebSocket 实时推送；关闭后前端退回轮询，握手返回 503 | true | 否 |
| `realtime.chat.enabled` | 实时推送聊天消息与撤回事件 | true | 否 |
| `realtime.notification.enabled` | 实时推送站内通知 | true | 否 |
| `realtime.heartbeatSeconds` | 客户端心跳间隔（秒），0=不发送心跳 | 25 | 否 |
| `realtime.maxConnectionsPerUser` | 单账号最大实时连接数，0=不限制 | 5 | 否 |

> 三个开关均在每次推送时重新读取，修改立即生效；`realtime.enabled=false` 时新握手被拒绝，
> 已建立的连接不再收到业务事件。事件只在事务提交后发送，配置中心的修改同样受审计。

## 18. 公司制度

| Key | 说明 | 默认 | 审批 |
|---|---|---|---|
| `policy.file.maxMb` | 单个制度文件大小上限（MB） | 50 | 是* |
| `policy.file.extensions` | 允许上传的扩展名，逗号分隔 | pdf,doc,docx,xls,xlsx,txt,md | 是* |
| `policy.ai.enabled` | 是否允许员工对制度进行 AI 问答 | true | 是* |
| `policy.ai.maxDocs` | 单次问答最多检索多少份制度 | 50 | 是* |
| `policy.ai.maxContextChars` | 单次问答最多送入多少正文字符 | 30000 | 是* |
| `policy.ai.maxCitations` | 单次问答最多返回几条引用 | 5 | 是* |
| `search.policy.enabled` | 全局搜索是否包含公司制度 | true | 是* |

> 正文提取仍复用 `doc.search.extractMaxChars` / `doc.search.extractMaxMb` 索引上限；PDF 和旧版
> doc/xls 可以上传下载，但暂不进入在线预览或 AI 检索。关闭 `policy.ai.enabled` 只影响问答，
> 不影响制度列表、查看与下载。
