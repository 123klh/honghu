<template>
  <div class="exam-run">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
      <p>加载试卷中...</p>
    </div>

    <div v-else-if="error" class="error-container">
      <el-alert type="error" :title="error" show-icon :closable="false" />
      <el-button @click="$router.push('/exams')" style="margin-top: 20px">
        返回考试列表
      </el-button>
    </div>

    <div v-else-if="questions.length > 0" class="exam-content">
      <!-- Fixed Header with Timer -->
      <div class="exam-header">
        <div class="header-left">
          <h3>{{ examTitle }}</h3>
          <span class="question-count">共 {{ questions.length }} 题</span>
        </div>
        <div class="header-right">
          <div class="timer" :class="{ warning: timeLeft < 300 }">
            <el-icon><Clock /></el-icon>
            <span>{{ formatTime(timeLeft) }}</span>
          </div>
          <el-button type="primary" @click="handleSubmit">
            提交试卷
          </el-button>
        </div>
      </div>

      <!-- Main Content -->
      <div class="exam-body">
        <!-- Question Navigation Sidebar -->
        <div class="question-nav">
          <div class="nav-title">答题卡</div>
          <div class="nav-grid">
            <div
              v-for="(q, idx) in questions"
              :key="q.id"
              class="nav-item"
              :class="{
                active: currentIndex === idx,
                answered: answers[idx]
              }"
              @click="currentIndex = idx"
            >
              {{ idx + 1 }}
            </div>
          </div>
          <div class="nav-stats">
            <div class="stat-row">
              <span>已答</span>
              <span class="value">{{ answeredCount }}</span>
            </div>
            <div class="stat-row">
              <span>未答</span>
              <span class="value">{{ questions.length - answeredCount }}</span>
            </div>
          </div>
        </div>

        <!-- Question Display -->
        <div class="question-area">
          <QuestionCard
            v-if="currentQuestion"
            :question="currentQuestion"
            :index="currentIndex"
            :model-value="answers[currentIndex]"
            @update:model-value="handleAnswer"
            :show-favorite="false"
          />

          <div class="navigation-buttons">
            <el-button
              :disabled="currentIndex === 0"
              @click="currentIndex--"
            >
              上一题
            </el-button>
            <el-button
              v-if="currentIndex < questions.length - 1"
              type="primary"
              @click="currentIndex++"
            >
              下一题
            </el-button>
            <el-button
              v-else
              type="success"
              @click="handleSubmit"
            >
              提交试卷
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Submit Confirmation Dialog -->
    <el-dialog
      v-model="showSubmitDialog"
      title="提交试卷"
      width="500px"
      :close-on-click-modal="false"
    >
      <div class="submit-dialog-content">
        <el-alert
          :type="unansweredCount > 0 ? 'warning' : 'info'"
          :closable="false"
          show-icon
        >
          <template #title>
            <div v-if="unansweredCount > 0">
              还有 {{ unansweredCount }} 题未作答
            </div>
            <div v-else>
              已完成所有题目
            </div>
          </template>
        </el-alert>

        <div class="submit-stats">
          <div class="stat-item">
            <span class="label">已答题数</span>
            <span class="value">{{ answeredCount }} / {{ questions.length }}</span>
          </div>
          <div class="stat-item">
            <span class="label">剩余时间</span>
            <span class="value">{{ formatTime(timeLeft) }}</span>
          </div>
        </div>

        <p class="submit-warning">
          提交后将不能修改答案，确定要提交吗？
        </p>
      </div>

      <template #footer>
        <el-button @click="showSubmitDialog = false">继续答题</el-button>
        <el-button type="primary" @click="confirmSubmit" :loading="submitting">
          确认提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Clock } from '@element-plus/icons-vue'
import QuestionCard from '@/components/QuestionCard.vue'
import { examApi } from '@/api'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const error = ref('')
const examTitle = ref('')
const questions = ref([])
const currentIndex = ref(0)
const answers = ref([])
const timeLeft = ref(0) // seconds
const showSubmitDialog = ref(false)
const submitting = ref(false)

let timerInterval = null
let sessionData = null

const currentQuestion = computed(() => questions.value[currentIndex.value])

const answeredCount = computed(() =>
  answers.value.filter(a => a).length
)

const unansweredCount = computed(() =>
  questions.value.length - answeredCount.value
)

async function loadExam() {
  try {
    loading.value = true
    error.value = ''

    // Get session data
    const sessionStr = sessionStorage.getItem('exam-session')
    if (!sessionStr) {
      error.value = '考试会话已过期'
      return
    }

    sessionData = JSON.parse(sessionStr)
    examTitle.value = sessionData.examTitle

    // Calculate time left
    const elapsed = Math.floor((Date.now() - sessionData.startTime) / 1000)
    timeLeft.value = Math.max(0, sessionData.duration * 60 - elapsed)

    if (timeLeft.value === 0) {
      error.value = '考试时间已结束'
      await autoSubmit()
      return
    }

    // Get questions from session data
    questions.value = sessionData.questions || []
    answers.value = new Array(questions.value.length).fill('')

    if (questions.value.length === 0) {
      error.value = '试卷加载失败'
      return
    }

    // Start timer
    startTimer()

  } catch (err) {
    console.error('Failed to load exam:', err)
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function startTimer() {
  timerInterval = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--
    } else {
      clearInterval(timerInterval)
      ElMessage.warning('考试时间到，自动提交')
      autoSubmit()
    }
  }, 1000)
}

function handleAnswer(value) {
  answers.value[currentIndex.value] = value
}

function formatTime(seconds) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = seconds % 60

  if (hours > 0) {
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  }
  return `${minutes}:${String(secs).padStart(2, '0')}`
}

function handleSubmit() {
  showSubmitDialog.value = true
}

async function confirmSubmit() {
  if (!sessionData) return

  try {
    submitting.value = true

    // Build answer payload
    const answerList = questions.value.map((q, idx) => ({
      questionId: q.id,
      userAnswer: answers.value[idx] || ''
    }))

    const duration = Math.floor((Date.now() - sessionData.startTime) / 1000 / 60)

    await examApi.submit(sessionData.examId, answerList, duration)

    ElMessage.success('提交成功')

    // Clear session
    sessionStorage.removeItem('exam-session')
    showSubmitDialog.value = false

    // Navigate to history detail
    router.push({ name: 'exam-history', params: { id: sessionData.examId } })

  } catch (err) {
    console.error('Failed to submit exam:', err)
    ElMessage.error('提交失败：' + (err.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

async function autoSubmit() {
  if (!sessionData) return

  try {
    const answerList = questions.value.map((q, idx) => ({
      questionId: q.id,
      userAnswer: answers.value[idx] || ''
    }))

    const duration = Math.floor((Date.now() - sessionData.startTime) / 1000 / 60)

    await examApi.submit(sessionData.examId, answerList, duration)
    sessionStorage.removeItem('exam-session')

    ElMessageBox.alert('考试时间已到，已自动提交', '提示', {
      confirmButtonText: '查看成绩',
      callback: () => {
        router.push({ name: 'exam-history', params: { id: sessionData.examId } })
      }
    })

  } catch (err) {
    console.error('Auto submit failed:', err)
    ElMessage.error('自动提交失败')
  }
}

onMounted(() => {
  loadExam()

  // Warn before leaving
  const handleBeforeUnload = (e) => {
    e.preventDefault()
    e.returnValue = ''
  }
  window.addEventListener('beforeunload', handleBeforeUnload)

  onUnmounted(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload)
    if (timerInterval) {
      clearInterval(timerInterval)
    }
  })
})
</script>

<style scoped>
.exam-run {
  min-height: 100vh;
  background: var(--el-fill-color-light);
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

.exam-content {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.exam-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: white;
  border-bottom: 1px solid var(--el-border-color);
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-left h3 {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.question-count {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.timer {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-color-primary);
  padding: 8px 16px;
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
}

.timer.warning {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.exam-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.question-nav {
  width: 280px;
  background: white;
  border-right: 1px solid var(--el-border-color);
  padding: 20px;
  overflow-y: auto;
}

.nav-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--el-text-color-primary);
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  margin-bottom: 20px;
}

.nav-item {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.nav-item:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.nav-item.active {
  background: var(--el-color-primary);
  color: white;
  border-color: var(--el-color-primary);
}

.nav-item.answered:not(.active) {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success);
  color: var(--el-color-success);
}

.nav-stats {
  border-top: 1px solid var(--el-border-color);
  padding-top: 16px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 14px;
}

.stat-row .value {
  font-weight: 600;
  color: var(--el-color-primary);
}

.question-area {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.navigation-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.submit-dialog-content {
  padding: 20px 0;
}

.submit-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin: 20px 0;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-item .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-item .value {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.submit-warning {
  text-align: center;
  color: var(--el-text-color-secondary);
  margin: 10px 0;
}

@media (max-width: 768px) {
  .exam-header {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    padding: 12px;
  }

  .header-left,
  .header-right {
    justify-content: space-between;
  }

  .timer {
    font-size: 16px;
  }

  .exam-body {
    flex-direction: column;
  }

  .question-nav {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid var(--el-border-color);
    padding: 12px;
  }

  .nav-grid {
    grid-template-columns: repeat(8, 1fr);
  }

  .question-area {
    padding: 12px;
  }

  .navigation-buttons {
    flex-direction: column;
  }

  .navigation-buttons .el-button {
    width: 100%;
  }
}
</style>
