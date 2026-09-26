<template>
  <div class="page">
    <el-card class="hero">
      <h2 class="hi">你好，{{ auth.displayName }}</h2>
      <p class="muted">今天也要坚持刷题，上岸就在前方。</p>
      <div class="kpis">
        <div class="kpi"><b>{{ stats?.totalAnswered ?? '—' }}</b><span>累计答题</span></div>
        <div class="kpi"><b>{{ accuracy }}</b><span>正确率</span></div>
        <div class="kpi"><b>{{ stats?.examsTaken ?? '—' }}</b><span>模拟考试</span></div>
        <div class="kpi"><b>{{ wrong?.wrongCount ?? '—' }}</b><span>待复习错题</span></div>
      </div>
    </el-card>

    <h3 class="page-title">快速开始</h3>
    <el-row :gutter="12">
      <el-col :xs="12" :sm="8" :md="6" v-for="e in entries" :key="e.to">
        <el-card class="entry" shadow="hover" @click="$router.push(e.to)">
          <el-icon class="ico" :style="{ color: e.color }"><component :is="e.icon" /></el-icon>
          <div class="name">{{ e.name }}</div>
          <div class="muted desc">{{ e.desc }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, markRaw } from 'vue'
import { EditPen, Notebook, Document, Collection, ChatDotRound, DataLine } from '@element-plus/icons-vue'
import { statsApi, practiceApi } from '../../api'
import { useAuthStore } from '../../store/auth'

const auth = useAuthStore()
const stats = ref(null)
const wrong = ref(null)

const accuracy = computed(() =>
  stats.value && stats.value.totalAnswered > 0
    ? (stats.value.accuracy * 100).toFixed(1) + '%'
    : '—')

const entries = [
  { name: '模块刷题', desc: '按题型专项训练', to: '/practice', icon: markRaw(EditPen), color: '#1f5fbf' },
  { name: '模拟考试', desc: '整卷计时实战', to: '/exams', icon: markRaw(Notebook), color: '#e6a23c' },
  { name: '申论评分', desc: '按点给分可解释', to: '/shenlun', icon: markRaw(Document), color: '#67c23a' },
  { name: '错题本', desc: '重练薄弱题目', to: '/wrong', icon: markRaw(Collection), color: '#f56c6c' },
  { name: '学习社区', desc: '提问与讨论', to: '/community', icon: markRaw(ChatDotRound), color: '#909399' },
  { name: '学习数据', desc: '趋势与画像', to: '/stats', icon: markRaw(DataLine), color: '#5470c6' }
]

onMounted(async () => {
  try { stats.value = await statsApi.me(30) } catch { /* 首页不因统计失败而中断 */ }
  try { wrong.value = await practiceApi.wrongStats() } catch { /* 同上 */ }
})
</script>

<style scoped>
.hero { margin-bottom: 18px; }
.hi { margin: 0 0 4px; font-size: 20px; }
.kpis { display: flex; gap: 10px; margin-top: 16px; flex-wrap: wrap; }
.kpi { flex: 1; min-width: 80px; background: #f5f7fa; border-radius: 8px; padding: 12px; text-align: center; }
.kpi b { display: block; font-size: 22px; color: var(--hh-primary); }
.kpi span { font-size: 12px; color: #909399; }
.entry { text-align: center; cursor: pointer; margin-bottom: 12px; }
.ico { font-size: 30px; }
.name { font-weight: 600; margin-top: 8px; }
.desc { margin-top: 2px; }
</style>
