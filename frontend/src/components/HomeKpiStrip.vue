<template>
  <section class="kpi-grid" aria-label="学习数据概览">
    <div class="kpi-card">
      <div class="kpi-label">累计答题</div>
      <div class="kpi-value num-tabular">{{ formatInt(totalAnswered) }}</div>
      <div class="kpi-meta">
        <span v-if="todayAnswered > 0">今日已刷 {{ todayAnswered }} 题</span>
        <span v-else>保持每日刷题节奏</span>
      </div>
    </div>

    <div class="kpi-card is-success">
      <div class="kpi-label">综合正确率</div>
      <div class="kpi-value num-tabular">{{ formatRate(accuracyRate) }}</div>
      <div class="kpi-meta">{{ accuracyHint }}</div>
    </div>

    <div class="kpi-card">
      <div class="kpi-label">已提交模考</div>
      <div class="kpi-value num-tabular">{{ formatInt(submittedExams) }}</div>
      <div class="kpi-meta">
        <span v-if="lastExamScore !== null">最近得分 {{ lastExamScore }} 分</span>
        <span v-else>定期模考检验配速</span>
      </div>
    </div>

    <div
      class="kpi-card is-warning kpi-actionable"
      role="button"
      tabindex="0"
      @click="emit('review-wrong')"
      @keydown.enter="emit('review-wrong')"
    >
      <div class="kpi-head-row">
        <span class="kpi-label">待复习错题</span>
        <span v-if="dueWrongCount > 0" class="kpi-action-link">去复习 →</span>
      </div>
      <div class="kpi-value num-tabular">{{ formatInt(dueWrongCount) }}</div>
      <div class="kpi-meta">
        <span v-if="dueWrongCount > 0">按 SM-2 记忆曲线到期优先</span>
        <span v-else>今日到期错题已清空</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  totalAnswered: { type: Number, default: 0 },
  todayAnswered: { type: Number, default: 0 },
  accuracyRate: { type: Number, default: 0 },
  submittedExams: { type: Number, default: 0 },
  lastExamScore: { type: Number, default: null },
  dueWrongCount: { type: Number, default: 0 }
})

const emit = defineEmits(['review-wrong'])

function formatInt(val) {
  const n = Number(val)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '0'
}

function formatRate(val) {
  const n = Number(val)
  if (!Number.isFinite(n)) return '0.0%'
  const pct = n <= 1 && n > 0 ? n * 100 : n
  return `${pct.toFixed(1)}%`
}

const accuracyHint = computed(() => {
  const pct = props.accuracyRate <= 1 && props.accuracyRate > 0
    ? props.accuracyRate * 100
    : props.accuracyRate
  if (pct >= 75) return '稳定保持，可冲刺高难题'
  if (pct >= 60) return '基础尚可，重点补薄弱模块'
  return '建议结合错题本夯实基础'
})
</script>

<style scoped>
.kpi-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.kpi-head-row .kpi-label {
  margin-bottom: 8px;
}

.kpi-action-link {
  font-size: 12px;
  font-weight: 500;
  color: var(--hh-primary, #4353e8);
  margin-bottom: 8px;
}

.kpi-actionable {
  cursor: pointer;
}

.kpi-actionable:hover .kpi-action-link {
  text-decoration: underline;
}
</style>
