<template>
  <div class="bubble-graph">
    <div class="bubble-bar">
      <div class="legend">
        <span class="legend-item"><i class="legend-bubble" />气泡代表员工（颜色区分部门）</span>
        <span class="legend-item"><i class="legend-line" />连线代表直属汇报</span>
        <span class="legend-item"><i class="legend-line cross" />虚线代表跨部门汇报</span>
      </div>
      <span class="legend-tip">
        {{ employees.length }} 人 · {{ layout.edges.length }} 条汇报关系 · 悬停高亮，点击查看档案
      </span>
    </div>

    <div v-if="employees.length === 0" class="bubble-empty">
      <el-empty description="当前筛选下没有员工" :image-size="70" />
    </div>

    <div v-else ref="wrapper" class="bubble-viewport" @mouseleave="clearHover">
      <svg
        class="bubble-svg"
        :width="layout.width"
        :height="layout.height"
        :viewBox="`0 0 ${layout.width} ${layout.height}`"
      >
        <g class="bubble-edges">
          <path
            v-for="edge in layout.edges"
            :key="edge.id"
            :d="edge.d"
            class="bubble-edge"
            :class="{
              active: activeEdgeIds.has(edge.id),
              cross: edge.cross,
              dimmed: activeId !== null && !activeEdgeIds.has(edge.id),
            }"
          />
        </g>

        <g
          v-for="node in layout.nodes"
          :key="node.id"
          class="bubble-node"
          :class="{
            hovered: hoveredId === node.id,
            related: activeId !== null && relatedIds.has(node.id),
            dimmed: activeId !== null && !relatedIds.has(node.id),
            inactive: node.employee.status !== 'ACTIVE',
          }"
          :transform="`translate(${node.x}, ${node.y})`"
          role="button"
          tabindex="0"
          :aria-label="`${node.employee.name}，点击查看员工档案`"
          @mouseenter="onNodeEnter(node, $event)"
          @mousemove="onNodeMove"
          @mouseleave="clearHover"
          @focus="hoveredId = node.id"
          @blur="clearHover"
          @click="select(node)"
          @keydown.enter.prevent="select(node)"
          @keydown.space.prevent="select(node)"
        >
          <circle
            class="bubble-halo"
            :r="radius + 7"
            :stroke="paletteFor(node.employee).stroke"
          />
          <circle
            class="bubble-circle"
            :r="radius"
            :fill="paletteFor(node.employee).fill"
            :stroke="paletteFor(node.employee).stroke"
          />
          <text
            class="bubble-initial"
            text-anchor="middle"
            y="9"
            :fill="paletteFor(node.employee).ink"
          >{{ initial(node.employee.name) }}</text>
          <text class="bubble-name" text-anchor="middle" :y="radius + 23">
            {{ truncate(node.employee.name, 8) }}
          </text>
          <text class="bubble-no" text-anchor="middle" :y="radius + 39">
            {{ node.employee.employeeNo }}
          </text>
          <circle
            v-if="node.employee.status !== 'ACTIVE'"
            class="bubble-off-dot"
            :cx="radius - 5"
            :cy="-radius + 5"
            r="6"
          />
        </g>
      </svg>

      <div v-if="hoveredNode" class="bubble-tooltip" :style="tooltipStyle">
        <div class="tip-title">
          {{ hoveredNode.employee.name }}
          <span class="tip-no">{{ hoveredNode.employee.employeeNo }}</span>
        </div>
        <div class="tip-meta">
          {{ hoveredNode.employee.orgName || '未分配部门' }}
          <template v-if="hoveredNode.employee.positionName">
            · {{ hoveredNode.employee.positionName }}
          </template>
        </div>
        <div class="tip-relation">{{ managerText(hoveredNode.employee) }}</div>
        <div class="tip-hint">点击气泡查看员工档案</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type { EmployeeView } from '@/types'

interface BubbleEmployee {
  id: string
  name: string
  employeeNo: string
  status: string
  orgId: string | null
  orgName: string | null
  positionName: string | null
  managerId: string | null
  managerName: string | null
}

interface BubbleNode {
  id: string
  employee: BubbleEmployee
  x: number
  y: number
  depth: number
  parentId: string | null
}

interface BubbleEdge {
  id: string
  from: string
  to: string
  d: string
  cross: boolean
}

const props = withDefaults(defineProps<{
  employees: EmployeeView[]
  minWidth?: number
}>(), {
  minWidth: 720,
})

const emit = defineEmits<{ select: [employeeId: string] }>()

const radius = 38
const columnGap = 132
const rowGap = 150
const margin = 42

const wrapper = ref<HTMLElement | null>(null)
const hoveredId = ref<string | null>(null)
const tooltip = reactive({ x: 0, y: 0 })

const paletteList = [
  { fill: '#eef6ff', stroke: '#7cb6f8', ink: '#2f6fb0' },
  { fill: '#eefaf1', stroke: '#8fd6a8', ink: '#2f7d4c' },
  { fill: '#fff7e8', stroke: '#efc269', ink: '#95670f' },
  { fill: '#fff0f0', stroke: '#efa4a4', ink: '#a94444' },
  { fill: '#f5f0ff', stroke: '#b99af0', ink: '#6841a8' },
  { fill: '#eefafb', stroke: '#87cbd2', ink: '#2a737b' },
  { fill: '#f7f3ec', stroke: '#cbb68f', ink: '#7c6234' },
  { fill: '#f2f4f8', stroke: '#aeb9c9', ink: '#4e5d73' },
]

function paletteFor(employee: BubbleEmployee) {
  const key = employee.orgName || employee.orgId || '未分配部门'
  let hash = 0
  for (let index = 0; index < key.length; index += 1) {
    hash = (hash * 31 + key.charCodeAt(index)) >>> 0
  }
  return paletteList[hash % paletteList.length]
}

function initial(name: string) {
  const trimmed = (name || '?').trim()
  if (!trimmed) return '?'
  return trimmed.slice(0, 1).toUpperCase()
}

function truncate(text: string, max: number) {
  const value = text || ''
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function managerText(employee: BubbleEmployee) {
  if (!employee.managerId) return '未设置直属上级'
  if (!employee.managerName) return '直属上级已按字段权限隐藏'
  return `直属上级：${employee.managerName}`
}

const layout = computed(() => {
  const employees = props.employees as unknown as BubbleEmployee[]
  const byId = new Map(employees.map((employee) => [employee.id, employee]))
  const children = new Map<string, string[]>()
  const roots: string[] = []

  for (const employee of employees) {
    const managerId = employee.managerId
    if (managerId && managerId !== employee.id && byId.has(managerId)) {
      const list = children.get(managerId) ?? []
      list.push(employee.id)
      children.set(managerId, list)
    } else {
      roots.push(employee.id)
    }
  }

  const treeChildren = new Map<string, string[]>()
  const visited = new Set<string>()
  const treeRoots: string[] = []

  function collect(id: string): number {
    if (visited.has(id)) return 0
    visited.add(id)
    const kids = (children.get(id) ?? []).filter((childId) => !visited.has(childId))
    treeChildren.set(id, kids)
    if (kids.length === 0) return 1
    return kids.reduce((total, childId) => total + collect(childId), 0)
  }

  for (const rootId of [...roots, ...employees.map((employee) => employee.id)]) {
    if (visited.has(rootId)) continue
    treeRoots.push(rootId)
    collect(rootId)
  }

  const nodes: BubbleNode[] = []
  let leafIndex = 0
  let maxDepth = 0

  function place(id: string, depth: number, parentId: string | null): number {
    maxDepth = Math.max(maxDepth, depth)
    const kids = treeChildren.get(id) ?? []
    let x: number
    if (kids.length === 0) {
      x = margin + radius + leafIndex * columnGap
      leafIndex += 1
    } else {
      const childXs = kids.map((childId) => place(childId, depth + 1, id))
      x = (childXs[0] + childXs[childXs.length - 1]) / 2
    }
    nodes.push({
      id,
      employee: byId.get(id)!,
      x,
      y: margin + radius + depth * rowGap,
      depth,
      parentId,
    })
    return x
  }

  for (const rootId of treeRoots) {
    place(rootId, 0, null)
  }

  const nodeById = new Map(nodes.map((node) => [node.id, node]))
  const rawEdges: Omit<BubbleEdge, 'd'>[] = []
  for (const node of nodes) {
    const parent = node.parentId ? nodeById.get(node.parentId) : null
    if (!parent) continue
    rawEdges.push({
      id: `${parent.id}->${node.id}`,
      from: parent.id,
      to: node.id,
      cross: Boolean(parent.employee.orgId && node.employee.orgId
        && parent.employee.orgId !== node.employee.orgId),
    })
  }

  let minX = Infinity
  let maxX = -Infinity
  for (const node of nodes) {
    minX = Math.min(minX, node.x)
    maxX = Math.max(maxX, node.x)
  }
  const contentWidth = nodes.length ? maxX - minX + radius * 2 : 0
  const width = Math.max(props.minWidth, contentWidth + margin * 2)
  const shift = nodes.length ? (width - contentWidth) / 2 - (minX - radius) : margin
  for (const node of nodes) {
    node.x += shift
  }

  const edges: BubbleEdge[] = rawEdges.map((edge) => {
    const parent = nodeById.get(edge.from)!
    const child = nodeById.get(edge.to)!
    const startY = parent.y + radius
    const endY = child.y - radius
    const curve = Math.max(34, (endY - startY) / 2)
    return {
      ...edge,
      d: `M ${parent.x} ${startY} C ${parent.x} ${startY + curve}, `
        + `${child.x} ${endY - curve}, ${child.x} ${endY}`,
    }
  })

  const height = margin * 2 + (maxDepth + 1) * rowGap
  return { nodes: nodes.sort((left, right) => left.depth - right.depth),
    edges, width, height, byId: nodeById }
})

const activeId = computed(() => hoveredId.value)

const relatedIds = computed(() => {
  const result = new Set<string>()
  if (!activeId.value) return result
  result.add(activeId.value)
  for (const edge of layout.value.edges) {
    if (edge.from === activeId.value) result.add(edge.to)
    if (edge.to === activeId.value) result.add(edge.from)
  }
  return result
})

const activeEdgeIds = computed(() => {
  const result = new Set<string>()
  if (!activeId.value) return result
  for (const edge of layout.value.edges) {
    if (edge.from === activeId.value || edge.to === activeId.value) {
      result.add(edge.id)
    }
  }
  return result
})

const hoveredNode = computed(() =>
  hoveredId.value ? layout.value.byId.get(hoveredId.value) ?? null : null,
)

const tooltipStyle = computed(() => ({
  left: `${tooltip.x}px`,
  top: `${tooltip.y}px`,
}))

function onNodeEnter(node: BubbleNode, event: MouseEvent) {
  hoveredId.value = node.id
  onNodeMove(event)
}

function onNodeMove(event: MouseEvent) {
  const element = wrapper.value
  const rect = element?.getBoundingClientRect()
  if (!rect || !element) return
  const tipWidth = 248
  const tipHeight = 116
  const scrollLeft = element.scrollLeft
  const scrollTop = element.scrollTop
  const localX = event.clientX - rect.left + scrollLeft
  const localY = event.clientY - rect.top + scrollTop
  let x = localX + 18
  let y = localY + 18
  if (x + tipWidth > scrollLeft + rect.width) {
    x = localX - tipWidth - 18
  }
  if (x < scrollLeft + 8) x = scrollLeft + 8
  if (y + tipHeight > scrollTop + rect.height) {
    y = localY - tipHeight - 18
  }
  if (y < scrollTop + 8) y = scrollTop + 8
  tooltip.x = x
  tooltip.y = y
}

function clearHover() {
  hoveredId.value = null
}

function select(node: BubbleNode) {
  emit('select', node.id)
}
</script>

<style scoped>
.bubble-graph {
  border: 1px solid #ebeef5;
  border-radius: 12px;
  background: #fbfcfe;
  overflow: hidden;
}

.bubble-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 10px 14px;
  border-bottom: 1px solid #ebeef5;
  background: #fff;
}

.legend {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  color: #606266;
  font-size: 12px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-bubble {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #7cb6f8;
  background: #eef6ff;
}

.legend-line {
  width: 24px;
  height: 0;
  border-top: 2px solid #b9c5d6;
}

.legend-line.cross {
  border-top-style: dashed;
}

.legend-tip {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}

.bubble-empty {
  padding: 32px 0;
}

.bubble-viewport {
  position: relative;
  width: 100%;
  max-height: 72vh;
  overflow: auto;
  background:
    radial-gradient(circle, #e8edf5 1px, transparent 1px) 0 0 / 22px 22px,
    #fbfcfe;
}

.bubble-svg {
  display: block;
  margin: 0 auto;
}

.bubble-edge {
  fill: none;
  stroke: #c8d2e0;
  stroke-width: 1.6;
  transition: stroke 0.15s ease, opacity 0.15s ease;
}

.bubble-edge.cross {
  stroke-dasharray: 6 5;
}

.bubble-edge.active {
  stroke: #409eff;
  stroke-width: 2.6;
}

.bubble-edge.dimmed {
  opacity: 0.24;
}

.bubble-node {
  cursor: pointer;
  outline: none;
  transition: opacity 0.15s ease;
}

.bubble-node.dimmed {
  opacity: 0.26;
}

.bubble-node.inactive .bubble-circle {
  stroke-dasharray: 5 4;
  opacity: 0.72;
}

.bubble-halo {
  fill: none;
  stroke-width: 2;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.bubble-node.hovered .bubble-halo,
.bubble-node.related .bubble-halo,
.bubble-node:focus-visible .bubble-halo {
  opacity: 0.5;
}

.bubble-circle {
  stroke-width: 2;
  filter: drop-shadow(0 4px 8px rgba(31, 45, 61, 0.1));
  transition: stroke-width 0.15s ease, filter 0.15s ease;
}

.bubble-node.hovered .bubble-circle,
.bubble-node:focus-visible .bubble-circle {
  stroke-width: 3;
  filter: drop-shadow(0 6px 12px rgba(64, 158, 255, 0.25));
}

.bubble-initial {
  font-size: 27px;
  font-weight: 700;
  pointer-events: none;
}

.bubble-name {
  fill: #303133;
  font-size: 13px;
  font-weight: 600;
  pointer-events: none;
}

.bubble-no {
  fill: #909399;
  font-size: 10px;
  pointer-events: none;
}

.bubble-off-dot {
  fill: #c0c4cc;
  stroke: #fff;
  stroke-width: 2;
}

.bubble-tooltip {
  position: absolute;
  width: 248px;
  box-sizing: border-box;
  padding: 10px 12px;
  border-radius: 9px;
  background: rgba(31, 45, 61, 0.95);
  color: #fff;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.2);
  pointer-events: none;
  z-index: 20;
}

.tip-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}

.tip-no {
  color: #b8c4d4;
  font-size: 11px;
  font-weight: 400;
}

.tip-meta {
  margin-top: 3px;
  color: #d6dee9;
  font-size: 12px;
}

.tip-relation {
  margin-top: 5px;
  color: #e4e9f0;
  font-size: 12px;
}

.tip-hint {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
  color: #9ecbff;
  font-size: 11px;
}
</style>
