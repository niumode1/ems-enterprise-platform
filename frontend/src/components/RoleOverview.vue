<template>
  <div class="role-overview">
    <el-empty v-if="users.length === 0" description="当前没有可展示的账号" :image-size="70" />

    <template v-else>
      <div class="summary">
        <div class="summary-item">
          <span class="summary-value">{{ users.length }}</span>
          <span class="summary-label">当前账号</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ assignedCount }}</span>
          <span class="summary-label">已分配角色</span>
        </div>
        <div class="summary-item">
          <span class="summary-value" :class="{ warn: unassignedUsers.length > 0 }">
            {{ unassignedUsers.length }}
          </span>
          <span class="summary-label">未分配角色</span>
        </div>
        <div class="summary-item">
          <span class="summary-value">{{ roleStats.length }}</span>
          <span class="summary-label">角色种类</span>
        </div>
      </div>

      <div v-if="unassignedUsers.length" class="unassigned">
        <span class="unassigned-title">未分配角色的账号</span>
        <el-tag v-for="user in unassignedUsers.slice(0, 10)" :key="user.id" size="small" type="info">
          {{ user.displayName || user.username }}
        </el-tag>
        <el-tag v-if="unassignedUsers.length > 10" size="small" type="info">
          +{{ unassignedUsers.length - 10 }}
        </el-tag>
      </div>

      <div class="role-grid">
        <div
          v-for="role in roleStats"
          :key="role.code"
          class="role-card"
          :class="{ empty: role.users.length === 0, expandable: role.users.length > 10 }"
          @click="toggle(role.code)"
        >
          <div class="role-head">
            <div class="role-title">
              <span class="role-name">{{ role.name }}</span>
              <el-tag size="small" type="info" effect="plain">{{ role.code }}</el-tag>
            </div>
            <span class="role-count">{{ role.users.length }} 人</span>
          </div>

          <div class="role-bar">
            <div class="role-bar-fill" :style="{ width: barWidth(role.users.length) }" />
          </div>

          <div v-if="role.users.length" class="holders">
            <el-tag
              v-for="user in shownUsers(role)"
              :key="user.id"
              size="small"
              class="holder"
              :title="`${user.displayName || user.username}（${user.username}）`"
            >
              {{ user.displayName || user.username }}
            </el-tag>
            <el-tag
              v-if="role.users.length > 10 && !expandedRole(role.code)"
              size="small"
              type="info"
              class="holder"
            >
              +{{ role.users.length - 10 }}，点击展开
            </el-tag>
            <el-tag
              v-if="role.users.length > 10 && expandedRole(role.code)"
              size="small"
              type="info"
              class="holder collapse"
            >
              收起
            </el-tag>
          </div>
          <div v-else class="no-holder">暂无账号</div>
        </div>
      </div>

      <div class="hint">
        按角色分组展示当前页账号；卡片长度表示相对人数，点击角色卡片可展开全部账号。生效日期与历史请切回表格使用“角色历史”。
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { RoleView, UserView } from '@/types'

const props = defineProps<{ users: UserView[]; roles: RoleView[] }>()

interface RoleStat {
  code: string
  name: string
  users: UserView[]
}

const expanded = reactive<Record<string, boolean>>({})

const roleStats = computed<RoleStat[]>(() => {
  const map = new Map<string, RoleStat>()
  for (const role of props.roles) {
    map.set(role.code, { code: role.code, name: role.name, users: [] })
  }
  for (const user of props.users) {
    for (const role of user.roles) {
      const entry = map.get(role.code) ?? { code: role.code, name: role.name, users: [] }
      entry.users.push(user)
      map.set(role.code, entry)
    }
  }
  return Array.from(map.values()).sort((a, b) => {
    if (b.users.length !== a.users.length) return b.users.length - a.users.length
    return a.name.localeCompare(b.name, 'zh-CN')
  })
})

const maxCount = computed(() =>
  roleStats.value.reduce((max, role) => Math.max(max, role.users.length), 0),
)

const assignedCount = computed(
  () => props.users.filter((user) => user.roles.length > 0).length,
)
const unassignedUsers = computed(() => props.users.filter((user) => user.roles.length === 0))

function barWidth(count: number) {
  if (count === 0) return '0%'
  const ratio = maxCount.value === 0 ? 0 : count / maxCount.value
  return `${Math.max(6, Math.round(ratio * 100))}%`
}

function expandedRole(code: string) {
  return Boolean(expanded[code])
}

function shownUsers(role: RoleStat) {
  return expandedRole(role.code) ? role.users : role.users.slice(0, 10)
}

function toggle(code: string) {
  expanded[code] = !expanded[code]
}
</script>

<style scoped>
.role-overview {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px;
  background: #fbfcfe;
}
.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.summary-item {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-value {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
}
.summary-value.warn {
  color: #e6a23c;
}
.summary-label {
  color: #909399;
  font-size: 12px;
}
.unassigned {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 10px 12px;
  margin-bottom: 14px;
  border: 1px dashed #e6a23c;
  border-radius: 8px;
  background: #fdf6ec;
}
.unassigned-title {
  color: #b88230;
  font-size: 13px;
  margin-right: 4px;
}
.role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 12px;
}
.role-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.role-card:hover {
  border-color: #c6e2ff;
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.08);
}
.role-card.expandable {
  cursor: pointer;
}
.role-card.empty {
  opacity: 0.72;
}
.role-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.role-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.role-name {
  font-weight: 600;
  font-size: 14px;
}
.role-count {
  color: #409eff;
  font-weight: 600;
  font-size: 13px;
  white-space: nowrap;
}
.role-bar {
  height: 7px;
  border-radius: 4px;
  background: #eef2f8;
  margin: 10px 0;
  overflow: hidden;
}
.role-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #79bbff, #409eff);
  transition: width 0.25s ease;
}
.role-card.empty .role-bar-fill {
  background: #dcdfe6;
}
.holders {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.holder {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.holder.collapse {
  cursor: pointer;
  color: #409eff;
}
.no-holder {
  color: #c0c4cc;
  font-size: 12px;
}
.hint {
  margin-top: 14px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>
