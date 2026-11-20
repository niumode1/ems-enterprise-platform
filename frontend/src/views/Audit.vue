<template>
  <div>
    <el-card>
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <el-tab-pane label="操作日志" name="audit" />
        <el-tab-pane v-if="auth.hasPermission('audit:sensitive:read')" label="敏感访问日志" name="access" />
        <el-tab-pane
          v-if="auth.hasPermission('audit:version:read')"
          label="版本与回滚"
          name="versions"
        />
      </el-tabs>

      <div class="toolbar" v-if="tab === 'audit'">
        <el-input v-model="filters.category" placeholder="分类，如 CONFIG / PERSONNEL" clearable class="filter" />
        <el-input v-model="filters.module" placeholder="模块，如 employee" clearable class="filter" />
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <div class="toolbar" v-else-if="tab === 'access'">
        <el-input v-model="filters.resourceType" placeholder="资源类型，如 EMPLOYEE" clearable class="filter" />
        <el-select v-model="filters.sensitive" clearable placeholder="敏感标记" class="short-filter">
          <el-option label="敏感" :value="true" />
          <el-option label="非敏感" :value="false" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <div class="toolbar" v-else>
        <el-select v-model="versionFilters.entityType" clearable placeholder="实体类型" class="short-filter">
          <el-option label="员工" value="EMPLOYEE" />
        </el-select>
        <el-input v-model="versionFilters.entityId" placeholder="实体 ID（员工 ID）" clearable class="filter" />
        <el-input v-model="versionFilters.operatorId" placeholder="操作人 ID" clearable class="filter" />
        <el-date-picker
          v-model="versionDateRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetVersions">重置</el-button>
      </div>

      <el-table v-if="tab === 'audit'" :data="auditLogs" v-loading="loading" stripe>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="actorName" label="操作人" width="110" />
        <el-table-column prop="category" label="分类" width="110" />
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="动作" width="130" />
        <el-table-column label="对象" width="170">
          <template #default="{ row }">{{ row.entityType || '—' }} / {{ row.entityId || '—' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因 / 备注" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="详情" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="tab === 'access'" :data="accessLogs" v-loading="loading" stripe>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="userId" label="用户ID" width="180" />
        <el-table-column prop="resourceType" label="资源类型" width="140" />
        <el-table-column prop="resourceId" label="资源ID" width="180" />
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column label="敏感" width="90">
          <template #default="{ row }">
            <el-tag :type="row.sensitive ? 'danger' : 'info'" size="small">{{ row.sensitive ? '敏感' : '普通' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="150" />
      </el-table>

      <el-table v-else :data="versions" v-loading="loading" stripe>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="实体" width="180">
          <template #default="{ row }">{{ entityTypeLabel(row.entityType) }} / {{ row.entityId }}</template>
        </el-table-column>
        <el-table-column label="版本" width="90">
          <template #default="{ row }">v{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="变更类型" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ changeTypeLabel(row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更字段" min-width="220">
          <template #default="{ row }">
            <span v-if="!row.changedFields?.length" class="muted">—</span>
            <el-tag
              v-for="field in row.changedFields || []"
              :key="field"
              size="small"
              class="field-tag"
            >{{ fieldLabel(field) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openVersion(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-dialog v-model="detailVisible" title="日志详情" width="760px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="分类">{{ detail.category }}</el-descriptions-item>
          <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ detail.action }}</el-descriptions-item>
          <el-descriptions-item label="对象">{{ detail.entityType }} / {{ detail.entityId }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.actorName }}</el-descriptions-item>
          <el-descriptions-item label="IP">{{ detail.ip || '—' }}</el-descriptions-item>
          <el-descriptions-item label="原因" :span="2">{{ detail.reason || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h4>修改前</h4>
        <pre class="json">{{ pretty(detail.beforeJson) }}</pre>
        <h4>修改后</h4>
        <pre class="json">{{ pretty(detail.afterJson) }}</pre>
      </template>
    </el-dialog>

    <el-dialog v-model="versionVisible" title="版本详情" width="760px">
      <template v-if="versionDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="实体">
            {{ entityTypeLabel(versionDetail.version.entityType) }} / {{ versionDetail.version.entityId }}
          </el-descriptions-item>
          <el-descriptions-item label="版本">v{{ versionDetail.version.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="变更类型">
            {{ changeTypeLabel(versionDetail.version.changeType) }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ versionDetail.version.operatorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ versionDetail.version.createdAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审批单">{{ versionDetail.version.approvalId || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="versionDetail.maskedFields.length"
          class="mask-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="`以下字段因权限已脱敏：${versionDetail.maskedFields.map(fieldLabel).join('、')}`"
        />

        <h4>字段变化</h4>
        <el-table :data="diffRows" size="small" border>
          <el-table-column label="字段" width="140">
            <template #default="{ row }">{{ fieldLabel(row.field) }}</template>
          </el-table-column>
          <el-table-column label="修改前">
            <template #default="{ row }">{{ formatValue(row.field, row.before, false) }}</template>
          </el-table-column>
          <el-table-column label="修改后">
            <template #default="{ row }">{{ formatValue(row.field, row.after, false) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="diffRows.length === 0" description="该版本没有字段差异" :image-size="48" />

        <el-collapse class="snapshot">
          <el-collapse-item title="完整快照" name="snapshot">
            <div class="snapshot-grid">
              <div v-for="row in snapshotRows" :key="row.field" class="snapshot-row">
                <span class="snapshot-label">{{ fieldLabel(row.field) }}</span>
                <span class="snapshot-value">{{ formatValue(row.field, row.value, true) }}</span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </template>
      <template #footer>
        <el-button @click="versionVisible = false">关闭</el-button>
        <el-button
          v-if="auth.hasPermission('audit:rollback') && versionDetail?.version.entityType === 'EMPLOYEE'"
          type="warning"
          @click="openRollback"
        >回滚到此版本</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rollbackVisible" title="回滚版本" width="560px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="回滚只恢复公司配置允许的字段，不会删除当前版本；若开启审批，需审批通过后生效。"
        class="mask-alert"
      />
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="实体">
          {{ entityTypeLabel(versionDetail?.version.entityType) }} / {{ versionDetail?.version.entityId }}
        </el-descriptions-item>
        <el-descriptions-item label="目标版本">v{{ versionDetail?.version.versionNo }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="86px" class="rollback-form">
        <el-form-item label="回滚原因" required>
          <el-input
            v-model="rollbackReason"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="请说明回滚原因，便于审批与审计"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rollbackVisible = false">取消</el-button>
        <el-button type="warning" :loading="rollingBack" @click="submitRollback">提交回滚</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  getEntityVersion,
  pageAccessLogs,
  pageAuditLogs,
  pageEntityVersions,
  rollbackEntityVersion,
} from '@/api/audit'
import type {
  AccessLog,
  AuditLog,
  EntityVersionDetail,
  EntityVersionView,
} from '@/types'

const auth = useAuthStore()
const tab = ref('audit')
const loading = ref(false)
const auditLogs = ref<AuditLog[]>([])
const accessLogs = ref<AccessLog[]>([])
const versions = ref<EntityVersionView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const dateRange = ref<[string, string] | null>(null)
const versionDateRange = ref<[string, string] | null>(null)
const filters = reactive<{ category: string; module: string; resourceType: string; sensitive: boolean | '' }>({
  category: '',
  module: '',
  resourceType: '',
  sensitive: '',
})
const versionFilters = reactive({ entityType: '', entityId: '', operatorId: '' })
const detailVisible = ref(false)
const detail = ref<AuditLog | null>(null)

const versionVisible = ref(false)
const versionDetail = ref<EntityVersionDetail | null>(null)
const rollbackVisible = ref(false)
const rollbackReason = ref('')
const rollingBack = ref(false)

const fieldLabels: Record<string, string> = {
  employeeNo: '工号',
  name: '姓名',
  gender: '性别',
  birthDate: '出生日期',
  phone: '手机',
  email: '邮箱',
  address: '住址',
  emergencyContact: '紧急联系人',
  hireDate: '入职日期',
  status: '状态',
  org: '部门',
  orgName: '部门名称',
  position: '岗位',
  positionName: '岗位名称',
  manager: '直属上级',
  managerName: '直属上级姓名',
  idCard: '身份证',
}

const diffRows = computed(() => {
  const diff = versionDetail.value?.diff || {}
  return Object.entries(diff).map(([field, change]) => ({
    field,
    before: change?.before,
    after: change?.after,
  }))
})

const snapshotRows = computed(() => {
  const snapshot = versionDetail.value?.snapshot || {}
  return Object.entries(snapshot)
    .filter(([field]) => field !== 'orgName' && field !== 'positionName' && field !== 'managerName')
    .map(([field, value]) => ({ field, value }))
})

async function load() {
  loading.value = true
  try {
    if (tab.value === 'audit') {
      const result = await pageAuditLogs({
        page: page.value,
        size: size.value,
        category: filters.category || undefined,
        module: filters.module || undefined,
        from: dateRange.value?.[0],
        to: dateRange.value?.[1],
      })
      auditLogs.value = result.records
      total.value = result.total
    } else if (tab.value === 'access') {
      const result = await pageAccessLogs({
        page: page.value,
        size: size.value,
        resourceType: filters.resourceType || undefined,
        sensitive: filters.sensitive === '' ? undefined : filters.sensitive,
      })
      accessLogs.value = result.records
      total.value = result.total
    } else {
      const result = await pageEntityVersions({
        page: page.value,
        size: size.value,
        entityType: versionFilters.entityType || undefined,
        entityId: versionFilters.entityId || undefined,
        operatorId: versionFilters.operatorId || undefined,
        from: versionDateRange.value?.[0],
        to: versionDateRange.value?.[1],
      })
      versions.value = result.records
      total.value = result.total
    }
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.category = ''
  filters.module = ''
  filters.resourceType = ''
  filters.sensitive = ''
  dateRange.value = null
  page.value = 1
  load()
}

function resetVersions() {
  versionFilters.entityType = ''
  versionFilters.entityId = ''
  versionFilters.operatorId = ''
  versionDateRange.value = null
  page.value = 1
  load()
}

function onTabChange() {
  page.value = 1
  load()
}

function openDetail(row: AuditLog) {
  detail.value = row
  detailVisible.value = true
}

async function openVersion(row: EntityVersionView) {
  versionDetail.value = await getEntityVersion(row.id)
  versionVisible.value = true
}

function openRollback() {
  rollbackReason.value = ''
  rollbackVisible.value = true
}

async function submitRollback() {
  if (!versionDetail.value) return
  if (!rollbackReason.value.trim()) {
    ElMessage.warning('请填写回滚原因')
    return
  }
  rollingBack.value = true
  try {
    const result = await rollbackEntityVersion(
      versionDetail.value.version.id,
      rollbackReason.value.trim(),
    )
    ElMessage({
      type: result.status === 'APPLIED' ? 'success' : 'info',
      message: result.message || (result.status === 'APPLIED' ? '回滚已生效' : '回滚审批已提交'),
      duration: 4000,
    })
    rollbackVisible.value = false
    versionVisible.value = false
    await load()
  } finally {
    rollingBack.value = false
  }
}

function entityTypeLabel(type?: string | null) {
  if (!type) return '—'
  return type === 'EMPLOYEE' ? '员工' : type
}

function changeTypeLabel(type: string) {
  const labels: Record<string, string> = {
    CREATE: '创建',
    UPDATE: '修改',
    UPDATE_APPROVED: '审批生效',
    ROLLBACK: '回滚',
    IMPORT_CREATE: '导入新建',
    IMPORT_UPDATE: '导入更新',
    IMPORT_ROLLBACK: '导入回滚',
  }
  return labels[type] || type
}

function fieldLabel(field: string) {
  return fieldLabels[field] || field
}

function formatValue(field: string, value: unknown, fromSnapshot: boolean) {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'object') return JSON.stringify(value)
  if (field === 'status') {
    if (value === 'ACTIVE') return '在职'
    if (value === 'LEFT') return '离职'
  }
  if (field === 'gender') {
    if (value === 'MALE') return '男'
    if (value === 'FEMALE') return '女'
    if (value === 'OTHER') return '其他'
  }
  if (fromSnapshot && field === 'org' && versionDetail.value?.snapshot?.orgName) {
    return `${value} · ${versionDetail.value.snapshot.orgName}`
  }
  if (fromSnapshot && field === 'position' && versionDetail.value?.snapshot?.positionName) {
    return `${value} · ${versionDetail.value.snapshot.positionName}`
  }
  if (fromSnapshot && field === 'manager' && versionDetail.value?.snapshot?.managerName) {
    return `${value} · ${versionDetail.value.snapshot.managerName}`
  }
  if ((field === 'org' || field === 'position' || field === 'manager') && !fromSnapshot) {
    return String(value)
  }
  return String(value)
}

function pretty(value: string | null) {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.filter {
  width: 220px;
}
.short-filter {
  width: 130px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.json {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 6px;
  max-height: 260px;
  overflow: auto;
  font-size: 12px;
}
h4 {
  margin: 16px 0 8px;
}
.mask-alert {
  margin-top: 12px;
}
.field-tag {
  margin: 2px 4px 2px 0;
}
.muted {
  color: #a8abb2;
}
.snapshot {
  margin-top: 16px;
}
.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px 18px;
}
.snapshot-row {
  display: flex;
  gap: 10px;
  font-size: 13px;
  border-bottom: 1px dashed #ebeef5;
  padding: 6px 0;
}
.snapshot-label {
  flex: none;
  width: 92px;
  color: #909399;
}
.snapshot-value {
  color: #303133;
  word-break: break-all;
}
.rollback-form {
  margin-top: 16px;
}
</style>
