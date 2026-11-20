<template>
  <div class="chat-page">
    <section class="chat-sidebar">
      <div class="sidebar-head">
        <div>
          <div class="sidebar-title">聊天</div>
          <div class="sidebar-sub">与同事一对一沟通或创建群聊</div>
        </div>
        <el-button type="primary" size="small" @click="openCreate">发起会话</el-button>
      </div>
      <div class="sidebar-search">
        <el-input v-model="keyword" size="small" clearable placeholder="搜索会话" />
      </div>
      <div v-loading="listLoading" class="conversation-list">
        <button
          v-for="item in filteredConversations"
          :key="item.id"
          class="conversation-item"
          :class="{ active: item.id === selectedId }"
          @click="selectConversation(item.id)"
        >
          <span class="avatar" :class="item.type === 'GROUP' ? 'group' : ''">
            {{ avatarText(item) }}
          </span>
          <span class="conversation-body">
            <span class="conversation-line">
              <span class="conversation-name">{{ item.name }}</span>
              <span class="conversation-time">{{ formatListTime(item.lastMessageAt) }}</span>
            </span>
            <span class="conversation-line">
              <span class="conversation-preview">{{ item.lastMessagePreview || '暂无消息' }}</span>
              <el-badge v-if="item.unreadCount > 0" :value="item.unreadCount" :max="99" class="unread" />
            </span>
          </span>
        </button>
        <el-empty
          v-if="!listLoading && filteredConversations.length === 0"
          :description="keyword ? '没有匹配的会话' : '还没有会话，点击右上角发起'"
          :image-size="64"
        />
      </div>
    </section>

    <section class="chat-main">
      <template v-if="activeConversation">
        <header class="chat-head">
          <div class="chat-head-main">
            <div class="chat-title">
              {{ activeConversation.name }}
              <el-tag size="small" effect="plain">
                {{ activeConversation.type === 'GROUP' ? '群聊' : '单聊' }}
              </el-tag>
              <el-tag v-if="activeConversation.burnAfterRead" size="small" type="warning" effect="plain">
                阅后即焚
              </el-tag>
              <el-tag v-if="activeConversation.antiRecall" size="small" type="info" effect="plain">
                防撤回
              </el-tag>
              <el-tag v-if="activeConversation.retentionDays" size="small" effect="plain">
                保留 {{ activeConversation.retentionDays }} 天
              </el-tag>
            </div>
            <div class="chat-sub">
              <span v-if="activeConversation.type === 'GROUP'">
                群主 {{ activeConversation.ownerName || '—' }} · {{ activeConversation.memberCount }} 人
              </span>
              <span v-else>一对一私聊</span>
              <span v-if="activeConversation.burnAfterRead" class="burn-tip">
                消息被所有接收人读取后清空正文
              </span>
            </div>
          </div>
          <div class="chat-head-actions">
            <el-button v-if="activeConversation.type === 'GROUP'" size="small" @click="openMembers">
              成员
            </el-button>
            <el-button size="small" :disabled="!activeConversation.canManageSettings" @click="openSettings">
              会话设置
            </el-button>
          </div>
        </header>

        <div ref="messageListRef" v-loading="messageLoading" class="message-list" @scroll="onMessageScroll">
          <div v-if="hasMore" class="load-more">
            <el-button link type="primary" :loading="loadingEarlier" @click="loadEarlier">加载更早消息</el-button>
          </div>
          <template v-for="(message, index) in messages" :key="message.id">
            <div v-if="showDateDivider(index)" class="date-divider">
              {{ formatDateDivider(message.createdAt) }}
            </div>
            <div class="message-row" :class="{ mine: message.mine }">
              <span v-if="!message.mine" class="avatar small">{{ (message.senderName || '?').slice(0, 1) }}</span>
              <div class="message-content">
                <div v-if="!message.mine && activeConversation.type === 'GROUP'" class="sender-name">
                  {{ message.senderName || '未知成员' }}
                </div>
                <div
                  class="bubble"
                  :class="{ recalled: message.recalled, burned: message.burnedForMe }"
                >
                  <template v-if="message.attachment">
                    <div
                      v-if="message.attachment.kind === 'IMAGE'"
                      class="image-attachment"
                      @click="openImage(message.attachment)"
                    >
                      <img
                        v-if="imageUrls[message.attachment.id]"
                        :src="imageUrls[message.attachment.id]"
                        :alt="message.attachment.originalName"
                      />
                      <span v-else class="image-loading">点击加载图片</span>
                    </div>
                    <button
                      v-else
                      class="file-attachment"
                      type="button"
                      @click="downloadAttachment(message.attachment)"
                    >
                      <span class="file-icon">📄</span>
                      <span class="file-info">
                        <span class="file-name">{{ message.attachment.originalName }}</span>
                        <span class="file-size">{{ formatFileSize(message.attachment.sizeBytes) }}</span>
                      </span>
                    </button>
                    <div v-if="message.content" class="attachment-caption">{{ message.content }}</div>
                  </template>
                  <template v-else>{{ message.content }}</template>
                </div>
                <div class="message-meta">
                  <span>{{ formatMessageTime(message.createdAt) }}</span>
                  <template v-if="message.mine">
                    <span v-if="message.recalled">已撤回</span>
                    <span v-else-if="message.burnedForMe">已焚毁</span>
                    <span v-else-if="activeConversation.type === 'DIRECT'">
                      {{ message.readCount > 0 ? '已读' : '未读' }}
                    </span>
                    <span v-else>已读 {{ message.readCount }} 人</span>
                    <el-button
                      v-if="!message.recalled && !message.burnedForMe && !activeConversation.antiRecall"
                      link
                      size="small"
                      @click="recall(message)"
                    >撤回</el-button>
                  </template>
                </div>
              </div>
              <span v-if="message.mine" class="avatar small me">{{ (message.senderName || '我').slice(0, 1) }}</span>
            </div>
          </template>
          <el-empty
            v-if="!messageLoading && messages.length === 0"
            description="还没有消息，发一条打个招呼吧"
            :image-size="72"
          />
        </div>

        <footer class="composer">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="3"
            resize="none"
            placeholder="输入消息，Enter 发送，Shift + Enter 换行"
            @keydown.enter.exact.prevent="onEnter"
          />
          <div class="composer-bar">
            <div class="composer-tools">
              <template v-if="capabilities?.enabled">
                <el-tooltip
                  :content="activeConversation.attachmentUploadAllowed
                    ? `支持图片/文件，单个最大 ${activeConversation.attachmentMaxSizeMb}MB`
                    : '当前会话策略不允许发送附件'"
                  placement="top"
                >
                  <span>
                    <el-button
                      size="small"
                      :disabled="!activeConversation.attachmentUploadAllowed || uploading || sending"
                      :loading="uploading"
                      @click="pickFile"
                    >📎 附件</el-button>
                  </span>
                </el-tooltip>
                <input
                  ref="fileInputRef"
                  class="hidden-file"
                  type="file"
                  :accept="acceptExtensions"
                  @change="onFileSelected"
                />
              </template>
              <span class="composer-hint">
                请遵守公司聊天规范，敏感信息谨慎发送
                <template v-if="activeConversation.attachmentUploadAllowed">
                  · 图片最大 {{ activeConversation.attachmentImageMaxSizeMb }}MB
                </template>
              </span>
            </div>
            <el-button
              type="primary"
              :loading="sending"
              :disabled="!draft.trim() || uploading"
              @click="send"
            >
              发送
            </el-button>
          </div>
        </footer>
      </template>

      <div v-else class="chat-empty">
        <div class="empty-orb">💬</div>
        <div class="empty-title">选择一个会话开始聊天</div>
        <div class="empty-desc">可以从左侧选择已有会话，或发起新的单聊、群聊。</div>
        <el-button type="primary" @click="openCreate">发起会话</el-button>
      </div>
    </section>

    <el-dialog v-model="createOpen" title="发起会话" width="560px" destroy-on-close>
      <el-form label-width="92px">
        <el-form-item label="会话类型">
          <el-radio-group v-model="createForm.type" @change="onCreateTypeChange">
            <el-radio-button value="DIRECT">单聊</el-radio-button>
            <el-radio-button value="GROUP">群聊</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createForm.type === 'GROUP'" label="群名称" required>
          <el-input v-model="createForm.name" maxlength="60" show-word-limit placeholder="例如：产品迭代讨论组" />
        </el-form-item>
        <el-form-item :label="createForm.type === 'GROUP' ? '邀请成员' : '选择同事'" required>
          <el-select
            v-model="createForm.employeeIds"
            multiple
            filterable
            remote
            reserve-keyword
            :remote-method="searchEmployees"
            :loading="employeeLoading"
            placeholder="输入姓名或工号搜索"
            style="width: 100%"
          >
            <el-option
              v-for="employee in employeeOptions"
              :key="employee.id"
              :label="employeeOptionLabel(employee)"
              :value="employee.id"
            />
          </el-select>
          <div class="form-hint">
            {{ createForm.type === 'GROUP' ? '至少选择一位同事，创建人为群主。' : '只能选择一位同事，重复发起会进入已有会话。' }}
          </div>
        </el-form-item>
        <el-form-item v-if="createForm.type === 'GROUP' && createForm.employeeIds.length > 0" label="已选">
          <div class="selected-members">
            <el-tag
              v-for="employee in selectedEmployees"
              :key="employee.id"
              closable
              size="small"
              @close="removeCreateEmployee(employee.id)"
            >{{ employeeOptionLabel(employee) }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="阅后即焚">
          <el-select v-model="createForm.burnMode" style="width: 100%">
            <el-option label="使用公司默认策略" value="DEFAULT" />
            <el-option label="开启" value="ON" />
            <el-option label="关闭" value="OFF" />
          </el-select>
        </el-form-item>
        <el-form-item label="防撤回">
          <el-select v-model="createForm.antiRecallMode" style="width: 100%">
            <el-option label="使用公司默认策略" value="DEFAULT" />
            <el-option label="开启" value="ON" />
            <el-option label="关闭" value="OFF" />
          </el-select>
        </el-form-item>
        <el-form-item label="保留期">
          <el-input-number
            v-model="createForm.retentionDays"
            :min="1"
            :max="36500"
            :controls="false"
            placeholder="留空使用公司默认"
            style="width: 180px"
          />
          <span class="form-hint inline">天</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createOpen = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建并进入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="settingsOpen" title="会话设置" width="520px">
      <el-form label-width="92px">
        <el-form-item v-if="activeConversation?.type === 'GROUP'" label="群名称">
          <el-input v-model="settingsForm.name" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="阅后即焚">
          <el-switch v-model="settingsForm.burnAfterRead" />
          <span class="form-hint inline">开启后，消息被所有接收人读取后清空正文</span>
        </el-form-item>
        <el-form-item label="防撤回">
          <el-switch v-model="settingsForm.antiRecall" />
          <span class="form-hint inline">开启后成员不能撤回已发送消息</span>
        </el-form-item>
        <el-form-item label="保留期">
          <el-input-number
            v-model="settingsForm.retentionDays"
            :min="1"
            :max="36500"
            :controls="false"
            placeholder="留空使用全局策略"
            style="width: 180px"
          />
          <span class="form-hint inline">天</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsOpen = false">取消</el-button>
        <el-button type="primary" :loading="savingSettings" @click="saveSettings">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="membersOpen" :title="`会话成员 · ${activeConversation?.memberCount || 0} 人`" width="560px">
      <div v-if="activeConversation?.type === 'GROUP'" class="member-add">
        <el-select
          v-model="newMemberId"
          filterable
          remote
          reserve-keyword
          :remote-method="searchEmployees"
          :loading="employeeLoading"
          placeholder="输入姓名或工号添加成员"
          style="flex: 1"
        >
          <el-option
            v-for="employee in addableEmployees"
            :key="employee.id"
            :label="employeeOptionLabel(employee)"
            :value="employee.id"
          />
        </el-select>
        <el-button
          type="primary"
          :disabled="!newMemberId || !activeConversation?.canManageMembers"
          :loading="addingMember"
          @click="addMember"
        >添加</el-button>
      </div>
      <div class="member-list">
        <div v-for="member in members" :key="member.employeeId" class="member-row">
          <span class="avatar small">{{ (member.employeeName || '?').slice(0, 1) }}</span>
          <div class="member-main">
            <div class="member-name">
              {{ member.employeeName || '未知成员' }}
              <el-tag v-if="member.role === 'OWNER'" size="small" type="warning">群主</el-tag>
              <el-tag v-else size="small" effect="plain">成员</el-tag>
              <el-tag v-if="member.mutedUntil" size="small" type="danger">禁言中</el-tag>
            </div>
            <div class="member-meta">
              加入于 {{ formatDateTime(member.joinedAt) }}
              <span v-if="member.lastReadAt"> · 最近已读 {{ formatDateTime(member.lastReadAt) }}</span>
            </div>
          </div>
          <el-button
            v-if="canRemoveMember(member)"
            link
            type="danger"
            size="small"
            @click="removeMember(member)"
          >{{ member.employeeId === auth.user?.employeeId ? '退出' : '移出' }}</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="membersOpen = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="imagePreviewOpen" title="图片预览" width="760px" destroy-on-close>
      <div class="image-preview-wrap">
        <img v-if="imagePreviewUrl" :src="imagePreviewUrl" class="image-preview" alt="聊天图片" />
        <el-empty v-else description="图片加载失败或已销毁" />
      </div>
      <template #footer>
        <el-button @click="imagePreviewOpen = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { pageEmployees } from '@/api/employees'
import {
  addChatMember,
  createChatConversation,
  downloadChatAttachment,
  getChatCapabilities,
  getChatConversation,
  listChatConversations,
  markChatRead,
  pageChatMessages,
  recallChatMessage,
  removeChatMember,
  sendChatMessage,
  updateChatConversation,
  uploadChatAttachment,
} from '@/api/chat'
import type { RealtimeEvent } from '@/realtime'
import type {
  ChatAttachmentCapabilities,
  ChatAttachmentView,
  ChatConversationView,
  ChatMemberView,
  ChatMessageView,
  EmployeeView,
} from '@/types'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const conversations = ref<ChatConversationView[]>([])
const listLoading = ref(false)
const keyword = ref('')
const selectedId = ref<string | null>(null)
const activeConversation = ref<ChatConversationView | null>(null)
const members = ref<ChatMemberView[]>([])
const messages = ref<ChatMessageView[]>([])
const messageLoading = ref(false)
const loadingEarlier = ref(false)
const hasMore = ref(false)
const messagePage = ref(1)
const messageListRef = ref<HTMLElement | null>(null)
const draft = ref('')
const sending = ref(false)
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const capabilities = ref<ChatAttachmentCapabilities | null>(null)
const imageUrls = ref<Record<string, string>>({})
const imagePreviewOpen = ref(false)
const imagePreviewUrl = ref('')
let pollTimer: number | undefined
let realtimeTimer: number | undefined
let pollBusy = false

const acceptExtensions = computed(() => {
  const list = capabilities.value?.allowedExtensions || []
  if (list.length === 0 || list.includes('*')) return undefined
  return list.map((extension) => `.${extension}`).join(',')
})

const filteredConversations = computed(() => {
  const text = keyword.value.trim().toLowerCase()
  if (!text) return conversations.value
  return conversations.value.filter(
    (item) =>
      item.name.toLowerCase().includes(text) ||
      (item.lastMessagePreview || '').toLowerCase().includes(text),
  )
})

const createOpen = ref(false)
const creating = ref(false)
const createForm = reactive({
  type: 'DIRECT' as 'DIRECT' | 'GROUP',
  name: '',
  employeeIds: [] as string[],
  burnMode: 'DEFAULT' as 'DEFAULT' | 'ON' | 'OFF',
  antiRecallMode: 'DEFAULT' as 'DEFAULT' | 'ON' | 'OFF',
  retentionDays: undefined as number | undefined,
})
const employeeOptions = ref<EmployeeView[]>([])
const employeeLoading = ref(false)
const selectedEmployeeCache = ref<Record<string, EmployeeView>>({})

const settingsOpen = ref(false)
const savingSettings = ref(false)
const settingsForm = reactive({
  name: '',
  burnAfterRead: false,
  antiRecall: true,
  retentionDays: undefined as number | undefined,
})

const membersOpen = ref(false)
const newMemberId = ref('')
const addingMember = ref(false)

const selectedEmployees = computed(() =>
  createForm.employeeIds
    .map((id) => selectedEmployeeCache.value[id] || employeeOptions.value.find((item) => item.id === id))
    .filter((item): item is EmployeeView => Boolean(item)),
)

const addableEmployees = computed(() => {
  const existing = new Set(members.value.map((member) => member.employeeId))
  return employeeOptions.value.filter(
    (employee) => !existing.has(employee.id) && employee.id !== auth.user?.employeeId,
  )
})

onMounted(async () => {
  try {
    capabilities.value = await getChatCapabilities()
  } catch {
    capabilities.value = null
  }
  await loadConversations()
  const routeId = route.params.id
  if (typeof routeId === 'string' && routeId) {
    await selectConversation(routeId, false)
  } else if (conversations.value.length > 0) {
    await selectConversation(conversations.value[0].id, false)
  }
  pollTimer = window.setInterval(poll, 20_000)
  window.addEventListener('ems-realtime', onRealtime)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
  if (realtimeTimer) window.clearTimeout(realtimeTimer)
  window.removeEventListener('ems-realtime', onRealtime)
  revokeImages()
})

watch(
  () => route.params.id,
  async (id) => {
    if (typeof id === 'string' && id && id !== selectedId.value) {
      await selectConversation(id, false)
    }
  },
)

async function loadConversations() {
  listLoading.value = true
  try {
    conversations.value = await listChatConversations()
  } finally {
    listLoading.value = false
  }
}

async function refreshConversationsSilently() {
  try {
    conversations.value = await listChatConversations()
    if (selectedId.value) {
      const fresh = conversations.value.find((item) => item.id === selectedId.value)
      if (fresh) activeConversation.value = fresh
    }
  } catch {
    // 轮询失败不打断当前会话
  }
}

async function selectConversation(id: string, updateRoute = true) {
  if (selectedId.value === id && activeConversation.value) {
    return
  }
  selectedId.value = id
  members.value = []
  messages.value = []
  revokeImages()
  imagePreviewOpen.value = false
  hasMore.value = false
  messagePage.value = 1
  draft.value = ''
  messageLoading.value = true
  try {
    const detail = await getChatConversation(id)
    activeConversation.value = detail.conversation
    members.value = detail.members
    if (updateRoute) {
      router.replace(`/chat/${id}`)
    }
    await loadInitialMessages(id)
    await markRead(id)
    scrollToBottom()
  } catch {
    selectedId.value = null
    activeConversation.value = null
    messages.value = []
    if (route.params.id) router.replace('/chat')
  } finally {
    messageLoading.value = false
  }
}

async function loadInitialMessages(id: string) {
  const result = await pageChatMessages(id, 1, 30)
  messages.value = result.records.slice().reverse()
  messagePage.value = 1
  hasMore.value = result.total > result.records.length
  ensureImages()
}

async function loadEarlier() {
  if (!selectedId.value || loadingEarlier.value || !hasMore.value) return
  loadingEarlier.value = true
  const el = messageListRef.value
  const beforeHeight = el?.scrollHeight || 0
  try {
    const next = messagePage.value + 1
    const result = await pageChatMessages(selectedId.value, next, 30)
    const existing = new Set(messages.value.map((message) => message.id))
    const earlier = result.records
      .slice()
      .reverse()
      .filter((message) => !existing.has(message.id))
    messages.value = [...earlier, ...messages.value]
    messagePage.value = next
    hasMore.value = result.total > messages.value.length
    ensureImages()
    await nextTick()
    if (el) el.scrollTop = el.scrollHeight - beforeHeight
  } finally {
    loadingEarlier.value = false
  }
}

async function markRead(id: string) {
  const before = activeConversation.value?.id === id
    ? activeConversation.value.unreadCount
    : conversations.value.find((conversation) => conversation.id === id)?.unreadCount || 0
  try {
    await markChatRead(id)
    if (activeConversation.value?.id === id) {
      activeConversation.value.unreadCount = 0
    }
    const item = conversations.value.find((conversation) => conversation.id === id)
    if (item) item.unreadCount = 0
    if (before > 0) window.dispatchEvent(new CustomEvent('ems-chat-read'))
  } catch {
    // 已读失败不阻断消息查看
  }
}

function mergeMessages(records: ChatMessageView[]) {
  const incomingAsc = records.slice().reverse()
  const existing = new Map(messages.value.map((message) => [message.id, message]))
  let freshIncoming = false
  for (const message of incomingAsc) {
    if (existing.has(message.id)) {
      const index = messages.value.findIndex((item) => item.id === message.id)
      if (index >= 0) {
        messages.value[index] = message
      }
    } else {
      messages.value.push(message)
      if (!message.mine && !message.recalled && !message.burnedForMe) {
        freshIncoming = true
      }
    }
  }
  ensureImages()
  return freshIncoming
}

function onRealtime(event: Event) {
  const detail = (event as CustomEvent<RealtimeEvent>).detail
  if (!detail) return
  if (detail.type === 'REALTIME_CONNECTED') {
    void refreshConversationsSilently()
    if (selectedId.value) scheduleRealtimePoll()
    return
  }
  const conversationId = detail.data?.conversationId
  if (detail.type === 'CHAT_MESSAGE' || detail.type === 'CHAT_RECALL') {
    if (conversationId != null && String(conversationId) === selectedId.value) {
      scheduleRealtimePoll()
    } else {
      void refreshConversationsSilently()
    }
  }
}

function scheduleRealtimePoll() {
  if (realtimeTimer) window.clearTimeout(realtimeTimer)
  realtimeTimer = window.setTimeout(() => {
    realtimeTimer = undefined
    void poll()
  }, 150)
}

async function poll() {
  if (pollBusy || document.hidden) return
  pollBusy = true
  try {
    await refreshConversationsSilently()
    if (selectedId.value && activeConversation.value) {
      const id = selectedId.value
      const result = await pageChatMessages(id, 1, 30)
      const atBottom = isNearBottom()
      const freshIncoming = mergeMessages(result.records)
      if (freshIncoming) {
        if (atBottom) scrollToBottom()
        if (document.hasFocus()) await markRead(id)
      }
    }
  } catch {
    // 静默轮询
  } finally {
    pollBusy = false
  }
}

function isNearBottom() {
  const el = messageListRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120
}

function scrollToBottom() {
  nextTick(() => {
    const el = messageListRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function onMessageScroll() {
  // 滚动本身不需要处理，保留扩展点（例如触底加载新消息）
}

function onEnter(event: KeyboardEvent) {
  if (event.isComposing || event.keyCode === 229) return
  send()
}

async function send() {
  const content = draft.value.trim()
  if (!content || !selectedId.value || sending.value) return
  sending.value = true
  try {
    const message = await sendChatMessage(selectedId.value, content)
    if (!messages.value.some((item) => item.id === message.id)) {
      messages.value.push(message)
    }
    draft.value = ''
    ensureImages()
    scrollToBottom()
    await refreshConversationsSilently()
  } finally {
    sending.value = false
  }
}

function pickFile() {
  fileInputRef.value?.click()
}

async function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !selectedId.value || uploading.value) return
  const conversation = activeConversation.value
  if (!conversation?.attachmentUploadAllowed) {
    ElMessage.warning('当前会话不允许发送附件')
    return
  }
  uploading.value = true
  try {
    const attachment = await uploadChatAttachment(selectedId.value, file)
    const message = await sendChatMessage(selectedId.value, draft.value.trim(), attachment.id)
    if (!messages.value.some((item) => item.id === message.id)) {
      messages.value.push(message)
    }
    draft.value = ''
    if (attachment.kind === 'IMAGE') {
      await loadImage(attachment.id)
    }
    ensureImages()
    scrollToBottom()
    await refreshConversationsSilently()
  } finally {
    uploading.value = false
  }
}

const imageLoads = new Map<string, Promise<string | null>>()
let imageEpoch = 0

function ensureImages() {
  for (const message of messages.value) {
    const attachment = message.attachment
    if (!attachment || attachment.kind !== 'IMAGE') continue
    if (message.recalled || message.burnedForMe) continue
    if (imageUrls.value[attachment.id]) continue
    void loadImage(attachment.id)
  }
}

function loadImage(attachmentId: string): Promise<string | null> {
  if (imageUrls.value[attachmentId]) {
    return Promise.resolve(imageUrls.value[attachmentId])
  }
  const existing = imageLoads.get(attachmentId)
  if (existing) return existing
  const epoch = imageEpoch
  const pending = downloadChatAttachment(attachmentId)
    .then((blob) => {
      const url = URL.createObjectURL(blob)
      if (epoch !== imageEpoch) {
        URL.revokeObjectURL(url)
        return null
      }
      imageUrls.value = { ...imageUrls.value, [attachmentId]: url }
      return url
    })
    .catch(() => null)
    .finally(() => imageLoads.delete(attachmentId))
  imageLoads.set(attachmentId, pending)
  return pending
}

async function openImage(attachment: ChatAttachmentView) {
  const url = imageUrls.value[attachment.id] || (await loadImage(attachment.id))
  if (!url) {
    ElMessage.error('图片加载失败或已销毁')
    return
  }
  imagePreviewUrl.value = url
  imagePreviewOpen.value = true
}

async function downloadAttachment(attachment: ChatAttachmentView) {
  try {
    const blob = await downloadChatAttachment(attachment.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = attachment.originalName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
  } catch {
    // 下载失败提示由请求层统一处理
  }
}

function revokeImages() {
  imageEpoch += 1
  imageLoads.clear()
  Object.values(imageUrls.value).forEach((url) => URL.revokeObjectURL(url))
  imageUrls.value = {}
  imagePreviewUrl.value = ''
}

function formatFileSize(bytes: number) {
  if (!bytes || bytes < 1024) return `${bytes || 0} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

async function recall(message: ChatMessageView) {
  try {
    await ElMessageBox.confirm('撤回后，所有人将看到“消息已撤回”，确定撤回吗？', '撤回消息', {
      type: 'warning',
    })
  } catch {
    return
  }
  const updated = await recallChatMessage(message.id)
  const index = messages.value.findIndex((item) => item.id === message.id)
  if (index >= 0) messages.value[index] = updated
  ElMessage.success('已撤回')
  await refreshConversationsSilently()
}

function openCreate() {
  createForm.type = 'DIRECT'
  createForm.name = ''
  createForm.employeeIds = []
  createForm.burnMode = 'DEFAULT'
  createForm.antiRecallMode = 'DEFAULT'
  createForm.retentionDays = undefined
  employeeOptions.value = []
  selectedEmployeeCache.value = {}
  createOpen.value = true
  searchEmployees('')
}

function onCreateTypeChange() {
  if (createForm.type === 'DIRECT' && createForm.employeeIds.length > 1) {
    createForm.employeeIds = createForm.employeeIds.slice(0, 1)
  }
}

async function searchEmployees(query: string) {
  employeeLoading.value = true
  try {
    const result = await pageEmployees({ page: 1, size: 20, keyword: query, status: 'ACTIVE' })
    employeeOptions.value = result.records.filter((employee) => employee.id !== auth.user?.employeeId)
    for (const employee of employeeOptions.value) {
      selectedEmployeeCache.value[employee.id] = employee
    }
  } finally {
    employeeLoading.value = false
  }
}

function employeeOptionLabel(employee: EmployeeView) {
  return employee.employeeNo ? `${employee.name}（${employee.employeeNo}）` : employee.name
}

function removeCreateEmployee(id: string) {
  createForm.employeeIds = createForm.employeeIds.filter((item) => item !== id)
}

async function submitCreate() {
  if (createForm.employeeIds.length === 0) {
    ElMessage.warning(createForm.type === 'GROUP' ? '请至少选择一位同事' : '请选择一位同事')
    return
  }
  if (createForm.type === 'DIRECT' && createForm.employeeIds.length !== 1) {
    ElMessage.warning('单聊只能选择一位同事')
    return
  }
  if (createForm.type === 'GROUP' && !createForm.name.trim()) {
    ElMessage.warning('请填写群名称')
    return
  }
  creating.value = true
  try {
    const detail = await createChatConversation({
      type: createForm.type,
      name: createForm.type === 'GROUP' ? createForm.name.trim() : undefined,
      employeeIds: createForm.employeeIds,
      burnAfterRead: createForm.burnMode === 'DEFAULT' ? undefined : createForm.burnMode === 'ON',
      antiRecall:
        createForm.antiRecallMode === 'DEFAULT' ? undefined : createForm.antiRecallMode === 'ON',
      retentionDays: createForm.retentionDays || undefined,
    })
    createOpen.value = false
    await loadConversations()
    selectedId.value = null
    await selectConversation(detail.conversation.id)
    ElMessage.success(createForm.type === 'GROUP' ? '群聊已创建' : '会话已开启')
  } finally {
    creating.value = false
  }
}

function openSettings() {
  const conversation = activeConversation.value
  if (!conversation) return
  settingsForm.name = conversation.name
  settingsForm.burnAfterRead = conversation.burnAfterRead
  settingsForm.antiRecall = conversation.antiRecall
  settingsForm.retentionDays = conversation.retentionDays ?? undefined
  settingsOpen.value = true
}

async function saveSettings() {
  if (!selectedId.value || !activeConversation.value) return
  savingSettings.value = true
  try {
    const detail = await updateChatConversation(selectedId.value, {
      name: activeConversation.value.type === 'GROUP' ? settingsForm.name.trim() : undefined,
      burnAfterRead: settingsForm.burnAfterRead,
      antiRecall: settingsForm.antiRecall,
      retentionDays: settingsForm.retentionDays || null,
    })
    activeConversation.value = detail.conversation
    members.value = detail.members
    settingsOpen.value = false
    ElMessage.success('会话设置已保存')
    await refreshConversationsSilently()
  } finally {
    savingSettings.value = false
  }
}

async function openMembers() {
  if (!selectedId.value) return
  membersOpen.value = true
  newMemberId.value = ''
  await searchEmployees('')
  try {
    const detail = await getChatConversation(selectedId.value)
    activeConversation.value = detail.conversation
    members.value = detail.members
  } catch {
    // 保持当前成员列表
  }
}

async function addMember() {
  if (!selectedId.value || !newMemberId.value || addingMember.value) return
  addingMember.value = true
  try {
    await addChatMember(selectedId.value, newMemberId.value)
    ElMessage.success('成员已添加')
    newMemberId.value = ''
    const detail = await getChatConversation(selectedId.value)
    activeConversation.value = detail.conversation
    members.value = detail.members
    await refreshConversationsSilently()
  } finally {
    addingMember.value = false
  }
}

function canRemoveMember(member: ChatMemberView) {
  const conversation = activeConversation.value
  if (!conversation || conversation.type !== 'GROUP') return false
  if (member.role === 'OWNER') return false
  if (member.employeeId === auth.user?.employeeId) return conversation.canLeave
  return conversation.canManageMembers
}

async function removeMember(member: ChatMemberView) {
  const conversation = activeConversation.value
  if (!conversation || !selectedId.value) return
  const self = member.employeeId === auth.user?.employeeId
  try {
    await ElMessageBox.confirm(
      self
        ? `退出群聊「${conversation.name}」后将看不到后续消息，确定退出吗？`
        : `确定将 ${member.employeeName || '该成员'} 移出群聊吗？`,
      self ? '退出群聊' : '移出成员',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const id = selectedId.value
  await removeChatMember(id, member.employeeId)
  ElMessage.success(self ? '已退出群聊' : '成员已移出')
  if (self) {
    membersOpen.value = false
    selectedId.value = null
    activeConversation.value = null
    messages.value = []
    router.replace('/chat')
    await loadConversations()
    if (conversations.value.length > 0) {
      await selectConversation(conversations.value[0].id, false)
    }
    return
  }
  const detail = await getChatConversation(id)
  activeConversation.value = detail.conversation
  members.value = detail.members
  await refreshConversationsSilently()
}

function avatarText(conversation: ChatConversationView) {
  return conversation.type === 'GROUP' ? '群' : (conversation.name || '聊').slice(0, 1)
}

function formatListTime(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(11, 16)
  const now = new Date()
  if (isSameDay(date, now)) return pad(date.getHours()) + ':' + pad(date.getMinutes())
  if (date.getFullYear() === now.getFullYear()) {
    return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function formatMessageTime(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const now = new Date()
  if (isSameDay(date, now)) return pad(date.getHours()) + ':' + pad(date.getMinutes())
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDateTime(value: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDateDivider(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const now = new Date()
  if (isSameDay(date, now)) return '今天'
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (isSameDay(date, yesterday)) return '昨天'
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function showDateDivider(index: number) {
  if (index === 0) return true
  const current = messages.value[index].createdAt
  const previous = messages.value[index - 1].createdAt
  if (!current || !previous) return false
  const a = new Date(current)
  const b = new Date(previous)
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime())) return false
  return !isSameDay(a, b)
}

function isSameDay(a: Date, b: Date) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}
</script>

<style scoped>
.chat-page {
  display: flex;
  gap: 12px;
  height: calc(100vh - 88px);
  min-height: 480px;
}
.chat-sidebar {
  width: 300px;
  flex: none;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.sidebar-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 16px 10px;
}
.sidebar-title {
  font-size: 17px;
  font-weight: 600;
}
.sidebar-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.sidebar-search {
  padding: 0 16px 10px;
}
.conversation-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 8px 10px;
}
.conversation-item {
  width: 100%;
  display: flex;
  gap: 10px;
  align-items: center;
  border: none;
  background: transparent;
  border-radius: 8px;
  padding: 10px;
  cursor: pointer;
  text-align: left;
}
.conversation-item:hover {
  background: #f5f7fa;
}
.conversation-item.active {
  background: #ecf5ff;
}
.avatar {
  flex: none;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: linear-gradient(135deg, #409eff, #6a5acd);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.avatar.group {
  background: linear-gradient(135deg, #67c23a, #409eff);
}
.avatar.small {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  font-size: 12px;
  background: #c0c4cc;
}
.avatar.small.me {
  background: linear-gradient(135deg, #409eff, #6a5acd);
}
.conversation-body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.conversation-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}
.conversation-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conversation-time {
  flex: none;
  font-size: 11px;
  color: #a8abb2;
}
.conversation-preview {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.unread {
  flex: none;
}
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid #ebeef5;
}
.chat-head-main {
  min-width: 0;
}
.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex-wrap: wrap;
}
.chat-sub {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.burn-tip {
  color: #e6a23c;
}
.chat-head-actions {
  flex: none;
}
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 16px 20px;
  background: #f7f9fc;
}
.load-more {
  text-align: center;
  margin-bottom: 8px;
}
.date-divider {
  text-align: center;
  color: #a8abb2;
  font-size: 12px;
  margin: 10px 0;
}
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
}
.message-row.mine {
  justify-content: flex-end;
}
.message-content {
  max-width: min(68%, 560px);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.message-row.mine .message-content {
  align-items: flex-end;
}
.sender-name {
  font-size: 12px;
  color: #909399;
  margin: 0 4px 4px;
}
.bubble {
  background: #fff;
  border-radius: 10px;
  padding: 9px 12px;
  line-height: 1.6;
  font-size: 13px;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
.message-row.mine .bubble {
  background: #409eff;
  color: #fff;
}
.bubble.recalled,
.bubble.burned {
  background: #f0f2f5;
  color: #909399;
  font-style: italic;
}
.message-row.mine .bubble.recalled,
.message-row.mine .bubble.burned {
  background: #e4e7ed;
  color: #909399;
}
.bubble:has(.image-attachment),
.bubble:has(.file-attachment) {
  padding: 6px;
}
.image-attachment {
  min-width: 160px;
  min-height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.04);
  cursor: pointer;
}
.image-attachment img {
  display: block;
  max-width: 280px;
  max-height: 220px;
  object-fit: contain;
}
.image-loading {
  color: #909399;
  font-size: 12px;
  padding: 24px;
}
.file-attachment {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 190px;
  max-width: 320px;
  padding: 8px 10px;
  border: none;
  border-radius: 7px;
  background: rgba(0, 0, 0, 0.04);
  text-align: left;
  cursor: pointer;
}
.message-row.mine .file-attachment {
  background: rgba(255, 255, 255, 0.18);
}
.file-icon {
  font-size: 24px;
}
.file-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.file-name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  font-size: 11px;
  opacity: 0.7;
}
.attachment-caption {
  padding: 6px 6px 2px;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}
.image-preview-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 160px;
}
.image-preview {
  max-width: 100%;
  max-height: 68vh;
  object-fit: contain;
}
.message-meta {
  margin-top: 4px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #a8abb2;
  font-size: 11px;
}
.composer {
  border-top: 1px solid #ebeef5;
  padding: 12px 16px;
  background: #fff;
}
.composer-bar {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.composer-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex-wrap: wrap;
}
.composer-hint {
  color: #a8abb2;
  font-size: 11px;
}
.hidden-file {
  display: none;
}
.chat-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #909399;
}
.empty-orb {
  font-size: 46px;
}
.empty-title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}
.empty-desc {
  font-size: 13px;
}
.form-hint {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}
.form-hint.inline {
  margin: 0 0 0 8px;
  display: inline;
}
.selected-members {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.member-add {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.member-list {
  max-height: 360px;
  overflow-y: auto;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px solid #f2f3f5;
}
.member-main {
  flex: 1;
  min-width: 0;
}
.member-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
}
.member-meta {
  color: #909399;
  font-size: 11px;
  margin-top: 3px;
}
</style>
