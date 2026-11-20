<template>
  <div class="search-page">
    <el-card shadow="never" class="search-card">
      <div class="search-hero">
        <div class="hero-title">全局搜索</div>
        <div class="hero-sub">员工、项目、任务、文档和公司制度统一检索；结果只包含你有权限查看的内容。</div>
        <div class="search-bar">
          <el-input
            ref="inputRef"
            v-model="keyword"
            size="large"
            clearable
            placeholder="输入姓名、工号、项目名称、任务或文档关键词"
            @keyup.enter="submitSearch"
          >
            <template #prefix>🔍</template>
          </el-input>
          <el-button type="primary" size="large" :loading="loading" @click="submitSearch">搜索</el-button>
        </div>
      </div>
    </el-card>

    <template v-if="result">
      <el-card shadow="never" class="search-card">
        <div class="result-head">
          <div class="result-summary">
            找到 <strong>{{ result.total }}</strong> 条与
            <strong>“{{ result.keyword }}”</strong> 相关的结果
          </div>
          <el-radio-group v-model="activeType" @change="changeType">
            <el-radio-button value="ALL">全部 {{ result.total }}</el-radio-button>
            <el-radio-button v-for="type in typeOrder" :key="type" :value="type">
              {{ typeMeta[type].label }} {{ result.counts[type] || 0 }}
            </el-radio-button>
          </el-radio-group>
        </div>

        <div v-loading="loading" class="result-list">
          <div
            v-for="item in result.items"
            :key="item.type + '-' + item.id"
            class="result-item"
            @click="open(item)"
          >
            <div class="result-icon">{{ typeMeta[item.type]?.icon || '🔎' }}</div>
            <div class="result-body">
              <div class="result-title-line">
                <el-tag size="small" :type="typeMeta[item.type]?.tag || 'info'" effect="plain">
                  {{ typeMeta[item.type]?.label || item.type }}
                </el-tag>
                <span class="result-title" v-html="item.highlightedTitle || escapeText(item.title)"></span>
              </div>
              <div v-if="item.subtitle" class="result-subtitle">{{ item.subtitle }}</div>
              <div
                v-if="item.highlightedSnippet || item.snippet"
                class="result-snippet"
                v-html="item.highlightedSnippet || escapeText(item.snippet)"
              ></div>
            </div>
            <div class="result-action">打开 →</div>
          </div>
          <el-empty
            v-if="!loading && result.items.length === 0"
            description="没有找到匹配内容，换个关键词试试"
            :image-size="72"
          />
        </div>

        <el-pagination
          v-if="result.total > result.size"
          v-model:current-page="page"
          :page-size="size"
          :total="result.total"
          layout="total, prev, pager, next"
          class="pagination"
          @current-change="load"
        />
      </el-card>
    </template>

    <el-card v-else shadow="never" class="search-card tips-card">
      <div class="tips-title">可以这样搜</div>
      <div class="tips-grid">
        <div class="tip">
          <span class="tip-icon">👤</span>
          <div>
            <div class="tip-name">找同事</div>
            <div class="tip-desc">输入姓名或工号，例如“李四”“E004”。</div>
          </div>
        </div>
        <div class="tip">
          <span class="tip-icon">📁</span>
          <div>
            <div class="tip-name">找项目</div>
            <div class="tip-desc">输入项目名称、编码或描述关键词。</div>
          </div>
        </div>
        <div class="tip">
          <span class="tip-icon">📝</span>
          <div>
            <div class="tip-name">找任务</div>
            <div class="tip-desc">你参与项目中的任务标题与描述。</div>
          </div>
        </div>
        <div class="tip">
          <span class="tip-icon">📄</span>
          <div>
            <div class="tip-name">找文档</div>
            <div class="tip-desc">按文件名、原名或正文内容检索。</div>
          </div>
        </div>
        <div class="tip">
          <span class="tip-icon">📘</span>
          <div>
            <div class="tip-name">找制度</div>
            <div class="tip-desc">搜索公司规章制度标题或正文，也可以直接问 AI。</div>
          </div>
        </div>
      </div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="搜索不会扩大权限：非成员项目、无权限文档和不可见字段不会出现在结果中。"
        class="tips-alert"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { globalSearch } from '@/api/search'
import type { SearchItemView, SearchResponseView } from '@/types'

const route = useRoute()
const router = useRouter()
const inputRef = ref<{ focus: () => void } | null>(null)
const keyword = ref('')
const activeType = ref('ALL')
const loading = ref(false)
const result = ref<SearchResponseView | null>(null)
const page = ref(1)
const size = 20

const typeOrder = ['EMPLOYEE', 'PROJECT', 'TASK', 'DOCUMENT', 'POLICY'] as const
const typeMeta: Record<
  string,
  { label: string; icon: string; tag: 'success' | 'primary' | 'warning' | 'info' }
> = {
  EMPLOYEE: { label: '员工', icon: '👤', tag: 'success' },
  PROJECT: { label: '项目', icon: '📁', tag: 'primary' },
  TASK: { label: '任务', icon: '📝', tag: 'warning' },
  DOCUMENT: { label: '文档', icon: '📄', tag: 'info' },
  POLICY: { label: '制度', icon: '📘', tag: 'info' },
}

onMounted(async () => {
  const q = route.query.q
  if (typeof q === 'string' && q.trim()) {
    keyword.value = q.trim()
    await load(true)
  } else {
    await nextTick()
    inputRef.value?.focus()
  }
})

watch(
  () => route.query.q,
  async (value) => {
    const q = typeof value === 'string' ? value.trim() : ''
    if (!q) return
    keyword.value = q
    page.value = 1
    activeType.value = 'ALL'
    await load(true)
  },
)

function submitSearch() {
  const text = keyword.value.trim()
  if (!text) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  page.value = 1
  activeType.value = 'ALL'
  if (route.query.q !== text) {
    router.replace({ path: '/search', query: { q: text } })
  } else {
    load(true)
  }
}

function changeType() {
  page.value = 1
  load(true)
}

async function load(reset = false) {
  const text = keyword.value.trim()
  if (!text) return
  loading.value = true
  try {
    const data = await globalSearch({
      keyword: text,
      types: activeType.value === 'ALL' ? undefined : activeType.value,
      page: page.value,
      size,
    })
    result.value = data
    if (reset) {
      // 切换类型后保留后端返回的总数与分项计数
      page.value = data.page
    }
  } finally {
    loading.value = false
  }
}

function open(item: SearchItemView) {
  if (item.link) router.push(item.link)
}

function escapeText(value: string | null) {
  if (!value) return ''
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.search-card {
  border-radius: 10px;
}
.search-hero {
  padding: 10px 8px 4px;
}
.hero-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}
.hero-sub {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
.search-bar {
  margin-top: 18px;
  display: flex;
  gap: 10px;
  max-width: 820px;
}
.search-bar .el-input {
  flex: 1;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.result-summary {
  color: #606266;
  font-size: 13px;
}
.result-summary strong {
  color: #303133;
}
.result-list {
  min-height: 120px;
}
.result-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 10px;
  border-bottom: 1px solid #f2f3f5;
  border-radius: 8px;
  cursor: pointer;
}
.result-item:hover {
  background: #f5f9ff;
}
.result-icon {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #f0f6ff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}
.result-body {
  flex: 1;
  min-width: 0;
}
.result-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.result-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.result-subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.result-snippet {
  margin-top: 5px;
  color: #606266;
  font-size: 12px;
  line-height: 1.6;
}
.result-action {
  flex: none;
  color: #409eff;
  font-size: 12px;
  padding-top: 4px;
}
.pagination {
  margin-top: 14px;
  justify-content: flex-end;
}
.tips-card {
  padding-bottom: 6px;
}
.tips-title {
  font-weight: 600;
  margin-bottom: 12px;
}
.tips-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}
.tip {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
}
.tip-icon {
  font-size: 20px;
}
.tip-name {
  font-weight: 600;
  font-size: 13px;
}
.tip-desc {
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
.tips-alert {
  margin-top: 14px;
}
:deep(mark) {
  background: #fff3c4;
  color: #b26a00;
  padding: 0 2px;
  border-radius: 2px;
}
</style>
