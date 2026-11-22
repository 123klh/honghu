<template>
  <div class="stats-page">
    <div class="stats-heading"><h1>学习统计</h1><p>可视化分析学习进度，科学调整复习策略</p></div>
    <el-skeleton v-if="loading" :rows="8" animated class="surface stats-loading" />
    <div v-else-if="error" class="surface stats-error"><el-alert title="统计数据暂时无法加载" type="error" :closable="false" show-icon /><el-button type="primary" @click="loadStats">重新加载</el-button></div>
    <template v-else>
      <div class="metric-grid" aria-label="答题统计概况">
        <div v-for="metric in metrics" :key="metric.label" class="metric-card" :class="'metric--' + metric.tone">
          <span class="metric-label">{{ metric.label }}</span><strong class="metric-value">{{ metric.value }}</strong><el-icon class="metric-icon"><component :is="metric.icon" /></el-icon>
        </div>
      </div>
      <div class="stats-panels">
        <section v-for="panel in panels" :key="panel.kind" class="surface stats-panel">
          <div class="stats-panel-heading"><h2>{{ panel.title }}</h2><el-icon><component :is="panel.icon" /></el-icon></div>
          <LearningChart :kind="panel.kind" :data="data[panel.field] || []" />
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { statsApi } from '@/api'
import LearningChart from '../../components/LearningChart.vue'

const loading = ref(true)
const error = ref(false)
const data = ref({})
const metrics = computed(() => [
  { label: '累计答题', value: data.value.totalAnswered || 0, tone: 'pink', icon: 'EditPen' },
  { label: '正确率', value: Math.round((data.value.accuracy || 0) * 100) + '%', tone: 'purple', icon: 'CircleCheck' },
  { label: '答错次数', value: (data.value.totalAnswered || 0) - (data.value.totalCorrect || 0), tone: 'blue', icon: 'Close' },
  { label: '已提交考试', value: data.value.examsTaken || 0, tone: 'orange', icon: 'Notebook' }
])
const panels = [
  { title: '近30天答题趋势', kind: 'activity', field: 'dailyActivity', icon: 'TrendCharts' },
  { title: '科目答题量分布', kind: 'subject', field: 'subjectBreakdown', icon: 'PieChart' },
  { title: '科目正确率', kind: 'radar', field: 'subjectBreakdown', icon: 'DataAnalysis' },
  { title: '每日答题正确率', kind: 'accuracy', field: 'dailyActivity', icon: 'CircleCheck' }
]
async function loadStats() {
  loading.value = true
  error.value = false
  try { data.value = await statsApi.me() }
  catch { error.value = true }
  finally { loading.value = false }
}
onMounted(loadStats)
</script>

<style scoped>
.stats-page { max-width: 1400px; margin: 0 auto; display: flex; flex-direction: column; gap: 22px; }
.stats-heading h1 { margin: 0 0 7px; font-size: 26px; font-weight: 650; line-height: 1.4; }
.stats-heading p { color: #8491a5; margin: 0; font-size: 13px; }
.stats-panels { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 22px; }
.stats-panel { padding: 22px; min-width: 0; }
.stats-panel-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 17px; }
.stats-panel-heading h2 { font-size: 16px; font-weight: 600; margin: 0; }
.stats-panel-heading .el-icon { color: #a2a8d9; font-size: 18px; }
.stats-loading { padding: 25px; }.stats-error { padding: 22px; display: flex; flex-direction: column; align-items: flex-start; gap: 18px; }
@media (max-width: 768px) { .stats-page { gap: 16px; }.stats-heading h1 { font-size: 23px; }.stats-panels { grid-template-columns: 1fr; gap: 16px; }.stats-panel { padding: 18px; } }
</style>
