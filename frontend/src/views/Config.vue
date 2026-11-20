<template>
  <div>
    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane label="系统参数" name="config">
          <div class="toolbar">
            <el-select v-model="group" clearable placeholder="全部分组" class="filter" @change="loadConfig">
              <el-option v-for="g in groups" :key="g" :label="g" :value="g" />
            </el-select>
            <el-input v-model="keyword" placeholder="搜索配置键" clearable class="filter" @keyup.enter="loadConfig" />
            <el-button type="primary" @click="loadConfig">查询</el-button>
          </div>

          <el-table :data="configs" v-loading="loading" stripe>
            <el-table-column prop="configKey" label="配置键" min-width="260" />
            <el-table-column label="当前值" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="mono">{{ row.configValue ?? '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="valueType" label="类型" width="100" />
            <el-table-column prop="configGroup" label="分组" width="110" />
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="auth.hasPermission('config:update')" link type="primary" @click="openEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="审批策略" name="policies">
          <el-alert
            class="tip"
            type="info"
            :closable="false"
            title="审批人数、申请人是否计入、策略开关均由每家公司自行配置；修改审批策略本身也需要审批通过后生效。"
          />
          <el-table :data="policies" v-loading="policyLoading" stripe>
            <el-table-column prop="policyCode" label="策略编码" width="180" />
            <el-table-column prop="name" label="名称" width="160" />
            <el-table-column prop="fieldCodes" label="适用字段" min-width="220" show-overflow-tooltip />
            <el-table-column prop="quorum" label="通过人数" width="100" />
            <el-table-column label="申请人计入" width="110">
              <template #default="{ row }">{{ row.applicantIncluded ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="changeQuorum" label="改配人数" width="100" />
            <el-table-column label="启用" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="auth.hasPermission('config:approval:update')"
                  link
                  type="primary"
                  @click="openPolicy(row)"
                >修改</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="editVisible" title="修改配置" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="配置键">
          <el-input :model-value="editKey" disabled />
        </el-form-item>
        <el-form-item label="新值">
          <el-input v-model="editValue" type="textarea" :rows="3" :placeholder="isSecretKey ? '敏感配置不回显，请输入新值' : ''" />
        </el-form-item>
        <el-form-item label="修改原因">
          <el-input v-model="editReason" type="textarea" :rows="2" placeholder="受保护配置需审批，请填写原因" />
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        title="审批、审计、安全、老板访问、AI 模式等受保护配置修改后会进入审批中心，通过后自动生效并保留版本记录。"
      />
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitConfig">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="policyVisible" title="修改审批策略" width="520px" destroy-on-close>
      <el-form label-width="120px">
        <el-form-item label="通过人数">
          <el-input-number v-model="policyForm.quorum" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="改配所需人数">
          <el-input-number v-model="policyForm.changeQuorum" :min="1" :max="20" />
        </el-form-item>
        <el-form-item label="申请人计入">
          <el-switch v-model="policyForm.applicantIncluded" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="policyForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="policyVisible = false">取消</el-button>
        <el-button type="primary" :loading="policySaving" @click="submitPolicy">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  listConfig,
  listPolicies,
  updateConfig,
  updatePolicy,
} from '@/api/config'
import type { ApprovalPolicy, ConfigItem } from '@/types'

const auth = useAuthStore()
const tab = ref('config')
const loading = ref(false)
const configs = ref<ConfigItem[]>([])
const allGroups = ref<string[]>([])
const group = ref('')
const keyword = ref('')
const editVisible = ref(false)
const editKey = ref('')
const editValue = ref('')
const editReason = ref('')
const saving = ref(false)

const policyLoading = ref(false)
const policies = ref<ApprovalPolicy[]>([])
const policyVisible = ref(false)
const policySaving = ref(false)
const policyId = ref('')
const policyForm = reactive({ quorum: 2, changeQuorum: 2, applicantIncluded: false, enabled: true })

const groups = computed(() => allGroups.value)
const isSecretKey = computed(() => /apikey|secret|password/i.test(editKey.value))

async function loadConfig() {
  loading.value = true
  try {
    configs.value = await listConfig(group.value || undefined, keyword.value || undefined)
    if (!group.value && !keyword.value) {
      allGroups.value = [...new Set(configs.value.map((item) => item.configGroup))].sort()
    }
  } finally {
    loading.value = false
  }
}

async function loadPolicy() {
  policyLoading.value = true
  try {
    policies.value = await listPolicies()
  } finally {
    policyLoading.value = false
  }
}

function openEdit(row: ConfigItem) {
  editKey.value = row.configKey
  editValue.value = /apikey|secret|password/i.test(row.configKey) ? '' : (row.configValue ?? '')
  editReason.value = ''
  editVisible.value = true
}

async function submitConfig() {
  if (isSecretKey.value && !editValue.value) {
    ElMessage.warning('敏感配置不回显，请输入新的配置值')
    return
  }
  saving.value = true
  try {
    const result = await updateConfig(editKey.value, editValue.value, editReason.value || undefined)
    ElMessage.success(result.message || '已提交')
    editVisible.value = false
    await loadConfig()
  } finally {
    saving.value = false
  }
}

function openPolicy(row: ApprovalPolicy) {
  policyId.value = row.id
  Object.assign(policyForm, {
    quorum: row.quorum,
    changeQuorum: row.changeQuorum,
    applicantIncluded: row.applicantIncluded,
    enabled: row.enabled,
  })
  policyVisible.value = true
}

async function submitPolicy() {
  policySaving.value = true
  try {
    await updatePolicy(policyId.value, { ...policyForm })
    ElMessage.success('审批策略修改已提交审批，通过后生效')
    policyVisible.value = false
    await loadPolicy()
  } finally {
    policySaving.value = false
  }
}

onMounted(() => {
  loadConfig()
  loadPolicy()
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
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.tip {
  margin-bottom: 12px;
}
</style>
