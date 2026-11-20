<template>
  <div class="page">
    <el-card shadow="never">
      <div class="header">
        <div>
          <div class="title">AI 厂商配置</div>
          <div class="hint">
            支持 OpenAI 兼容接口（OpenAI、DeepSeek、通义、私有网关等）。Key 加密存储，页面只显示掩码。
          </div>
        </div>
        <el-button
          v-if="auth.hasPermission('ai:config:update')"
          type="primary"
          @click="openCreate"
        >
          新增厂商
        </el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="花名册解析只把表头发送给模型补全列映射，不发送员工数据；未配置厂商时自动使用本地列名词典。"
        class="tip"
      />

      <el-table :data="providers" stripe>
        <el-table-column prop="name" label="名称" width="160" />
        <el-table-column prop="baseUrl" label="Base URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="model" label="模型" width="160" />
        <el-table-column label="Key" width="160">
          <template #default="{ row }">
            <span v-if="row.hasApiKey">{{ row.maskedApiKey }}</span>
            <el-tag v-else type="danger" size="small">未配置</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="默认" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isDefault" type="warning" size="small">默认</el-tag>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="auth.hasPermission('ai:config:update')"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="auth.hasPermission('ai:config:update')"
              link
              :loading="testingId === row.id"
              @click="test(row)"
            >
              测试连接
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="providers.length === 0" description="尚未配置 AI 厂商" />
    </el-card>

    <el-card shadow="never">
      <div class="header">
        <div>
          <div class="title">操作指引入口</div>
          <div class="hint">
            AI 助手在这些入口中按标题和关键词推荐位置；用户没有对应权限的入口不会展示。
          </div>
        </div>
        <el-button
          v-if="auth.hasPermission('ai:config:update')"
          type="primary"
          @click="openHelpCreate"
        >
          新增入口
        </el-button>
      </div>

      <el-table :data="helpIndex" stripe>
        <el-table-column prop="title" label="名称" width="140" />
        <el-table-column prop="keywords" label="关键词" min-width="200" show-overflow-tooltip />
        <el-table-column prop="route" label="路径" width="180" show-overflow-tooltip />
        <el-table-column prop="requiredPermission" label="所需权限" width="150">
          <template #default="{ row }">
            <span v-if="row.requiredPermission" class="mono">{{ row.requiredPermission }}</span>
            <span v-else class="muted">登录即可</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="auth.hasPermission('ai:config:update')">
              <el-button link type="primary" @click="openHelpEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="removeHelp(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="auth.hasPermission('ai:log:read')" shadow="never">
      <div class="header">
        <div>
          <div class="title">AI 调用日志</div>
          <div class="hint">记录功能、模型、耗时与成功状态，问题只保存摘要。</div>
        </div>
        <el-button link @click="loadLogs">刷新</el-button>
      </div>
      <el-table :data="logs" stripe>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="功能" width="130">
          <template #default="{ row }">{{ featureLabel(row.feature) }}</template>
        </el-table-column>
        <el-table-column prop="questionSummary" label="问题摘要" min-width="220" show-overflow-tooltip />
        <el-table-column label="模型" width="150">
          <template #default="{ row }">
            <span v-if="row.model" class="mono">{{ row.model }}</span>
            <span v-else class="muted">本地规则</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="latencyMs" label="耗时(ms)" width="100" />
      </el-table>
      <el-pagination
        v-if="logTotal > 0"
        v-model:current-page="logPage"
        :page-size="10"
        :total="logTotal"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="loadLogs"
      />
    </el-card>

    <el-dialog v-model="helpVisible" :title="helpForm.id ? '编辑入口' : '新增入口'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="helpForm.title" placeholder="例如：报销申请" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="helpForm.keywords" placeholder="逗号分隔，例如：报销,费用,发票" />
        </el-form-item>
        <el-form-item label="站内路径" required>
          <el-input v-model="helpForm.route" placeholder="/projects" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="helpForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="所需权限">
          <el-input v-model="helpForm.requiredPermission" placeholder="留空表示登录即可，例如 employee:read" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="helpForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="helpForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="helpVisible = false">取消</el-button>
        <el-button type="primary" :loading="helpSaving" @click="saveHelp">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="visible" :title="form.id ? '编辑 AI 厂商' : '新增 AI 厂商'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="例如：公司外部网关" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="模型" required>
          <el-input v-model="form.model" placeholder="gpt-4o-mini / deepseek-chat" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="form.id && form.hasApiKey ? '已保存，留空保持不变' : 'sk-...'"
          />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createHelpIndex,
  deleteHelpIndex,
  listAiProviders,
  listHelpIndex,
  pageAiLogs,
  saveAiProvider,
  testAiProvider,
  updateHelpIndex,
  type AiProviderPayload,
} from '@/api/ai'
import type {
  AiHelpIndexPayload,
  AiHelpIndexView,
  AiProviderView,
  AiQueryLogView,
} from '@/types'

const auth = useAuthStore()
const providers = ref<AiProviderView[]>([])
const visible = ref(false)
const saving = ref(false)
const testingId = ref<string | null>(null)

const helpIndex = ref<AiHelpIndexView[]>([])
const helpVisible = ref(false)
const helpSaving = ref(false)
const helpForm = reactive<AiHelpIndexPayload & { id?: string | null }>({
  id: null,
  title: '',
  keywords: '',
  route: '',
  description: '',
  requiredPermission: '',
  sort: 0,
  enabled: true,
})

const logs = ref<AiQueryLogView[]>([])
const logTotal = ref(0)
const logPage = ref(1)

const featureLabels: Record<string, string> = {
  HELP_LOCATE: '操作指引',
  PEOPLE_QA: '人员问答',
  ROSTER_MAPPING: '花名册列映射',
  PROVIDER_TEST: '连接测试',
}

const form = reactive<AiProviderPayload & { hasApiKey?: boolean }>({
  id: null,
  name: '',
  providerType: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  apiKey: '',
  model: '',
  enabled: true,
  isDefault: false,
  hasApiKey: false,
})

onMounted(async () => {
  await load()
  await loadHelpIndex()
  if (auth.hasPermission('ai:log:read')) {
    await loadLogs()
  }
})

async function load() {
  providers.value = await listAiProviders()
}

async function loadHelpIndex() {
  if (!auth.hasPermission('ai:config:read')) return
  helpIndex.value = await listHelpIndex()
}

async function loadLogs() {
  const result = await pageAiLogs({ page: logPage.value, size: 10 })
  logs.value = result.records
  logTotal.value = result.total
}

function featureLabel(feature: string) {
  return featureLabels[feature] ?? feature
}

function openHelpCreate() {
  Object.assign(helpForm, {
    id: null,
    title: '',
    keywords: '',
    route: '',
    description: '',
    requiredPermission: '',
    sort: helpIndex.value.length * 10 + 10,
    enabled: true,
  })
  helpVisible.value = true
}

function openHelpEdit(entry: AiHelpIndexView) {
  Object.assign(helpForm, {
    id: entry.id,
    title: entry.title,
    keywords: entry.keywords,
    route: entry.route,
    description: entry.description || '',
    requiredPermission: entry.requiredPermission || '',
    sort: entry.sort,
    enabled: entry.enabled,
  })
  helpVisible.value = true
}

async function saveHelp() {
  if (!helpForm.title || !helpForm.route) {
    ElMessage.warning('请填写名称和站内路径')
    return
  }
  helpSaving.value = true
  try {
    const payload: AiHelpIndexPayload = {
      title: helpForm.title,
      keywords: helpForm.keywords,
      route: helpForm.route,
      description: helpForm.description || null,
      requiredPermission: helpForm.requiredPermission || null,
      sort: helpForm.sort,
      enabled: helpForm.enabled,
    }
    if (helpForm.id) {
      await updateHelpIndex(helpForm.id, payload)
    } else {
      await createHelpIndex(payload)
    }
    ElMessage.success('已保存')
    helpVisible.value = false
    await loadHelpIndex()
  } finally {
    helpSaving.value = false
  }
}

async function removeHelp(entry: AiHelpIndexView) {
  await ElMessageBox.confirm(`确定删除入口“${entry.title}”吗？`, '提示', { type: 'warning' })
  await deleteHelpIndex(entry.id)
  ElMessage.success('已删除')
  await loadHelpIndex()
}

function openCreate() {
  Object.assign(form, {
    id: null,
    name: '',
    providerType: 'OPENAI_COMPATIBLE',
    baseUrl: '',
    apiKey: '',
    model: '',
    enabled: true,
    isDefault: providers.value.length === 0,
    hasApiKey: false,
  })
  visible.value = true
}

function openEdit(provider: AiProviderView) {
  Object.assign(form, {
    id: provider.id,
    name: provider.name,
    providerType: provider.providerType,
    baseUrl: provider.baseUrl,
    apiKey: '',
    model: provider.model,
    enabled: provider.enabled,
    isDefault: provider.isDefault,
    hasApiKey: provider.hasApiKey,
  })
  visible.value = true
}

async function save() {
  if (!form.name || !form.baseUrl || !form.model) {
    ElMessage.warning('请填写名称、Base URL 和模型')
    return
  }
  saving.value = true
  try {
    await saveAiProvider({
      id: form.id,
      name: form.name,
      providerType: form.providerType,
      baseUrl: form.baseUrl,
      model: form.model,
      apiKey: form.apiKey || undefined,
      enabled: form.enabled,
      isDefault: form.isDefault,
    })
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function test(provider: AiProviderView) {
  testingId.value = provider.id
  try {
    const result = await testAiProvider(provider.id)
    if (result.success) {
      ElMessage.success(`连接成功（${result.latencyMs} ms）：${result.message}`)
    } else {
      ElMessage.error(result.message)
    }
  } finally {
    testingId.value = null
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
.tip {
  margin-bottom: 12px;
}
.muted {
  color: #c0c4cc;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
