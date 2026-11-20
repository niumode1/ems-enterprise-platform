<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-select v-model="entityType" class="filter" @change="load">
          <el-option label="员工字段" value="EMPLOYEE" />
        </el-select>
        <el-text type="info">默认规则（角色=全部）对所有人生效；角色专属规则优先于默认规则。</el-text>
        <div class="spacer" />
        <el-button v-if="auth.hasPermission('field:update')" type="primary" @click="openCreate">新增规则</el-button>
      </div>

      <el-table :data="rules" v-loading="loading" stripe>
        <el-table-column prop="fieldCode" label="字段" width="160" />
        <el-table-column label="适用角色" width="130">
          <template #default="{ row }">{{ roleLabel(row.roleId) }}</template>
        </el-table-column>
        <el-table-column prop="visibility" label="可见范围" min-width="260" show-overflow-tooltip />
        <el-table-column prop="maskRule" label="脱敏" width="110" />
        <el-table-column label="本人可改" width="90">
          <template #default="{ row }">
            <el-tag :type="row.selfEdit ? 'success' : 'info'" size="small">{{ row.selfEdit ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="可导出" width="80">
          <template #default="{ row }">{{ row.exportable ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="AI可读" width="80">
          <template #default="{ row }">{{ row.aiReadable ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="敏感" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.sensitive" type="danger" size="small">敏感</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="关键" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.keyField" type="warning" size="small">关键</el-tag>
            <span v-else>否</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.hasPermission('field:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑字段权限' : '新增字段权限'" width="600px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-form-item label="实体类型">
          <el-input v-model="form.entityType" disabled />
        </el-form-item>
        <el-form-item label="字段编码" required>
          <el-input v-model="form.fieldCode" :disabled="Boolean(editingId)" placeholder="如 phone / idCard / position" />
        </el-form-item>
        <el-form-item label="适用角色">
          <el-select v-model="form.roleId" placeholder="默认规则">
            <el-option label="默认（全部角色）" value="0" />
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见范围" required>
          <el-input v-model="form.visibility" placeholder="ALL 或 SELF,DIRECT_REPORTS,HR_ADMIN,PROJECT_MEMBER,BOSS" />
        </el-form-item>
        <el-form-item label="脱敏规则">
          <el-select v-model="form.maskRule">
            <el-option label="不脱敏" value="NONE" />
            <el-option label="手机号" value="PHONE" />
            <el-option label="邮箱" value="EMAIL" />
            <el-option label="身份证" value="ID_CARD" />
            <el-option label="地址" value="ADDRESS" />
            <el-option label="联系人" value="CONTACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据范围">
          <el-input v-model="form.scopeType" placeholder="ALL / DEPT / SELF" />
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="本人可改"><el-switch v-model="form.selfEdit" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可导出"><el-switch v-model="form.exportable" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="AI 可读"><el-switch v-model="form.aiReadable" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="敏感字段"><el-switch v-model="form.sensitive" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关键字段"><el-switch v-model="form.keyField" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createFieldPermission,
  listFieldPermissions,
  updateFieldPermission,
  type FieldPermissionPayload,
} from '@/api/fieldPermissions'
import { listRoles } from '@/api/users'
import type { FieldPermission, RoleView } from '@/types'

const auth = useAuthStore()
const entityType = ref('EMPLOYEE')
const loading = ref(false)
const saving = ref(false)
const rules = ref<FieldPermission[]>([])
const roles = ref<RoleView[]>([])
const dialogVisible = ref(false)
const editingId = ref('')

const form = reactive({
  entityType: 'EMPLOYEE',
  fieldCode: '',
  roleId: '0',
  scopeType: 'ALL',
  visibility: 'ALL',
  maskRule: 'NONE',
  selfEdit: false,
  exportable: false,
  aiReadable: true,
  sensitive: false,
  keyField: false,
})

async function load() {
  loading.value = true
  try {
    rules.value = await listFieldPermissions(entityType.value)
  } finally {
    loading.value = false
  }
}

function roleLabel(roleId: string) {
  if (!roleId || roleId === '0') return '默认'
  return roles.value.find((role) => role.id === roleId)?.name ?? `角色 ${roleId}`
}

function openCreate() {
  editingId.value = ''
  Object.assign(form, {
    entityType: entityType.value,
    fieldCode: '',
    roleId: '0',
    scopeType: 'ALL',
    visibility: 'ALL',
    maskRule: 'NONE',
    selfEdit: false,
    exportable: false,
    aiReadable: true,
    sensitive: false,
    keyField: false,
  })
  dialogVisible.value = true
}

function openEdit(row: FieldPermission) {
  editingId.value = row.id
  Object.assign(form, {
    entityType: row.entityType,
    fieldCode: row.fieldCode,
    roleId: row.roleId ?? '0',
    scopeType: row.scopeType,
    visibility: row.visibility,
    maskRule: row.maskRule,
    selfEdit: row.selfEdit,
    exportable: row.exportable,
    aiReadable: row.aiReadable,
    sensitive: row.sensitive,
    keyField: row.keyField,
  })
  dialogVisible.value = true
}

async function submit() {
  if (!form.fieldCode.trim() || !form.visibility.trim()) {
    ElMessage.warning('请填写字段编码与可见范围')
    return
  }
  const payload: FieldPermissionPayload = {
    entityType: form.entityType,
    fieldCode: form.fieldCode.trim(),
    roleId: form.roleId,
    scopeType: form.scopeType || 'ALL',
    visibility: form.visibility.trim(),
    maskRule: form.maskRule,
    selfEdit: form.selfEdit,
    exportable: form.exportable,
    aiReadable: form.aiReadable,
    sensitive: form.sensitive,
    keyField: form.keyField,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateFieldPermission(editingId.value, payload)
      ElMessage.success('字段权限已更新')
    } else {
      await createFieldPermission(payload)
      ElMessage.success('字段权限已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  if (auth.hasPermission('role:read')) {
    roles.value = await listRoles()
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
  width: 160px;
}
.spacer {
  flex: 1;
}
</style>
