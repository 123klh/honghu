<template>
  <div class="practice-session">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
      <p>加载题目中...</p>
    </div>

    <div v-else-if="error" class="error-container">
      <el-alert type="error" :title="error" show-icon :closable="false" />
      <el-button @click="$router.push('/practice')" style="margin-top: 20px">
        返回练习选择
      </el-button>
    </div>

    <div v-else-if="questions.length > 0" class="session-content">
      <!-- Progress Header -->
      <div class="progress-header">
        <div class="progress-info">
          <span class="current-progress">{{ currentIndex + 1 }} / {{ questions.length }}</span>
          <el-progress
            :percentage="progressPercentage"
            :stroke-width="8"
            :show-text="false"
            style="flex: 1; margin: 0 20px"
          />
          <span class="stats">
            <el-icon><Check /></el-icon> {{ correctCount }}
            <el-icon style="margin-left: 12px"><Close /></el-icon> {{ wrongCount }}
          </span>
        </div>
      </div>

      <!-- Current Question -->
      <div class="question-container">
        <QuestionCard
          v-if="currentQuestion"
          :question="currentQuestion"
          :index="currentIndex"
          :model-value="currentAnswer"
          @update:model-value="handleAnswerChange"
          :result="currentResult"
          :disabled="answered"
          :show-favorite="true"
          :favored="currentQuestion.favored"
          @toggle-favorite="handleToggleFavorite"
        />
      </div>

      <!-- Action Buttons -->
      <div class="action-buttons">
        <el-button
          v-if="!answered"
          type="primary"
          size="large"
          :disabled="!currentAnswer"
          @click="submitAnswer"
        >
          提交答案
        </el-button>

        <template v-else>
          <el-button
            v-if="currentIndex > 0"
            size="large"
            @click="goToPrevious"
          >
            上一题
          </el-button>

          <el-button
            v-if="currentIndex < questions.length - 1"
            type="primary"
            size="large"
            @click="goToNext"
          >
            下一题
          </el-button>

          <el-button
            v-else
            type="success"
            size="large"
            @click="finishSession"
          >
            完成练习
          </el-button>
        </template>
      </div>

      <!-- Summary Dialog -->
      <el-dialog
        v-model="showSummary"
        title="练习总结"
        width="500px"
        :close-on-click-modal="false"
      >
        <div class="summary-content">
          <div class="summary-stats">
            <div class="stat-item">
              <div class="stat-label">总题数</div>
              <div class="stat-value">{{ questions.length }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">正确</div>
              <div class="stat-value correct">{{ correctCount }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">错误</div>
              <div class="stat-value wrong">{{ wrongCount }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">正确率</div>
              <div class="stat-value">{{ accuracyRate }}%</div>
            </div>
          </div>

          <div class="summary-message">
            <el-alert
              :type="accuracyRate >= 80 ? 'success' : accuracyRate >= 60 ? 'warning' : 'error'"
              :title="getSummaryMessage()"
              show-icon
              :closable="false"
            />
          </div>
        </div>

        <template #footer>
          <el-button @click="reviewWrong" v-if="wrongCount > 0">
            查看错题
          </el-button>
          <el-button @click="$router.push('/practice')">
            继续练习
          </el-button>
          <el-button type="primary" @click="$router.push('/')">
            返回首页
          </el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Check, Close } from '@element-plus/icons-vue'
import QuestionCard from '@/components/QuestionCard.vue'
import { practiceApi, favoriteApi } from '@/api'

const router = useRouter()

const loading = ref(true)
const error = ref('')
const questions = ref([])
const currentIndex = ref(0)
const currentAnswer = ref('')
const answered = ref(false)
const currentResult = ref(null)
const answers = ref([]) // Track all answers: { questionId, userAnswer, correct }
const showSummary = ref(false)

const currentQuestion = computed(() => questions.value[currentIndex.value])

const progressPercentage = computed(() => {
  if (questions.value.length === 0) return 0
  return Math.round(((currentIndex.value + 1) / questions.value.length) * 100)
})

const correctCount = computed(() =>
  answers.value.filter(a => a && a.correct).length
)

const wrongCount = computed(() =>
  answers.value.filter(a => a && !a.correct).length
)

const accuracyRate = computed(() => {
  const total = answers.value.length
  if (total === 0) return 0
  return Math.round((correctCount.value / total) * 100)
})

async function loadSession() {
  try {
    loading.value = true
    error.value = ''

    // Get session data from sessionStorage
    const sessionData = sessionStorage.getItem('practice-session')
    if (!sessionData) {
      error.value = '练习会话已过期，请重新开始'
      return
    }

    const session = JSON.parse(sessionData)

    // Use the questions that were already fetched in Practice.vue
    questions.value = session.questions || []

    if (questions.value.length === 0) {
      error.value = '没有找到符合条件的题目'
      return
    }

    // Initialize answers array
    answers.value = new Array(questions.value.length).fill(null)

  } catch (err) {
    console.error('Failed to load practice session:', err)
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function handleAnswerChange(value) {
  currentAnswer.value = value
}

async function submitAnswer() {
  if (!currentAnswer.value) return

  try {
    const result = await practiceApi.submit(
      currentQuestion.value.questionId,
      currentAnswer.value,
      0
    )

    // Store result
    answered.value = true
    currentResult.value = {
      correct: result.correct,
      correctAnswer: result.correctAnswer,
      explanation: result.explanation
    }

    // Add explanation to current question if not already there
    if (result.explanation && !currentQuestion.value.explanation) {
      currentQuestion.value.explanation = result.explanation
    }

    // Record answer
    answers.value[currentIndex.value] = {
      questionId: currentQuestion.value.questionId,
      userAnswer: currentAnswer.value,
      correct: result.correct
    }

    // Show feedback
    if (result.correct) {
      ElMessage.success('回答正确！')
    } else {
      ElMessage.error(`回答错误，正确答案是 ${result.correctAnswer}`)
    }

  } catch (err) {
    console.error('Failed to submit answer:', err)
    ElMessage.error('提交失败：' + (err.message || '未知错误'))
  }
}

function goToNext() {
  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value++
    resetQuestion()
  }
}

function goToPrevious() {
  if (currentIndex.value > 0) {
    currentIndex.value--
    resetQuestion()
  }
}

function resetQuestion() {
  // Check if this question was already answered
  const prevAnswer = answers.value[currentIndex.value]
  if (prevAnswer) {
    currentAnswer.value = prevAnswer.userAnswer
    answered.value = true
    currentResult.value = {
      correct: prevAnswer.correct,
      correctAnswer: currentQuestion.value.correctAnswer
    }
  } else {
    currentAnswer.value = ''
    answered.value = false
    currentResult.value = null
  }
}

function finishSession() {
  showSummary.value = true
  // Clear session storage
  sessionStorage.removeItem('practice-session')
}

function getSummaryMessage() {
  const rate = accuracyRate.value
  if (rate >= 90) return '优秀！继续保持！'
  if (rate >= 80) return '很好！稍加努力就能更上一层楼'
  if (rate >= 60) return '不错！继续加油'
  if (rate >= 40) return '需要加强练习，建议多做几遍'
  return '基础需要巩固，建议从基础知识开始复习'
}

function reviewWrong() {
  showSummary.value = false
  router.push('/wrong')
}

async function handleToggleFavorite() {
  const q = currentQuestion.value
  try {
    if (q.favored) {
      await favoriteApi.removeQuestion(q.questionId)
      q.favored = false
      ElMessage.success('已取消收藏')
    } else {
      await favoriteApi.addQuestion(q.questionId)
      q.favored = true
      ElMessage.success('已添加到收藏')
    }
  } catch (err) {
    console.error('Failed to toggle favorite:', err)
    ElMessage.error('操作失败：' + (err.message || '未知错误'))
  }
}

onMounted(() => {
  loadSession()
})
</script>

<style scoped>
.practice-session {
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

.session-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.progress-header {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.current-progress {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-color-primary);
  min-width: 80px;
}

.stats {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 500;
  min-width: 100px;
}

.stats .el-icon {
  margin-right: 4px;
}

.stats .el-icon:first-child {
  color: var(--el-color-success);
}

.stats .el-icon:nth-child(3) {
  color: var(--el-color-danger);
}

.question-container {
  min-height: 400px;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.action-buttons .el-button {
  min-width: 120px;
}

.summary-content {
  padding: 20px 0;
}

.summary-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 30px;
}

.stat-item {
  text-align: center;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.stat-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-value.correct {
  color: var(--el-color-success);
}

.stat-value.wrong {
  color: var(--el-color-danger);
}

@media (max-width: 768px) {
  .practice-session {
    padding: 12px;
  }

  .progress-info {
    flex-wrap: wrap;
  }

  .current-progress {
    font-size: 16px;
    min-width: auto;
  }

  .stats {
    font-size: 14px;
  }

  .summary-stats {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stat-value {
    font-size: 24px;
  }

  .action-buttons {
    flex-direction: column;
    padding: 16px;
  }

  .action-buttons .el-button {
    width: 100%;
  }
}
</style>
