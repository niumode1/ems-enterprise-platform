<template>
  <div class="profile">
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>我的档案</span>
              <el-tag v-if="employee" size="small" :type="employee.status === 'ACTIVE' ? 'success' : 'info'">
                {{ employee.status === 'ACTIVE' ? '在职' : employee.status }}
              </el-tag>
            </div>
          </template>
          <el-empty v-if="!employee" description="当前账号未关联员工档案" />
          <template v-else>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="工号">{{ employee.employeeNo }}</el-descriptions-item>
              <el-descriptions-item label="姓名">{{ employee.name }}</el-descriptions-item>
              <el-descriptions-item label="部门">{{ employee.orgName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="岗位">{{ employee.positionName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="直属上级">{{ employee.managerName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="入职日期">{{ employee.hireDate || '—' }}</el-descriptions-item>
            </el-descriptions>

            <h3 class="section-title">本人可维护的信息</h3>
            <el-form :model="form" label-width="110px">
              <el-form-item label="手机">
                <div class="field-line">
                  <el-input v-model="form.phone" />
                  <el-tooltip content="关闭后，普通同事将看不到该信息；本人与有敏感读取权限的人仍可见" placement="top">
                    <el-switch
                      v-if="canHide('phone')"
                      v-model="visibilityState.phone"
                      inline-prompt
                      active-text="👁"
                      inactive-text="🙈"
                      @change="saveVisibility('phone', Boolean($event))"
                    />
                  </el-tooltip>
                </div>
              </el-form-item>
              <el-form-item label="邮箱">
                <div class="field-line">
                  <el-input v-model="form.email" />
                  <el-tooltip content="关闭后，普通同事将看不到该信息；本人与有敏感读取权限的人仍可见" placement="top">
                    <el-switch
                      v-if="canHide('email')"
                      v-model="visibilityState.email"
                      inline-prompt
                      active-text="👁"
                      inactive-text="🙈"
                      @change="saveVisibility('email', Boolean($event))"
                    />
                  </el-tooltip>
                </div>
              </el-form-item>
              <el-form-item label="住址">
                <div class="field-line">
                  <el-input v-model="form.address" />
                  <el-tooltip content="关闭后，普通同事将看不到该信息；本人与有敏感读取权限的人仍可见" placement="top">
                    <el-switch
                      v-if="canHide('address')"
                      v-model="visibilityState.address"
                      inline-prompt
                      active-text="👁"
                      inactive-text="🙈"
                      @change="saveVisibility('address', Boolean($event))"
                    />
                  </el-tooltip>
                </div>
              </el-form-item>
              <el-form-item label="紧急联系人">
                <div class="field-line">
                  <el-input v-model="form.emergencyContact" />
                  <el-tooltip content="关闭后，普通同事将看不到该信息；本人与有敏感读取权限的人仍可见" placement="top">
                    <el-switch
                      v-if="canHide('emergencyContact')"
                      v-model="visibilityState.emergencyContact"
                      inline-prompt
                      active-text="👁"
                      inactive-text="🙈"
                      @change="saveVisibility('emergencyContact', Boolean($event))"
                    />
                  </el-tooltip>
                </div>
              </el-form-item>

              <el-form-item
                v-for="item in extraVisibilityFields"
                :key="item.fieldCode"
                :label="item.label"
              >
                <div class="field-line">
                  <el-text type="info">该字段由公司配置为可隐藏</el-text>
                  <el-tooltip content="关闭后，普通同事将看不到该信息" placement="top">
                    <el-switch
                      v-model="visibilityState[item.fieldCode]"
                      inline-prompt
                      active-text="👁"
                      inactive-text="🙈"
                      @change="saveVisibility(item.fieldCode, Boolean($event))"
                    />
                  </el-tooltip>
                </div>
              </el-form-item>

              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
              </el-form-item>
            </el-form>
            <el-alert
              type="info"
              :closable="false"
              title="身份证、部门、岗位、直属上级等关键信息不能本人修改，需由人事发起并多人审批。"
            />
            <el-alert
              class="visibility-tip"
              type="success"
              :closable="false"
              show-icon
              title="小眼睛开关用于控制普通同事能否看到您的手机、邮箱、住址和紧急联系人；关闭后仅本人和公司配置的敏感读取角色可见。"
            />
          </template>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card>
          <template #header>账号信息</template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">{{ auth.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="显示名">{{ auth.user?.displayName }}</el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag v-for="role in auth.user?.roles || []" :key="role" size="small" class="tag">{{ roleLabel(role) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="权限点数量">{{ auth.permissions.length }}</el-descriptions-item>
          </el-descriptions>

          <h3 class="section-title">修改密码</h3>
          <el-form :model="passwordForm" label-width="86px">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.currentPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changingPassword" @click="changePassword">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  getMyEmployee,
  getMyFieldVisibility,
  updateMyEmployee,
  updateMyFieldVisibility,
  type EmployeePayload,
} from '@/api/employees'
import { changePassword as changePasswordApi } from '@/api/auth'
import type { EmployeeView, FieldVisibilityView } from '@/types'

const auth = useAuthStore()
const employee = ref<EmployeeView | null>(null)
const saving = ref(false)
const changingPassword = ref(false)

const form = reactive({ phone: '', email: '', address: '', emergencyContact: '' })
const original = reactive({ phone: '', email: '', address: '', emergencyContact: '' })

const visibilityOptions = ref<FieldVisibilityView[]>([])
const visibilityState = reactive<Record<string, boolean>>({})
const editableVisibilityFields = new Set(['phone', 'email', 'address', 'emergencyContact'])
const extraVisibilityFields = computed(() =>
  visibilityOptions.value.filter(
    (item) => item.canHide && !editableVisibilityFields.has(item.fieldCode),
  ),
)

function canHide(fieldCode: string) {
  const item = visibilityOptions.value.find((entry) => entry.fieldCode === fieldCode)
  return Boolean(item?.canHide)
}

async function loadVisibility() {
  try {
    const items = await getMyFieldVisibility()
    visibilityOptions.value = items
    for (const item of items) {
      visibilityState[item.fieldCode] = item.visible
    }
  } catch {
    visibilityOptions.value = []
  }
}

async function saveVisibility(fieldCode: string, visible: boolean) {
  const previous = !visible
  try {
    const result = await updateMyFieldVisibility(fieldCode, visible)
    visibilityState[fieldCode] = result.visible
    ElMessage.success(result.visible ? '普通同事可见' : '已对普通同事隐藏')
  } catch {
    visibilityState[fieldCode] = previous
  }
}

const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })

const roleLabels: Record<string, string> = {
  BOSS: '老板',
  SYS_ADMIN: '系统管理员',
  HR_ADMIN: '人事管理员',
  DEPT_LEADER: '行政组长',
  PROJECT_LEADER: '项目组长',
  EMPLOYEE: '员工',
  AUDITOR: '审计员',
}

function roleLabel(role: string) {
  return roleLabels[role] ?? role
}

async function load() {
  try {
    const data = await getMyEmployee()
    employee.value = data
    Object.assign(form, {
      phone: data.phone ?? '',
      email: data.email ?? '',
      address: data.address ?? '',
      emergencyContact: data.emergencyContact ?? '',
    })
    Object.assign(original, form)
    await loadVisibility()
  } catch {
    employee.value = null
  }
}

async function saveProfile() {
  const payload: EmployeePayload = {}
  let changed = false
  for (const key of ['phone', 'email', 'address', 'emergencyContact'] as const) {
    if (form[key] !== original[key]) {
      payload[key] = form[key]
      changed = true
    }
  }
  if (!changed) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  saving.value = true
  try {
    const result = await updateMyEmployee(payload)
    if (result.status === 'PENDING_APPROVAL') {
      ElMessage.warning(result.message)
    } else {
      ElMessage.success(result.message || '已保存')
    }
    await load()
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  if (!passwordForm.currentPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写当前密码和新密码')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    await changePasswordApi(passwordForm.currentPassword, passwordForm.newPassword)
    ElMessage.success('密码已修改')
    passwordForm.currentPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } finally {
    changingPassword.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-title {
  margin: 20px 0 12px;
  font-size: 15px;
}
.field-line {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.field-line .el-input {
  flex: 1;
}
.visibility-tip {
  margin-top: 12px;
}
.tag {
  margin-right: 6px;
}
</style>
