<template>
  <div class="page">
    <el-card shadow="never">
      <div class="header">
        <div>
          <div class="title">保留期清理</div>
          <div class="subtitle">
            每日定时清理过期数据；清理动作本身受审计保护，审计日志没有手工删除入口。
          </div>
        </div>
        <el-tag :type="status?.jobEnabled ? 'success' : 'info'" effect="plain">
          {{ status?.jobEnabled ? '每日任务已开启' : '每日任务已关闭' }}
        </el-tag>
      </div>

      <el-alert
        type="info"
        :closable="false"
        title="先预演确认影响范围，再执行真实清理。项目/文档的删除动作还需要在配置中心显式开启 autoPurge。"
        class="tip"
      />

      <el-table :data="status?.scopes || []" v-loading="loading" stripe class="scope-table">
        <el-table-column prop="scope" label="范围" width="180" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retentionDays" label="保留天数" width="110" />
        <el-table-column prop="action" label="到期动作" width="120" />
        <el-table-column prop="detail" label="说明" min-width="240" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!loading && !status?.scopes.length" description="暂无清理范围" />
    </el-card>

    <el-card v-if="canRun" shadow="never">
      <template #header>手动执行</template>
      <el-form :inline="true" class="run-form">
        <el-form-item label="清理范围">
          <el-select
            v-model="selectedScopes"
            multiple
            collapse-tags
            clearable
            placeholder="全部已启用范围"
            style="width: 360px"
          >
            <el-option
              v-for="scope in status?.scopes || []"
              :key="scope.scope"
              :label="`${scope.scope}（${scope.action}）`"
              :value="scope.scope"
              :disabled="!scope.enabled"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预演">
          <el-switch v-model="dryRun" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="running" @click="run">
            {{ dryRun ? '预演清理' : '执行清理' }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="!dryRun"
        type="warning"
        :closable="false"
        title="真实清理会按各范围配置归档、提醒或删除数据，且无法通过界面恢复；请确认已完成预演。"
      />
    </el-card>

    <el-card v-if="result" shadow="never">
      <template #header>
        执行结果
        <el-tag :type="result.dryRun ? 'info' : 'warning'" size="small" class="result-tag">
          {{ result.dryRun ? '仅预演' : '已执行' }}
        </el-tag>
      </template>
      <el-table :data="result.results" stripe>
        <el-table-column prop="scope" label="范围" width="180" />
        <el-table-column prop="retentionDays" label="保留天数" width="110" />
        <el-table-column prop="action" label="动作" width="120" />
        <el-table-column prop="matched" label="匹配" width="100" />
        <el-table-column prop="affected" label="影响" width="100" />
        <el-table-column prop="note" label="说明" min-width="240" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getRetentionStatus, runRetention } from '@/api/retention'
import type { RetentionRunResult, RetentionStatusView } from '@/types'

const auth = useAuthStore()
const status = ref<RetentionStatusView | null>(null)
const selectedScopes = ref<string[]>([])
const dryRun = ref(true)
const loading = ref(false)
const running = ref(false)
const result = ref<RetentionRunResult | null>(null)

const canRun = computed(() => auth.hasPermission('config:recovery'))

onMounted(() => {
  void load()
})

async function load() {
  loading.value = true
  try {
    status.value = await getRetentionStatus()
  } finally {
    loading.value = false
  }
}

async function run() {
  if (!dryRun.value) {
    await ElMessageBox.confirm(
      '真实清理会按配置归档、提醒或删除到期数据，请确认已完成预演。',
      '确认执行清理',
      { type: 'warning', confirmButtonText: '执行清理', cancelButtonText: '取消' },
    )
  }
  running.value = true
  try {
    result.value = await runRetention({
      dryRun: dryRun.value,
      scopes: selectedScopes.value.length ? selectedScopes.value : undefined,
    })
    ElMessage.success(dryRun.value ? '预演完成' : '清理完成')
  } finally {
    running.value = false
  }
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
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}
.tip {
  margin: 14px 0;
}
.scope-table {
  margin-top: 4px;
}
.run-form {
  margin-bottom: 4px;
}
.result-tag {
  margin-left: 8px;
}
</style>
