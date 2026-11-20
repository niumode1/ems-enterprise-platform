<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">EMS</div>
      <el-menu :default-active="activeMenu" router class="menu" background-color="#1f2d3d" text-color="#c0c4cc" active-text-color="#ffffff">
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          <span>{{ item.title }}</span>
          <el-badge
            v-if="item.path === '/chat' && chatUnread"
            :value="chatUnread"
            :max="99"
            class="menu-badge"
          />
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="body">
      <el-header class="header">
        <div class="page-title">{{ route.meta.title || '工作台' }}</div>
        <div class="header-right">
          <el-input
            v-model="headerKeyword"
            class="global-search"
            size="small"
            clearable
            placeholder="搜索员工 / 项目 / 任务 / 文档 / 制度"
            @keyup.enter="goSearch"
          >
            <template #prefix>🔍</template>
          </el-input>
          <el-badge :value="unread" :hidden="!unread" :max="99" class="bell">
            <el-button link class="bell-button" @click="router.push('/notifications')">🔔 通知</el-button>
          </el-badge>
          <el-dropdown @command="onCommand">
          <span class="user-entry">
            {{ auth.user?.displayName || auth.user?.username }}
            <el-tag v-for="role in auth.user?.roles || []" :key="role" size="small" class="role-tag">
              {{ roleLabel(role) }}
            </el-tag>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">我的资料</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
    <AiFloating />
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { unreadNotificationCount } from '@/api/notifications'
import { listChatConversations } from '@/api/chat'
import { startRealtime, stopRealtime, type RealtimeEvent } from '@/realtime'
import AiFloating from '@/components/AiFloating.vue'

interface MenuItem {
  path: string
  title: string
  permissions?: string[]
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const menus: MenuItem[] = [
  { path: '/dashboard', title: '工作台' },
  { path: '/search', title: '全局搜索' },
  { path: '/chat', title: '聊天', permissions: ['chat:use'] },
  { path: '/projects', title: '项目管理', permissions: ['project:read'] },
  { path: '/employees', title: '员工管理', permissions: ['employee:read'] },
  { path: '/org', title: '组织架构', permissions: ['org:read'] },
  { path: '/custom-fields', title: '自定义属性', permissions: ['attr:read'] },
  { path: '/imports', title: '花名册导入', permissions: ['import:read'] },
  { path: '/imports/my-confirmations', title: '我的确认', permissions: ['import:confirm'] },
  { path: '/approvals', title: '审批中心', permissions: ['approval:read'] },
  {
    path: '/boss-access',
    title: '敏感信息访问',
    permissions: ['employee:sensitive:request', 'audit:read'],
  },
  {
    path: '/recovery',
    title: '紧急恢复',
    permissions: ['config:recovery', 'audit:read'],
  },
  {
    path: '/retention',
    title: '保留期清理',
    permissions: ['config:read', 'config:recovery'],
  },
  { path: '/audit', title: '审计日志', permissions: ['audit:read'] },
  { path: '/config', title: '系统配置', permissions: ['config:read'] },
  { path: '/field-permissions', title: '字段权限', permissions: ['field:read'] },
  { path: '/users', title: '账号权限', permissions: ['user:read'] },
  { path: '/profile', title: '我的资料' },
]

const unread = ref(0)
const chatUnread = ref(0)
const headerKeyword = ref('')
let unreadTimer: number | undefined
let realtimeRefreshTimer: number | undefined

function goSearch() {
  const keyword = headerKeyword.value.trim()
  if (!keyword) return
  router.push({ path: '/search', query: { q: keyword } })
}

onMounted(async () => {
  await refreshUnread()
  startRealtime(auth.token)
  unreadTimer = window.setInterval(refreshUnread, 60_000)
  window.addEventListener('ems-chat-read', refreshUnread)
  window.addEventListener('ems-realtime', onRealtime)
})

onUnmounted(() => {
  stopRealtime()
  if (unreadTimer) window.clearInterval(unreadTimer)
  if (realtimeRefreshTimer) window.clearTimeout(realtimeRefreshTimer)
  window.removeEventListener('ems-chat-read', refreshUnread)
  window.removeEventListener('ems-realtime', onRealtime)
})

function onRealtime(event: Event) {
  const detail = (event as CustomEvent<RealtimeEvent>).detail
  if (!detail) return
  if (detail.type === 'NOTIFICATION') {
    const data = detail.data || {}
    if (route.path !== '/notifications') {
      ElNotification({
        title: String(data.title || '新通知'),
        message: String(data.content || ''),
        type: 'info',
        duration: 4500,
        onClick: () => {
          if (data.link) router.push(String(data.link))
        },
      })
    }
  }
  if (detail.type === 'NOTIFICATION' || detail.type.startsWith('CHAT_')
    || detail.type === 'REALTIME_CONNECTED') {
    scheduleUnreadRefresh()
  }
}

function scheduleUnreadRefresh() {
  if (realtimeRefreshTimer) window.clearTimeout(realtimeRefreshTimer)
  realtimeRefreshTimer = window.setTimeout(() => {
    realtimeRefreshTimer = undefined
    void refreshUnread()
  }, 250)
}

async function refreshUnread() {
  try {
    unread.value = (await unreadNotificationCount()).count
  } catch {
    unread.value = 0
  }
  if (auth.hasPermission('chat:use')) {
    try {
      const conversations = await listChatConversations()
      chatUnread.value = conversations.reduce((sum, item) => sum + item.unreadCount, 0)
    } catch {
      chatUnread.value = 0
    }
  }
}

const visibleMenus = computed(() =>
  menus.filter(
    (item) => !item.permissions?.length
      || item.permissions.some((permission) => auth.hasPermission(permission)),
  ),
)

const activeMenu = computed(() => (route.path.startsWith('/chat') ? '/chat' : route.path))

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

async function onCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await auth.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
  overflow: hidden;
}
.aside {
  height: 100vh;
  overflow-y: auto;
  background: #1f2d3d;
}
.body {
  height: 100vh;
  min-width: 0;
  overflow: hidden;
}
.brand {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  letter-spacing: 4px;
  font-size: 20px;
}
.menu {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}
.page-title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 18px;
}
.global-search {
  width: 260px;
}
@media (max-width: 1180px) {
  .global-search {
    width: 180px;
  }
}
@media (max-width: 960px) {
  .global-search {
    display: none;
  }
}
.bell-button {
  color: #303133;
}
.user-entry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #303133;
}
.role-tag {
  margin-left: 2px;
}
.menu-badge {
  margin-left: auto;
}
.menu-badge :deep(.el-badge__content) {
  border: none;
}
.main {
  min-height: 0;
  overflow-y: auto;
  background: #f5f7fa;
  padding: 16px;
}
</style>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
}
</style>
