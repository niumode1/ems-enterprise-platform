<template>
  <div class="page">
    <el-card shadow="never">
      <div class="header">
        <div>
          <div class="title">待我确认的档案</div>
          <div class="hint">这里只显示管理员指派给您本人的资料，其他员工的行不会出现在此页面；可编辑字段由公司配置决定。</div>
        </div>
        <el-button @click="load">刷新</el-button>
      </div>

      <el-alert
        class="own-tip"
        type="info"
        :closable="false"
        show-icon
        title="确认范围仅限本人：您只能确认或驳回与您本人匹配的待确认行；即使直接调用接口，系统也会拒绝确认他人的资料。"
      />

      <el-empty v-if="rows.length === 0" description="暂无待确认资料" />
      <el-table v-else :data="rows" stripe>
        <el-table-column prop="batchNo" label="批次" width="190" />
        <el-table-column prop="rowNo" label="行号" width="70" />
        <el-table-column label="工号 / 姓名" width="150">
          <template #default="{ row }">
            {{ row.values.employeeNo || '-' }} / {{ row.values.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="部门 / 岗位" min-width="160">
          <template #default="{ row }">
            {{ row.values.orgName || '-' }} / {{ row.values.positionName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="手机" width="130">
          <template #default="{ row }">{{ row.values.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="150">
          <template #default="{ row }">{{ row.values.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConfirm(row)">查看 / 编辑</el-button>
            <el-button link type="danger" @click="reject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" title="确认员工资料" width="620px">
      <el-descriptions v-if="current" :column="2" border class="summary">
        <el-descriptions-item label="批次">{{ current.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="行号">{{ current.rowNo }}</el-descriptions-item>
        <el-descriptions-item label="工号">{{ current.values.employeeNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ current.values.name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ current.values.orgName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ current.values.positionName || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="editableFields.length === 0"
        type="info"
        :closable="false"
        title="当前没有可编辑字段，请直接确认。"
        class="tip"
      />
      <el-form v-else label-width="110px" class="form">
        <el-form-item v-for="field in editableFieldDefs" :key="field.key" :label="field.label">
          <el-date-picker
            v-if="field.date"
            v-model="editValues[field.key]"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
          <el-input v-else v-model="editValues[field.key]" clearable />
        </el-form-item>
      </el-form>

      <el-form label-width="110px">
        <el-form-item label="确认备注">
          <el-input v-model="comment" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirm">确认无误</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmImportRow,
  myConfirmationFields,
  myImportConfirmations,
  rejectImportRow,
} from '@/api/imports'
import type { ImportRowView } from '@/types'

const rows = ref<ImportRowView[]>([])
const editableFields = ref<string[]>([])
const visible = ref(false)
const saving = ref(false)
const comment = ref('')
const current = ref<ImportRowView | null>(null)
const editValues = reactive<Record<string, string>>({})

const fieldDefinitions: Record<string, { label: string; date?: boolean }> = {
  employeeNo: { label: '工号' },
  name: { label: '姓名' },
  gender: { label: '性别' },
  birthDate: { label: '出生日期', date: true },
  phone: { label: '手机' },
  email: { label: '邮箱' },
  idCard: { label: '身份证号' },
  address: { label: '地址' },
  emergencyContact: { label: '紧急联系人' },
  hireDate: { label: '入职日期', date: true },
  orgName: { label: '部门' },
  positionName: { label: '岗位' },
}

const editableFieldDefs = computed(() =>
  editableFields.value
    .filter((key) => fieldDefinitions[key])
    .map((key) => ({ key, ...fieldDefinitions[key] })),
)

onMounted(load)

async function load() {
  const [items, fields] = await Promise.all([myImportConfirmations(), myConfirmationFields()])
  rows.value = items
  editableFields.value = fields
}

function openConfirm(row: ImportRowView) {
  current.value = row
  comment.value = ''
  for (const key of Object.keys(editValues)) delete editValues[key]
  for (const field of editableFieldDefs.value) {
    const value = row.values[field.key]
    editValues[field.key] = value == null ? '' : String(value)
  }
  visible.value = true
}

async function confirm() {
  if (!current.value) return
  const values: Record<string, unknown> = {}
  for (const field of editableFieldDefs.value) {
    if (editValues[field.key] !== '') values[field.key] = editValues[field.key]
  }
  saving.value = true
  try {
    await confirmImportRow(current.value.id, { values, comment: comment.value || undefined })
    ElMessage.success('已确认，等待管理员提交')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function reject(row: ImportRowView) {
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因，管理员会看到该说明。', '驳回资料', {
      inputValidator: (value) => (value && value.trim() ? true : '驳回原因不能为空'),
      type: 'warning',
    })
    await rejectImportRow(row.id, result.value.trim())
    ElMessage.success('已驳回')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // interceptor already reports
    }
  }
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.hint {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.summary {
  margin-bottom: 14px;
}
.own-tip {
  margin-bottom: 12px;
}
.tip {
  margin-bottom: 12px;
}
.form {
  margin-top: 8px;
}
</style>
