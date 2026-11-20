<template>
  <div class="page" v-if="project">
    <el-card shadow="never">
      <div class="project-head">
        <div>
          <div class="title-row">
            <el-button link @click="router.push('/projects')">← 返回</el-button>
            <h2>{{ project.name }}</h2>
            <el-tag size="small">{{ roleLabel(project.myRole) }}</el-tag>
          </div>
          <div class="meta">
            <span>负责人：{{ project.ownerName || '—' }}</span>
            <span>成员：{{ project.memberCount }}</span>
            <span>文档：{{ project.documentCount }}</span>
            <span>进度：{{ project.progress }}%（{{ project.progressMode === 'AUTO' ? '自动' : '手动' }}）</span>
            <span>下载策略：{{ downloadPolicyLabel(project.downloadPolicy) }}</span>
            <span v-if="project.retentionDays">保留 {{ project.retentionDays }} 天</span>
          </div>
          <p v-if="project.description" class="description">{{ project.description }}</p>
        </div>
        <el-button
          v-if="auth.hasPermission('project:delete') && project.myRole === 'OWNER'"
          type="danger"
          plain
          @click="removeProject"
        >删除项目</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="任务" name="tasks">
          <div class="toolbar">
            <div class="filters">
              <el-select v-model="taskFilters.status" clearable placeholder="全部状态" style="width: 140px" @change="loadTasks">
                <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select
                v-model="taskFilters.assigneeEmployeeId"
                clearable
                filterable
                placeholder="全部负责人"
                style="width: 170px"
                @change="loadTasks"
              >
                <el-option
                  v-for="item in members"
                  :key="item.employeeId"
                  :label="item.employeeName || item.employeeNo || item.employeeId"
                  :value="item.employeeId"
                />
              </el-select>
              <el-input
                v-model="taskFilters.keyword"
                clearable
                placeholder="搜索任务标题"
                style="width: 200px"
                @keyup.enter="loadTasks"
                @clear="loadTasks"
              />
              <el-button @click="loadTasks">查询</el-button>
            </div>
            <el-button v-if="canCreateTask" type="primary" @click="openTaskCreate">新建任务</el-button>
          </div>

          <el-table v-loading="tasksLoading" :data="tasks" stripe>
            <el-table-column prop="title" label="任务" min-width="220">
              <template #default="{ row }">
                <div class="task-title">{{ row.title }}</div>
                <div v-if="row.dependencies.length" class="hint">
                  依赖：{{ row.dependencies.map((item: TaskRefView) => item.title).join('、') }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <el-select
                  v-if="row.canEdit"
                  :model-value="row.status"
                  size="small"
                  @change="(value: string) => changeTaskStatus(row, value)"
                >
                  <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <el-tag v-else size="small" :type="taskStatusType(row.status)">{{ taskStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="priorityType(row.priority)">{{ priorityLabel(row.priority) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="assigneeName" label="负责人" width="110">
              <template #default="{ row }">{{ row.assigneeName || '未分配' }}</template>
            </el-table-column>
            <el-table-column prop="dueDate" label="截止日期" width="120">
              <template #default="{ row }">{{ row.dueDate || '—' }}</template>
            </el-table-column>
            <el-table-column label="进度" width="140">
              <template #default="{ row }">
                <el-progress :percentage="row.progress" :stroke-width="8" />
              </template>
            </el-table-column>
            <el-table-column label="评论" width="80">
              <template #default="{ row }">
                <el-button link type="primary" @click="openComments(row)">{{ row.commentCount }}</el-button>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openComments(row)">评论</el-button>
                <el-button v-if="row.canEdit" link type="warning" @click="openTaskEdit(row)">编辑</el-button>
                <el-button v-if="row.canDelete" link type="danger" @click="removeTask(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!tasksLoading && !tasks.length" description="暂无任务" />
        </el-tab-pane>

        <el-tab-pane label="里程碑" name="milestones">
          <div class="toolbar">
            <span class="hint">里程碑用于标记项目关键节点，可由项目负责人按配置的角色管理。</span>
            <el-button v-if="canCreateMilestone" type="primary" @click="openMilestoneCreate">新建里程碑</el-button>
          </div>
          <el-table :data="milestones" stripe>
            <el-table-column prop="title" label="里程碑" min-width="200" />
            <el-table-column prop="dueDate" label="计划日期" width="130">
              <template #default="{ row }">{{ row.dueDate || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="milestoneStatusType(row.status)">{{ milestoneStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reachedAt" label="达成时间" width="180">
              <template #default="{ row }">{{ row.reachedAt || '—' }}</template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canUpdateMilestone && row.status !== 'REACHED'"
                  link
                  type="success"
                  @click="markMilestoneReached(row)"
                >标记达成</el-button>
                <el-button v-if="canUpdateMilestone" link type="warning" @click="openMilestoneEdit(row)">编辑</el-button>
                <el-button v-if="canDeleteMilestone" link type="danger" @click="removeMilestone(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!milestones.length" description="暂无里程碑" />
        </el-tab-pane>

        <el-tab-pane label="项目进度" name="progress">
          <div class="progress-panel">
            <div class="progress-main">
              <el-progress type="dashboard" :percentage="projectProgress?.progress ?? 0" :width="150" />
              <div class="progress-meta">
                <el-tag size="small">{{ projectProgress?.mode === 'AUTO' ? '按任务自动计算' : '手动维护' }}</el-tag>
                <span>任务总数：{{ projectProgress?.taskTotal ?? 0 }}</span>
                <span>已完成：{{ projectProgress?.taskDone ?? 0 }}</span>
                <span>最后更新：{{ projectProgress?.updatedAt || '—' }}</span>
              </div>
            </div>
            <div v-if="canUpdateProgress" class="progress-form">
              <template v-if="projectProgress?.mode === 'AUTO'">
                <el-alert type="info" :closable="false" show-icon
                  title="当前项目按任务完成率自动计算进度，可在系统配置中切换为手动模式。" />
              </template>
              <template v-else>
                <el-form label-width="70px">
                  <el-form-item label="进度">
                    <el-slider v-model="progressForm.progress" :min="0" :max="100" show-input />
                  </el-form-item>
                  <el-form-item label="备注">
                    <el-input v-model="progressForm.note" maxlength="500" show-word-limit placeholder="可选，记录本次调整原因" />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :loading="progressSaving" @click="saveProgress">更新进度</el-button>
                  </el-form-item>
                </el-form>
              </template>
            </div>
          </div>
          <el-divider>进度记录</el-divider>
          <el-table :data="snapshots" stripe>
            <el-table-column prop="progress" label="进度" width="90">
              <template #default="{ row }">{{ row.progress }}%</template>
            </el-table-column>
            <el-table-column label="任务完成" width="120">
              <template #default="{ row }">{{ row.taskDone }} / {{ row.taskTotal }}</template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="200" show-overflow-tooltip />
            <el-table-column prop="createdByName" label="操作人" width="110" />
            <el-table-column prop="createdAt" label="时间" width="180" />
          </el-table>
          <el-empty v-if="!snapshots.length" description="暂无进度记录" />
        </el-tab-pane>

        <el-tab-pane label="项目文档" name="docs">
          <div class="toolbar">
            <div class="filters">
              <el-select v-model="folderFilter" clearable placeholder="全部文件夹" style="width: 180px" @change="loadDocuments">
                <el-option v-for="folder in folders" :key="folder.id" :label="folder.name" :value="folder.id" />
              </el-select>
              <el-button v-if="canCreateFolder" @click="folderVisible = true">新建文件夹</el-button>
            </div>
            <div>
              <input ref="fileInput" type="file" class="hidden-input" @change="onFileSelected" />
              <el-button v-if="canUpload" type="primary" :loading="uploading" @click="fileInput?.click()">
                上传文档
              </el-button>
            </div>
          </div>

          <el-table :data="documents" stripe>
            <el-table-column prop="name" label="名称" min-width="200" />
            <el-table-column label="大小" width="110">
              <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
            </el-table-column>
            <el-table-column prop="uploaderName" label="上传人" width="110" />
            <el-table-column prop="createdAt" label="上传时间" width="170" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="auth.hasPermission('doc:download') && row.canDownload"
                  link
                  type="primary"
                  @click="download(row)"
                >下载</el-button>
                <el-button
                  v-if="row.canPrint"
                  link
                  type="success"
                  @click="openPrint(row)"
                >打印</el-button>
                <el-button v-if="canManageAcl" link type="warning" @click="openPermissions(row)">权限</el-button>
                <el-button
                  v-if="auth.hasPermission('doc:delete') && row.canDelete"
                  link
                  type="danger"
                  @click="removeDocument(row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!documents.length" description="暂无文档" />
        </el-tab-pane>

        <el-tab-pane label="项目成员" name="members">
          <div class="toolbar">
            <span class="hint">项目级角色决定文档的默认查看与下载范围，单文档可再单独授权。</span>
            <el-button v-if="canManageMembers" type="primary" @click="memberVisible = true">添加成员</el-button>
          </div>
          <el-table :data="members" stripe>
            <el-table-column prop="employeeName" label="姓名" width="120" />
            <el-table-column prop="employeeNo" label="工号" width="110" />
            <el-table-column label="项目角色" width="130">
              <template #default="{ row }">
                <el-tag size="small">{{ roleLabel(row.memberRole) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" v-if="canManageMembers">
              <template #default="{ row }">
                <el-button
                  v-if="row.memberRole !== 'OWNER'"
                  link
                  type="danger"
                  @click="removeMember(row)"
                >移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="folderVisible" title="新建文件夹" width="420px">
      <el-input v-model="folderName" placeholder="文件夹名称" />
      <template #footer>
        <el-button @click="folderVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFolder">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="memberVisible" title="添加项目成员" width="460px">
      <el-form label-width="80px">
        <el-form-item label="成员">
          <el-select v-model="memberForm.employeeId" filterable style="width: 260px">
            <el-option
              v-for="item in employees"
              :key="item.id"
              :label="`${item.name}（${item.employeeNo}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.memberRole" style="width: 180px">
            <el-option label="项目管理员" value="ADMIN" />
            <el-option label="成员" value="MEMBER" />
            <el-option label="只读" value="VIEWER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMember">添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" :title="`文档权限 · ${permissionDocument?.name || ''}`" width="640px">
      <el-table :data="documentPermissions" stripe>
        <el-table-column prop="employeeName" label="成员" width="120" />
        <el-table-column label="查看" width="80">
          <template #default="{ row }">{{ row.canView ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="下载" width="80">
          <template #default="{ row }">{{ row.canDownload ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="打印" width="80">
          <template #default="{ row }">{{ row.canPrint ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="删除" width="80">
          <template #default="{ row }">{{ row.canDelete ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
      <el-divider>新增/更新授权</el-divider>
      <el-form :inline="true">
        <el-form-item label="成员">
          <el-select v-model="permissionForm.employeeId" filterable style="width: 180px">
            <el-option
              v-for="item in members"
              :key="item.employeeId"
              :label="item.employeeName || item.employeeNo"
              :value="item.employeeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="查看"><el-switch v-model="permissionForm.canView" /></el-form-item>
        <el-form-item label="下载"><el-switch v-model="permissionForm.canDownload" /></el-form-item>
        <el-form-item label="打印"><el-switch v-model="permissionForm.canPrint" /></el-form-item>
        <el-form-item label="删除"><el-switch v-model="permissionForm.canDelete" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="savePermission">保存授权</el-button>
        </el-form-item>
      </el-form>
      <p class="hint">未单独授权的成员按项目打印策略执行；负责人、项目管理员与上传人始终可打印。</p>
    </el-dialog>

    <el-dialog v-model="printVisible" :title="`打印预览 · ${printDocumentView?.name || ''}`" width="820px">
      <template v-if="printDocumentView">
        <el-alert
          v-if="!printDocumentView.previewable"
          type="warning"
          :closable="false"
          title="该格式暂不支持文本预览；可先下载后再使用本机软件打印，打印权限与访问记录同样受控。"
        />
        <div v-else class="print-sheet">
          <div v-if="printDocumentView.watermarkEnabled" class="watermark-layer">
            <span v-for="n in 48" :key="n" class="watermark-tile">
              {{ printDocumentView.watermarkText }}
            </span>
          </div>
          <pre class="print-content">{{ printDocumentView.content }}</pre>
        </div>
      </template>
      <template #footer>
        <el-button @click="printVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="!printDocumentView?.previewable"
          @click="printCurrentDocument"
        >打印</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskVisible" :title="editingTask ? '编辑任务' : '新建任务'" width="620px">
      <el-form :model="taskForm" label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="taskForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="taskForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="taskForm.status" style="width: 160px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <span class="form-inline-label">优先级</span>
          <el-select v-model="taskForm.priority" style="width: 120px">
            <el-option v-for="item in priorityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="taskForm.assigneeEmployeeId" clearable filterable style="width: 260px" placeholder="未分配">
            <el-option
              v-for="item in members"
              :key="item.employeeId"
              :label="`${item.employeeName || item.employeeNo}（${roleLabel(item.memberRole)}）`"
              :value="item.employeeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="起止日期">
          <el-date-picker v-model="taskForm.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始" style="width: 160px" />
          <span class="form-inline-label">至</span>
          <el-date-picker v-model="taskForm.dueDate" type="date" value-format="YYYY-MM-DD" placeholder="截止" style="width: 160px" />
        </el-form-item>
        <el-form-item label="进度">
          <el-slider v-model="taskForm.progress" :min="0" :max="100" show-input />
        </el-form-item>
        <el-form-item label="参与人">
          <el-select v-model="taskForm.participantEmployeeIds" multiple filterable style="width: 100%" placeholder="可多选项目成员">
            <el-option
              v-for="item in members"
              :key="item.employeeId"
              :label="`${item.employeeName || item.employeeNo}（${item.employeeNo || ''}）`"
              :value="item.employeeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="前置任务">
          <el-select v-model="taskForm.dependsOnTaskIds" multiple filterable style="width: 100%" placeholder="任务需在前置任务完成后开始">
            <el-option
              v-for="item in dependencyOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSaving" @click="saveTask">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="commentsVisible" :title="`任务评论 · ${commentTask?.title || ''}`" width="560px">
      <div class="comment-list">
        <div v-for="item in comments" :key="item.id" class="comment-item">
          <div class="comment-head">
            <strong>{{ item.authorName || '未知成员' }}</strong>
            <span class="hint">{{ item.createdAt }}</span>
          </div>
          <div class="comment-body">{{ item.content }}</div>
        </div>
        <el-empty v-if="!comments.length" description="暂无评论" :image-size="60" />
      </div>
      <div class="comment-input">
        <el-input v-model="commentText" type="textarea" :rows="2" maxlength="2000" placeholder="输入评论内容" />
        <el-button type="primary" :loading="commentSaving" @click="submitComment">发送</el-button>
      </div>
    </el-dialog>

    <el-dialog v-model="milestoneVisible" :title="editingMilestone ? '编辑里程碑' : '新建里程碑'" width="480px">
      <el-form :model="milestoneForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="milestoneForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="milestoneForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="milestoneForm.status" style="width: 160px">
            <el-option label="计划中" value="PLANNED" />
            <el-option label="已达成" value="REACHED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="milestoneForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="milestoneVisible = false">取消</el-button>
        <el-button type="primary" :loading="milestoneSaving" @click="saveMilestone">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { pageEmployees } from '@/api/employees'
import {
  addProjectMember,
  addTaskComment,
  createMilestone,
  createProjectFolder,
  createTask,
  deleteDocument,
  deleteMilestone,
  deleteProject,
  deleteTask,
  downloadDocument,
  getProject,
  getProjectProgress,
  listDocumentPermissions,
  listDocuments,
  listMilestones,
  listProjectFolders,
  listProjectMembers,
  listProgressSnapshots,
  listTaskComments,
  listTasks,
  printDocument,
  removeProjectMember,
  setDocumentPermission,
  updateMilestone,
  updateProjectProgress,
  updateTask,
  updateTaskStatus,
  uploadDocument,
} from '@/api/projects'
import type {
  DocumentPermissionView,
  DocumentPrintView,
  DocumentView,
  EmployeeView,
  MilestoneView,
  ProjectFolder,
  ProjectMemberView,
  ProjectProgressView,
  ProjectView,
  ProgressSnapshotView,
  TaskCommentView,
  TaskRefView,
  TaskView,
} from '@/types'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const projectId = String(route.params.id)

const project = ref<ProjectView | null>(null)
const documents = ref<DocumentView[]>([])
const members = ref<ProjectMemberView[]>([])
const folders = ref<ProjectFolder[]>([])
const employees = ref<EmployeeView[]>([])
const activeTab = ref('tasks')
const folderFilter = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

const statusOptions = [
  { value: 'TODO', label: '待开始' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'BLOCKED', label: '阻塞' },
  { value: 'DONE', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]
const priorityOptions = [
  { value: 'LOW', label: '低' },
  { value: 'NORMAL', label: '普通' },
  { value: 'HIGH', label: '高' },
  { value: 'URGENT', label: '紧急' },
]

const tasks = ref<TaskView[]>([])
const tasksLoading = ref(false)
const taskFilters = reactive({ status: '', assigneeEmployeeId: '', keyword: '' })
const taskVisible = ref(false)
const taskSaving = ref(false)
const editingTask = ref<TaskView | null>(null)
const taskForm = reactive({
  title: '',
  description: '',
  status: 'TODO',
  priority: 'NORMAL',
  assigneeEmployeeId: '',
  startDate: '',
  dueDate: '',
  progress: 0,
  participantEmployeeIds: [] as string[],
  dependsOnTaskIds: [] as string[],
})

const commentsVisible = ref(false)
const commentTask = ref<TaskView | null>(null)
const comments = ref<TaskCommentView[]>([])
const commentText = ref('')
const commentSaving = ref(false)

const milestones = ref<MilestoneView[]>([])
const milestoneVisible = ref(false)
const milestoneSaving = ref(false)
const editingMilestone = ref<MilestoneView | null>(null)
const milestoneForm = reactive({
  title: '',
  description: '',
  dueDate: '',
  status: 'PLANNED',
})

const projectProgress = ref<ProjectProgressView | null>(null)
const snapshots = ref<ProgressSnapshotView[]>([])
const progressForm = reactive({ progress: 0, note: '' })
const progressSaving = ref(false)

const folderVisible = ref(false)
const folderName = ref('')
const memberVisible = ref(false)
const memberForm = reactive({ employeeId: '', memberRole: 'MEMBER' })

const permissionVisible = ref(false)
const permissionDocument = ref<DocumentView | null>(null)
const documentPermissions = ref<DocumentPermissionView[]>([])
const permissionForm = reactive({
  employeeId: '',
  canView: true,
  canDownload: false,
  canPrint: false,
  canDelete: false,
})

const printVisible = ref(false)
const printDocumentView = ref<DocumentPrintView | null>(null)

const isManager = computed(() => ['OWNER', 'ADMIN'].includes(project.value?.myRole || ''))
const canManageMembers = computed(() => isManager.value && auth.hasPermission('project:member:manage'))
const canManageAcl = computed(() => isManager.value && auth.hasPermission('doc:acl:manage'))
const canUpload = computed(
  () =>
    auth.hasPermission('doc:upload')
    && ['OWNER', 'ADMIN', 'MEMBER'].includes(project.value?.myRole || ''),
)
const canCreateFolder = computed(() => canUpload.value)
const canCreateTask = computed(() => auth.hasPermission('task:create'))
const canCreateMilestone = computed(() => auth.hasPermission('milestone:create'))
const canUpdateMilestone = computed(() => auth.hasPermission('milestone:update'))
const canDeleteMilestone = computed(() => auth.hasPermission('milestone:delete'))
const canUpdateProgress = computed(() => auth.hasPermission('project:progress:update'))
const dependencyOptions = computed(() => tasks.value.filter((item) => item.id !== editingTask.value?.id))

onMounted(async () => {
  await Promise.all([
    loadProject(),
    loadMembers(),
    loadFolders(),
    loadDocuments(),
    loadTasks(),
    loadMilestones(),
    loadProgress(),
  ])
  if (auth.hasPermission('employee:read')) {
    const page = await pageEmployees({ page: 1, size: 200 })
    employees.value = page.records
  }
})

async function loadProject() {
  project.value = await getProject(projectId)
}

async function loadMembers() {
  members.value = await listProjectMembers(projectId)
}

async function loadFolders() {
  folders.value = await listProjectFolders(projectId)
}

async function loadDocuments() {
  documents.value = await listDocuments(projectId, folderFilter.value || undefined)
}

// ------------------------------------------------------------------ tasks

async function loadTasks() {
  tasksLoading.value = true
  try {
    tasks.value = await listTasks(projectId, {
      status: taskFilters.status || undefined,
      assigneeEmployeeId: taskFilters.assigneeEmployeeId || undefined,
      keyword: taskFilters.keyword.trim() || undefined,
    })
  } finally {
    tasksLoading.value = false
  }
}

function resetTaskForm() {
  taskForm.title = ''
  taskForm.description = ''
  taskForm.status = 'TODO'
  taskForm.priority = 'NORMAL'
  taskForm.assigneeEmployeeId = ''
  taskForm.startDate = ''
  taskForm.dueDate = ''
  taskForm.progress = 0
  taskForm.participantEmployeeIds = []
  taskForm.dependsOnTaskIds = []
}

function openTaskCreate() {
  editingTask.value = null
  resetTaskForm()
  taskVisible.value = true
}

function openTaskEdit(task: TaskView) {
  editingTask.value = task
  taskForm.title = task.title
  taskForm.description = task.description ?? ''
  taskForm.status = task.status
  taskForm.priority = task.priority
  taskForm.assigneeEmployeeId = task.assigneeEmployeeId ?? ''
  taskForm.startDate = task.startDate ?? ''
  taskForm.dueDate = task.dueDate ?? ''
  taskForm.progress = task.progress
  taskForm.participantEmployeeIds = task.participants.map((item) => item.employeeId)
  taskForm.dependsOnTaskIds = task.dependencies.map((item) => item.id)
  taskVisible.value = true
}

async function saveTask() {
  if (!taskForm.title.trim()) {
    ElMessage.warning('请填写任务标题')
    return
  }
  const payload = {
    title: taskForm.title.trim(),
    description: taskForm.description || null,
    status: taskForm.status,
    priority: taskForm.priority,
    assigneeEmployeeId: taskForm.assigneeEmployeeId || null,
    startDate: taskForm.startDate || null,
    dueDate: taskForm.dueDate || null,
    progress: taskForm.progress,
    participantEmployeeIds: taskForm.participantEmployeeIds,
    dependsOnTaskIds: taskForm.dependsOnTaskIds,
  }
  taskSaving.value = true
  try {
    if (editingTask.value) {
      await updateTask(projectId, editingTask.value.id, payload)
    } else {
      await createTask(projectId, payload)
    }
    ElMessage.success('任务已保存')
    taskVisible.value = false
    await refreshAfterTaskChange()
  } finally {
    taskSaving.value = false
  }
}

async function changeTaskStatus(task: TaskView, status: string) {
  if (status === task.status) return
  await updateTaskStatus(projectId, task.id, status)
  ElMessage.success('任务状态已更新')
  await refreshAfterTaskChange()
}

async function removeTask(task: TaskView) {
  await ElMessageBox.confirm(`确定删除任务「${task.title}」吗？`, '提示', { type: 'warning' })
  await deleteTask(projectId, task.id)
  ElMessage.success('任务已删除')
  await refreshAfterTaskChange()
}

async function refreshAfterTaskChange() {
  await Promise.all([loadTasks(), loadProgress(), loadProject()])
}

async function openComments(task: TaskView) {
  commentTask.value = task
  commentText.value = ''
  comments.value = await listTaskComments(projectId, task.id)
  commentsVisible.value = true
}

async function submitComment() {
  if (!commentTask.value || !commentText.value.trim()) {
    ElMessage.warning('请填写评论内容')
    return
  }
  commentSaving.value = true
  try {
    await addTaskComment(projectId, commentTask.value.id, commentText.value.trim())
    commentText.value = ''
    comments.value = await listTaskComments(projectId, commentTask.value.id)
    await loadTasks()
  } finally {
    commentSaving.value = false
  }
}

// ------------------------------------------------------------- milestones

async function loadMilestones() {
  milestones.value = await listMilestones(projectId)
}

function resetMilestoneForm() {
  milestoneForm.title = ''
  milestoneForm.description = ''
  milestoneForm.dueDate = ''
  milestoneForm.status = 'PLANNED'
}

function openMilestoneCreate() {
  editingMilestone.value = null
  resetMilestoneForm()
  milestoneVisible.value = true
}

function openMilestoneEdit(milestone: MilestoneView) {
  editingMilestone.value = milestone
  milestoneForm.title = milestone.title
  milestoneForm.description = milestone.description ?? ''
  milestoneForm.dueDate = milestone.dueDate ?? ''
  milestoneForm.status = milestone.status
  milestoneVisible.value = true
}

async function saveMilestone() {
  if (!milestoneForm.title.trim()) {
    ElMessage.warning('请填写里程碑名称')
    return
  }
  const payload = {
    title: milestoneForm.title.trim(),
    description: milestoneForm.description || null,
    dueDate: milestoneForm.dueDate || null,
    status: milestoneForm.status,
  }
  milestoneSaving.value = true
  try {
    if (editingMilestone.value) {
      await updateMilestone(projectId, editingMilestone.value.id, payload)
    } else {
      await createMilestone(projectId, payload)
    }
    ElMessage.success('里程碑已保存')
    milestoneVisible.value = false
    await loadMilestones()
  } finally {
    milestoneSaving.value = false
  }
}

async function markMilestoneReached(milestone: MilestoneView) {
  await updateMilestone(projectId, milestone.id, {
    title: milestone.title,
    description: milestone.description,
    dueDate: milestone.dueDate,
    status: 'REACHED',
  })
  ElMessage.success('里程碑已标记达成')
  await loadMilestones()
}

async function removeMilestone(milestone: MilestoneView) {
  await ElMessageBox.confirm(`确定删除里程碑「${milestone.title}」吗？`, '提示', { type: 'warning' })
  await deleteMilestone(projectId, milestone.id)
  ElMessage.success('里程碑已删除')
  await loadMilestones()
}

// --------------------------------------------------------------- progress

async function loadProgress() {
  const [progress, history] = await Promise.all([
    getProjectProgress(projectId),
    listProgressSnapshots(projectId),
  ])
  projectProgress.value = progress
  snapshots.value = history
  progressForm.progress = progress.progress
}

async function saveProgress() {
  progressSaving.value = true
  try {
    await updateProjectProgress(projectId, progressForm.progress, progressForm.note.trim() || undefined)
    progressForm.note = ''
    ElMessage.success('项目进度已更新')
    await Promise.all([loadProgress(), loadProject()])
  } finally {
    progressSaving.value = false
  }
}

async function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    await uploadDocument(projectId, file, folderFilter.value || undefined)
    ElMessage.success('上传成功')
    await Promise.all([loadDocuments(), loadProject()])
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function download(document: DocumentView) {
  try {
    await downloadDocument(document)
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function openPrint(document: DocumentView) {
  printDocumentView.value = await printDocument(document.id)
  printVisible.value = true
}

function printCurrentDocument() {
  const view = printDocumentView.value
  if (!view?.content) return
  const win = window.open('', '_blank', 'width=900,height=720')
  if (!win) {
    ElMessage.warning('浏览器阻止了打印窗口，请允许弹出窗口后重试')
    return
  }
  const escape = (value: string) =>
    value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
  const tiles = view.watermarkEnabled && view.watermarkText
    ? `<div class="watermark">${Array.from({ length: 48 },
        () => `<span>${escape(view.watermarkText || '')}</span>`).join('')}</div>`
    : ''
  win.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${escape(view.name)}</title><style>
    body{margin:28px;font-family:-apple-system,"Segoe UI","Microsoft YaHei",sans-serif;color:#1f2937}
    .watermark{position:fixed;inset:0;display:grid;grid-template-columns:repeat(4,1fr);gap:26px;transform:rotate(-22deg) scale(1.35);pointer-events:none;z-index:0}
    .watermark span{font-size:13px;color:rgba(15,23,42,.14);white-space:nowrap}
    pre{position:relative;z-index:1;white-space:pre-wrap;word-break:break-word;font-family:"SFMono-Regular",Consolas,monospace;font-size:13px;line-height:1.65}
    @media print{.watermark{position:fixed}}
  </style></head><body>${tiles}<pre>${escape(view.content)}</pre></body></html>`)
  win.document.close()
  win.focus()
  win.setTimeout(() => win.print(), 300)
}

async function removeDocument(document: DocumentView) {
  await ElMessageBox.confirm(`确定永久删除「${document.name}」吗？`, '提示', { type: 'warning' })
  await deleteDocument(document.id)
  ElMessage.success('已删除')
  await Promise.all([loadDocuments(), loadProject()])
}

async function submitFolder() {
  if (!folderName.value.trim()) {
    ElMessage.warning('请填写文件夹名称')
    return
  }
  await createProjectFolder(projectId, folderName.value.trim())
  ElMessage.success('文件夹已创建')
  folderVisible.value = false
  folderName.value = ''
  await loadFolders()
}

async function submitMember() {
  if (!memberForm.employeeId) {
    ElMessage.warning('请选择成员')
    return
  }
  await addProjectMember(projectId, memberForm.employeeId, memberForm.memberRole)
  ElMessage.success('成员已保存')
  memberVisible.value = false
  memberForm.employeeId = ''
  await Promise.all([loadMembers(), loadProject()])
}

async function removeMember(member: ProjectMemberView) {
  await ElMessageBox.confirm(`确定移除「${member.employeeName}」吗？`, '提示', { type: 'warning' })
  await removeProjectMember(projectId, member.id)
  ElMessage.success('已移除')
  await Promise.all([loadMembers(), loadProject()])
}

async function openPermissions(document: DocumentView) {
  permissionDocument.value = document
  permissionForm.employeeId = ''
  permissionForm.canView = true
  permissionForm.canDownload = false
  permissionForm.canPrint = false
  permissionForm.canDelete = false
  documentPermissions.value = await listDocumentPermissions(document.id)
  permissionVisible.value = true
}

async function savePermission() {
  if (!permissionDocument.value || !permissionForm.employeeId) {
    ElMessage.warning('请选择成员')
    return
  }
  await setDocumentPermission(permissionDocument.value.id, {
    employeeId: permissionForm.employeeId,
    canView: permissionForm.canView,
    canDownload: permissionForm.canDownload,
    canPrint: permissionForm.canPrint,
    canDelete: permissionForm.canDelete,
  })
  ElMessage.success('授权已保存')
  documentPermissions.value = await listDocumentPermissions(permissionDocument.value.id)
  await loadDocuments()
}

async function removeProject() {
  await ElMessageBox.confirm('删除项目会永久删除全部文档且不可恢复，确定继续吗？', '危险操作', {
    type: 'error',
  })
  await deleteProject(projectId)
  ElMessage.success('项目已删除')
  router.push('/projects')
}

function taskStatusLabel(status: string) {
  return statusOptions.find((item) => item.value === status)?.label ?? status
}

function taskStatusType(status: string): TagType {
  const types: Record<string, TagType> = {
    TODO: 'info',
    IN_PROGRESS: 'primary',
    BLOCKED: 'warning',
    DONE: 'success',
    CANCELLED: 'info',
  }
  return types[status] ?? 'info'
}

function priorityLabel(priority: string) {
  return priorityOptions.find((item) => item.value === priority)?.label ?? priority
}

function priorityType(priority: string): TagType {
  const types: Record<string, TagType> = {
    LOW: 'info',
    NORMAL: 'primary',
    HIGH: 'warning',
    URGENT: 'danger',
  }
  return types[priority] ?? 'info'
}

function milestoneStatusLabel(status: string) {
  const labels: Record<string, string> = {
    PLANNED: '计划中',
    REACHED: '已达成',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}

function milestoneStatusType(status: string): TagType {
  const types: Record<string, TagType> = {
    PLANNED: 'primary',
    REACHED: 'success',
    CANCELLED: 'info',
  }
  return types[status] ?? 'info'
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
    ALL: '全部成员可下载',
    ADMINS: '仅管理员',
    NONE: '禁止下载',
  }
  return labels[policy] ?? policy
}

function formatSize(size: number | null) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.project-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.title-row h2 {
  margin: 0;
  font-size: 18px;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  color: #606266;
  font-size: 13px;
  margin-top: 6px;
}
.description {
  color: #909399;
  margin: 6px 0 0;
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
.hint {
  color: #909399;
  font-size: 12px;
}
.hidden-input {
  display: none;
}
.task-title {
  font-weight: 500;
}
.form-inline-label {
  margin: 0 10px;
  color: #606266;
}
.progress-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 32px;
  align-items: center;
}
.progress-main {
  display: flex;
  align-items: center;
  gap: 24px;
}
.progress-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #606266;
  font-size: 13px;
}
.progress-form {
  flex: 1;
  min-width: 320px;
  max-width: 480px;
}
.comment-list {
  max-height: 320px;
  overflow-y: auto;
  margin-bottom: 16px;
}
.comment-item {
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}
.comment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.comment-body {
  margin-top: 6px;
  white-space: pre-wrap;
  color: #303133;
}
.comment-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.print-sheet {
  position: relative;
  max-height: 480px;
  overflow: auto;
  padding: 18px;
  border: 1px solid #ebeef5;
  background: #fff;
  border-radius: 6px;
}
.watermark-layer {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 22px;
  transform: rotate(-22deg) scale(1.35);
  pointer-events: none;
  overflow: hidden;
}
.watermark-tile {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.13);
  white-space: nowrap;
}
.print-content {
  position: relative;
  z-index: 1;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  line-height: 1.65;
}
</style>
