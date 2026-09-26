<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="brand">波波公考 <small>管理端</small></div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/admin"><el-icon><DataBoard /></el-icon>数据概览</el-menu-item>
        <el-menu-item index="/admin/questions"><el-icon><Tickets /></el-icon>题库管理</el-menu-item>
        <el-menu-item index="/admin/community"><el-icon><ChatDotRound /></el-icon>社区管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="muted">管理员：{{ auth.displayName }}（{{ auth.user?.role }}）</span>
        <el-button link type="primary" @click="logout">退出登录</el-button>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { authApi } from '../api'

const auth = useAuthStore()
const router = useRouter()

async function logout() {
  try { await authApi.logout() } catch { /* ignore */ }
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.aside { background: #fff; border-right: 1px solid #e4e7ed; }
.brand { padding: 16px; font-size: 17px; font-weight: 700; color: var(--hh-primary); }
.brand small { font-size: 11px; color: #909399; font-weight: 400; }
.header { display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  background: #fff; border-bottom: 1px solid #e4e7ed; }
</style>
