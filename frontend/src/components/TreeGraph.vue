<template>
  <div ref="wrapper" class="graph-wrapper" @mouseleave="clearHover">
    <div v-if="layout.nodes.length === 0" class="graph-empty">
      <el-empty description="暂无可展示的节点" :image-size="70" />
    </div>
    <svg
      v-else
      class="graph-svg"
      :width="layout.width"
      :height="layout.height"
      :viewBox="`0 0 ${layout.width} ${layout.height}`"
    >
      <g class="edges">
        <path
          v-for="edge in layout.edges"
          :key="edge.id"
          :d="edge.d"
          class="edge"
          :class="{ active: activeEdgeIds.has(edge.id) }"
        />
      </g>

      <g
        v-for="node in layout.nodes"
        :key="node.id"
        class="node"
        :class="{ hovered: hoveredId === node.id }"
        :transform="`translate(${node.x}, ${node.y})`"
        @mouseenter="onNodeEnter(node, $event)"
        @mousemove="onNodeMove"
        @mouseleave="clearHover"
      >
        <rect
          class="node-box"
          :width="nodeWidth"
          :height="nodeHeight"
          :x="-nodeWidth / 2"
          :y="-nodeHeight / 2"
          rx="10"
          ry="10"
        />
        <text class="node-label" text-anchor="middle" :y="node.sublabel ? -5 : 5">
          {{ truncate(node.label, 13) }}
        </text>
        <text v-if="node.sublabel" class="node-sub" text-anchor="middle" y="15">
          {{ truncate(node.sublabel, 20) }}
        </text>
        <g v-if="node.badge" :transform="`translate(${nodeWidth / 2 - 8}, ${-nodeHeight / 2 + 6})`">
          <rect
            class="node-badge-bg"
            :x="-badgeWidth(node.badge)"
            y="0"
            :width="badgeWidth(node.badge)"
            height="18"
            rx="9"
          />
          <text class="node-badge-text" text-anchor="middle" :x="-badgeWidth(node.badge) / 2" y="13">
            {{ node.badge }}
          </text>
        </g>
      </g>
    </svg>

    <div v-if="hoveredNode" class="graph-tooltip" :style="tooltipStyle">
      <div class="tip-title">{{ hoveredNode.label }}</div>
      <div v-if="hoveredNode.sublabel" class="tip-sub">{{ hoveredNode.sublabel }}</div>
      <div class="tip-body">{{ hoveredNode.tooltip || '暂无关系说明' }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'

export interface GraphNode {
  id: string
  label: string
  sublabel?: string
  badge?: string
  tooltip?: string
  children?: GraphNode[]
}

interface LayoutNode extends GraphNode {
  x: number
  y: number
  depth: number
  parentId?: string
}

interface LayoutEdge {
  id: string
  d: string
  from: string
  to: string
}

const props = withDefaults(defineProps<{ data: GraphNode[]; minWidth?: number }>(), {
  minWidth: 600,
})

const nodeWidth = 172
const nodeHeight = 56
const xGap = 34
const yGap = 54
const margin = 28

const wrapper = ref<HTMLElement | null>(null)
const hoveredId = ref<string | null>(null)
const tooltip = reactive({ x: 0, y: 0 })

const layout = computed(() => {
  const nodes: LayoutNode[] = []
  let leafIndex = 0
  let maxDepth = 0

  function walk(node: GraphNode, depth: number, parentId?: string): number {
    maxDepth = Math.max(maxDepth, depth)
    const children = node.children ?? []
    let centerX: number
    if (children.length === 0) {
      centerX = margin + nodeWidth / 2 + leafIndex * (nodeWidth + xGap)
      leafIndex += 1
    } else {
      const childXs = children.map((child) => walk(child, depth + 1, node.id))
      centerX = (childXs[0] + childXs[childXs.length - 1]) / 2
    }
    nodes.push({
      ...node,
      x: centerX,
      y: margin + nodeHeight / 2 + depth * (nodeHeight + yGap),
      depth,
      parentId,
    })
    return centerX
  }

  for (const root of props.data) {
    walk(root, 0)
  }

  const byId = new Map(nodes.map((node) => [node.id, node]))
  const edges: LayoutEdge[] = []
  for (const node of nodes) {
    if (!node.parentId) continue
    const parent = byId.get(node.parentId)
    if (!parent) continue
    const startY = parent.y + nodeHeight / 2
    const endY = node.y - nodeHeight / 2
    const midY = startY + (endY - startY) / 2
    edges.push({
      id: `${parent.id}->${node.id}`,
      from: parent.id,
      to: node.id,
      d: `M ${parent.x} ${startY} V ${midY} H ${node.x} V ${endY}`,
    })
  }

  const width = Math.max(
    props.minWidth,
    margin * 2 + Math.max(leafIndex, 1) * (nodeWidth + xGap) - xGap,
  )
  const height = margin * 2 + (maxDepth + 1) * (nodeHeight + yGap) - yGap

  return { nodes, edges, width, height, byId }
})

const hoveredNode = computed(() =>
  hoveredId.value ? layout.value.byId.get(hoveredId.value) ?? null : null,
)

const activeEdgeIds = computed(() => {
  const result = new Set<string>()
  if (!hoveredId.value) return result
  for (const edge of layout.value.edges) {
    if (edge.from === hoveredId.value || edge.to === hoveredId.value) {
      result.add(edge.id)
    }
  }
  return result
})

const tooltipStyle = computed(() => ({
  left: `${tooltip.x}px`,
  top: `${tooltip.y}px`,
}))

function badgeWidth(text: string) {
  return Math.max(26, text.length * 11 + 12)
}

function truncate(text: string, max: number) {
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function onNodeEnter(node: LayoutNode, event: MouseEvent) {
  hoveredId.value = node.id
  onNodeMove(event)
}

function onNodeMove(event: MouseEvent) {
  const el = wrapper.value
  const rect = el?.getBoundingClientRect()
  if (!rect || !el) return
  const tipWidth = 280
  const tipHeight = 96
  const scrollLeft = el.scrollLeft
  const scrollTop = el.scrollTop
  const localX = event.clientX - rect.left + scrollLeft
  const localY = event.clientY - rect.top + scrollTop
  let x = localX + 16
  let y = localY + 16
  if (x + tipWidth > scrollLeft + rect.width) {
    x = localX - tipWidth - 16
  }
  if (x < scrollLeft + 8) x = scrollLeft + 8
  if (y + tipHeight > scrollTop + rect.height) {
    y = localY - tipHeight - 16
  }
  if (y < scrollTop + 8) y = scrollTop + 8
  tooltip.x = x
  tooltip.y = y
}

function clearHover() {
  hoveredId.value = null
}
</script>

<style scoped>
.graph-wrapper {
  position: relative;
  width: 100%;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  background:
    radial-gradient(circle, #e8edf5 1px, transparent 1px) 0 0 / 22px 22px,
    #fbfcfe;
  max-height: 70vh;
}
.graph-empty {
  padding: 30px 0;
}
.graph-svg {
  display: block;
}
.edge {
  fill: none;
  stroke: #c8d0dc;
  stroke-width: 1.6;
  transition: stroke 0.15s ease;
}
.edge.active {
  stroke: #409eff;
  stroke-width: 2.4;
}
.node {
  cursor: default;
}
.node-box {
  fill: #fff;
  stroke: #d5dce6;
  stroke-width: 1.2;
  filter: drop-shadow(0 2px 4px rgba(31, 45, 61, 0.06));
  transition: stroke 0.15s ease, fill 0.15s ease;
}
.node.hovered .node-box {
  stroke: #409eff;
  fill: #ecf5ff;
}
.node-label {
  fill: #303133;
  font-size: 13px;
  font-weight: 600;
}
.node-sub {
  fill: #909399;
  font-size: 11px;
}
.node-badge-bg {
  fill: #eef3fa;
  stroke: #d5dce6;
  stroke-width: 0.8;
}
.node-badge-text {
  fill: #606266;
  font-size: 10px;
}
.graph-tooltip {
  position: absolute;
  width: 280px;
  box-sizing: border-box;
  background: rgba(31, 45, 61, 0.94);
  color: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  pointer-events: none;
  z-index: 20;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.2);
}
.tip-title {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 2px;
}
.tip-sub {
  color: #b8c4d4;
  font-size: 12px;
  margin-bottom: 4px;
}
.tip-body {
  font-size: 12px;
  line-height: 1.6;
  color: #e4e9f0;
}
</style>
