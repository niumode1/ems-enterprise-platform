<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="姓名 / 工号 / 手机 / 邮箱" clearable class="search" @keyup.enter="runQuery" />
        <el-tree-select
          v-model="query.orgId"
          :data="orgTree"
          :props="orgProps"
          clearable
          check-strictly
          placeholder="按部门筛选"
          class="org-filter"
        />
        <el-select v-model="query.status" clearable placeholder="状态" class="status-filter">
          <el-option label="在职" value="ACTIVE" />
          <el-option label="离职" value="LEFT" />
        </el-select>
        <el-button type="primary" @click="runQuery">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-radio-group v-model="viewMode" size="small" class="view-switch">
          <el-radio-button value="list">列表</el-radio-button>
          <el-radio-button value="bubble">气泡关系</el-radio-button>
        </el-radio-group>
        <div class="spacer" />
        <el-button v-if="auth.hasPermission('employee:create')" type="primary" @click="openCreate">新增员工</el-button>
      </div>

      <template v-if="viewMode === 'list'">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="employeeNo" label="工号" width="100" />
        <el-table-column prop="name" label="姓名" width="110" />
        <el-table-column prop="orgName" label="部门" width="140" />
        <el-table-column prop="positionName" label="岗位" width="140" />
        <el-table-column prop="managerName" label="直属上级" width="110" />
        <el-table-column label="手机" width="150">
          <template #default="{ row }">
            <span>{{ row.phone || '—' }}</span>
            <el-tag v-if="row.maskedFields?.includes('phone')" size="small" type="warning" class="mask-tag">脱敏</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="180">
          <template #default="{ row }">
            <span>{{ row.email || '—' }}</span>
            <el-tag v-if="row.maskedFields?.includes('email')" size="small" type="warning" class="mask-tag">脱敏</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '在职' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="auth.hasPermission('employee:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="load"
          @current-change="load"
        />
      </div>
      </template>

      <div v-else v-loading="bubbleLoading" class="bubble-wrap">
        <el-alert
          v-if="bubbleTotal > bubbleEmployees.length"
          class="bubble-alert"
          type="info"
          :closable="false"
          :title="`当前筛选共 ${bubbleTotal} 人，气泡图展示前 ${bubbleEmployees.length} 人，请用关键词或部门缩小范围。`"
        />
        <EmployeeBubbleGraph :employees="bubbleEmployees" @select="showDetail" />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="员工档案" size="520px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="工号">{{ detail.employeeNo }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="性别">{{ genderLabel(detail.gender) }}</el-descriptions-item>
          <el-descriptions-item label="出生日期">{{ detail.birthDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detail.orgName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ detail.positionName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="直属上级">{{ detail.managerName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="入职日期">{{ detail.hireDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="手机">
            {{ detail.phone || '—' }}
            <el-tag v-if="detail.maskedFields?.includes('phone')" size="small" type="warning">脱敏</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ detail.email || '—' }}
            <el-tag v-if="detail.maskedFields?.includes('email')" size="small" type="warning">脱敏</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="身份证">
            {{ detail.idCard || '—' }}
            <el-tag v-if="detail.maskedFields?.includes('idCard')" size="small" type="warning">脱敏</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="住址">
            {{ detail.address || '—' }}
            <el-tag v-if="detail.maskedFields?.includes('address')" size="small" type="warning">脱敏</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="紧急联系人">
            {{ detail.emergencyContact || '—' }}
            <el-tag v-if="detail.maskedFields?.includes('emergencyContact')" size="small" type="warning">脱敏</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ detail.status }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-descriptions v-if="relations" class="relation-block" :column="1" border size="small">
          <template #title>汇报关系</template>
          <el-descriptions-item label="直属上级">{{ relationText(relations.manager) }}</el-descriptions-item>
          <el-descriptions-item label="汇报链">
            {{ relations.superiors.length ? relations.superiors.map(relationText).join(' → ') : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="直接下属">
            {{ relations.subordinates.length ? relations.subordinates.map(relationText).join('、') : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="同级同事">
            {{ relations.siblings.length ? relations.siblings.map(relationText).join('、') : '—' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="detail.maskedFields?.length"
          class="mask-alert"
          type="warning"
          :closable="false"
          title="部分字段已按字段权限脱敏；如需查看完整内容，请联系管理员或发起敏感信息申请。"
        />
      </template>
    </el-drawer>

    <el-dialog v-model="formVisible" :title="editingId ? '编辑员工' : '新增员工'" width="760px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="工号" required>
              <el-input v-model="form.employeeNo" :disabled="Boolean(editingId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" required>
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别">
              <el-select v-model="form.gender" clearable placeholder="请选择">
                <el-option label="男" value="MALE" />
                <el-option label="女" value="FEMALE" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="部门">
              <el-tree-select
                v-model="form.primaryOrgId"
                :data="orgTree"
                :props="orgProps"
                clearable
                check-strictly
                placeholder="选择部门"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="岗位">
              <el-select v-model="form.primaryPositionId" clearable placeholder="选择岗位">
                <el-option v-for="p in positions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="直属上级">
              <el-select v-model="form.managerId" clearable filterable placeholder="选择上级">
                <el-option
                  v-for="e in managerList"
                  :key="e.id"
                  :label="`${e.name}（${e.employeeNo}）`"
                  :value="e.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="入职日期">
              <el-date-picker v-model="form.hireDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机">
              <el-input v-model="form.phone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model="form.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身份证">
              <el-input v-model="form.idCard" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="紧急联系人">
              <el-input v-model="form.emergencyContact" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="住址">
              <el-input v-model="form.address" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="修改原因">
              <el-input v-model="form.reason" type="textarea" :rows="2" placeholder="关键信息修改需要填写原因，便于审批与审计" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import EmployeeBubbleGraph from '@/components/EmployeeBubbleGraph.vue'
import {
  createEmployee,
  getEmployee,
  getEmployeeRelations,
  pageEmployees,
  updateEmployee,
  type EmployeePayload,
} from '@/api/employees'
import { listPositions, orgTree as loadOrgTree } from '@/api/org'
import type { EmployeeRelations, EmployeeView, OrgNode, Position, RelationEmployee } from '@/types'

const BUBBLE_LIMIT = 500

const auth = useAuthStore()
const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const list = ref<EmployeeView[]>([])
const total = ref(0)
const orgTree = ref<OrgNode[]>([])
const positions = ref<Position[]>([])
const managerOptions = ref<EmployeeView[]>([])
const query = reactive({ page: 1, size: 20, keyword: '', orgId: '' as string, status: '' })
const viewMode = ref<'list' | 'bubble'>('list')
const bubbleLoading = ref(false)
const bubbleEmployees = ref<EmployeeView[]>([])
const bubbleTotal = ref(0)

const orgProps = { label: 'name', children: 'children', value: 'id' }

const detailVisible = ref(false)
const detail = ref<EmployeeView | null>(null)
const relations = ref<EmployeeRelations | null>(null)

interface EmployeeForm {
  employeeNo: string
  name: string
  gender: string
  birthDate: string
  phone: string
  email: string
  idCard: string
  address: string
  emergencyContact: string
  hireDate: string
  primaryOrgId: string
  primaryPositionId: string
  managerId: string
  reason: string
}

const emptyForm = (): EmployeeForm => ({
  employeeNo: '',
  name: '',
  gender: '',
  birthDate: '',
  phone: '',
  email: '',
  idCard: '',
  address: '',
  emergencyContact: '',
  hireDate: '',
  primaryOrgId: '',
  primaryPositionId: '',
  managerId: '',
  reason: '',
})

const form = reactive<EmployeeForm>(emptyForm())
const formVisible = ref(false)
const editingId = ref('')
const original = ref<EmployeeForm>(emptyForm())

const managerList = computed(() => managerOptions.value.filter((e) => e.id !== editingId.value))

function filterParams() {
  return {
    keyword: query.keyword || undefined,
    orgId: query.orgId || undefined,
    status: query.status || undefined,
  }
}

async function load() {
  loading.value = true
  try {
    const result = await pageEmployees({
      page: query.page,
      size: query.size,
      ...filterParams(),
    })
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

async function loadBubble() {
  bubbleLoading.value = true
  try {
    const result = await pageEmployees({
      page: 1,
      size: BUBBLE_LIMIT,
      ...filterParams(),
    })
    bubbleEmployees.value = result.records
    bubbleTotal.value = result.total
  } finally {
    bubbleLoading.value = false
  }
}

function runQuery() {
  if (viewMode.value === 'bubble') {
    loadBubble()
  } else {
    load()
  }
}

function reloadCurrent() {
  return viewMode.value === 'bubble' ? loadBubble() : load()
}

function resetQuery() {
  query.page = 1
  query.keyword = ''
  query.orgId = ''
  query.status = ''
  runQuery()
}

watch(viewMode, (mode) => {
  if (mode === 'bubble') loadBubble()
})

async function loadLookups() {
  const [orgs, pos] = await Promise.all([loadOrgTree(), listPositions()])
  orgTree.value = orgs
  positions.value = pos
  const employees = await pageEmployees({ page: 1, size: 200, status: 'ACTIVE' })
  managerOptions.value = employees.records
}

function relationText(person: RelationEmployee | null) {
  return person ? `${person.name}（${person.employeeNo}）` : '—'
}

function showDetail(id: string) {
  detail.value = null
  relations.value = null
  detailVisible.value = true
  getEmployee(id).then((data) => {
    detail.value = data
  })
  if (auth.hasPermission('org:read')) {
    getEmployeeRelations(id)
      .then((data) => {
        relations.value = data
      })
      .catch(() => {
        relations.value = null
      })
  }
}

function openDetail(row: EmployeeView) {
  showDetail(row.id)
}

function openCreate() {
  editingId.value = ''
  Object.assign(form, emptyForm())
  original.value = emptyForm()
  formVisible.value = true
}

function openEdit(row: EmployeeView) {
  getEmployee(row.id).then((data) => {
    editingId.value = data.id
    const snapshot: EmployeeForm = {
      employeeNo: data.employeeNo,
      name: data.name ?? '',
      gender: data.gender ?? '',
      birthDate: data.birthDate ?? '',
      phone: data.phone ?? '',
      email: data.email ?? '',
      idCard: data.idCard ?? '',
      address: data.address ?? '',
      emergencyContact: data.emergencyContact ?? '',
      hireDate: data.hireDate ?? '',
      primaryOrgId: data.orgId ?? '',
      primaryPositionId: data.positionId ?? '',
      managerId: data.managerId ?? '',
      reason: '',
    }
    Object.assign(form, snapshot)
    original.value = { ...snapshot }
    formVisible.value = true
  })
}

function buildPayload(): EmployeePayload {
  if (!editingId.value) {
    return {
      employeeNo: form.employeeNo.trim(),
      name: form.name.trim(),
      gender: form.gender || undefined,
      birthDate: form.birthDate || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined,
      idCard: form.idCard || undefined,
      address: form.address || undefined,
      emergencyContact: form.emergencyContact || undefined,
      hireDate: form.hireDate || undefined,
      primaryOrgId: form.primaryOrgId || undefined,
      primaryPositionId: form.primaryPositionId || undefined,
      managerId: form.managerId || undefined,
    }
  }
  const keys: Array<keyof EmployeeForm> = [
    'name', 'gender', 'birthDate', 'phone', 'email', 'idCard', 'address',
    'emergencyContact', 'hireDate', 'primaryOrgId', 'primaryPositionId', 'managerId',
  ]
  const payload: Record<string, unknown> = { reason: form.reason || undefined }
  for (const key of keys) {
    if (form[key] !== original.value[key]) {
      payload[key] = form[key] === '' ? '' : form[key]
    }
  }
  return payload as unknown as EmployeePayload
}

async function submitForm() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写姓名')
    return
  }
  if (!editingId.value && !form.employeeNo.trim()) {
    ElMessage.warning('请填写工号')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      const result = await updateEmployee(editingId.value, buildPayload())
      ElMessage.success(result.message || '保存成功')
    } else {
      await createEmployee(buildPayload())
      ElMessage.success('员工已创建')
    }
    formVisible.value = false
    await reloadCurrent()
  } finally {
    saving.value = false
  }
}

function genderLabel(value: string | null) {
  if (value === 'MALE') return '男'
  if (value === 'FEMALE') return '女'
  if (value === 'OTHER') return '其他'
  return '—'
}

onMounted(async () => {
  load()
  loadLookups()
  const focus = route.query.focus
  if (typeof focus === 'string' && focus) {
    try {
      detail.value = await getEmployee(focus)
      detailVisible.value = true
      if (auth.hasPermission('org:read')) {
        getEmployeeRelations(focus)
          .then((data) => {
            relations.value = data
          })
          .catch(() => {
            relations.value = null
          })
      }
    } catch {
      // 搜索结果指向的员工可能已不可见，保持列表页
    }
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.search {
  width: 240px;
}
.org-filter {
  width: 180px;
}
.status-filter {
  width: 120px;
}
.view-switch {
  margin-left: 4px;
}
.bubble-wrap {
  min-height: 200px;
}
.bubble-alert {
  margin-bottom: 12px;
}
.spacer {
  flex: 1;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.mask-tag {
  margin-left: 6px;
}
.mask-alert {
  margin-top: 14px;
}
.relation-block {
  margin-top: 16px;
}
</style>
