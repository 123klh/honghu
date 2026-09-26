<template>
  <div class="wrap">
    <el-card class="card">
      <h1 class="title">波波公考</h1>
      <p class="muted sub">公考刷题 · 模拟考试 · 申论评分 · 学习社区</p>

      <el-tabs v-model="kind">
        <el-tab-pane label="考生登录" name="user" />
        <el-tab-pane label="管理员登录" name="manager" />
      </el-tabs>

      <el-form :model="form" @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.userName" placeholder="用户名" size="large" :prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large"
                    :prefix-icon="Lock" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="foot" v-if="kind === 'user'">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '../api'
import { useAuthStore } from '../store/auth'

const kind = ref('user')
const form = reactive({ userName: '', password: '' })
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

async function submit() {
  if (!form.userName || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = kind.value === 'manager'
      ? await authApi.loginManager(form.userName, form.password)
      : await authApi.loginUser(form.userName, form.password)
    auth.setLogin(data.token, data.userInfo)
    ElMessage.success('登录成功')
    const target = route.query.redirect
    router.push(auth.isManager ? '/admin' : (target || '/'))
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #1f5fbf 0%, #4b8be0 100%); padding: 16px; }
.card { width: 100%; max-width: 380px; }
.title { margin: 0; font-size: 24px; color: var(--hh-primary); text-align: center; }
.plus { font-size: 13px; background: var(--hh-primary); color: #fff; padding: 2px 6px; border-radius: 4px; }
.sub { text-align: center; margin: 6px 0 4px; }
.btn { width: 100%; }
.foot { text-align: center; margin-top: 14px; font-size: 13px; color: #909399; }
</style>
