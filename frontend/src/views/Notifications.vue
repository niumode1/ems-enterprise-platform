<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="unreadOnly" @change="load">
          <el-radio-button :value="false">全部通知</el-radio-button>
          <el-radio-button :value="true">未读</el-radio-button>
        </el-radio-group>
        <div class="toolbar-actions">
          <el-button v-if="preferencesEnabled" @click="openPreferences">通知偏好</el-button>
          <el-button :disabled="!hasUnread" @click="markAll">全部标为已读</el-button>
        </div>
      </div>

      <el-table :data="notifications" stripe>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.read ? 'info' : 'danger'" size="small">
              {{ row.read ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="通知" min-width="280">
          <template #default="{ row }">
            <div class="title">{{ row.title }}</div>
            <div v-if="row.content" class="content">{{ row.content }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.link" link type="primary" @click="openLink(row)">查看</el-button>
            <el-button v-if="!row.read" link @click="read(row)">标为已读</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!notifications.length" description="暂无通知" />

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="preferencesOpen" title="通知偏好" width="520px">
      <p class="preference-hint">
        {{ behaviorHint }}
        安全类通知不接受个人关闭；公司可在配置中心调整可设置的类型。
      </p>
      <div v-if="preferencesDraft.length" class="preference-list">
        <div v-for="item in preferencesDraft" :key="item.type" class="preference-row">
          <div>
            <div class="preference-label">{{ item.label }}</div>
            <div class="preference-code">{{ item.type }}</div>
          </div>
          <el-switch
            v-model="item.muted"
            active-text="关闭"
            inactive-text="接收"
            inline-prompt
          />
        </div>
      </div>
      <el-empty v-else description="当前公司未开放可设置的通知类型" :image-size="60" />
      <template #footer>
        <el-button @click="preferencesOpen = false">取消</el-button>
        <el-button type="primary" :loading="savingPreferences" @click="savePreferences">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getNotificationPreferences,
  markAllNotificationsRead,
  markNotificationRead,
  pageNotifications,
  updateNotificationPreferences,
} from '@/api/notifications'
import type { RealtimeEvent } from '@/realtime'
import type {
  NotificationItem,
  NotificationPreferenceItem,
  NotificationPreferencesView,
} from '@/types'

const router = useRouter()
const notifications = ref<NotificationItem[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const unreadOnly = ref(false)
const preferences = ref<NotificationPreferencesView | null>(null)
const preferencesDraft = ref<NotificationPreferenceItem[]>([])
const preferencesOpen = ref(false)
const savingPreferences = ref(false)

const preferencesEnabled = computed(() => preferences.value?.enabled === true)
const behaviorHint = computed(() =>
  preferences.value?.behavior === 'SUPPRESS'
    ? '关闭某类通知后将不再生成该类通知，可能错过待办，请谨慎设置。'
    : '关闭某类通知后仍会保留站内记录，只是不再实时提醒。',
)
const hasUnread = computed(() => notifications.value.some((item) => !item.read))

onMounted(() => {
  void load()
  void loadPreferences()
  window.addEventListener('ems-realtime', onRealtime)
})

onUnmounted(() => {
  window.removeEventListener('ems-realtime', onRealtime)
})

function onRealtime(event: Event) {
  const detail = (event as CustomEvent<RealtimeEvent>).detail
  if ((detail?.type === 'NOTIFICATION' || detail?.type === 'REALTIME_CONNECTED')
    && page.value === 1) {
    void load()
  }
}

async function load() {
  const result = await pageNotifications(page.value, size.value, unreadOnly.value)
  notifications.value = result.records
  total.value = result.total
}

async function loadPreferences() {
  try {
    preferences.value = await getNotificationPreferences()
  } catch {
    preferences.value = null
  }
}

async function openPreferences() {
  const fresh = await getNotificationPreferences()
  preferences.value = fresh
  preferencesDraft.value = fresh.preferences.map((item) => ({ ...item }))
  preferencesOpen.value = true
}

async function savePreferences() {
  savingPreferences.value = true
  try {
    const updated = await updateNotificationPreferences(
      preferencesDraft.value.map((item) => ({ type: item.type, muted: item.muted })),
    )
    preferences.value = updated
    preferencesDraft.value = updated.preferences.map((item) => ({ ...item }))
    preferencesOpen.value = false
    ElMessage.success('通知偏好已保存')
  } finally {
    savingPreferences.value = false
  }
}

async function read(item: NotificationItem) {
  await markNotificationRead(item.id)
  await load()
}

async function markAll() {
  await markAllNotificationsRead()
  ElMessage.success('已全部标为已读')
  await load()
}

async function openLink(item: NotificationItem) {
  if (!item.read) {
    await markNotificationRead(item.id)
  }
  if (item.link) {
    router.push(item.link)
  }
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
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.preference-hint {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
  margin: 0 0 12px;
}
.preference-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.preference-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f2f5;
}
.preference-row:last-child {
  border-bottom: none;
}
.preference-label {
  font-weight: 500;
}
.preference-code {
  color: #c0c4cc;
  font-size: 11px;
  margin-top: 2px;
}
.title {
  font-weight: 500;
}
.content {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
