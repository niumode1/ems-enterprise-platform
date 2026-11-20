<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input
            v-model="query.keyword"
            placeholder="文件名"
            clearable
            style="width: 220px"
            @keyup.enter="load"
          />
          <el-select v-model="query.status" clearable placeholder="批次状态" style="width: 160px">
            <el-option v-for="item in batchStatuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
        </div>
        <div class="actions">
          <el-button v-if="auth.hasPermission('ai:config:read')" @click="router.push('/ai-settings')">
            AI 配置
          </el-button>
          <el-button
            v-if="auth.hasPermission('import:upload')"
            type="primary"
            :loading="uploading"
            @click="fileInput?.click()"
          >
            上传花名册
          </el-button>
          <input ref="fileInput" type="file" accept=".csv,.xlsx,.xls" hidden @change="onFileChosen" />
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="导入流程：上传 → 解析 → 管理员校对 → 推送员工确认 → 提交落库（关键提交按审批策略执行）。"
        class="tip"
      />

      <el-table :data="batches" stripe>
        <el-table-column prop="batchNo" label="批次号" width="190" />
        <el-table-column prop="fileName" label="文件" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="batchTagType(row.status)" size="small">{{ batchStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="行数" width="90">
          <template #default="{ row }">{{ row.totalRows }}</template>
        </el-table-column>
        <el-table-column label="需校对" width="90">
          <template #default="{ row }">
            <span :class="{ warning: row.lowConfidenceRows > 0 }">{{ row.lowConfidenceRows }}</span>
          </template>
        </el-table-column>
        <el-table-column label="待确认" width="90">
          <template #default="{ row }">{{ row.pendingConfirmRows }}</template>
        </el-table-column>
        <el-table-column label="已确认" width="90">
          <template #default="{ row }">{{ row.confirmedRows }}</template>
        </el-table-column>
        <el-table-column label="已落库" width="90">
          <template #default="{ row }">{{ row.committedRows }}</template>
        </el-table-column>
        <el-table-column label="AI" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.aiUsed" type="success" size="small">AI</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdByName" label="上传人" width="100" />
        <el-table-column prop="createdAt" label="上传时间" width="165" />
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="['UPLOADED', 'FAILED'].includes(row.status) && auth.hasPermission('import:parse')"
              link
              type="primary"
              @click="runParse(row)"
            >
              解析
            </el-button>
            <el-button link type="primary" @click="openRows(row)">校对</el-button>
            <el-button
              v-if="row.status === 'PARSED' && auth.hasPermission('import:push')"
              link
              type="primary"
              @click="runPush(row)"
            >
              推送确认
            </el-button>
            <el-button
              v-if="['PARSED', 'PENDING_CONFIRM'].includes(row.status) && auth.hasPermission('import:commit')"
              link
              type="success"
              @click="runCommit(row)"
            >
              提交
            </el-button>
            <el-button
              v-if="row.status === 'COMMITTED' && auth.hasPermission('import:rollback')"
              link
              type="danger"
              @click="runRollback(row)"
            >
              回滚
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="load"
      />
    </el-card>

    <el-drawer v-model="rowsVisible" :title="`批次 ${currentBatch?.batchNo || ''} 数据校对`" size="76%">
      <div class="drawer-toolbar">
        <el-select v-model="rowQuery.status" clearable placeholder="行状态" style="width: 160px" @change="loadRows">
          <el-option v-for="item in rowStatuses" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button @click="loadRows">刷新</el-button>
        <span v-if="currentBatch?.errorMessage" class="batch-error">{{ currentBatch.errorMessage }}</span>
      </div>
      <el-table :data="rows" stripe size="small">
        <el-table-column prop="rowNo" label="#" width="55" />
        <el-table-column label="工号" width="110">
          <template #default="{ row }">{{ row.values.employeeNo || '-' }}</template>
        </el-table-column>
        <el-table-column label="姓名" width="110">
          <template #default="{ row }">{{ row.values.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="部门 / 岗位" min-width="150">
          <template #default="{ row }">
            {{ row.values.orgName || '-' }} / {{ row.values.positionName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="匹配员工" width="120">
          <template #default="{ row }">
            {{ row.matchedEmployeeName || (row.status === 'NEW' ? '新员工' : '-') }}
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">
            <span :class="{ warning: row.confidence < 0.8 }">{{ (row.confidence * 100).toFixed(0) }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="rowTagType(row.status)" size="small">{{ rowStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="确认人" width="100">
          <template #default="{ row }">{{ row.confirmUserName || '-' }}</template>
        </el-table-column>
        <el-table-column label="提示" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="warning">{{ row.errorMessage || row.rejectReason || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="auth.hasPermission('import:review') && !['COMMITTED', 'ROLLED_BACK'].includes(row.status)"
              link
              type="primary"
              @click="openEdit(row)"
            >
              校对
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="rowQuery.page"
        v-model:page-size="rowQuery.size"
        :total="rowTotal"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="loadRows"
      />
    </el-drawer>

    <el-dialog v-model="editVisible" title="校对导入行" width="620px">
      <el-form label-width="110px">
        <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
          <el-date-picker
            v-if="field.date"
            v-model="editValues[field.key]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <el-input v-else v-model="editValues[field.key]" clearable />
        </el-form-item>
        <el-form-item v-if="auth.hasPermission('import:review')" label="管理员确认">
          <el-checkbox v-model="editConfirm">保存时视为已确认（新员工或无法登录确认的员工）</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  commitImport,
  pageImportRows,
  pageImports,
  parseImport,
  pushImport,
  reviewImportRow,
  rollbackImport,
  uploadRoster,
} from '@/api/imports'
import type { ImportBatchView, ImportRowView } from '@/types'

const auth = useAuthStore()
const router = useRouter()
const fileInput = ref<HTMLInputElement>()
const uploading = ref(false)
const saving = ref(false)
const batches = ref<ImportBatchView[]>([])
const total = ref(0)
const rows = ref<ImportRowView[]>([])
const rowTotal = ref(0)
const rowsVisible = ref(false)
const editVisible = ref(false)
const currentBatch = ref<ImportBatchView | null>(null)
const currentRow = ref<ImportRowView | null>(null)
const editConfirm = ref(false)
const editValues = reactive<Record<string, string>>({})

const query = reactive({ page: 1, size: 20, keyword: '', status: '' })
const rowQuery = reactive({ page: 1, size: 50, status: '' })

const fields = [
  { key: 'employeeNo', label: '工号' },
  { key: 'name', label: '姓名' },
  { key: 'gender', label: '性别' },
  { key: 'birthDate', label: '出生日期', date: true },
  { key: 'phone', label: '手机' },
  { key: 'email', label: '邮箱' },
  { key: 'idCard', label: '身份证号' },
  { key: 'address', label: '地址' },
  { key: 'emergencyContact', label: '紧急联系人' },
  { key: 'hireDate', label: '入职日期', date: true },
  { key: 'orgName', label: '部门名称' },
  { key: 'positionName', label: '岗位名称' },
]

const batchStatuses = [
  { value: 'UPLOADED', label: '待解析' },
  { value: 'PARSED', label: '待校对' },
  { value: 'PENDING_CONFIRM', label: '待员工确认' },
  { value: 'PENDING_APPROVAL', label: '待审批' },
  { value: 'COMMITTED', label: '已提交' },
  { value: 'ROLLED_BACK', label: '已回滚' },
  { value: 'FAILED', label: '解析失败' },
]

const rowStatuses = [
  { value: 'PARSED', label: '已解析' },
  { value: 'NEEDS_REVIEW', label: '需校对' },
  { value: 'NEW', label: '新员工' },
  { value: 'PENDING_CONFIRM', label: '待确认' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'COMMITTED', label: '已落库' },
  { value: 'FAILED', label: '失败' },
]

onMounted(load)

async function load() {
  const page = await pageImports({
    page: query.page,
    size: query.size,
    keyword: query.keyword || undefined,
    status: query.status || undefined,
  })
  batches.value = page.records
  total.value = page.total
}

async function onFileChosen(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  target.value = ''
  if (!file) return
  const lower = file.name.toLowerCase()
  if (!['.csv', '.xlsx', '.xls'].some((ext) => lower.endsWith(ext))) {
    ElMessage.warning('仅支持 CSV、XLSX、XLS 文件')
    return
  }
  uploading.value = true
  try {
    const batch = await uploadRoster(file)
    ElMessage.success('上传成功，正在解析')
    await load()
    await runParse(batch)
  } catch {
    // interceptor already reports the error
  } finally {
    uploading.value = false
  }
}

async function runParse(batch: ImportBatchView) {
  try {
    const parsed = await parseImport(batch.id)
    ElMessage.success(
      `解析完成：${parsed.totalRows} 行，需校对 ${parsed.lowConfidenceRows} 行${parsed.aiUsed ? '（已用 AI 补全列映射）' : ''}`,
    )
    await load()
  } catch {
    // reported by interceptor
  }
}

async function runPush(batch: ImportBatchView) {
  try {
    const result = await pushImport(batch.id)
    ElMessage.success(
      `已推送 ${result.pushed} 人确认；新员工 ${result.createdAsNew} 行；${result.skipped} 人无账号需管理员确认`,
    )
    await load()
  } catch {
    // reported
  }
}

async function runCommit(batch: ImportBatchView) {
  try {
    await ElMessageBox.confirm(
      '提交后已确认的数据将写入员工档案（按配置可能先进入审批）。确定提交？',
      '提交花名册',
      { type: 'warning' },
    )
    const result = await commitImport(batch.id)
    if (result.status === 'PENDING_APPROVAL') {
      ElMessage.success('已提交审批，审批通过后自动落库')
    } else {
      ElMessage.success(result.message)
    }
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // interceptor already reports
    }
  }
}

async function runRollback(batch: ImportBatchView) {
  try {
    await ElMessageBox.confirm(
      '回滚会把本批次更新过的员工恢复到导入前状态；本批次新建的员工将被停用。提交后又被修改的数据会跳过。确定回滚？',
      '回滚批次',
      { type: 'warning', confirmButtonText: '回滚' },
    )
    const result = await rollbackImport(batch.id)
    ElMessage.success(`回滚完成${result.errorMessage ? '：' + result.errorMessage : ''}`)
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // reported
    }
  }
}

async function openRows(batch: ImportBatchView) {
  currentBatch.value = batch
  rowQuery.page = 1
  rowQuery.status = ''
  rowsVisible.value = true
  await loadRows()
}

async function loadRows() {
  if (!currentBatch.value) return
  const page = await pageImportRows(currentBatch.value.id, {
    page: rowQuery.page,
    size: rowQuery.size,
    status: rowQuery.status || undefined,
  })
  rows.value = page.records
  rowTotal.value = page.total
}

function openEdit(row: ImportRowView) {
  currentRow.value = row
  editConfirm.value = false
  for (const key of Object.keys(editValues)) delete editValues[key]
  for (const field of fields) {
    const value = row.values[field.key]
    editValues[field.key] = value == null ? '' : String(value)
  }
  editVisible.value = true
}

async function saveEdit() {
  if (!currentBatch.value || !currentRow.value) return
  const values: Record<string, unknown> = {}
  for (const field of fields) {
    if (editValues[field.key] !== '') values[field.key] = editValues[field.key]
  }
  saving.value = true
  try {
    await reviewImportRow(currentBatch.value.id, currentRow.value.id, {
      values,
      confirm: editConfirm.value,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await loadRows()
    await load()
  } finally {
    saving.value = false
  }
}

function batchStatusLabel(status: string) {
  return batchStatuses.find((item) => item.value === status)?.label ?? status
}

function batchTagType(status: string) {
  const map: Record<string, string> = {
    UPLOADED: 'info',
    PARSED: 'warning',
    PENDING_CONFIRM: 'warning',
    PENDING_APPROVAL: 'warning',
    COMMITTED: 'success',
    ROLLED_BACK: 'info',
    FAILED: 'danger',
  }
  return map[status] ?? 'info'
}

function rowStatusLabel(status: string) {
  return rowStatuses.find((item) => item.value === status)?.label ?? status
}

function rowTagType(status: string) {
  const map: Record<string, string> = {
    PARSED: 'info',
    NEEDS_REVIEW: 'warning',
    NEW: '',
    PENDING_CONFIRM: 'warning',
    CONFIRMED: 'success',
    REJECTED: 'danger',
    COMMITTED: 'success',
    FAILED: 'danger',
    ROLLED_BACK: 'info',
  }
  return map[status] ?? 'info'
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar,
.drawer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.filters,
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tip {
  margin-bottom: 12px;
}
.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
.warning {
  color: #e6a23c;
}
.muted {
  color: #c0c4cc;
}
.batch-error {
  color: #f56c6c;
  font-size: 12px;
}
</style>
