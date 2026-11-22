<template>
  <AppShell :groups="groups" :display-name="auth.displayName" :role-label="auth.user?.role" manager @command="onCommand">
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
    label: '管理工作台',
    items: [
      { to: '/admin', label: '数据概览', icon: 'DataBoard' },
      { to: '/admin/questions', label: '题库管理', icon: 'Tickets' },
      { to: '/admin/community', label: '社区管理', icon: 'ChatDotRound' }
    ]
  }
]

async function onCommand(cmd) {
  if (cmd !== 'logout') return
  try { await authApi.logout() } catch { /* 令牌失效时仍清除本地登录态。 */ }
  auth.logout()
  router.push({ name: 'login' })
}
</script>
