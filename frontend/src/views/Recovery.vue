<template>
  <div class="page">
    <el-card shadow="never">
      <div class="header">
        <div>
          <div class="title">紧急恢复</div>
          <div class="subtitle">
            双签或部署级账号执行的应急通道。密码只保存哈希，申请、签署、执行全部写入安全审计。
          </div>
        </div>
        <el-tag :type="status?.enabled ? 'success' : 'info'" effect="plain">
          {{ status?.enabled ? '已启用' : '未启用' }}
        </el-tag>
      </div>

      <el-descriptions v-if="status" :column="4" border size="small" class="summary">
        <el-descriptions-item label="执行模式">
          {{ modeLabel(status.mode) }}
        </el-descriptions-item>
        <el-descriptions-item label="默认签署人数">
          {{ status.requiredSigners }} 人
        </el-descriptions-item>
        <el-descriptions-item label="签署角色">
          {{ status.signerRoles.join('、') || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="部署级账号">
          {{ status.deploymentAccountEnabled ? (status.deploymentAccount ? '当前账号可用' : '已开启') : '未开启' }}
        </el-descriptions-item>
        <el-descriptions-item label="可用操作" :span="2">
          {{ status.allowedActions.map(actionLabel).join('、') || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="当前身份">
          {{ status.deploymentAccount ? '部署级恢复账号' : status.signer ? '签署人' : '普通账号' }}
          <span v-if="status.canRequest">，可提交申请</span>
        </el-descriptions-item>
        <el-descriptions-item label="有效期">
          申请过期后自动失效，需重新提交
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="status && !status.enabled"
        type="info"
        :closable="false"
        title="公司已关闭紧急恢复通道；如需使用请联系系统管理员。"
      />
    </el-card>

    <el-card v-if="status?.canRequest" shadow="never">
      <template #header>提交恢复申请</template>
      <el-form :model="form" label-width="100px" class="request-form">
        <el-form-item label="恢复操作" required>
          <el-select v-model="form.action" style="width: 260px">
            <el-option
              v-for="action in status.allowedActions"
              :key="action"
              :label="actionLabel(action)"
              :value="action"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标账号" required>
          <el-select
            v-if="users.length"
            v-model="form.userId"
            filterable
            clearable
            placeholder="选择账号"
            style="width: 320px"
            @change="form.username = ''"
          >
            <el-option
              v-for="user in users"
              :key="user.id"
              :label="`${user.displayName}（${user.username}）`"
              :value="user.id"
            />
          </el-select>
          <el-input
            v-else
            v-model="form.username"
            placeholder="输入目标账号用户名"
            style="width: 320px"
          />
          <span class="hint">仅紧急情况下使用，请确认恢复对象。</span>
        </el-form-item>
        <el-form-item v-if="form.action === 'RESET_PASSWORD'" label="新密码" required>
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            placeholder="至少满足公司密码策略"
            style="width: 320px"
          />
        </el-form-item>
        <el-form-item label="恢复事由">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请说明紧急情况"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitRequest">提交申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">恢复申请</span>
        <el-button @click="loadAll">刷新</el-button>
      </div>
      <el-table :data="requests" v-loading="loading" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="signatures">
              <div v-if="!row.signatures.length" class="muted">暂无签署记录</div>
              <div v-for="signature in row.signatures" :key="signature.id" class="signature-row">
                <el-tag size="small" effect="plain">{{ signature.signerRole || 'SIGNER' }}</el-tag>
                <span>{{ signature.signerName || signature.signerId }}</span>
                <span class="muted">{{ signature.createdAt }}</span>
                <span v-if="signature.comment" class="signature-comment">{{ signature.comment }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="目标账号" width="160">
          <template #default="{ row }">
            {{ row.targetDisplayName || row.targetUsername || row.targetUserId }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="事由" min-width="200" show-overflow-tooltip />
        <el-table-column prop="requesterName" label="申请人" width="110" />
        <el-table-column label="签署进度" width="100">
          <template #default="{ row }">
            {{ row.signatureCount }} / {{ row.requiredSigners }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" label="有效期至" width="170" />
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <template v-if="canSign(row)">
                <el-button link type="success" @click="sign(row)">签署</el-button>
                <el-button link type="danger" @click="reject(row)">驳回</el-button>
              </template>
              <el-button
                v-else-if="row.requesterId === auth.user?.userId"
                link
                type="warning"
                @click="cancel(row)"
              >撤回</el-button>
              <span v-else class="muted">—</span>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!requests.length" description="暂无恢复申请" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { pageUsers } from '@/api/users'
import {
  cancelRecoveryRequest,
  createRecoveryRequest,
  getRecoveryStatus,
  listRecoveryRequests,
  rejectRecoveryRequest,
  signRecoveryRequest,
} from '@/api/recovery'
import type {
  RecoveryRequestView,
  RecoveryStatusView,
  UserView,
} from '@/types'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const auth = useAuthStore()
const status = ref<RecoveryStatusView | null>(null)
const requests = ref<RecoveryRequestView[]>([])
const users = ref<UserView[]>([])
const loading = ref(false)
const submitting = ref(false)
const form = reactive({
  action: 'RESET_PASSWORD',
  userId: '',
  username: '',
  reason: '',
  newPassword: '',
})

onMounted(() => {
  void loadAll()
})

async function loadAll() {
  status.value = await getRecoveryStatus()
  if (status.value.allowedActions.length) {
    form.action = status.value.allowedActions[0]
  }
  await Promise.all([loadRequests(), loadUsers()])
}

async function loadUsers() {
  if (!auth.hasPermission('user:read')) return
  try {
    const page = await pageUsers({ page: 1, size: 200 })
    users.value = page.records.filter((user) => user.status === 'ACTIVE')
  } catch {
    users.value = []
  }
}

async function loadRequests() {
  loading.value = true
  try {
    requests.value = await listRecoveryRequests()
  } finally {
    loading.value = false
  }
}

async function submitRequest() {
  if (!form.userId && !form.username.trim()) {
    ElMessage.warning('请选择或输入目标账号')
    return
  }
  if (form.action === 'RESET_PASSWORD' && !form.newPassword) {
    ElMessage.warning('请填写新密码')
    return
  }
  submitting.value = true
  try {
    const result = await createRecoveryRequest({
      action: form.action,
      userId: form.userId || null,
      username: form.username.trim() || null,
      reason: form.reason.trim() || null,
      newPassword: form.action === 'RESET_PASSWORD' ? form.newPassword : null,
    })
    ElMessage.success(result.message)
    form.reason = ''
    form.newPassword = ''
    await Promise.all([loadRequests(), loadUsers()])
  } finally {
    submitting.value = false
  }
}

function canSign(row: RecoveryRequestView) {
  return status.value?.signer === true
    && row.requesterId !== auth.user?.userId
    && !row.signatures.some((signature) => signature.signerId === auth.user?.userId)
}

async function sign(row: RecoveryRequestView) {
  const { value } = await ElMessageBox.prompt('请确认已知晓本次恢复操作的风险', '签署恢复申请', {
    inputPlaceholder: '签署意见（可选）',
    confirmButtonText: '确认签署',
    cancelButtonText: '取消',
  })
  await signRecoveryRequest(row.id, value || undefined)
  ElMessage.success('已签署')
  await loadRequests()
}

async function reject(row: RecoveryRequestView) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回恢复申请', {
    inputPlaceholder: '驳回原因',
    confirmButtonText: '驳回',
    cancelButtonText: '取消',
  })
  await rejectRecoveryRequest(row.id, value || undefined)
  ElMessage.success('已驳回')
  await loadRequests()
}

async function cancel(row: RecoveryRequestView) {
  await ElMessageBox.confirm('确定撤回该恢复申请吗？', '提示', { type: 'warning' })
  await cancelRecoveryRequest(row.id)
  ElMessage.success('已撤回')
  await loadRequests()
}

function actionLabel(action: string) {
  const labels: Record<string, string> = {
    RESET_PASSWORD: '重置密码',
    UNLOCK: '解除锁定',
    GRANT_ADMIN: '授予管理员',
  }
  return labels[action] ?? action
}

function modeLabel(mode: string) {
  const labels: Record<string, string> = {
    DUAL_SIGN: '双签审批',
    DIRECT: '直接执行',
    DISABLED: '已关闭',
  }
  return labels[mode] ?? mode
}

function statusLabel(value: string) {
  const labels: Record<string, string> = {
    PENDING: '待签署',
    APPLIED: '已执行',
    REJECTED: '已驳回',
    CANCELLED: '已撤回',
    EXPIRED: '已过期',
  }
  return labels[value] ?? value
}

function statusTag(value: string): TagType {
  if (value === 'APPLIED') return 'success'
  if (value === 'PENDING') return 'warning'
  if (value === 'REJECTED') return 'danger'
  return 'info'
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
.summary {
  margin-top: 16px;
}
.request-form {
  max-width: 720px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #c0c4cc;
}
.signatures {
  padding: 6px 16px 6px 48px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
}
.signature-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.signature-comment {
  color: #606266;
}
</style>
