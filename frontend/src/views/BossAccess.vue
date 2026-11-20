<template>
  <div class="page">
    <el-card shadow="never">
      <div class="header">
        <div>
          <div class="title">敏感信息访问</div>
          <div class="subtitle">
            老板按工作必要性申请指定员工或全部员工的敏感字段，审批通过后在有效期内查看。
          </div>
        </div>
        <el-tag :type="modeTag" effect="plain">{{ modeLabel }}</el-tag>
      </div>

      <el-descriptions v-if="status" :column="4" border size="small" class="summary">
        <el-descriptions-item label="审批模式">{{ modeLabel }}</el-descriptions-item>
        <el-descriptions-item label="通过人数">{{ status.approverCount }} 人</el-descriptions-item>
        <el-descriptions-item label="默认时长">
          {{ formatMinutes(status.defaultDurationMinutes) }}
        </el-descriptions-item>
        <el-descriptions-item label="最长时长">
          {{ formatMinutes(status.maxDurationMinutes) }}
        </el-descriptions-item>
        <el-descriptions-item label="可申请全部员工">
          {{ status.allowAllEmployees ? '是' : '否' }}
        </el-descriptions-item>
        <el-descriptions-item label="必须填写事由">
          {{ status.requireReason ? '是' : '否' }}
        </el-descriptions-item>
        <el-descriptions-item label="授权范围">
          仅取消已有字段的脱敏，不扩大可见范围
        </el-descriptions-item>
        <el-descriptions-item label="审计">
          授予、撤销与敏感读取均记录
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="status && !status.enabled"
        type="info"
        :closable="false"
        title="公司已关闭老板敏感信息访问；如需使用请联系系统管理员。"
      />
      <el-alert
        v-else-if="status && !status.canRequest"
        type="info"
        :closable="false"
        title="当前账号无需申请（已具备敏感信息查看权限或没有申请权限）。"
      />
    </el-card>

    <el-card v-if="status?.canRequest" shadow="never">
      <template #header>提交访问申请</template>
      <el-form :model="form" label-width="100px" class="request-form">
        <el-form-item label="访问范围">
          <el-select
            v-model="form.employeeId"
            filterable
            clearable
            :placeholder="status.allowAllEmployees ? '全部员工' : '请选择员工'"
            style="width: 320px"
          >
            <el-option
              v-if="status.allowAllEmployees"
              label="全部员工"
              value=""
            />
            <el-option
              v-for="item in employees"
              :key="item.id"
              :label="`${item.name}（${item.employeeNo}）`"
              :value="item.id"
            />
          </el-select>
          <span class="hint">留空表示申请全部员工，需公司开启该选项。</span>
        </el-form-item>
        <el-form-item label="访问时长">
          <el-input-number
            v-model="form.durationMinutes"
            :min="1"
            :max="status.maxDurationMinutes"
          />
          <span class="hint">分钟，最长 {{ formatMinutes(status.maxDurationMinutes) }}</span>
        </el-form-item>
        <el-form-item label="工作事由" :required="status.requireReason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="请说明查看敏感信息的业务必要性"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submitRequest">提交申请</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <span class="title">授权记录</span>
        <div>
          <el-checkbox
            v-if="canViewAll"
            v-model="allGrants"
            label="查看全部授权"
            @change="loadGrants"
          />
          <el-button @click="loadAll">刷新</el-button>
        </div>
      </div>
      <el-table :data="grants" v-loading="loading" stripe>
        <el-table-column prop="userName" label="申请人" width="110" />
        <el-table-column label="访问范围" width="160">
          <template #default="{ row }">
            {{ row.employeeName || '全部员工' }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="事由" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row)" size="small">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" label="有效期至" width="170" />
        <el-table-column prop="createdAt" label="申请时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.active"
              link
              type="danger"
              @click="revoke(row)"
            >撤销</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!grants.length" description="暂无授权记录" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { pageEmployees } from '@/api/employees'
import {
  getBossAccessStatus,
  listBossAccessGrants,
  requestBossAccess,
  revokeBossAccessGrant,
} from '@/api/boss'
import type {
  BossAccessGrantView,
  BossAccessStatusView,
  EmployeeView,
} from '@/types'

const auth = useAuthStore()
const status = ref<BossAccessStatusView | null>(null)
const grants = ref<BossAccessGrantView[]>([])
const employees = ref<EmployeeView[]>([])
const loading = ref(false)
const submitting = ref(false)
const allGrants = ref(false)
const form = reactive({
  employeeId: '',
  durationMinutes: 30,
  reason: '',
})

const canViewAll = computed(
  () => auth.hasPermission('audit:read') || auth.hasPermission('config:read'),
)
const modeLabel = computed(() => {
  const mode = status.value?.mode
  if (mode === 'DIRECT') return '直接授予'
  if (mode === 'DISABLED') return '已关闭'
  return '审批后授予'
})
const modeTag = computed(() => {
  const mode = status.value?.mode
  if (mode === 'DIRECT') return 'warning'
  if (mode === 'DISABLED') return 'info'
  return 'success'
})

onMounted(() => {
  void loadAll()
})

async function loadAll() {
  status.value = await getBossAccessStatus()
  form.durationMinutes = status.value.defaultDurationMinutes
  await Promise.all([loadGrants(), loadEmployees()])
}

async function loadEmployees() {
  try {
    const page = await pageEmployees({ page: 1, size: 200 })
    employees.value = page.records
  } catch {
    employees.value = []
  }
}

async function loadGrants() {
  loading.value = true
  try {
    grants.value = await listBossAccessGrants(allGrants.value)
  } finally {
    loading.value = false
  }
}

async function submitRequest() {
  if (!status.value) return
  if (!form.employeeId && !status.value.allowAllEmployees) {
    ElMessage.warning('请选择员工')
    return
  }
  if (status.value.requireReason && !form.reason.trim()) {
    ElMessage.warning('请填写工作事由')
    return
  }
  submitting.value = true
  try {
    const result = await requestBossAccess({
      employeeId: form.employeeId || null,
      reason: form.reason.trim(),
      durationMinutes: form.durationMinutes,
    })
    ElMessage.success(result.message)
    form.reason = ''
    await Promise.all([loadGrants(), refreshStatus()])
  } finally {
    submitting.value = false
  }
}

async function refreshStatus() {
  status.value = await getBossAccessStatus()
}

async function revoke(row: BossAccessGrantView) {
  await ElMessageBox.confirm(
    `确定撤销「${row.userName || row.userId}」的敏感信息访问授权吗？`,
    '提示',
    { type: 'warning' },
  )
  await revokeBossAccessGrant(row.id)
  ElMessage.success('授权已撤销')
  await loadGrants()
}

function formatMinutes(minutes: number) {
  if (minutes % 60 === 0) return `${minutes / 60} 小时`
  return `${minutes} 分钟`
}

function statusLabel(row: BossAccessGrantView) {
  if (row.active) return '生效中'
  if (row.status === 'REVOKED') return '已撤销'
  if (row.status === 'EXPIRED') return '已过期'
  return row.status
}

function statusTag(row: BossAccessGrantView) {
  if (row.active) return 'success'
  if (row.status === 'REVOKED') return 'danger'
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
</style>
