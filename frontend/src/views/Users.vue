<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="用户名 / 显示名" clearable class="filter" @keyup.enter="load" />
        <el-select v-model="query.status" clearable placeholder="状态" class="short-filter">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-radio-group v-model="viewMode" size="small" @change="onViewModeChange">
          <el-radio-button value="table">表格</el-radio-button>
          <el-radio-button value="graph">角色概览</el-radio-button>
        </el-radio-group>
        <div class="spacer" />
        <el-button v-if="auth.hasPermission('user:create')" type="primary" @click="openCreate">新增账号</el-button>
      </div>

      <template v-if="viewMode === 'table'">
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="displayName" label="显示名" width="140" />
        <el-table-column label="关联员工" min-width="170">
          <template #default="{ row }">
            <template v-if="row.employeeName">
              {{ row.employeeName }}
              <span class="muted">（{{ row.employeeNo }}）</span>
            </template>
            <span v-else class="muted">未关联</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="260">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role.id" size="small" class="role-tag" :type="role.code === 'SYS_ADMIN' ? 'danger' : 'info'">
              {{ role.name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.hasPermission('user:role:assign')" link type="primary" @click="openRoles(row)">角色</el-button>
            <el-button v-if="auth.hasPermission('user:read')" link type="primary" @click="openHistory(row)">角色历史</el-button>
            <el-button v-if="auth.hasPermission('user:update')" link type="primary" @click="onResetPassword(row)">重置密码</el-button>
            <el-button
              v-if="auth.hasPermission('user:disable') && row.status === 'ACTIVE' && row.id !== auth.user?.userId"
              link
              type="danger"
              @click="onDisable(row)"
            >禁用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="load"
          @current-change="load"
        />
      </div>
      </template>

      <template v-else>
        <RoleOverview v-loading="graphLoading" :users="graphUsers" :roles="roles" />
      </template>
    </el-card>

    <el-dialog v-model="createVisible" title="新增账号" width="560px" destroy-on-close>
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="显示名" required>
          <el-input v-model="createForm.displayName" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item label="关联员工">
          <el-select v-model="createForm.employeeId" clearable filterable placeholder="选择员工档案（一人一账号）">
            <el-option v-for="e in employees" :key="e.id" :label="`${e.name}（${e.employeeNo}）`" :value="e.id" />
          </el-select>
          <div class="hint">每位员工只能有一个账号；兼任多个岗位请在该账号下分配多个角色。</div>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleCodes" multiple placeholder="选择角色">
            <el-option v-for="role in roles" :key="role.code" :label="role.name" :value="role.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" :title="`分配角色 - ${roleUserName}`" width="760px">
      <el-alert
        type="info"
        :closable="false"
        title="同一个人可以同时担任多个角色，也可设置生效/失效日期；到期后自动失效并保留历史，调岗无需新建账号。数据范围与字段可见性另由字段权限和范围配置决定。"
        class="tip"
      />
      <el-table :data="roleRows" size="small">
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.roleCode" filterable placeholder="选择角色" style="width: 100%">
              <el-option v-for="role in roles" :key="role.code" :label="`${role.name}（${role.code}）`" :value="role.code" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="生效日期" width="150">
          <template #default="{ row }">
            <el-date-picker v-model="row.startDate" type="date" value-format="YYYY-MM-DD" placeholder="立即" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="失效日期" width="150">
          <template #default="{ row }">
            <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" placeholder="长期" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="140">
          <template #default="{ row }">
            <el-input v-model="row.note" placeholder="如：项目负责人 / 兼任审计" />
          </template>
        </el-table-column>
        <el-table-column width="64">
          <template #default="{ $index }">
            <el-button link type="danger" @click="roleRows.splice($index, 1)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="add-row" @click="addRoleRow">+ 增加角色</el-button>
      <div class="hint">不填生效日期表示立即生效；不填失效日期表示长期有效，离职/调岗时由管理员撤销。</div>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" :title="`角色变更历史 - ${historyUserName}`" width="820px">
      <el-table :data="historyRows" size="small" v-loading="historyLoading">
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">{{ row.roleName || '—' }} <span class="muted">{{ row.roleCode }}</span></template>
        </el-table-column>
        <el-table-column prop="startDate" label="生效" width="110" />
        <el-table-column label="失效" width="110">
          <template #default="{ row }">{{ row.endDate || '长期' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">{{ row.note || '—' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="操作时间" width="170" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  assignRoles,
  createUser,
  disableUser,
  listRoles,
  listUserRoles,
  pageUsers,
  resetPassword,
} from '@/api/users'
import { pageEmployees } from '@/api/employees'
import RoleOverview from '@/components/RoleOverview.vue'
import type { EmployeeView, RoleAssignmentView, RoleView, UserView } from '@/types'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const list = ref<UserView[]>([])
const total = ref(0)
const roles = ref<RoleView[]>([])
const employees = ref<EmployeeView[]>([])
const query = reactive({ page: 1, size: 20, keyword: '', status: '' })
const viewMode = ref<'table' | 'graph'>('table')
const graphUsers = ref<UserView[]>([])
const graphLoading = ref(false)

const createVisible = ref(false)
const createForm = reactive({ username: '', displayName: '', password: '', employeeId: '', roleCodes: ['EMPLOYEE'] })

interface RoleRow {
  roleCode: string
  startDate: string
  endDate: string
  note: string
}

const roleVisible = ref(false)
const roleUserId = ref('')
const roleUserName = ref('')
const roleRows = ref<RoleRow[]>([])

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyUserName = ref('')
const historyRows = ref<RoleAssignmentView[]>([])

const statusLabels: Record<string, string> = {
  ACTIVE: '生效中',
  SCHEDULED: '待生效',
  EXPIRED: '已到期',
  REVOKED: '已撤销',
}
const statusTypes: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
  ACTIVE: 'success',
  SCHEDULED: 'warning',
  EXPIRED: 'info',
  REVOKED: 'danger',
}

function statusLabel(status: string) {
  return statusLabels[status] || status
}

function statusType(status: string) {
  return statusTypes[status] || 'info'
}

async function load() {
  loading.value = true
  try {
    const result = await pageUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    })
    list.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.page = 1
  query.keyword = ''
  query.status = ''
  load()
  if (viewMode.value === 'graph') loadGraphUsers()
}

async function loadGraphUsers() {
  graphLoading.value = true
  try {
    const result = await pageUsers({
      page: 1,
      size: 100,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    })
    graphUsers.value = result.records
  } finally {
    graphLoading.value = false
  }
}

function onViewModeChange(value: string | number | boolean | undefined) {
  if (value === 'graph') loadGraphUsers()
}

function openCreate() {
  Object.assign(createForm, { username: '', displayName: '', password: '', employeeId: '', roleCodes: ['EMPLOYEE'] })
  createVisible.value = true
}

async function submitCreate() {
  if (!createForm.username.trim() || !createForm.displayName.trim() || !createForm.password) {
    ElMessage.warning('请填写用户名、显示名和初始密码')
    return
  }
  saving.value = true
  try {
    await createUser({
      username: createForm.username.trim(),
      displayName: createForm.displayName.trim(),
      password: createForm.password,
      employeeId: createForm.employeeId || undefined,
      roleCodes: createForm.roleCodes,
    })
    ElMessage.success('账号已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function addRoleRow() {
  roleRows.value.push({ roleCode: '', startDate: '', endDate: '', note: '' })
}

async function openRoles(row: UserView) {
  roleUserId.value = row.id
  roleUserName.value = row.displayName || row.username
  roleRows.value = []
  roleVisible.value = true
  if (auth.hasPermission('user:read')) {
    const history = await listUserRoles(row.id)
    roleRows.value = history
      .filter((item) => item.status === 'ACTIVE' && item.roleCode)
      .map((item) => ({
        roleCode: item.roleCode as string,
        startDate: item.startDate || '',
        endDate: item.endDate || '',
        note: item.note || '',
      }))
  } else {
    roleRows.value = row.roles.map((role) => ({
      roleCode: role.code,
      startDate: '',
      endDate: '',
      note: '',
    }))
  }
  if (roleRows.value.length === 0) addRoleRow()
}

async function submitRoles() {
  const rows = roleRows.value.filter((row) => row.roleCode)
  if (rows.length === 0) {
    ElMessage.warning('请至少选择一个角色')
    return
  }
  if (new Set(rows.map((row) => row.roleCode)).size !== rows.length) {
    ElMessage.warning('同一個角色不能重复添加')
    return
  }
  const invalid = rows.find((row) => row.startDate && row.endDate && row.endDate < row.startDate)
  if (invalid) {
    ElMessage.warning('失效日期不能早于生效日期')
    return
  }
  saving.value = true
  try {
    await assignRoles(
      roleUserId.value,
      rows.map((row) => ({
        roleCode: row.roleCode,
        startDate: row.startDate || null,
        endDate: row.endDate || null,
        note: row.note || null,
      })),
    )
    ElMessage.success('角色已更新')
    roleVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function openHistory(row: UserView) {
  historyUserName.value = row.displayName || row.username
  historyRows.value = []
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyRows.value = await listUserRoles(row.id)
  } finally {
    historyLoading.value = false
  }
}

async function onResetPassword(row: UserView) {
  const { value } = await ElMessageBox.prompt(`为 ${row.username} 设置新密码（至少 8 位）`, '重置密码', {
    inputType: 'password',
    inputValidator: (input) => (input && input.length >= 8) || '密码至少 8 位',
  }).catch(() => ({ value: null }))
  if (!value) return
  await resetPassword(row.id, value)
  ElMessage.success('密码已重置')
}

async function onDisable(row: UserView) {
  await ElMessageBox.confirm(`确定禁用账号“${row.username}”吗？`, '提示', { type: 'warning' })
  await disableUser(row.id)
  ElMessage.success('账号已禁用')
  await load()
}

onMounted(async () => {
  await load()
  if (auth.hasPermission('role:read')) {
    roles.value = await listRoles()
  }
  if (auth.hasPermission('employee:read')) {
    const result = await pageEmployees({ page: 1, size: 200, status: 'ACTIVE' })
    employees.value = result.records
  }
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.filter {
  width: 220px;
}
.short-filter {
  width: 120px;
}
.spacer {
  flex: 1;
}
.role-tag {
  margin-right: 4px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.tip {
  margin-bottom: 12px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.add-row {
  margin-top: 10px;
}
.hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>
