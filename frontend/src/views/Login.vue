<template>
  <AuthLayout title="欢迎回来" description="登录波波公考，继续今天的学习。">
      <el-tabs v-model="kind" class="login-tabs" stretch>
        <el-tab-pane label="考生登录" name="user" />
        <el-tab-pane label="管理员登录" name="manager" />
      </el-tabs>

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.userName" placeholder="请输入用户名" size="large" :prefix-icon="User"
                    autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large"
                    :prefix-icon="Lock" autocomplete="current-password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="btn" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <div class="foot" v-if="kind === 'user'">
        还没有账号？<router-link to="/register">立即注册</router-link>
      </div>
  </AuthLayout>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '../api'
import { useAuthStore } from '../store/auth'
import AuthLayout from '../components/AuthLayout.vue'

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
.login-tabs { margin-bottom: 20px; }
.login-tabs :deep(.el-tabs__header) { margin: 0; }
.login-tabs :deep(.el-tabs__nav-wrap::after) { background: #edf0f6; height: 1px; }
.login-tabs :deep(.el-tabs__item) { height: 42px; color: #738097; font-size: 14px; }
.login-tabs :deep(.el-tabs__item.is-active) { color: var(--hh-primary, #646cff); font-weight: 600; }
.login-tabs :deep(.el-tabs__active-bar) { height: 3px; border-radius: 3px; }
.login-tabs :deep(.el-tabs__content) { display: none; }
.btn { width: 100%; height: 46px; border-radius: 9px; font-weight: 600; }
.foot { margin-top: 22px; color: #738097; font-size: 13px; text-align: center; }
.foot a { margin-left: 6px; color: var(--hh-primary, #646cff); font-weight: 600; text-decoration: none; }
.foot a:hover { text-decoration: underline; }
.foot a:focus-visible { outline: 2px solid var(--hh-primary, #646cff); outline-offset: 4px; }
</style>
