<template>
  <div class="login-page">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>企业人员与项目协同系统</h2>
          <p>设置驱动 · 权限隔离 · 全程留痕</p>
        </div>
      </template>
      <el-form :model="form" label-width="72px" @keyup.enter="onLogin">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" class="submit" :loading="loading" @click="onLogin">登录</el-button>
      </el-form>
      <el-divider />
      <div class="demo">
        <div class="demo-title">首次启动示例账号（密码均为 admin123）</div>
        <div class="demo-list">
          <span>admin / admin2 系统管理员</span>
          <span>hr 人事管理员</span>
          <span>boss 老板</span>
          <span>auditor 审计员</span>
          <span>employee 员工</span>
          <span>pm 项目组长</span>
        </div>
        <div class="demo-tip">正式使用后请立即修改密码并清理示例数据。</div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function onLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #3a4a5c 100%);
}
.login-card {
  width: 420px;
}
.card-header {
  text-align: center;
}
.card-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
}
.card-header p {
  margin: 0;
  color: #909399;
  font-size: 12px;
}
.submit {
  width: 100%;
}
.demo {
  font-size: 12px;
  color: #606266;
}
.demo-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.demo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-bottom: 6px;
}
.demo-tip {
  color: #909399;
}
</style>
