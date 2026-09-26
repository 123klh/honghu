<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="brand" @click="$router.push('/')">波波公考</div>
      <el-menu mode="horizontal" :default-active="active" class="nav hide-mobile" router>
        <el-menu-item index="/">首页</el-menu-item>
        <el-menu-item index="/practice">刷题</el-menu-item>
        <el-menu-item index="/exams">模拟考试</el-menu-item>
        <el-menu-item index="/shenlun">申论</el-menu-item>
        <el-menu-item index="/wrong">错题本</el-menu-item>
        <el-menu-item index="/community">社区</el-menu-item>
        <el-menu-item index="/stats">学习数据</el-menu-item>
      </el-menu>
      <el-dropdown @command="onCommand">
        <span class="user">{{ auth.displayName }}<el-icon><ArrowDown /></el-icon></span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="favorites">我的收藏</el-dropdown-item>
            <el-dropdown-item command="history">考试记录</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-header>

    <el-main class="main"><router-view /></el-main>

    <!-- 移动端底部导航 -->
    <nav class="tabbar">
      <router-link to="/" :class="{ on: $route.path === '/' }"><el-icon><HomeFilled /></el-icon><span>首页</span></router-link>
      <router-link to="/practice" :class="{ on: $route.path.startsWith('/practice') }"><el-icon><EditPen /></el-icon><span>刷题</span></router-link>
      <router-link to="/exams" :class="{ on: $route.path.startsWith('/exams') }"><el-icon><Notebook /></el-icon><span>考试</span></router-link>
      <router-link to="/community" :class="{ on: $route.path.startsWith('/community') }"><el-icon><ChatDotRound /></el-icon><span>社区</span></router-link>
      <router-link to="/stats" :class="{ on: $route.path.startsWith('/stats') }"><el-icon><DataLine /></el-icon><span>数据</span></router-link>
    </nav>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store/auth'
import { authApi } from '../api'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const active = computed(() => '/' + (route.path.split('/')[1] || ''))

async function onCommand(cmd) {
  if (cmd === 'logout') {
    try { await authApi.logout() } catch { /* 令牌已失效也要清本地态 */ }
    auth.logout()
    router.push({ name: 'login' })
  } else if (cmd === 'favorites') router.push('/favorites')
  else if (cmd === 'history') router.push('/exams/history')
}
</script>

<style scoped>
.layout { min-height: 100vh; }
.header { display: flex; align-items: center; gap: 16px; background: #fff;
  border-bottom: 1px solid #e4e7ed; padding: 0 16px; }
.brand { font-size: 18px; font-weight: 700; color: var(--hh-primary); cursor: pointer; white-space: nowrap; }
.plus { font-size: 12px; background: var(--hh-primary); color: #fff; padding: 1px 5px; border-radius: 4px; }
.nav { flex: 1; border-bottom: none; }
.user { cursor: pointer; display: flex; align-items: center; gap: 4px; color: #606266; }
.main { padding: 0; }
.tabbar { display: none; }

@media (max-width: 768px) {
  .nav { display: none; }
  .header { justify-content: space-between; }
  .tabbar { display: flex; position: fixed; bottom: 0; left: 0; right: 0; background: #fff;
    border-top: 1px solid #e4e7ed; padding: 6px 0 calc(6px + env(safe-area-inset-bottom)); z-index: 100; }
  .tabbar a { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px;
    font-size: 11px; color: #909399; text-decoration: none; }
  .tabbar a.on { color: var(--hh-primary); }
  .tabbar .el-icon { font-size: 20px; }
}
</style>
