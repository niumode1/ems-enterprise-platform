<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <div class="filters">
          <el-input v-model="query.keyword" placeholder="项目名称" clearable style="width: 220px" @keyup.enter="load" />
          <el-select v-model="query.status" clearable placeholder="状态" style="width: 140px">
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="已结束" value="CLOSED" />
          </el-select>
          <el-button type="primary" @click="load">查询</el-button>
        </div>
        <el-button v-if="auth.hasPermission('project:create')" type="primary" @click="openCreate">
          新建项目
        </el-button>
      </div>

      <el-table :data="projects" stripe @row-click="openProject">
        <el-table-column prop="name" label="项目名称" min-width="180">
          <template #default="{ row }">
            <el-link type="primary" @click.stop="openProject(row)">{{ row.name }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="编码" width="120" />
        <el-table-column prop="ownerName" label="负责人" width="110" />
        <el-table-column prop="myRole" label="我的角色" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ roleLabel(row.myRole) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="memberCount" label="成员" width="80" />
        <el-table-column prop="documentCount" label="文档" width="80" />
        <el-table-column label="进度" width="140">
          <template #default="{ row }">
            <el-progress :percentage="row.progress" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="下载策略" width="120">
          <template #default="{ row }">{{ downloadPolicyLabel(row.downloadPolicy) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '进行中' : row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" />
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="createVisible" title="新建项目" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目名称" required><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="项目编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="项目描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="保留天数">
          <el-input-number v-model="form.retentionDays" :min="1" :max="36500" />
          <span class="hint">项目初始化时决定，删除为永久删除</span>
        </el-form-item>
        <el-form-item label="下载策略">
          <el-select v-model="form.downloadPolicy" style="width: 180px">
            <el-option label="全部成员可下载" value="ALL" />
            <el-option label="仅负责人/管理员" value="ADMINS" />
            <el-option label="不可下载" value="NONE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { createProject, pageProjects, type ProjectPayload } from '@/api/projects'
import type { ProjectView } from '@/types'

const auth = useAuthStore()
const router = useRouter()
const projects = ref<ProjectView[]>([])
const total = ref(0)
const saving = ref(false)
const createVisible = ref(false)
const query = reactive({ page: 1, size: 20, keyword: '', status: '' })
const form = reactive<ProjectPayload>({
  name: '',
  code: '',
  description: '',
  startDate: null,
  endDate: null,
  retentionDays: 3650,
  downloadPolicy: 'ALL',
})

onMounted(load)

async function load() {
  const page = await pageProjects({
    page: query.page,
    size: query.size,
    keyword: query.keyword || undefined,
    status: query.status || undefined,
  })
  projects.value = page.records
  total.value = page.total
}

function openProject(project: ProjectView) {
  router.push(`/projects/${project.id}`)
}

function openCreate() {
  Object.assign(form, {
    name: '',
    code: '',
    description: '',
    startDate: null,
    endDate: null,
    retentionDays: 3650,
    downloadPolicy: 'ALL',
  })
  createVisible.value = true
}

async function submitCreate() {
  if (!form.name) {
    ElMessage.warning('请填写项目名称')
    return
  }
  saving.value = true
  try {
    const project = await createProject(form)
    ElMessage.success('项目已创建')
    createVisible.value = false
    router.push(`/projects/${project.id}`)
  } finally {
    saving.value = false
  }
}

function roleLabel(role: string) {
  const labels: Record<string, string> = {
    OWNER: '负责人',
    ADMIN: '项目管理员',
    MEMBER: '成员',
    VIEWER: '只读',
  }
  return labels[role] ?? role
}

function downloadPolicyLabel(policy: string) {
  const labels: Record<string, string> = {
    ALL: '全部成员',
    ADMINS: '仅管理员',
    NONE: '禁止下载',
  }
  return labels[policy] ?? policy
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
  gap: 12px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
.hint {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
:deep(.el-table__row) {
  cursor: pointer;
}
</style>
