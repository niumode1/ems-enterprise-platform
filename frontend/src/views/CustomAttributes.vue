<template>
  <div class="page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="属性定义" name="defs">
        <el-card shadow="never">
          <div class="toolbar">
            <span class="hint">不修改表结构即可扩展员工字段；时间线属性可保留历史版本。</span>
            <el-button v-if="auth.hasPermission('attr:create')" type="primary" @click="openCreate">新增属性</el-button>
          </div>
          <el-table :data="defs" stripe>
            <el-table-column prop="name" label="名称" min-width="120" />
            <el-table-column prop="fieldCode" label="编码" min-width="140" />
            <el-table-column prop="fieldType" label="类型" width="90" />
            <el-table-column label="时间线" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.timeline" size="small" type="warning">历史</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="visibility" label="可见范围" min-width="200" />
            <el-table-column label="敏感/脱敏" width="140">
              <template #default="{ row }">
                <el-tag v-if="row.sensitive" size="small" type="danger">敏感</el-tag>
                <el-tag v-if="row.maskRule !== 'NONE'" size="small" type="warning">{{ row.maskRule }}</el-tag>
                <span v-if="!row.sensitive && row.maskRule === 'NONE'">—</span>
              </template>
            </el-table-column>
            <el-table-column label="本人可改" width="90">
              <template #default="{ row }">{{ row.selfEditable ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button v-if="auth.hasPermission('attr:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button
                  v-if="auth.hasPermission('attr:disable') && row.status === 'ACTIVE'"
                  link
                  type="danger"
                  @click="disable(row)"
                >停用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="员工属性值" name="values">
        <el-card shadow="never">
          <div class="toolbar">
            <el-select
              v-model="selectedEmployeeId"
              filterable
              placeholder="选择员工"
              style="width: 280px"
              @change="loadValues"
            >
              <el-option
                v-for="item in employees"
                :key="item.id"
                :label="`${item.name}（${item.employeeNo}）`"
                :value="item.id"
              />
            </el-select>
            <el-button :disabled="!selectedEmployeeId" @click="loadValues">刷新</el-button>
          </div>

          <el-empty v-if="!selectedEmployeeId" description="请选择员工" />
          <el-empty v-else-if="!values.length" description="暂无自定义属性" />
          <el-form v-else label-width="150px" class="value-form">
            <el-form-item v-for="item in values" :key="item.fieldCode" :label="item.name">
              <template v-if="item.visible">
                <div class="value-row">
                  <template v-if="item.fieldType === 'SELECT'">
                    <el-select
                      v-model="valueForm[item.fieldCode]"
                      :disabled="!item.editable"
                      clearable
                      style="width: 240px"
                    >
                      <el-option v-for="option in item.options" :key="option" :label="option" :value="option" />
                    </el-select>
                  </template>
                  <template v-else-if="item.fieldType === 'BOOLEAN'">
                    <el-select
                      v-model="valueForm[item.fieldCode]"
                      :disabled="!item.editable"
                      clearable
                      style="width: 160px"
                    >
                      <el-option label="是" value="true" />
                      <el-option label="否" value="false" />
                    </el-select>
                  </template>
                  <template v-else-if="item.fieldType === 'DATE'">
                    <el-date-picker
                      v-model="valueForm[item.fieldCode]"
                      :disabled="!item.editable"
                      type="date"
                      value-format="YYYY-MM-DD"
                      style="width: 200px"
                    />
                  </template>
                  <template v-else>
                    <el-input
                      v-model="valueForm[item.fieldCode]"
                      :disabled="!item.editable"
                      style="width: 280px"
                    />
                  </template>
                  <el-tag v-if="item.masked" size="small" type="warning">脱敏</el-tag>
                  <el-tag v-if="item.timeline" size="small" type="warning">时间线</el-tag>
                  <el-button v-if="item.timeline" link type="primary" @click="openHistory(item)">历史</el-button>
                </div>
              </template>
              <span v-else class="hidden-value">无查看权限</span>
            </el-form-item>
            <el-form-item>
              <el-button
                v-if="hasEditable"
                type="primary"
                :loading="saving"
                @click="saveValues"
              >保存</el-button>
              <span class="hint">仅可修改当前账号有权限的字段。</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="defVisible" :title="defForm.id ? '编辑属性' : '新增属性'" width="560px">
      <el-form :model="defForm" label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="defForm.name" />
        </el-form-item>
        <el-form-item label="字段编码" required>
          <el-input v-model="defForm.fieldCode" :disabled="!!defForm.id" placeholder="如 employment_type" />
        </el-form-item>
        <el-form-item label="字段类型">
          <el-select v-model="defForm.fieldType" style="width: 200px">
            <el-option label="文本" value="TEXT" />
            <el-option label="数字" value="NUMBER" />
            <el-option label="日期" value="DATE" />
            <el-option label="布尔" value="BOOLEAN" />
            <el-option label="下拉" value="SELECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="defForm.fieldType === 'SELECT'" label="下拉选项">
          <el-input v-model="optionsText" placeholder="用英文逗号分隔，如 全职,兼职,实习" />
        </el-form-item>
        <el-form-item label="时间线">
          <el-switch v-model="defForm.timeline" />
          <span class="hint">开启后每次保存保留生效日期与历史记录</span>
        </el-form-item>
        <el-form-item label="可见范围">
          <el-input v-model="defForm.visibility" placeholder="ALL,SELF,DIRECT_REPORTS,HR_ADMIN,BOSS" />
        </el-form-item>
        <el-form-item label="脱敏规则">
          <el-select v-model="defForm.maskRule" style="width: 200px">
            <el-option label="不脱敏" value="NONE" />
            <el-option label="手机号" value="PHONE" />
            <el-option label="邮箱" value="EMAIL" />
            <el-option label="身份证" value="ID_CARD" />
            <el-option label="地址" value="ADDRESS" />
            <el-option label="联系人" value="CONTACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="敏感字段"><el-switch v-model="defForm.sensitive" /></el-form-item>
        <el-form-item label="本人可改"><el-switch v-model="defForm.selfEditable" /></el-form-item>
        <el-form-item label="必填"><el-switch v-model="defForm.required" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="defForm.sort" :min="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="defVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitDef">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" :title="`${historyTitle} · 历史记录`" width="620px">
      <el-table :data="historyRows" stripe>
        <el-table-column prop="value" label="值" min-width="160" />
        <el-table-column prop="startDate" label="生效日期" width="130" />
        <el-table-column prop="endDate" label="结束日期" width="130">
          <template #default="{ row }">{{ row.endDate || '至今' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { pageEmployees } from '@/api/employees'
import {
  createCustomFieldDef,
  disableCustomFieldDef,
  getCustomFieldValues,
  listCustomFieldDefs,
  setCustomFieldValues,
  updateCustomFieldDef,
  type CustomFieldDefPayload,
} from '@/api/customFields'
import type { CustomFieldDef, CustomFieldValue, EmployeeView } from '@/types'

const auth = useAuthStore()
const activeTab = ref('defs')
const defs = ref<CustomFieldDef[]>([])
const employees = ref<EmployeeView[]>([])
const selectedEmployeeId = ref('')
const values = ref<CustomFieldValue[]>([])
const valueForm = reactive<Record<string, string>>({})
const saving = ref(false)

const defVisible = ref(false)
const defForm = reactive<CustomFieldDefPayload & { id?: string }>({
  fieldCode: '',
  name: '',
  fieldType: 'TEXT',
  timeline: false,
  visibility: 'ALL',
  maskRule: 'NONE',
  sensitive: false,
  selfEditable: false,
  required: false,
  sort: 0,
})
const optionsText = ref('')

const historyVisible = ref(false)
const historyTitle = ref('')
const historyRows = ref<CustomFieldValue[]>([])

const hasEditable = computed(() => values.value.some((item) => item.visible && item.editable))

onMounted(async () => {
  await Promise.all([loadDefs(), loadEmployees()])
})

async function loadDefs() {
  if (!auth.hasPermission('attr:read')) return
  defs.value = await listCustomFieldDefs('EMPLOYEE', true)
}

async function loadEmployees() {
  if (!auth.hasPermission('employee:read')) return
  const page = await pageEmployees({ page: 1, size: 200 })
  employees.value = page.records
}

function openCreate() {
  Object.assign(defForm, {
    id: undefined,
    fieldCode: '',
    name: '',
    fieldType: 'TEXT',
    timeline: false,
    visibility: 'ALL',
    maskRule: 'NONE',
    sensitive: false,
    selfEditable: false,
    required: false,
    sort: 0,
  })
  optionsText.value = ''
  defVisible.value = true
}

function openEdit(row: CustomFieldDef) {
  Object.assign(defForm, {
    id: row.id,
    fieldCode: row.fieldCode,
    name: row.name,
    fieldType: row.fieldType,
    timeline: row.timeline,
    visibility: row.visibility,
    maskRule: row.maskRule,
    sensitive: row.sensitive,
    selfEditable: row.selfEditable,
    required: row.required,
    sort: row.sort,
  })
  optionsText.value = row.options.join(',')
  defVisible.value = true
}

async function submitDef() {
  if (!defForm.name || !defForm.fieldCode) {
    ElMessage.warning('请填写名称和字段编码')
    return
  }
  const payload: CustomFieldDefPayload = {
    ...defForm,
    entityType: 'EMPLOYEE',
    options: optionsText.value
      ? optionsText.value.split(',').map((item) => item.trim()).filter(Boolean)
      : [],
  }
  saving.value = true
  try {
    if (defForm.id) {
      await updateCustomFieldDef(defForm.id, payload)
    } else {
      await createCustomFieldDef(payload)
    }
    ElMessage.success('已保存')
    defVisible.value = false
    await loadDefs()
  } finally {
    saving.value = false
  }
}

async function disable(row: CustomFieldDef) {
  await ElMessageBox.confirm(`确定停用属性「${row.name}」吗？现有值会保留。`, '提示', { type: 'warning' })
  await disableCustomFieldDef(row.id)
  ElMessage.success('已停用')
  await loadDefs()
}

async function loadValues() {
  if (!selectedEmployeeId.value) return
  values.value = await getCustomFieldValues(selectedEmployeeId.value)
  Object.keys(valueForm).forEach((key) => delete valueForm[key])
  for (const item of values.value) {
    valueForm[item.fieldCode] = item.value ?? ''
  }
}

async function saveValues() {
  const payload: Record<string, string | null> = {}
  for (const item of values.value) {
    if (!item.visible || !item.editable) continue
    payload[item.fieldCode] = valueForm[item.fieldCode] || null
  }
  if (!Object.keys(payload).length) {
    ElMessage.warning('没有可修改的字段')
    return
  }
  saving.value = true
  try {
    await setCustomFieldValues(selectedEmployeeId.value, payload)
    ElMessage.success('已保存')
    await loadValues()
  } finally {
    saving.value = false
  }
}

async function openHistory(item: CustomFieldValue) {
  const rows = await getCustomFieldValues(selectedEmployeeId.value, true)
  historyRows.value = rows.filter((row) => row.defId === item.defId)
  historyTitle.value = item.name
  historyVisible.value = true
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
}
.hint {
  color: #909399;
  font-size: 12px;
}
.value-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hidden-value {
  color: #c0c4cc;
}
.value-form {
  max-width: 760px;
}
</style>
