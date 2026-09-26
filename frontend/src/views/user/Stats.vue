<template>
  <div class="stats-page">
    <div class="page-header">
      <h2>学习统计</h2>
      <p class="subtitle">可视化分析学习进度，科学调整复习策略</p>
    </div>

    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else class="content">
      <!-- Overview Cards -->
      <div class="overview-cards">
        <div class="stat-card">
          <div class="stat-icon" style="background: #e8f4fd; color: #409eff">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ overview.totalAnswered }}</div>
            <div class="stat-label">累计答题</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #f0f9ff; color: #67c23a">
            <el-icon><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ overview.accuracy }}%</div>
            <div class="stat-label">正确率</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #fef0f0; color: #f56c6c">
            <el-icon><Close /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ overview.wrongCount }}</div>
            <div class="stat-label">错题数</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #fdf6ec; color: #e6a23c">
            <el-icon><Timer /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ overview.studyDays }}</div>
            <div class="stat-label">学习天数</div>
          </div>
        </div>
      </div>

      <!-- Charts Row 1 -->
      <div class="charts-row">
        <!-- Daily Activity Chart -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>近30天答题趋势</h3>
            <el-icon><TrendCharts /></el-icon>
          </div>
          <div ref="dailyChartRef" class="chart-container"></div>
        </div>

        <!-- Subject Distribution Chart -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>题型正确率分布</h3>
            <el-icon><PieChart /></el-icon>
          </div>
          <div ref="subjectChartRef" class="chart-container"></div>
        </div>
      </div>

      <!-- Charts Row 2 -->
      <div class="charts-row">
        <!-- Ability Radar Chart -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>能力雷达图</h3>
            <el-icon><Radar /></el-icon>
          </div>
          <div ref="abilityChartRef" class="chart-container"></div>
        </div>

        <!-- Study Time Chart -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>每日学习时长</h3>
            <el-icon><Clock /></el-icon>
          </div>
          <div ref="timeChartRef" class="chart-container"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Loading,
  Document,
  CircleCheck,
  Close,
  Timer,
  TrendCharts,
  PieChart,
  Clock
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { statsApi } from '@/api'

const loading = ref(false)
const overview = ref({
  totalAnswered: 0,
  accuracy: 0,
  wrongCount: 0,
  studyDays: 0
})

const dailyChartRef = ref(null)
const subjectChartRef = ref(null)
const abilityChartRef = ref(null)
const timeChartRef = ref(null)

let dailyChart = null
let subjectChart = null
let abilityChart = null
let timeChart = null

async function loadStats() {
  try {
    loading.value = true

    const data = await statsApi.me()

    // Map backend fields to frontend
    overview.value = {
      totalAnswered: data.totalAnswered || 0,
      accuracy: Math.round((data.accuracy || 0) * 100),
      wrongCount: data.totalAnswered - data.totalCorrect || 0,
      studyDays: data.examsTaken || 0
    }

    // Initialize charts after data is loaded
    await initCharts(data)

  } catch (err) {
    console.error('Failed to load stats:', err)
    ElMessage.error('加载统计数据失败')
  } finally {
    loading.value = false
  }
}

async function initCharts(data) {
  await new Promise(resolve => setTimeout(resolve, 100))

  if (dailyChartRef.value) {
    dailyChart = echarts.init(dailyChartRef.value)
    initDailyChart(data.dailyActivity || [])
  }

  if (subjectChartRef.value) {
    subjectChart = echarts.init(subjectChartRef.value)
    initSubjectChart(data.subjectBreakdown || [])
  }

  if (abilityChartRef.value) {
    abilityChart = echarts.init(abilityChartRef.value)
    initAbilityChart(data.subjectBreakdown || [])
  }

  if (timeChartRef.value) {
    timeChart = echarts.init(timeChartRef.value)
    initTimeChart(data.dailyActivity || [])
  }

  // Handle window resize
  window.addEventListener('resize', handleResize)
}

function initDailyChart(data) {
  const dates = data.map(d => d.day)
  const counts = data.map(d => d.answered)
  const correctCounts = data.map(d => d.correct)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      data: ['答题数', '正确数']
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '答题数',
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
      },
      {
        name: '正确数',
        type: 'line',
        smooth: true,
        data: correctCounts,
        itemStyle: { color: '#67c23a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
            { offset: 1, color: 'rgba(103, 194, 58, 0.05)' }
          ])
        }
      }
    ]
  }

  dailyChart.setOption(option)
}

function initSubjectChart(data) {
  const chartData = data.map(d => ({
    name: d.subject,
    value: Math.round((d.accuracy || 0) * 100)
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c}%'
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
          formatter: '{b}\n{c}%'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: chartData
      }
    ]
  }

  subjectChart.setOption(option)
}

function initAbilityChart(data) {
  const indicators = data.map(d => ({
    name: d.subject,
    max: 100
  }))

  const values = data.map(d => Math.round((d.accuracy || 0) * 100))

  const option = {
    tooltip: {
      trigger: 'item'
    },
    radar: {
      indicator: indicators,
      shape: 'circle',
      splitNumber: 5,
      axisName: {
        color: '#666'
      },
      splitLine: {
        lineStyle: {
          color: [
            'rgba(64, 158, 255, 0.1)',
            'rgba(64, 158, 255, 0.2)',
            'rgba(64, 158, 255, 0.3)',
            'rgba(64, 158, 255, 0.4)',
            'rgba(64, 158, 255, 0.5)'
          ].reverse()
        }
      },
      splitArea: {
        show: false
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(64, 158, 255, 0.5)'
        }
      }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: values,
            name: '能力得分',
            areaStyle: {
              color: new echarts.graphic.RadialGradient(0.5, 0.5, 1, [
                { offset: 0, color: 'rgba(64, 158, 255, 0.5)' },
                { offset: 1, color: 'rgba(64, 158, 255, 0.1)' }
              ])
            },
            itemStyle: {
              color: '#409eff'
            }
          }
        ]
      }
    ]
  }

  abilityChart.setOption(option)
}

function initTimeChart(data) {
  const dates = data.map(d => d.day)
  const minutes = data.map(d => d.answered || 0)

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: '{b}<br/>{c} 分钟'
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
      type: 'value',
      name: '分钟',
      axisLabel: {
        formatter: '{value}'
      }
    },
    series: [
      {
        type: 'bar',
        data: minutes,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#e6a23c' },
            { offset: 1, color: '#f5d9a8' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        emphasis: {
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#d89f2a' },
              { offset: 1, color: '#e6a23c' }
            ])
          }
        }
      }
    ]
  }

  timeChart.setOption(option)
}

function handleResize() {
  dailyChart?.resize()
  subjectChart?.resize()
  abilityChart?.resize()
  timeChart?.resize()
}

onMounted(() => {
  loadStats()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  dailyChart?.dispose()
  subjectChart?.dispose()
  abilityChart?.dispose()
  timeChart?.dispose()
})
</script>

<style scoped>
.stats-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 30px;
}

.page-header h2 {
  font-size: 28px;
  margin: 0 0 8px 0;
  color: var(--el-text-color-primary);
}

.subtitle {
  color: var(--el-text-color-secondary);
  margin: 0;
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

.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: transform 0.3s;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.stat-icon {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 32px;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
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
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.chart-header .el-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}

.chart-container {
  width: 100%;
  height: 350px;
}

@media (max-width: 1200px) {
  .charts-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-page {
    padding: 12px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .overview-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-card {
    padding: 16px;
  }

  .stat-icon {
    width: 48px;
    height: 48px;
    font-size: 24px;
  }

  .stat-value {
    font-size: 28px;
  }

  .chart-card {
    padding: 16px;
  }

  .chart-container {
    height: 300px;
  }
}
</style>
