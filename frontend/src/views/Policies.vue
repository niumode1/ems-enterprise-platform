<template>
  <div class="policy-page">
    <el-card shadow="never" class="hero-card">
      <div class="hero-row">
        <div>
          <div class="hero-title">公司制度</div>
          <p class="hero-sub">
            查看公司规章制度，或直接问 AI。只展示你有权限查看的制度，AI 回答会附带原文引用。
          </p>
        </div>
        <el-button
          v-if="capabilities?.canManage"
          type="primary"
          @click="openUpload"
        >
          上传制度
        </el-button>
      </div>
    </el-card>

    <el-card shadow="never" class="ai-card">
      <div class="ai-head">
        <div>
          <div class="ai-title">AI 快速问答</div>
          <div class="ai-sub">
            例如：年假有多少天？报销流程是什么？出差住宿标准是多少？
          </div>
        </div>
        <el-tag v-if="answer?.aiUsed" type="success" effect="plain">AI 改写</el-tag>
      </div>
      <div class="ai-row">
        <el-input
          v-model="question"
          size="large"
          clearable
          maxlength="500"
          placeholder="输入你想问的制度问题"
          @keyup.enter="ask"
        />
        <el-button
          type="primary"
          size="large"
          :loading="asking"
          :disabled="!canAskAi || !question.trim()"
          @click="ask"
        >
          问 AI
        </el-button>
      </div>
      <div v-if="!capabilities?.aiEnabled" class="ai-hint">
        公司暂未开启制度 AI 问答。
      </div>
      <div v-else-if="!capabilities?.canAskAi" class="ai-hint">
        当前账号没有 AI 使用权限，仍可查看和下载制度原文。
      </div>

      <div v-if="answer" class="answer-box">
        <div class="answer-text">{{ answer.answer }}</div>
        <div class="answer-meta">
          <el-tag v-if="answer.lowConfidence" type="warning" effect="plain">
            低置信，请核对原文
          </el-tag>
          <span v-if="answer.citationsRequired" class="meta-note">
            回答需带原文引用
          </span>
        </div>
        <div v-if="answer.citations.length" class="citation-list">
          <div
            v-for="(citation, index) in answer.citations"
            :key="citation.policyId + '-' + citation.chunkIndex"
            class="citation"
            @click="openDetail(citation.policyId)"
          >
            <span class="citation-index">[{{ index + 1 }}]</span>
            <span class="citation-title">{{ citation.title }}</span>
            <span class="citation-snippet">{{ citation.snippet }}</span>
          </div>
        </div>
        <div v-else class="meta-note">
          没有命中制度原文，建议换一种问法，或直接在下方查看制度。
        </div>
        <div class="answer-disclaimer">AI 回答仅供快速定位，正式依据以制度原文为准。</div>
      </div>
    </el-card>

    <el-card shadow="never" class="list-card">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索制度标题或正文"
          class="search-input"
          @keyup.enter="load"
        >
          <template #prefix>🔍</template>
        </el-input>
        <el-button :loading="loading" @click="load">搜索</el-button>
      </div>

      <el-table v-loading="loading" :data="policies" style="width: 100%">
        <el-table-column prop="title" label="制度名称" min-width="220">
          <template #default="{ row }">
            <div class="policy-name">{{ row.title }}</div>
            <div class="policy-file">{{ row.originalName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small" effect="plain">{{ row.category }}</el-tag>
            <span v-else class="muted">未分类</span>
          </template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="120">
          <template #default="{ row }">{{ row.uploaderName || '-' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="AI 可检索" width="110">
          <template #default="{ row }">
            <el-tag :type="row.searchable ? 'success' : 'info'" size="small" effect="plain">
              {{ row.searchable ? '是' : '仅下载' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
            <el-button link @click="download(row)">下载</el-button>
            <el-button
              v-if="capabilities?.canManage"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!loading && policies.length === 0"
        description="暂无制度文件，管理员可以上传第一份公司制度"
        :image-size="72"
      />
    </el-card>

    <el-drawer v-model="detailOpen" size="720px" :title="detail?.title || '制度详情'">
      <div v-if="detail" class="detail">
        <div class="detail-meta">
          <el-tag v-if="detail.category" size="small" effect="plain">{{ detail.category }}</el-tag>
          <span>{{ detail.originalName }}</span>
          <span>{{ formatSize(detail.sizeBytes) }}</span>
          <span>{{ formatTime(detail.updatedAt) }}</span>
        </div>
        <div v-if="detail.contentText" class="policy-text">{{ detail.contentText }}</div>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="该文件暂不支持在线预览或 AI 检索，请下载原文件查看。"
        />
        <div class="detail-actions">
          <el-button type="primary" @click="download(detail)">下载原文件</el-button>
        </div>
      </div>
      <el-empty v-else description="加载中" />
    </el-drawer>

    <el-dialog v-model="uploadOpen" title="上传公司制度" width="540px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="制度名称" required>
          <el-input v-model="form.title" maxlength="200" show-word-limit placeholder="例如：员工考勤管理制度" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" maxlength="64" placeholder="例如：人事、财务、行政" />
        </el-form-item>
        <el-form-item label="文件" required>
          <input ref="fileInput" type="file" :accept="accept" @change="onFileChange" />
          <div class="upload-hint">
            支持 {{ (capabilities?.allowedExtensions || []).join('、') || '常见文档格式' }}，
            最大 {{ capabilities?.maxFileMb || 50 }}MB。txt/docx/xlsx 可被 AI 检索，PDF 目前仅支持下载。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadOpen = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  askPolicies,
  deletePolicy,
  downloadPolicy,
  getPolicy,
  listPolicies,
  policyCapabilities,
  uploadPolicy,
} from '@/api/policies'
import type {
  PolicyAnswer,
  PolicyCapabilities,
  PolicyDetail,
  PolicyView,
} from '@/types'

const route = useRoute()
const capabilities = ref<PolicyCapabilities | null>(null)
const policies = ref<PolicyView[]>([])
const keyword = ref('')
const loading = ref(false)
const asking = ref(false)
const question = ref('')
const answer = ref<PolicyAnswer | null>(null)
const detailOpen = ref(false)
const detail = ref<PolicyDetail | null>(null)
const uploadOpen = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const form = reactive<{ title: string; category: string; file: File | null }>({
  title: '',
  category: '',
  file: null,
})

const canAskAi = computed(() => Boolean(capabilities.value?.canAskAi))
const accept = computed(() => {
  const extensions = capabilities.value?.allowedExtensions || []
  return extensions.length ? extensions.map((item) => `.${item}`).join(',') : undefined
})

onMounted(async () => {
  await Promise.all([load(), loadCapabilities()])
  const focus = route.query.focus
  if (typeof focus === 'string' && focus) {
    await openDetail(focus)
  }
})

async function loadCapabilities() {
  try {
    capabilities.value = await policyCapabilities()
  } catch {
    capabilities.value = null
  }
}

async function load() {
  loading.value = true
  try {
    policies.value = await listPolicies(keyword.value.trim() || undefined)
  } finally {
    loading.value = false
  }
}

async function ask() {
  const text = question.value.trim()
  if (!text) {
    ElMessage.warning('请输入想问的制度问题')
    return
  }
  asking.value = true
  try {
    answer.value = await askPolicies({ question: text })
  } finally {
    asking.value = false
  }
}

async function openDetail(id: string | number) {
  detailOpen.value = true
  detail.value = null
  try {
    detail.value = await getPolicy(String(id))
  } catch {
    detailOpen.value = false
  }
}

async function download(row: PolicyView | PolicyDetail) {
  try {
    await downloadPolicy(row.id, row.originalName || row.title)
  } catch {
    ElMessage.error('下载失败')
  }
}

function openUpload() {
  form.title = ''
  form.category = ''
  form.file = null
  uploadOpen.value = true
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  form.file = input.files?.[0] || null
}

async function submitUpload() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写制度名称')
    return
  }
  if (!form.file) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  uploading.value = true
  try {
    await uploadPolicy({
      title: form.title.trim(),
      category: form.category.trim(),
      file: form.file,
    })
    ElMessage.success('制度上传成功')
    uploadOpen.value = false
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    keyword.value = ''
    await load()
  } finally {
    uploading.value = false
  }
}

async function remove(row: PolicyView) {
  await ElMessageBox.confirm(`确定删除“${row.title}”吗？删除后无法恢复。`, '删除制度', {
    type: 'warning',
  })
  await deletePolicy(row.id)
  ElMessage.success('已删除')
  if (detail.value?.id === row.id) {
    detailOpen.value = false
  }
  await load()
}

function formatTime(value: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
}

function formatSize(value: number) {
  if (!value) return '0 B'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}
</script>

<style scoped>
.policy-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.hero-card,
.ai-card,
.list-card {
  border-radius: 10px;
}
.hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.hero-title {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}
.hero-sub {
  margin: 8px 0 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
}
.ai-card {
  background: linear-gradient(135deg, #f4f8ff 0%, #ffffff 70%);
}
.ai-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.ai-title {
  font-size: 16px;
  font-weight: 600;
}
.ai-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}
.ai-row {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}
.ai-row .el-input {
  flex: 1;
}
.ai-hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}
.answer-box {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e4e7ed;
}
.answer-text {
  color: #303133;
  line-height: 1.8;
  white-space: pre-wrap;
}
.answer-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}
.meta-note {
  color: #909399;
  font-size: 12px;
}
.citation-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}
.citation {
  padding: 8px 10px;
  border-radius: 6px;
  background: #f5f7fa;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.6;
}
.citation:hover {
  background: #ecf5ff;
}
.citation-index {
  color: #409eff;
  margin-right: 4px;
}
.citation-title {
  font-weight: 600;
  color: #303133;
  margin-right: 6px;
}
.citation-snippet {
  color: #606266;
}
.answer-disclaimer {
  margin-top: 10px;
  color: #c0c4cc;
  font-size: 12px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.search-input {
  max-width: 360px;
}
.policy-name {
  font-weight: 600;
  color: #303133;
}
.policy-file {
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #c0c4cc;
}
.detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #909399;
  font-size: 12px;
  flex-wrap: wrap;
}
.policy-text {
  max-height: 62vh;
  overflow: auto;
  padding: 16px;
  border-radius: 8px;
  background: #fafafa;
  border: 1px solid #ebeef5;
  white-space: pre-wrap;
  line-height: 1.8;
  color: #303133;
}
.detail-actions {
  display: flex;
  justify-content: flex-end;
}
.upload-hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
</style>
