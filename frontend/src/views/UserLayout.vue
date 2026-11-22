<template>
  <AppShell :groups="groups" :tabs="tabs" :display-name="auth.displayName" @command="onCommand">
    <router-view />
  </AppShell>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppShell from '../components/AppShell.vue'
import { useAuthStore } from '../store/auth'
import { authApi } from '../api'

const auth = useAuthStore()
const router = useRouter()
const groups = [
  {
    label: '学习空间',
    items: [
      { to: '/', label: '首页', icon: 'HomeFilled' },
      { to: '/practice', label: '模块刷题', icon: 'EditPen' },
      { to: '/exams', label: '模拟考试', icon: 'Notebook' },
      { to: '/shenlun', label: '申论练习', icon: 'Document' },
      { to: '/community', label: '学习社区', icon: 'ChatDotRound' }
    ]
  },
  {
    label: '个人学习',
    items: [
      { to: '/wrong', label: '错题本', icon: 'Collection' },
      { to: '/favorites', label: '我的收藏', icon: 'Star' },
      { to: '/stats', label: '学习统计', icon: 'DataLine' },
      { to: '/exams/history', label: '考试记录', icon: 'Timer' }
    ]
  }
]
const tabs = [
  { to: '/', label: '首页', icon: 'HomeFilled' },
  { to: '/practice', label: '刷题', icon: 'EditPen' },
  { to: '/exams', label: '考试', icon: 'Notebook' },
  { to: '/community', label: '社区', icon: 'ChatDotRound' },
  { to: '/stats', label: '数据', icon: 'DataLine' }
]

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try { await authApi.logout() } catch { /* 令牌失效时仍清除本地登录态。 */ }
    auth.logout()
    router.push({ name: 'login' })
  } else if (cmd === 'favorites') router.push('/favorites')
  else if (cmd === 'history') router.push('/exams/history')
}
</script>
