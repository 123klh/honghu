<template>
  <div ref="container" class="chart-container" :class="{ compact }" role="img" :aria-label="description"></div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { learningChartOption } from '../utils/learningChartOptions'

const props = defineProps({ kind: { type: String, required: true }, data: { type: Array, default: () => [] }, compact: Boolean })
const container = ref(null)
const labels = { activity: '每日答题数与正确数趋势', subject: '科目答题量分布', accuracy: '每日答题正确率', radar: '各科目答题正确率' }
const description = computed(() => props.data.length ? labels[props.kind] : '暂无答题数据')
let chart
let observer

function render() {
  const option = learningChartOption(props.kind, props.data)
  option.animation = !window.matchMedia('(prefers-reduced-motion: reduce)').matches
  chart?.setOption(option, true)
}

onMounted(() => {
  chart = echarts.init(container.value)
  render()
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(container.value)
})
watch(() => [props.kind, props.data], render, { deep: true })
onUnmounted(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.chart-container { width: 100%; height: 290px; min-width: 0; }
.chart-container.compact { height: 255px; }
@media (max-width: 600px) { .chart-container, .chart-container.compact { height: 245px; } }
</style>
