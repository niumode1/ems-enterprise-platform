export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId?: string | null
}

export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface UserInfo {
  userId: string
  username: string
  displayName: string
  employeeId: string | null
  roles: string[]
  permissions: string[]
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserInfo
}

export interface RoleView {
  id: string
  code: string
  name: string
}

export interface UserView {
  id: string
  username: string
  displayName: string
  employeeId: string | null
  employeeName: string | null
  employeeNo: string | null
  status: string
  mfaEnabled: boolean
  lastLoginAt: string | null
  createdAt: string | null
  roles: RoleView[]
}

export interface RoleAssignmentView {
  id: string
  roleId: string
  roleCode: string | null
  roleName: string | null
  startDate: string | null
  endDate: string | null
  status: string
  note: string | null
  grantedBy: string | null
  revokedAt: string | null
  createdAt: string | null
}

export interface EmployeeView {
  id: string
  employeeNo: string
  name: string
  gender: string | null
  birthDate: string | null
  phone: string | null
  email: string | null
  idCard: string | null
  address: string | null
  emergencyContact: string | null
  hireDate: string | null
  status: string
  orgId: string | null
  orgName: string | null
  positionId: string | null
  positionName: string | null
  managerId: string | null
  managerName: string | null
  maskedFields: string[]
}

export interface RelationEmployee {
  id: string
  employeeNo: string
  name: string
}

export interface EmployeeRelations {
  employeeId: string
  manager: RelationEmployee | null
  superiors: RelationEmployee[]
  subordinates: RelationEmployee[]
  siblings: RelationEmployee[]
}

export interface UpdateResult {
  status: string
  approvalId: string | null
  message: string
  employee: EmployeeView | null
}

export interface FieldVisibilityView {
  fieldCode: string
  label: string
  visible: boolean
  canHide: boolean
}

export interface OrgNode {
  id: string
  parentId: string | null
  name: string
  code: string | null
  type: string
  leaderEmployeeId: string | null
  path: string | null
  sort: number | null
  status: string
  employeeCount: number
  children: OrgNode[]
}

export interface ReportingNode {
  employeeId: string
  name: string
  employeeNo: string
  positionId: string | null
  positionName: string | null
  children: ReportingNode[]
}

export interface Position {
  id: string
  code: string
  name: string
  level: string | null
  status: string
}

export interface ApprovalActionView {
  id: string
  approverId: string
  approverName: string | null
  action: string
  comment: string | null
  createdAt: string | null
}

export interface ApprovalView {
  id: string
  policyCode: string
  policyName: string | null
  entityType: string | null
  entityId: string | null
  payload: Record<string, unknown>
  reason: string | null
  status: string
  requesterId: string
  requesterName: string | null
  currentApprovals: number
  requiredApprovals: number
  myAction: string | null
  expiresAt: string | null
  createdAt: string | null
  updatedAt: string | null
  conflict: boolean
  conflictingRequestIds: string[]
  staleFields: string[]
  actions: ApprovalActionView[]
}

export interface AuditLog {
  id: string
  category: string
  module: string
  action: string
  entityType: string | null
  entityId: string | null
  actorId: string | null
  actorName: string | null
  ip: string | null
  beforeJson: string | null
  afterJson: string | null
  reason: string | null
  approvalId: string | null
  createdAt: string | null
}

export interface EntityVersionView {
  id: string
  entityType: string
  entityId: string
  versionNo: number
  changeType: string
  changedFields: string[]
  operatorId: string | null
  operatorName: string | null
  approvalId: string | null
  createdAt: string | null
}

export interface EntityVersionDetail {
  version: EntityVersionView
  snapshot: Record<string, unknown>
  diff: Record<string, { before: unknown; after: unknown }>
  maskedFields: string[]
}

export interface RollbackResult {
  status: string
  approvalId: string | null
  newVersionId: string | null
  message: string
}

export interface AccessLog {
  id: string
  userId: string
  resourceType: string
  resourceId: string | null
  action: string
  sensitive: boolean
  ip: string | null
  createdAt: string | null
}

export interface ConfigItem {
  id: string
  configKey: string
  configValue: string | null
  valueType: string
  configGroup: string
  description: string | null
  version: number
  updatedBy: string | null
  updatedAt: string | null
}

export interface ConfigUpdateResult {
  status: string
  approvalId: string | null
  message: string
  value: string | null
}

export interface FieldPermission {
  id: string
  entityType: string
  fieldCode: string
  roleId: string
  scopeType: string
  visibility: string
  maskRule: string
  selfEdit: boolean
  exportable: boolean
  aiReadable: boolean
  sensitive: boolean
  keyField: boolean
}

export interface ApprovalPolicy {
  id: string
  policyCode: string
  name: string
  entityType: string
  fieldCodes: string | null
  quorum: number
  applicantIncluded: boolean
  changeQuorum: number
  enabled: boolean
}

export interface CustomFieldDef {
  id: string
  entityType: string
  fieldCode: string
  name: string
  fieldType: string
  options: string[]
  timeline: boolean
  visibility: string
  maskRule: string
  sensitive: boolean
  selfEditable: boolean
  required: boolean
  sort: number
  status: string
}

export interface CustomFieldValue {
  defId: string
  fieldCode: string
  name: string
  fieldType: string
  options: string[]
  timeline: boolean
  value: string | null
  startDate: string | null
  endDate: string | null
  visible: boolean
  masked: boolean
  editable: boolean
}

export interface ProjectView {
  id: string
  name: string
  code: string | null
  description: string | null
  ownerId: string
  ownerName: string | null
  orgId: string | null
  status: string
  startDate: string | null
  endDate: string | null
  retentionDays: number | null
  downloadPolicy: string
  progress: number
  progressMode: string
  myRole: string
  memberCount: number
  documentCount: number
  createdAt: string | null
}

export interface TaskParticipantView {
  employeeId: string
  name: string | null
  employeeNo: string | null
}

export interface TaskRefView {
  id: string
  title: string | null
  status: string | null
}

export interface TaskView {
  id: string
  projectId: string
  title: string
  description: string | null
  status: string
  priority: string
  assigneeEmployeeId: string | null
  assigneeName: string | null
  creatorEmployeeId: string | null
  creatorName: string | null
  startDate: string | null
  dueDate: string | null
  progress: number
  sort: number
  completedAt: string | null
  createdAt: string | null
  updatedAt: string | null
  participants: TaskParticipantView[]
  dependencies: TaskRefView[]
  commentCount: number
  canEdit: boolean
  canDelete: boolean
}

export interface TaskCommentView {
  id: string
  taskId: string
  authorEmployeeId: string
  authorName: string | null
  content: string
  createdAt: string | null
}

export interface MilestoneView {
  id: string
  projectId: string
  title: string
  description: string | null
  dueDate: string | null
  status: string
  reachedAt: string | null
  createdAt: string | null
}

export interface ProjectProgressView {
  projectId: string
  progress: number
  mode: string
  taskTotal: number
  taskDone: number
  updatedAt: string | null
}

export interface ProgressSnapshotView {
  id: string
  projectId: string
  progress: number
  taskTotal: number
  taskDone: number
  note: string | null
  createdByName: string | null
  createdAt: string | null
}

export interface ProjectMemberView {
  id: string
  projectId: string
  employeeId: string
  employeeName: string | null
  employeeNo: string | null
  memberRole: string
}

export interface ProjectFolder {
  id: string
  projectId: string
  parentId: string | null
  name: string
}

export interface DocumentView {
  id: string
  projectId: string
  folderId: string | null
  name: string
  originalName: string | null
  sizeBytes: number
  contentType: string | null
  uploaderId: string
  uploaderName: string | null
  createdAt: string | null
  canDownload: boolean
  canPrint: boolean
  canDelete: boolean
}

export interface DocumentPermissionView {
  id: string
  documentId: string
  employeeId: string
  employeeName: string | null
  canView: boolean
  canDownload: boolean
  canPrint: boolean
  canDelete: boolean
}

export interface DocumentPrintView {
  id: string
  name: string
  originalName: string | null
  contentType: string | null
  previewable: boolean
  content: string | null
  watermarkEnabled: boolean
  watermarkText: string | null
}

export interface RealtimeCapabilities {
  enabled: boolean
  chatEnabled: boolean
  notificationEnabled: boolean
  heartbeatSeconds: number
  maxConnectionsPerUser: number
}

export interface NotificationPreferenceItem {
  type: string
  label: string
  muted: boolean
}

export interface NotificationPreferencesView {
  enabled: boolean
  behavior: string
  preferences: NotificationPreferenceItem[]
}

export interface NotificationItem {
  id: string
  userId: string
  notificationType: string
  title: string
  content: string | null
  link: string | null
  read: boolean
  createdAt: string | null
}

export interface ImportBatchView {
  id: string
  batchNo: string
  fileName: string
  fileSize: number
  format: string
  status: string
  totalRows: number
  parsedRows: number
  lowConfidenceRows: number
  pendingConfirmRows: number
  confirmedRows: number
  rejectedRows: number
  committedRows: number
  aiUsed: boolean
  aiModel: string | null
  errorMessage: string | null
  createdByName: string | null
  createdAt: string | null
  committedAt: string | null
}

export interface ImportRowView {
  id: string
  rowNo: number
  batchNo: string | null
  values: Record<string, unknown>
  raw: Record<string, unknown>
  confidence: number
  status: string
  matchedEmployeeId: string | null
  matchedEmployeeName: string | null
  confirmUserId: string | null
  confirmUserName: string | null
  rejectReason: string | null
  errorMessage: string | null
  updatedAt: string | null
}

export interface PushResult {
  pushed: number
  skipped: number
  createdAsNew: number
  batch: ImportBatchView
}

export interface CommitResult {
  status: string
  approvalId: string | null
  message: string
  batch: ImportBatchView
}

export interface AiProviderView {
  id: string
  name: string
  providerType: string
  baseUrl: string
  model: string
  enabled: boolean
  isDefault: boolean
  hasApiKey: boolean
  maskedApiKey: string | null
  updatedAt: string | null
}

export interface AiTestResult {
  success: boolean
  message: string
  latencyMs: number
}

export interface AiHelpHit {
  title: string
  route: string
  description: string | null
  reason: string
}

export interface AiCitation {
  employeeId: string
  name: string
  employeeNo: string | null
}

export interface AiPeopleAnswer {
  answer: string
  citations: AiCitation[]
  maskedFields: string[]
  aiUsed: boolean
}

export interface AiAskResult {
  feature: string
  answer: string
  locations: AiHelpHit[]
  citations: AiCitation[]
  maskedFields: string[]
  aiUsed: boolean
}

export interface AiQuickQuestion {
  id: string
  question: string
  feature: string
}

export interface AiQueryLogView {
  id: string
  feature: string
  questionSummary: string | null
  model: string | null
  success: boolean
  latencyMs: number
  answerSummary: string | null
  createdAt: string | null
}

export interface AiHelpIndexView {
  id: string
  title: string
  keywords: string
  route: string
  description: string | null
  requiredPermission: string | null
  sort: number
  enabled: boolean
}

export interface AiHelpIndexPayload {
  title: string
  keywords?: string
  route: string
  description?: string | null
  requiredPermission?: string | null
  sort?: number
  enabled?: boolean
}

export interface ChatConversationView {
  id: string
  type: 'DIRECT' | 'GROUP'
  name: string
  ownerId: string | null
  ownerName: string | null
  memberCount: number
  burnAfterRead: boolean
  antiRecall: boolean
  retentionDays: number | null
  lastMessagePreview: string | null
  lastMessageAt: string | null
  unreadCount: number
  myRole: string
  createdAt: string | null
  canManageSettings: boolean
  canManageMembers: boolean
  canLeave: boolean
  attachmentUploadAllowed: boolean
  attachmentMaxSizeMb: number
  attachmentImageMaxSizeMb: number
}

export interface ChatAttachmentView {
  id: string
  conversationId: string
  messageId: string | null
  kind: 'IMAGE' | 'FILE'
  originalName: string
  contentType: string
  sizeBytes: number
  createdAt: string | null
}

export interface ChatAttachmentCapabilities {
  enabled: boolean
  directEnabled: boolean
  groupEnabled: boolean
  burnAfterReadEnabled: boolean
  maxSizeMb: number
  imageMaxSizeMb: number
  allowedExtensions: string[]
  imageExtensions: string[]
}

export interface ChatMemberView {
  employeeId: string
  employeeName: string | null
  role: string
  status: string
  mutedUntil: string | null
  lastReadAt: string | null
  joinedAt: string | null
}

export interface ChatConversationDetail {
  conversation: ChatConversationView
  members: ChatMemberView[]
}

export interface ChatMessageView {
  id: string
  conversationId: string
  senderId: string
  senderName: string | null
  type: string
  content: string
  recalled: boolean
  burnAfterRead: boolean
  burnedForMe: boolean
  mine: boolean
  readCount: number
  createdAt: string | null
  attachment: ChatAttachmentView | null
}

export interface SearchItemView {
  type: string
  id: string
  title: string | null
  subtitle: string | null
  snippet: string | null
  link: string
  highlightedTitle: string | null
  highlightedSnippet: string | null
}

export interface SearchResponseView {
  keyword: string
  total: number
  page: number
  size: number
  types: string[]
  counts: Record<string, number>
  items: SearchItemView[]
}

export interface BossAccessGrantView {
  id: string
  userId: string
  userName: string | null
  employeeId: string | null
  employeeName: string | null
  reason: string | null
  approvalId: string | null
  status: string
  active: boolean
  startsAt: string | null
  expiresAt: string | null
  revokedAt: string | null
  createdAt: string | null
}

export interface BossAccessStatusView {
  mode: string
  enabled: boolean
  canRequest: boolean
  requireReason: boolean
  approverCount: number
  defaultDurationMinutes: number
  maxDurationMinutes: number
  allowAllEmployees: boolean
  grants: BossAccessGrantView[]
}

export interface BossAccessRequestResult {
  status: string
  approvalId: string | null
  grantId: string | null
  message: string
  grant: BossAccessGrantView | null
}

export interface RecoverySignatureView {
  id: string
  signerId: string
  signerName: string | null
  signerRole: string | null
  comment: string | null
  createdAt: string | null
}

export interface RecoveryRequestView {
  id: string
  action: string
  targetUserId: string
  targetUsername: string | null
  targetDisplayName: string | null
  reason: string | null
  status: string
  requesterId: string
  requesterName: string | null
  requiredSigners: number
  signatureCount: number
  expiresAt: string | null
  appliedAt: string | null
  createdAt: string | null
  signatures: RecoverySignatureView[]
}

export interface RecoveryStatusView {
  enabled: boolean
  mode: string
  requiredSigners: number
  signerRoles: string[]
  allowedActions: string[]
  deploymentAccountEnabled: boolean
  deploymentAccount: boolean
  signer: boolean
  canRequest: boolean
}

export interface RecoveryRequestResult {
  status: string
  request: RecoveryRequestView
  message: string
}

export interface RetentionScopeStatus {
  scope: string
  enabled: boolean
  retentionDays: number
  action: string
  detail: string | null
}

export interface RetentionStatusView {
  jobEnabled: boolean
  scopes: RetentionScopeStatus[]
}

export interface RetentionScopeResult {
  scope: string
  retentionDays: number
  action: string
  matched: number
  affected: number
  dryRun: boolean
  note: string | null
}

export interface RetentionRunResult {
  dryRun: boolean
  results: RetentionScopeResult[]
}

export interface ApprovalReminderResult {
  checked: number
  reminded: number
  expired: number
}

export interface PolicyView {
  id: string
  title: string
  category: string | null
  originalName: string
  contentType: string | null
  sizeBytes: number
  uploaderId: string | null
  uploaderName: string | null
  searchable: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface PolicyDetail extends PolicyView {
  contentText: string | null
}

export interface PolicySearchResult {
  id: string
  title: string
  category: string | null
  snippet: string | null
  updatedAt: string | null
}

export interface PolicyCapabilities {
  maxFileMb: number
  allowedExtensions: string[]
  aiEnabled: boolean
  searchEnabled: boolean
  canManage: boolean
  canAskAi: boolean
}

export interface PolicyCitation {
  policyId: string
  title: string
  chunkIndex: number
  snippet: string
}

export interface PolicyAnswer {
  answer: string
  citations: PolicyCitation[]
  confidence: number
  lowConfidence: boolean
  aiUsed: boolean
  citationsRequired: boolean
}
