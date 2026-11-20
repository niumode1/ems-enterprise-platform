import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/Login.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/Dashboard.vue'),
          meta: { title: '工作台' },
        },
        {
          path: 'projects',
          name: 'projects',
          component: () => import('@/views/Projects.vue'),
          meta: { title: '项目管理', permission: 'project:read' },
        },
        {
          path: 'projects/:id',
          name: 'project-detail',
          component: () => import('@/views/ProjectDetail.vue'),
          meta: { title: '项目详情', permission: 'project:read' },
        },
        {
          path: 'employees',
          name: 'employees',
          component: () => import('@/views/Employees.vue'),
          meta: { title: '员工管理', permission: 'employee:read' },
        },
        {
          path: 'org',
          name: 'org',
          component: () => import('@/views/Org.vue'),
          meta: { title: '组织架构', permission: 'org:read' },
        },
        {
          path: 'custom-fields',
          name: 'custom-fields',
          component: () => import('@/views/CustomAttributes.vue'),
          meta: { title: '自定义属性', permission: 'attr:read' },
        },
        {
          path: 'imports',
          name: 'imports',
          component: () => import('@/views/RosterImport.vue'),
          meta: { title: '花名册导入', permission: 'import:read' },
        },
        {
          path: 'imports/my-confirmations',
          name: 'my-confirmations',
          component: () => import('@/views/MyConfirmations.vue'),
          meta: { title: '我的确认', permission: 'import:confirm' },
        },
        {
          path: 'policies',
          name: 'policies',
          component: () => import('@/views/Policies.vue'),
          meta: { title: '公司制度', permission: 'policy:read' },
        },
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/Search.vue'),
          meta: { title: '全局搜索' },
        },
        {
          path: 'chat/:id?',
          name: 'chat',
          component: () => import('@/views/Chat.vue'),
          meta: { title: '聊天', permission: 'chat:use' },
        },
        {
          path: 'ai-assistant',
          name: 'ai-assistant',
          component: () => import('@/views/AiAssistant.vue'),
          meta: { title: 'AI 助手', permission: 'ai:use' },
        },
        {
          path: 'ai-settings',
          name: 'ai-settings',
          component: () => import('@/views/AiSettings.vue'),
          meta: { title: 'AI 设置', permission: 'ai:config:read' },
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/Notifications.vue'),
          meta: { title: '通知中心', permission: 'notification:read' },
        },
        {
          path: 'approvals',
          name: 'approvals',
          component: () => import('@/views/Approvals.vue'),
          meta: { title: '审批中心', permission: 'approval:read' },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/views/Audit.vue'),
          meta: { title: '审计日志', permission: 'audit:read' },
        },
        {
          path: 'config',
          name: 'config',
          component: () => import('@/views/Config.vue'),
          meta: { title: '系统配置', permission: 'config:read' },
        },
        {
          path: 'field-permissions',
          name: 'field-permissions',
          component: () => import('@/views/FieldPermissions.vue'),
          meta: { title: '字段权限', permission: 'field:read' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/Users.vue'),
          meta: { title: '账号权限', permission: 'user:read' },
        },
        {
          path: 'boss-access',
          name: 'boss-access',
          component: () => import('@/views/BossAccess.vue'),
          meta: {
            title: '敏感信息访问',
            permissions: ['employee:sensitive:request', 'audit:read'],
          },
        },
        {
          path: 'recovery',
          name: 'recovery',
          component: () => import('@/views/Recovery.vue'),
          meta: {
            title: '紧急恢复',
            permissions: ['config:recovery', 'audit:read'],
          },
        },
        {
          path: 'retention',
          name: 'retention',
          component: () => import('@/views/Retention.vue'),
          meta: {
            title: '保留期清理',
            permissions: ['config:read', 'config:recovery'],
          },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/Profile.vue'),
          meta: { title: '我的资料' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    if (to.path === '/login' && auth.isLoggedIn) {
      return '/dashboard'
    }
    return true
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      auth.clear()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  const permissions = (to.meta.permissions as string[] | undefined)
    ?? (to.meta.permission ? [to.meta.permission as string] : [])
  if (permissions.length && !permissions.some((permission) => auth.hasPermission(permission))) {
    return '/dashboard'
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · EMS` : 'EMS'
})

export default router
