<template>
  <div>
    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane label="行政组织树" name="org">
          <div class="toolbar">
            <el-text type="info">行政组织用于人员归属、权限范围与部门统计；图形视图可悬停查看上下级关系。</el-text>
            <div class="spacer" />
            <el-radio-group v-model="orgView" size="small">
              <el-radio-button value="tree">树形</el-radio-button>
              <el-radio-button value="graph">图形</el-radio-button>
            </el-radio-group>
            <el-button v-if="auth.hasPermission('org:create')" type="primary" @click="openCreate()">新增组织</el-button>
          </div>
          <TreeGraph v-if="orgView === 'graph'" v-loading="loading" :data="orgGraph" :min-width="720" />
          <el-tree
            v-else
            v-loading="loading"
            :data="orgs"
            node-key="id"
            default-expand-all
            :expand-on-click-node="false"
            :props="{ label: 'name', children: 'children' }"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span class="node-name">{{ data.name }}</span>
                <el-tag size="small" type="info">{{ data.employeeCount }} 人</el-tag>
                <el-tag v-if="data.code" size="small">{{ data.code }}</el-tag>
                <el-tag v-if="data.status !== 'ACTIVE'" size="small" type="danger">停用</el-tag>
                <span class="node-actions">
                  <el-button v-if="auth.hasPermission('org:update')" link type="primary" @click.stop="openEdit(data)">编辑</el-button>
                  <el-button
                    v-if="auth.hasPermission('org:disable') && data.status === 'ACTIVE'"
                    link
                    type="danger"
                    @click.stop="disable(data)"
                  >停用</el-button>
                </span>
              </div>
            </template>
          </el-tree>
        </el-tab-pane>

        <el-tab-pane label="业务汇报树" name="reporting">
          <div class="toolbar">
            <el-text type="info">业务汇报关系与行政组织相互独立，可交叉汇报；切换图形视图并悬停人员可查看汇报关系。</el-text>
            <div class="spacer" />
            <el-radio-group v-model="reportingView" size="small">
              <el-radio-button value="tree">树形</el-radio-button>
              <el-radio-button value="graph">图形</el-radio-button>
            </el-radio-group>
          </div>
          <el-alert
            class="tip"
            type="info"
            :closable="false"
            title="业务汇报关系与行政组织相互独立，可交叉汇报；汇报关系会影响“直属上级”字段的可见范围。"
          />
          <TreeGraph v-if="reportingView === 'graph'" v-loading="loading" :data="reportingGraph" :min-width="720" />
          <el-tree
            v-else
            v-loading="loading"
            :data="reporting"
            node-key="employeeId"
            default-expand-all
            :props="{ label: 'name', children: 'children' }"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span class="node-name">{{ data.name }}</span>
                <el-tag size="small" type="info">{{ data.employeeNo }}</el-tag>
                <span class="node-sub">{{ data.positionName || '' }}</span>
              </div>
            </template>
          </el-tree>
        </el-tab-pane>

        <el-tab-pane label="岗位" name="positions">
          <div class="toolbar">
            <el-text type="info">岗位是员工任职的字典项，可由人事维护。</el-text>
            <div class="spacer" />
            <el-button v-if="auth.hasPermission('employee:position:update')" type="primary" @click="positionVisible = true">新增岗位</el-button>
          </div>
          <el-table :data="positions" stripe>
            <el-table-column prop="code" label="编码" width="160" />
            <el-table-column prop="name" label="岗位名称" />
            <el-table-column prop="level" label="职级" width="120" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑组织' : '新增组织'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="96px">
        <el-form-item label="上级组织">
          <el-tree-select
            v-model="form.parentId"
            :data="orgs"
            :props="treeProps"
            clearable
            check-strictly
            placeholder="不选则为根组织"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="form.code" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option label="公司" value="COMPANY" />
            <el-option label="部门" value="DEPARTMENT" />
            <el-option label="小组" value="TEAM" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.leaderEmployeeId" clearable filterable placeholder="选择负责人">
            <el-option v-for="e in employees" :key="e.id" :label="`${e.name}（${e.employeeNo}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="positionVisible" title="新增岗位" width="480px" destroy-on-close>
      <el-form :model="positionForm" label-width="88px">
        <el-form-item label="编码" required>
          <el-input v-model="positionForm.code" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="positionForm.name" />
        </el-form-item>
        <el-form-item label="职级">
          <el-input v-model="positionForm.level" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="positionVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPosition">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import TreeGraph, { type GraphNode } from '@/components/TreeGraph.vue'
import {
  createOrg,
  createPosition,
  disableOrg,
  listPositions,
  orgTree as loadOrgTree,
  reportingTree as loadReportingTree,
  updateOrg,
  type OrgPayload,
} from '@/api/org'
import { pageEmployees } from '@/api/employees'
import type { EmployeeView, OrgNode, Position, ReportingNode } from '@/types'

const auth = useAuthStore()
const tab = ref('org')
const loading = ref(false)
const saving = ref(false)
const orgs = ref<OrgNode[]>([])
const reporting = ref<ReportingNode[]>([])
const positions = ref<Position[]>([])
const employees = ref<EmployeeView[]>([])
const treeProps = { label: 'name', children: 'children', value: 'id' }
const orgView = ref<'tree' | 'graph'>('tree')
const reportingView = ref<'tree' | 'graph'>('tree')

const orgTypeLabels: Record<string, string> = {
  COMPANY: '公司',
  DEPARTMENT: '部门',
  TEAM: '小组',
}

function orgTypeLabel(type: string) {
  return orgTypeLabels[type] ?? type
}

function leaderName(employeeId: string | null) {
  if (!employeeId) return ''
  return employees.value.find((item) => item.id === employeeId)?.name ?? ''
}

const orgGraph = computed<GraphNode[]>(() => {
  function build(node: OrgNode, parent?: OrgNode): GraphNode {
    const leader = leaderName(node.leaderEmployeeId)
    const head = parent
      ? `上级组织：${parent.name}。${node.name} 是 ${parent.name} 的下级${orgTypeLabel(node.type)}。`
      : `顶级组织：${node.name}，没有上级组织。`
    const leaderText = leader ? `负责人：${leader}。` : ''
    return {
      id: node.id,
      label: node.name,
      sublabel: `${orgTypeLabel(node.type)}${node.code ? ` · ${node.code}` : ''}`,
      badge: `${node.employeeCount}人`,
      tooltip: `${head}${leaderText}当前人数：${node.employeeCount} 人。`,
      children: node.children.map((child) => build(child, node)),
    }
  }
  return orgs.value.map((node) => build(node))
})

const reportingGraph = computed<GraphNode[]>(() => {
  function build(node: ReportingNode, parent?: ReportingNode): GraphNode {
    const position = node.positionName ? `（${node.positionName}）` : ''
    return {
      id: node.employeeId,
      label: node.name,
      sublabel: node.positionName || node.employeeNo,
      badge: node.employeeNo,
      tooltip: parent
        ? `${node.name}${position} 的直属上级是 ${parent.name}；${parent.name} 直接管理 ${node.name}。`
        : `${node.name}${position} 是当前汇报链的顶层，系统中未设置直属上级。`,
      children: node.children.map((child) => build(child, node)),
    }
  }
  return reporting.value.map((node) => build(node))
})

const dialogVisible = ref(false)
const editingId = ref('')
const form = reactive({
  parentId: '' as string,
  name: '',
  code: '',
  type: 'DEPARTMENT',
  leaderEmployeeId: '' as string,
  sort: 0,
})

const positionVisible = ref(false)
const positionForm = reactive({ code: '', name: '', level: '' })

async function load() {
  loading.value = true
  try {
    const [orgData, reportData, posData] = await Promise.all([
      loadOrgTree(),
      loadReportingTree(),
      listPositions(),
    ])
    orgs.value = orgData
    reporting.value = reportData
    positions.value = posData
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = ''
  Object.assign(form, { parentId: '', name: '', code: '', type: 'DEPARTMENT', leaderEmployeeId: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(data: OrgNode) {
  editingId.value = data.id
  Object.assign(form, {
    parentId: data.parentId ?? '',
    name: data.name,
    code: data.code ?? '',
    type: data.type,
    leaderEmployeeId: data.leaderEmployeeId ?? '',
    sort: data.sort ?? 0,
  })
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写组织名称')
    return
  }
  const payload: OrgPayload = {
    parentId: form.parentId || null,
    name: form.name.trim(),
    code: form.code || undefined,
    type: form.type,
    leaderEmployeeId: form.leaderEmployeeId || null,
    sort: form.sort,
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateOrg(editingId.value, payload)
      ElMessage.success('组织已更新')
    } else {
      await createOrg(payload)
      ElMessage.success('组织已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function disable(data: OrgNode) {
  await ElMessageBox.confirm(`确定停用组织“${data.name}”吗？停用前需先处理下级组织与在职员工。`, '提示', { type: 'warning' })
  await disableOrg(data.id)
  ElMessage.success('组织已停用')
  await load()
}

async function submitPosition() {
  if (!positionForm.code.trim() || !positionForm.name.trim()) {
    ElMessage.warning('请填写岗位编码与名称')
    return
  }
  await createPosition({
    code: positionForm.code.trim(),
    name: positionForm.name.trim(),
    level: positionForm.level || undefined,
  })
  ElMessage.success('岗位已创建')
  positionVisible.value = false
  Object.assign(positionForm, { code: '', name: '', level: '' })
  await load()
}

async function loadEmployees() {
  const result = await pageEmployees({ page: 1, size: 200, status: 'ACTIVE' })
  employees.value = result.records
}

onMounted(() => {
  load()
  loadEmployees()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.spacer {
  flex: 1;
}
.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 4px 0;
}
.node-name {
  font-weight: 500;
}
.node-sub {
  color: #909399;
  font-size: 12px;
}
.node-actions {
  margin-left: auto;
  padding-right: 12px;
}
.tip {
  margin-bottom: 12px;
}
</style>
