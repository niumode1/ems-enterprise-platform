<template>
  <div>
    <el-card>
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="待我审批" name="pending" />
        <el-tab-pane label="我发起的" name="mine" />
        <el-tab-pane v-if="auth.hasPermission('approval:approve')" label="全部申请" name="all" />
      </el-tabs>

      <div class="toolbar">
        <span class="toolbar-hint">审批等待超过公司配置的时长后，系统会自动提醒审批人。</span>
        <el-button
          v-if="auth.hasPermission('approval:approve')"
          :loading="reminding"
          @click="checkReminders"
        >检查超时提醒</el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="policyName" label="审批类型" width="150" />
        <el-table-column prop="requesterName" label="申请人" width="110" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批进度" width="120">
          <template #default="{ row }">{{ row.currentApprovals }} / {{ row.requiredApprovals }}</template>
        </el-table-column>
        <el-table-column label="提示" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.conflict" type="warning" size="small" class="hint-tag">重复申请</el-tag>
            <el-tag
              v-if="row.staleFields && row.staleFields.length"
              type="danger"
              size="small"
              class="hint-tag"
            >数据已变更</el-tag>
            <span v-if="!row.conflict && !(row.staleFields && row.staleFields.length)">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="申请原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="提交时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING'">
              <el-button
                v-if="canApprove(row)"
                link
                type="success"
                @click="onApprove(row)"
              >通过</el-button>
              <el-button
                v-if="canApprove(row)"
                link
                type="danger"
                @click="onReject(row)"
              >驳回</el-button>
              <el-button
                v-if="row.requesterId === auth.user?.userId"
                link
                type="warning"
                @click="onCancel(row)"
              >撤回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="审批详情" size="560px">
      <template v-if="detail">
        <el-alert
          v-if="detail.staleFields && detail.staleFields.length"
          type="error"
          :closable="false"
          show-icon
          title="审批期间数据已变更"
          :description="`以下关键信息已被他人修改：${fieldLabels(detail.staleFields)}。通过前请确认；公司如启用阻止策略，本次通过会被拒绝，需驳回后重新提交。`"
          class="conflict-alert"
        />
        <el-alert
          v-if="detail.conflict"
          type="warning"
          :closable="false"
          show-icon
          title="存在其他待审批申请"
          description="该对象还有未结束的审批申请，多个结果可能互相覆盖，请先协调处理。"
          class="conflict-alert"
        />
        <el-descriptions :column="1" border>
          <el-descriptions-item label="审批类型">{{ detail.policyName || detail.policyCode }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.requesterName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="进度">{{ detail.currentApprovals }} / {{ detail.requiredApprovals }}</el-descriptions-item>
          <el-descriptions-item label="原因">{{ detail.reason || '—' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.createdAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ detail.expiresAt || '—' }}</el-descriptions-item>
        </el-descriptions>

        <h4>申请内容</h4>
        <pre class="payload">{{ prettyPayload(detail.payload) }}</pre>

        <h4>审批记录</h4>
        <el-timeline>
          <el-timeline-item
            v-for="action in detail.actions"
            :key="action.id"
            :timestamp="action.createdAt || ''"
            :type="action.action === 'REJECT' ? 'danger' : 'primary'"
          >
            {{ action.approverName || action.approverId }} · {{ actionLabel(action.action) }}
            <div v-if="action.comment" class="comment">{{ action.comment }}</div>
          </el-timeline-item>
          <el-empty v-if="!detail.actions.length" description="暂无审批记录" :image-size="64" />
        </el-timeline>

        <div v-if="detail.status === 'PENDING' && canApprove(detail)" class="drawer-actions">
          <el-button type="success" @click="onApprove(detail)">通过</el-button>
          <el-button type="danger" @click="onReject(detail)">驳回</el-button>
        </div>
        <div v-else-if="detail.status === 'PENDING' && detail.requesterId === auth.user?.userId" class="drawer-actions">
          <el-button type="warning" @click="onCancel(detail)">撤回申请</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  approve,
  cancelApproval,
  getApproval,
  pageApprovals,
  reject,
  runApprovalReminders,
} from '@/api/approvals'
import type { ApprovalView } from '@/types'

const auth = useAuthStore()
const tab = ref('pending')
const loading = ref(false)
const list = ref<ApprovalView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const detailVisible = ref(false)
const detail = ref<ApprovalView | null>(null)
const reminding = ref(false)

async function checkReminders() {
  reminding.value = true
  try {
    const result = await runApprovalReminders()
    ElMessage.success(
      `检查 ${result.checked} 条，提醒 ${result.reminded} 条，自动过期 ${result.expired} 条`,
    )
    await load()
  } finally {
    reminding.value = false
  }
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    CANCELLED: '已撤回',
    EXPIRED: '已过期',
  }
  return labels[status] ?? status
}

function statusType(status: string) {
  if (status === 'APPROVED') return 'success'
  if (status === 'REJECTED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

function actionLabel(action: string) {
  const labels: Record<string, string> = {
    APPROVE: '同意',
    REJECT: '驳回',
    APPLICANT: '提交申请',
  }
  return labels[action] ?? action
}

function canApprove(row: ApprovalView) {
  return (
    row.status === 'PENDING' &&
    auth.hasPermission('approval:approve') &&
    row.requesterId !== auth.user?.userId &&
    !row.myAction
  )
}

const FIELD_LABELS: Record<string, string> = {
  name: '姓名',
  gender: '性别',
  birthDate: '出生日期',
  phone: '手机号',
  email: '邮箱',
  idCard: '身份证',
  address: '地址',
  emergencyContact: '紧急联系人',
  hireDate: '入职日期',
  org: '部门',
  position: '岗位',
  manager: '直属上级',
}

function fieldLabels(fields: string[]) {
  return fields.map((field) => FIELD_LABELS[field] ?? field).join('、')
}

function prettyPayload(payload: Record<string, unknown>) {
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
}

async function load() {
  loading.value = true
  try {
    const result = await pageApprovals({
      page: page.value,
      size: size.value,
      pendingForMe: tab.value === 'pending',
      mine: tab.value === 'mine',
    })
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  page.value = 1
  load()
}

function openDetail(row: ApprovalView) {
  getApproval(row.id).then((data) => {
    detail.value = data
    detailVisible.value = true
  })
}

async function onApprove(row: ApprovalView) {
  const { value } = await ElMessageBox.prompt('可填写审批意见（选填）', '通过审批', {
    confirmButtonText: '确认通过',
    cancelButtonText: '取消',
    inputType: 'textarea',
  }).catch(() => ({ value: null }))
  if (value === null) return
  await approve(row.id, value || undefined)
  ElMessage.success('已通过')
  detailVisible.value = false
  await load()
}

async function onReject(row: ApprovalView) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回审批', {
    confirmButtonText: '确认驳回',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputValidator: (input) => Boolean(input && input.trim()) || '驳回原因不能为空',
  }).catch(() => ({ value: null }))
  if (value === null) return
  await reject(row.id, value)
  ElMessage.success('已驳回')
  detailVisible.value = false
  await load()
}

async function onCancel(row: ApprovalView) {
  await ElMessageBox.confirm('确定撤回该申请吗？', '提示', { type: 'warning' })
  await cancelApproval(row.id)
  ElMessage.success('已撤回')
  detailVisible.value = false
  await load()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin: 4px 0 12px;
}
.toolbar-hint {
  color: #909399;
  font-size: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.payload {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
.comment {
  color: #606266;
  font-size: 12px;
  margin-top: 4px;
}
.drawer-actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
}
.hint-tag {
  margin-right: 4px;
}
.conflict-alert {
  margin-bottom: 12px;
}
h4 {
  margin: 18px 0 8px;
}
</style>
