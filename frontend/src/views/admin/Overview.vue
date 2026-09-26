<template>
  <div class="admin-overview">
    <div class="page-header">
      <h2>数据概览</h2>
      <el-button @click="loadData" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <div v-if="loading && !data" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else class="content">
      <!-- Key Metrics -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-icon" style="background: #e8f4fd; color: #409eff">
            <el-icon><User /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ data.userCount || 0 }}</div>
            <div class="metric-label">注册用户</div>
            <div class="metric-trend">
              <span class="trend-up">+{{ data.newUsersToday || 0 }}</span> 今日新增
            </div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon" style="background: #f0f9ff; color: #67c23a">
            <el-icon><Document /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ data.questionCount || 0 }}</div>
            <div class="metric-label">题库总量</div>
            <div class="metric-detail">
              申论题: {{ data.shenlunCount || 0 }}
            </div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon" style="background: #fdf6ec; color: #e6a23c">
            <el-icon><EditPen /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ data.todayAnswers || 0 }}</div>
            <div class="metric-label">今日答题</div>
            <div class="metric-detail">
              本周: {{ data.weekAnswers || 0 }}
            </div>
          </div>
        </div>

        <div class="metric-card">
          <div class="metric-icon" style="background: #fef0f0; color: #f56c6c">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="metric-content">
            <div class="metric-value">{{ data.discussionCount || 0 }}</div>
            <div class="metric-label">讨论总数</div>
            <div class="metric-detail">
              回复: {{ data.replyCount || 0 }}
            </div>
          </div>
        </div>
      </div>

      <!-- Charts Row 1 -->
      <div class="charts-row">
        <!-- User Growth Chart -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>用户增长趋势</h3>
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div ref="userGrowthChartRef" class="chart-container"></div>
        </div>

        <!-- Activity Chart -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>每日活跃用户</h3>
            <el-icon><Histogram /></el-icon>
          </div>
          <div ref="activityChartRef" class="chart-container"></div>
        </div>
      </div>

      <!-- Charts Row 2 -->
      <div class="charts-row">
        <!-- Question Type Distribution -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>题型分布</h3>
            <el-icon><PieChart /></el-icon>
          </div>
          <div ref="questionTypeChartRef" class="chart-container"></div>
        </div>

        <!-- Popular Questions -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>热门题目排行</h3>
            <el-icon><TrophyBase /></el-icon>
          </div>
          <div ref="popularChartRef" class="chart-container"></div>
        </div>
      </div>

      <!-- Recent Activities -->
      <div class="activity-section">
        <div class="section-header">
          <h3>最近活动</h3>
        </div>
        <el-table :data="data.recentActivities || []" stripe>
          <el-table-column prop="userName" label="用户" width="150" />
          <el-table-column prop="action" label="动作" width="120">
            <template #default="{ row }">
              <el-tag :type="getActionType(row.action)" size="small">
                {{ row.action }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" />
          <el-table-column prop="createdAt" label="时间" width="180">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Loading,
  Refresh,
  User,
  Document,
  EditPen,
  ChatDotRound,
  TrendCharts,
  Histogram,
  PieChart,
  TrophyBase
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { statsApi } from '@/api'

const loading = ref(false)
const data = ref({
  userCount: 0,
  newUsersToday: 0,
  questionCount: 0,
  shenlunCount: 0,
  todayAnswers: 0,
  activeUsersToday: 0
})

const userGrowthChartRef = ref(null)
const activityChartRef = ref(null)
const questionTypeChartRef = ref(null)
const popularChartRef = ref(null)

let userGrowthChart = null
let activityChart = null
let questionTypeChart = null
let popularChart = null

async function loadData() {
  try {
    loading.value = true
    const result = await statsApi.adminOverview()
    data.value = result

    await initCharts(result)
  } catch (err) {
    console.error('Failed to load admin overview:', err)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

async function initCharts(statsData) {
  await new Promise(resolve => setTimeout(resolve, 100))

  if (userGrowthChartRef.value) {
    userGrowthChart = echarts.init(userGrowthChartRef.value)
    initUserGrowthChart(statsData.userGrowth || [])
  }

  if (activityChartRef.value) {
    activityChart = echarts.init(activityChartRef.value)
    initActivityChart(statsData.dailyActive || [])
  }

  if (questionTypeChartRef.value) {
    questionTypeChart = echarts.init(questionTypeChartRef.value)
    initQuestionTypeChart(statsData.questionTypeStats || [])
  }

  if (popularChartRef.value) {
    popularChart = echarts.init(popularChartRef.value)
    initPopularChart(statsData.popularQuestions || [])
  }

  window.addEventListener('resize', handleResize)
}

function initUserGrowthChart(growthData) {
  const dates = growthData.map(d => d.date)
  const counts = growthData.map(d => d.count)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: counts,
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        }
      }
    ]
  }

  userGrowthChart.setOption(option)
}

function initActivityChart(activityData) {
  const dates = activityData.map(d => d.date)
  const counts = activityData.map(d => d.activeUsers)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: counts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#67c23a' },
            { offset: 1, color: '#a8e6a1' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }
    ]
  }

  activityChart.setOption(option)
}

function initQuestionTypeChart(typeStats) {
  const chartData = typeStats.map(d => ({
    name: d.questionType,
    value: d.count
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 题 ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}\n{c} 题'
        },
        data: chartData
      }
    ]
  }

  questionTypeChart.setOption(option)
}

function initPopularChart(popularData) {
  const questions = popularData.map(d => d.questionStem?.substring(0, 20) + '...')
  const counts = popularData.map(d => d.answerCount)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'value'
    },
    yAxis: {
      type: 'category',
      data: questions
    },
    series: [
      {
        type: 'bar',
        data: counts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#e6a23c' },
            { offset: 1, color: '#f5d9a8' }
          ]),
          borderRadius: [0, 4, 4, 0]
        }
      }
    ]
  }

  popularChart.setOption(option)
}

function handleResize() {
  userGrowthChart?.resize()
  activityChart?.resize()
  questionTypeChart?.resize()
  popularChart?.resize()
}

function getActionType(action) {
  const map = {
    '答题': 'success',
    '考试': 'warning',
    '讨论': 'info',
    '登录': 'primary'
  }
  return map[action] || ''
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  userGrowthChart?.dispose()
  activityChart?.dispose()
  questionTypeChart?.dispose()
  popularChart?.dispose()
})
</script>

<style scoped>
.admin-overview {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.loading-container .el-icon {
  font-size: 48px;
  color: var(--el-color-primary);
}

.content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 20px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.metric-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 32px;
}

.metric-content {
  flex: 1;
}

.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1;
  margin-bottom: 6px;
}

.metric-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.metric-trend {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.trend-up {
  color: var(--el-color-success);
  font-weight: 600;
}

.metric-detail {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.charts-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 24px;
}

.chart-card {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color);
}

.chart-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.chart-header .el-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.chart-container {
  width: 100%;
  height: 320px;
}

.activity-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.section-header {
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color);
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

@media (max-width: 1200px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .admin-overview {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .chart-container {
    height: 280px;
  }
}
</style>
