<template>
  <div class="dashboard">
    <el-card class="welcome">
      <div class="welcome-row">
        <div>
          <h2>你好，{{ auth.user?.displayName || auth.user?.username }}</h2>
          <p>左侧菜单是完整功能入口；这里只汇总与你有关的待办和常用操作，避免重复找菜单。</p>
        </div>
        <div class="welcome-stats">
          <div class="stat">
            <span class="stat-value">{{ auth.roles.length }}</span>
            <span class="stat-label">角色</span>
          </div>
          <div class="stat">
            <span class="stat-value">{{ auth.permissions.length }}</span>
            <span class="stat-label">权限点</span>
          </div>
        </div>
      </div>
    </el-card>

    <div class="section-title">我的待办</div>
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card
          v-if="auth.hasPermission('approval:read')"
          shadow="hover"
          class="todo"
          @click="router.push('/approvals')"
        >
          <div class="todo-value">{{ pendingApprovals ?? '-' }}</div>
          <div class="todo-label">待我审批</div>
          <div class="todo-action">前往审批中心 →</div>
        </el-card>
        <el-card v-else shadow="never" class="todo muted-card">
          <div class="todo-value">—</div>
          <div class="todo-label">当前无需处理审批</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card
          v-if="auth.hasPermission('import:confirm')"
          shadow="hover"
          class="todo"
          @click="router.push('/imports/my-confirmations')"
        >
          <div class="todo-value">{{ pendingConfirmations ?? '-' }}</div>
          <div class="todo-label">待我确认的档案</div>
          <div class="todo-action">去确认自己的资料 →</div>
        </el-card>
        <el-card v-else shadow="never" class="todo muted-card">
          <div class="todo-value">—</div>
          <div class="todo-label">暂无档案确认任务</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="todo" @click="router.push('/notifications')">
          <div class="todo-value">{{ unread }}</div>
          <div class="todo-label">未读通知</div>
          <div class="todo-action">打开通知中心 →</div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card shadow="hover" class="todo" @click="router.push('/profile')">
          <div class="todo-value">👤</div>
          <div class="todo-label">我的档案</div>
          <div class="todo-action">查看与维护个人信息 →</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="quick-card">
      <template #header>
        <div class="quick-header">
          <span>常用入口</span>
          <span class="quick-note">仅保留高频操作，完整功能请使用左侧菜单</span>
        </div>
      </template>
      <div class="quick-list">
        <el-button
          v-for="item in quickLinks"
          :key="item.path"
          class="quick-item"
          @click="router.push(item.path)"
        >
          <span class="quick-icon">{{ item.icon }}</span>
          <span>{{ item.title }}</span>
        </el-button>
      </div>
    </el-card>

    <el-card class="notes">
      <template #header>使用提示</template>
      <ul>
        <li>普通员工只能看到字段权限允许的字段；本人可在“我的资料”用眼睛开关控制哪些信息对普通同事可见。</li>
        <li>身份证、部门、岗位、直属上级等关键信息修改需要多人审批后生效。</li>
        <li>审批、配置、账号与员工变更都会写入不可删除的审计日志。</li>
        <li>不同公司可在“系统配置”和“字段权限”中调整规则，代码不写死公司制度。</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { pageApprovals } from '@/api/approvals'
import { myImportConfirmations } from '@/api/imports'
import { unreadNotificationCount } from '@/api/notifications'

const auth = useAuthStore()
const router = useRouter()
const pendingApprovals = ref<number | null>(null)
const pendingConfirmations = ref<number | null>(null)
const unread = ref(0)

const quickLinks = computed(() =>
  [
    { path: '/chat', title: '聊天', icon: '💬', permission: 'chat:use' },
    { path: '/projects', title: '项目管理', icon: '📁', permission: 'project:read' },
    { path: '/policies', title: '公司制度', icon: '📘', permission: 'policy:read' },
    { path: '/employees', title: '员工管理', icon: '👥', permission: 'employee:read' },
    { path: '/org', title: '组织架构', icon: '🏢', permission: 'org:read' },
    { path: '/imports', title: '花名册导入', icon: '📥', permission: 'import:read' },
    { path: '/approvals', title: '审批中心', icon: '✅', permission: 'approval:read' },
    { path: '/notifications', title: '通知中心', icon: '🔔' },
    { path: '/config', title: '系统配置', icon: '⚙️', permission: 'config:read' },
  ].filter((item) => !item.permission || auth.hasPermission(item.permission)),
)

onMounted(async () => {
  const tasks: Promise<void>[] = []

  if (auth.hasPermission('approval:read')) {
    tasks.push(
      pageApprovals({ page: 1, size: 1, pendingForMe: true })
        .then((result) => {
          pendingApprovals.value = result.total
        })
        .catch(() => {
          pendingApprovals.value = null
        }),
    )
  }

  if (auth.hasPermission('import:confirm')) {
    tasks.push(
      myImportConfirmations()
        .then((rows) => {
          pendingConfirmations.value = rows.length
        })
        .catch(() => {
          pendingConfirmations.value = null
        }),
    )
  }

  tasks.push(
    unreadNotificationCount()
      .then((result) => {
        unread.value = result.count
      })
      .catch(() => {
        unread.value = 0
      }),
  )

  await Promise.all(tasks)
})
</script>

<style scoped>
.welcome-row {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.welcome h2 {
  margin: 0 0 8px;
}
.welcome p {
  margin: 0;
  color: #606266;
}
.welcome-stats {
  display: flex;
  gap: 24px;
  align-items: center;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 72px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.section-title {
  margin: 20px 0 12px;
  font-size: 15px;
  font-weight: 600;
}
.todo {
  cursor: pointer;
  margin-bottom: 16px;
}
.todo-value {
  font-size: 30px;
  font-weight: 700;
  color: #409eff;
}
.todo-label {
  margin-top: 4px;
  color: #303133;
}
.todo-action {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
}
.muted-card {
  cursor: default;
}
.muted-card .todo-value {
  color: #c0c4cc;
}
.quick-card {
  margin-top: 4px;
}
.quick-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.quick-note {
  color: #909399;
  font-size: 12px;
}
.quick-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.quick-item {
  height: auto;
  padding: 10px 16px;
}
.quick-icon {
  margin-right: 6px;
}
.notes {
  margin-top: 16px;
}
.notes ul {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  line-height: 1.9;
}
</style>
