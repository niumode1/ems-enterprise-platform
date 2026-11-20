<template>
  <div v-if="auth.hasPermission('ai:use')">
    <button
      v-show="!open"
      ref="ballRef"
      class="ai-ball"
      :style="{ left: pos.left + 'px', top: pos.top + 'px' }"
      title="AI 助手（可拖动）"
      @pointerdown="startDrag"
      @click="onBallClick"
    >
      <span class="ai-ball-text">AI</span>
    </button>

    <el-drawer
      v-model="open"
      direction="rtl"
      size="430px"
      :with-header="false"
      append-to-body
      class="ai-drawer"
      @opened="onOpened"
    >
      <div class="ai-panel">
        <div class="ai-header">
          <div>
            <div class="ai-title">AI 助手</div>
            <div class="ai-hint">按权限与字段可见性回答，敏感信息自动脱敏</div>
          </div>
          <div class="ai-header-actions">
            <el-tooltip v-if="auth.hasPermission('ai:config:read')" content="模型与厂商设置" placement="bottom">
              <button class="icon-button" @click="openSettings">⚙</button>
            </el-tooltip>
            <el-tooltip v-if="auth.hasPermission('ai:config:read')" content="打开完整 AI 设置页" placement="bottom">
              <button class="icon-button" @click="go('/ai-settings')">⋯</button>
            </el-tooltip>
            <button class="icon-button" @click="open = false">✕</button>
          </div>
        </div>

        <div ref="chatRef" class="ai-chat">
          <div v-if="messages.length === 0" class="ai-welcome">
            <p>你好，我可以帮你找功能入口、查询有权限查看的人员信息。</p>
            <div v-if="quickQuestions.length" class="ai-quick">
              <span class="quick-label">试试这样问：</span>
              <el-tag
                v-for="item in quickQuestions"
                :key="item.id"
                class="quick-item"
                effect="plain"
                @click="useQuick(item.question)"
              >
                {{ item.question }}
              </el-tag>
            </div>
          </div>

          <div v-for="(message, index) in messages" :key="index" class="message-row" :class="message.role">
            <div v-if="message.role === 'assistant'" class="avatar">AI</div>
            <div class="bubble">
              <div class="bubble-text">{{ message.text }}</div>
              <el-alert
                v-if="message.maskedFields?.length"
                class="bubble-alert"
                type="warning"
                :closable="false"
                :title="`以下字段已脱敏：${message.maskedFields.join('、')}`"
              />
              <div v-if="message.locations?.length" class="locations">
                <div
                  v-for="hit in message.locations"
                  :key="hit.route"
                  class="location"
                  @click="go(hit.route)"
                >
                  <div class="loc-title">{{ hit.title }} <span class="loc-route">{{ hit.route }}</span></div>
                  <div class="loc-desc">{{ hit.description || '点击进入' }}</div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="loading" class="message-row assistant">
            <div class="avatar">AI</div>
            <div class="bubble"><span class="typing">正在思考…</span></div>
          </div>
        </div>

        <div class="ai-composer">
          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            resize="none"
            placeholder="例如：怎么导入花名册？李四的电话是多少？"
            @keydown.enter.exact.prevent="submit"
          />
          <el-button type="primary" :loading="loading" :disabled="!question.trim()" @click="submit">
            发送
          </el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog
      v-model="settingsOpen"
      title="AI 模型与厂商"
      width="700px"
      append-to-body
      destroy-on-close
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持 OpenAI 兼容接口。保存默认厂商后，AI 助手与花名册解析会使用该配置。"
        class="settings-tip"
      />
      <div class="provider-list">
        <div
          v-for="provider in providers"
          :key="provider.id"
          class="provider-card"
          :class="{ selected: form.id === provider.id }"
          @click="selectProvider(provider)"
        >
          <div class="provider-name">
            {{ provider.name }}
            <el-tag v-if="provider.isDefault" size="small" type="warning">默认</el-tag>
            <el-tag v-if="!provider.enabled" size="small" type="info">停用</el-tag>
          </div>
          <div class="provider-meta">{{ provider.model }} · {{ provider.baseUrl }}</div>
        </div>
        <el-empty v-if="providers.length === 0" description="尚未配置厂商" :image-size="60" />
      </div>

      <el-form label-width="100px" class="provider-form">
        <el-form-item label="厂商名称" required>
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
        <div class="settings-footer">
          <el-button v-if="auth.hasPermission('ai:config:update')" @click="newProvider">新增厂商</el-button>
          <div class="spacer" />
          <el-button @click="settingsOpen = false">取消</el-button>
          <el-button
            v-if="auth.hasPermission('ai:config:update')"
            type="primary"
            :loading="saving"
            @click="saveProvider"
          >保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  askAi,
  listAiProviders,
  listQuickQuestions,
  saveAiProvider,
  type AiProviderPayload,
} from '@/api/ai'
import type { AiHelpHit, AiProviderView, AiQuickQuestion } from '@/types'

interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  maskedFields?: string[]
  locations?: AiHelpHit[]
}

const auth = useAuthStore()
const router = useRouter()
const open = ref(false)
const loading = ref(false)
const question = ref('')
const messages = ref<ChatMessage[]>([])
const quickQuestions = ref<AiQuickQuestion[]>([])
const chatRef = ref<HTMLElement | null>(null)
const ballRef = ref<HTMLElement | null>(null)

const pos = reactive({ left: 0, top: 0 })
const dragging = ref(false)
const moved = ref(false)
let userPositioned = false
let dragOffsetX = 0
let dragOffsetY = 0

const fieldLabels: Record<string, string> = {
  phone: '电话',
  email: '邮箱',
  address: '地址',
  idCard: '身份证',
  emergencyContact: '紧急联系人',
  birthDate: '出生日期',
  gender: '性别',
  hireDate: '入职日期',
  name: '姓名',
}

const settingsOpen = ref(false)
const providers = ref<AiProviderView[]>([])
const saving = ref(false)
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

function initPosition() {
  const size = 58
  if (userPositioned) {
    pos.left = Math.min(Math.max(8, pos.left), window.innerWidth - size - 8)
    pos.top = Math.min(Math.max(8, pos.top), window.innerHeight - size - 8)
    return
  }
  pos.left = Math.max(12, window.innerWidth - size - 26)
  pos.top = Math.max(12, window.innerHeight - size - 26)
}

function startDrag(event: PointerEvent) {
  if (event.button !== 0) return
  dragging.value = true
  moved.value = false
  userPositioned = true
  dragOffsetX = event.clientX - pos.left
  dragOffsetY = event.clientY - pos.top
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', endDrag)
}

function onDrag(event: PointerEvent) {
  if (!dragging.value) return
  const size = 58
  const left = Math.min(Math.max(8, event.clientX - dragOffsetX), window.innerWidth - size - 8)
  const top = Math.min(Math.max(8, event.clientY - dragOffsetY), window.innerHeight - size - 8)
  if (Math.abs(left - pos.left) > 2 || Math.abs(top - pos.top) > 2) {
    moved.value = true
  }
  pos.left = left
  pos.top = top
}

function endDrag() {
  dragging.value = false
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', endDrag)
}

function onBallClick() {
  if (moved.value) {
    moved.value = false
    return
  }
  open.value = true
}

function onOpened() {
  scrollToBottom()
}

function scrollToBottom() {
  nextTick(() => {
    if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  })
}

async function loadQuickQuestions() {
  if (quickQuestions.value.length) return
  try {
    quickQuestions.value = await listQuickQuestions()
  } catch {
    quickQuestions.value = []
  }
}

function useQuick(text: string) {
  question.value = text
  submit()
}

async function submit() {
  const text = question.value.trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', text })
  question.value = ''
  loading.value = true
  scrollToBottom()
  try {
    const result = await askAi({ question: text })
    messages.value.push({
      role: 'assistant',
      text: result.answer,
      maskedFields: result.maskedFields.map((field) => fieldLabels[field] ?? field),
      locations: result.locations,
    })
  } catch {
    messages.value.push({ role: 'assistant', text: '暂时无法回答，请稍后再试。' })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function go(path: string) {
  open.value = false
  router.push(path)
}

async function openSettings() {
  settingsOpen.value = true
  await loadProviders()
  if (providers.value.length) {
    selectProvider(providers.value.find((item) => item.isDefault) || providers.value[0])
  } else {
    newProvider()
  }
}

async function loadProviders() {
  try {
    providers.value = await listAiProviders()
  } catch {
    providers.value = []
  }
}

function selectProvider(provider: AiProviderView) {
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
}

function newProvider() {
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
}

async function saveProvider() {
  if (!form.name || !form.baseUrl || !form.model) {
    ElMessage.warning('请填写厂商名称、Base URL 和模型')
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
    settingsOpen.value = false
    await loadProviders()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  initPosition()
  window.addEventListener('resize', initPosition)
  loadQuickQuestions()
})

onUnmounted(() => {
  window.removeEventListener('resize', initPosition)
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', endDrag)
})
</script>

<style scoped>
.ai-ball {
  position: fixed;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  border: none;
  cursor: grab;
  color: #fff;
  font-weight: 700;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #409eff, #6a5acd);
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.45);
  z-index: 2500;
  touch-action: none;
  user-select: none;
}
.ai-ball:active {
  cursor: grabbing;
}
.ai-ball-text {
  font-size: 17px;
}
.ai-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #ebeef5;
}
.ai-title {
  font-size: 16px;
  font-weight: 600;
}
.ai-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.ai-header-actions {
  display: flex;
  gap: 6px;
}
.icon-button {
  border: none;
  background: #f5f7fa;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  cursor: pointer;
  color: #606266;
  font-size: 15px;
}
.icon-button:hover {
  background: #e4e7ed;
}
.ai-chat {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f7f9fc;
}
.ai-welcome {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
}
.ai-quick {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.quick-label {
  color: #909399;
  font-size: 12px;
}
.quick-item {
  cursor: pointer;
}
.message-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.message-row.user {
  justify-content: flex-end;
}
.avatar {
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #6a5acd);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}
.bubble {
  max-width: 320px;
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.message-row.user .bubble {
  background: #409eff;
  color: #fff;
}
.bubble-text {
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 13px;
}
.bubble-alert {
  margin-top: 8px;
}
.locations {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.location {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px;
  cursor: pointer;
}
.location:hover {
  border-color: #409eff;
}
.loc-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.loc-route {
  color: #909399;
  font-weight: 400;
  font-size: 11px;
  margin-left: 4px;
}
.loc-desc {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.typing {
  color: #909399;
  font-size: 13px;
}
.ai-composer {
  border-top: 1px solid #ebeef5;
  padding: 12px 14px;
  display: flex;
  gap: 10px;
  align-items: flex-end;
  background: #fff;
}
.ai-composer .el-textarea {
  flex: 1;
}
.settings-tip {
  margin-bottom: 12px;
}
.provider-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 180px;
  overflow-y: auto;
  margin-bottom: 14px;
}
.provider-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
}
.provider-card.selected {
  border-color: #409eff;
  background: #ecf5ff;
}
.provider-name {
  font-weight: 600;
  font-size: 13px;
  display: flex;
  gap: 6px;
  align-items: center;
}
.provider-meta {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.provider-form {
  margin-top: 4px;
}
.settings-footer {
  display: flex;
  align-items: center;
}
.spacer {
  flex: 1;
}
</style>

<style>
.ai-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
}
</style>
