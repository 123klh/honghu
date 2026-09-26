<template>
  <div class="exam-history-page">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
      <p>加载成绩详情中...</p>
    </div>

    <div v-else-if="error" class="error-container">
      <el-alert type="error" :title="error" show-icon :closable="false" />
      <el-button @click="$router.push('/exams')" style="margin-top: 20px">
        返回考试列表
      </el-button>
    </div>

    <div v-else-if="record" class="history-content">
      <!-- Score Summary Card -->
      <div class="score-card">
        <div class="score-header">
          <h2>{{ record.examTitle }}</h2>
          <el-tag :type="getScoreType(record.scorePercentage)" size="large">
            {{ getScoreLevel(record.scorePercentage) }}
          </el-tag>
        </div>

        <div class="score-main">
          <div class="score-circle">
            <div class="score-number">{{ record.score }}</div>
            <div class="score-total">/ {{ record.totalScore }}</div>
          </div>

          <div class="score-stats">
            <div class="stat-item">
              <div class="stat-label">正确率</div>
              <div class="stat-value">{{ record.accuracy }}%</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">用时</div>
              <div class="stat-value">{{ record.duration }} 分钟</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">完成时间</div>
              <div class="stat-value">{{ formatDate(record.endTime) }}</div>
            </div>
          </div>
        </div>

        <div v-if="record.rank" class="rank-info">
          <el-icon><TrophyBase /></el-icon>
          <span>超越了 {{ record.rank }}% 的考生</span>
        </div>
      </div>

      <!-- Answer Details -->
      <div class="answers-section">
        <div class="section-header">
          <h3>答题详情</h3>
          <div class="filter-tabs">
            <el-radio-group v-model="filterType" size="small">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="correct">正确</el-radio-button>
              <el-radio-button label="wrong">错误</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <div class="questions-list">
          <div
            v-for="(item, idx) in filteredAnswers"
            :key="item.questionId"
            class="question-item"
          >
            <div class="question-header">
              <span class="question-number"># {{ idx + 1 }}</span>
              <el-tag
                :type="item.correct ? 'success' : 'danger'"
                size="small"
              >
                {{ item.correct ? '正确' : '错误' }}
              </el-tag>
            </div>

            <QuestionCard
              :question="item.question"
              :index="idx"
              :model-value="item.userAnswer"
              :result="{
                correct: item.correct,
                correctAnswer: item.question.correctAnswer
              }"
              :disabled="true"
              :show-favorite="true"
              :favored="item.question.favored"
              @toggle-favorite="handleToggleFavorite(item.question)"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, TrophyBase } from '@element-plus/icons-vue'
import QuestionCard from '@/components/QuestionCard.vue'
import { examApi, favoriteApi } from '@/api'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const error = ref('')
const record = ref(null)
const filterType = ref('all')

const filteredAnswers = computed(() => {
  if (!record.value?.answers) return []

  if (filterType.value === 'correct') {
    return record.value.answers.filter(a => a.correct)
  }
  if (filterType.value === 'wrong') {
    return record.value.answers.filter(a => !a.correct)
  }
  return record.value.answers
})

async function loadHistory() {
  try {
    loading.value = true
    error.value = ''

    const recordId = route.params.id
    if (!recordId) {
      error.value = '缺少记录ID'
      return
    }

    const data = await examApi.detail(recordId)
    record.value = data

  } catch (err) {
    console.error('Failed to load exam history:', err)
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function getScoreType(percentage) {
  if (percentage >= 90) return 'success'
  if (percentage >= 80) return ''
  if (percentage >= 60) return 'warning'
  return 'danger'
}

function getScoreLevel(percentage) {
  if (percentage >= 90) return '优秀'
  if (percentage >= 80) return '良好'
  if (percentage >= 60) return '及格'
  return '不及格'
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function handleToggleFavorite(question) {
  try {
    if (question.favored) {
      await favoriteApi.remove(question.id)
      question.favored = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.add(question.id)
      question.favored = true
      ElMessage.success('已添加到收藏')
    }
  } catch (err) {
    console.error('Failed to toggle favorite:', err)
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.exam-history-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 16px;
}

.loading-container .el-icon {
  font-size: 48px;
  color: var(--el-color-primary);
}

.history-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.score-card {
  background: white;
  border-radius: 12px;
  padding: 32px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.score-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color);
}

.score-header h2 {
  margin: 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
}

.score-main {
  display: flex;
  gap: 48px;
  margin-bottom: 24px;
}

.score-circle {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.score-number {
  font-size: 72px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1;
}

.score-total {
  font-size: 24px;
  color: var(--el-text-color-secondary);
}

.score-stats {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.rank-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: linear-gradient(135deg, #ffeaa7 0%, #fdcb6e 100%);
  border-radius: 8px;
  color: #d63031;
  font-weight: 500;
}

.rank-info .el-icon {
  font-size: 20px;
}

.answers-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color);
}

.section-header h3 {
  margin: 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.questions-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.question-item {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 20px;
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.question-number {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
}

@media (max-width: 768px) {
  .exam-history-page {
    padding: 12px;
  }

  .score-card {
    padding: 20px;
  }

  .score-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .score-header h2 {
    font-size: 20px;
  }

  .score-main {
    flex-direction: column;
    gap: 24px;
  }

  .score-stats {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .question-item {
    padding: 16px;
  }
}
</style>
