<template>
  <div class="page">
    <el-card shadow="never">
      <div class="header">
        <div>
          <div class="title">AI 助手</div>
          <div class="hint">
            支持操作入口推荐和人员问答。回答只使用你有权限查看且允许 AI 读取的字段，敏感字段自动脱敏。
          </div>
        </div>
        <el-tag type="info" size="small">
          {{ aiUsedLabel }}
        </el-tag>
      </div>

      <div class="quick">
        <span class="quick-label">快速问：</span>
        <el-tag
          v-for="item in quickQuestions"
          :key="item.id"
          class="quick-item"
          effect="plain"
          @click="useQuick(item.question)"
        >
          {{ item.question }}
        </el-tag>
        <span v-if="quickQuestions.length === 0" class="muted">暂无可用问题</span>
      </div>

      <div class="ask-row">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="例如：怎么导入花名册？李四的电话是多少？"
          @keydown.enter.exact.prevent="submit"
        />
        <el-button type="primary" :loading="loading" @click="submit">提问</el-button>
      </div>
    </el-card>

    <el-card v-if="result" shadow="never" class="result">
      <div class="result-head">
        <el-tag :type="result.feature === 'PEOPLE' ? 'success' : 'info'" size="small">
          {{ result.feature === 'PEOPLE' ? '人员问答' : '操作指引' }}
        </el-tag>
        <el-tag :type="result.aiUsed ? 'warning' : 'info'" size="small">
          {{ result.aiUsed ? '模型生成' : '本地规则' }}
        </el-tag>
      </div>
      <p class="answer">{{ result.answer }}</p>

      <el-alert
        v-if="maskedFields.length"
        type="warning"
        :closable="false"
        show-icon
        :title="`以下字段按字段权限脱敏：${maskedFields.join('、')}`"
      />

      <div v-if="result.locations.length" class="locations">
        <div
          v-for="hit in result.locations"
          :key="hit.route"
          class="location"
          @click="go(hit.route)"
        >
          <div class="loc-head">
            <span class="loc-title">{{ hit.title }}</span>
            <span class="loc-route">{{ hit.route }}</span>
          </div>
          <div class="loc-desc">{{ hit.description || '点击进入' }}</div>
          <div class="loc-reason">{{ hit.reason }}</div>
        </div>
      </div>

      <div v-if="result.citations.length" class="citations">
        <span class="cite-label">依据：</span>
        <el-tag
          v-for="cite in result.citations"
          :key="cite.employeeId"
          size="small"
          effect="plain"
        >
          {{ cite.name }}{{ cite.employeeNo ? `（${cite.employeeNo}）` : '' }}
        </el-tag>
      </div>
    </el-card>

    <el-card shadow="never" class="examples">
      <template #header>可以这样问</template>
      <ul>
        <li>“员工档案在哪里修改？”——返回最多 3 个可点击的功能入口。</li>
        <li>“技术部有哪些人？”——按字段权限返回可见姓名。</li>
        <li>“李四的电话是多少？”——只回答字段权限允许且 AI 可读的字段。</li>
        <li>没有配置 AI 厂商时使用本地规则作答；配置后由模型在已过滤的资料上生成简短回答。</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { askAi, listQuickQuestions } from '@/api/ai'
import type { AiAskResult, AiQuickQuestion } from '@/types'

const router = useRouter()
const question = ref('')
const loading = ref(false)
const result = ref<AiAskResult | null>(null)
const quickQuestions = ref<AiQuickQuestion[]>([])

const fieldLabels: Record<string, string> = {
  phone: '电话',
  email: '邮箱',
  address: '地址',
  idCard: '身份证',
  emergencyContact: '紧急联系人',
  birthDate: '出生日期',
  gender: '性别',
  hireDate: '入职日期',
  name: '姓名',
}

const maskedFields = computed(() =>
  (result.value?.maskedFields || []).map((field) => fieldLabels[field] ?? field),
)

const aiUsedLabel = computed(() => {
  if (!result.value) return '本地规则 + 已配置模型'
  return result.value.aiUsed ? '本次由模型生成' : '本次由本地规则回答'
})

onMounted(async () => {
  try {
    quickQuestions.value = await listQuickQuestions()
  } catch {
    quickQuestions.value = []
  }
})

function useQuick(text: string) {
  question.value = text
  submit()
}

async function submit() {
  const text = question.value.trim()
  if (!text || loading.value) return
  loading.value = true
  try {
    result.value = await askAi({ question: text })
  } catch {
    result.value = null
  } finally {
    loading.value = false
  }
}

function go(route: string) {
  router.push(route)
}
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.hint {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.quick {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.quick-label {
  color: #606266;
  font-size: 13px;
}
.quick-item {
  cursor: pointer;
}
.ask-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}
.ask-row .el-button {
  height: 54px;
  min-width: 88px;
}
.result-head {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}
.answer {
  margin: 0 0 12px;
  font-size: 15px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
}
.locations {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.location {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.location:hover {
  border-color: #409eff;
  background: #ecf5ff;
}
.loc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.loc-title {
  font-weight: 600;
}
.loc-route {
  color: #909399;
  font-size: 12px;
}
.loc-desc {
  color: #606266;
  font-size: 13px;
  margin-top: 4px;
}
.loc-reason {
  color: #a8abb2;
  font-size: 12px;
  margin-top: 4px;
}
.citations {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}
.cite-label {
  color: #909399;
  font-size: 12px;
}
.muted {
  color: #c0c4cc;
  font-size: 12px;
}
.examples ul {
  margin: 0;
  padding-left: 20px;
  color: #606266;
  line-height: 1.9;
}
</style>
