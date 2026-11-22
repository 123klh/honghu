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

    <ExamFocusWorkspace
      v-else-if="questions.length > 0"
      :title="examTitle"
      :questions="focusQuestions"
      :initial-answers="initialAnswers"
      :total-seconds="timeLeft"
      @autosave="handleAutosave"
      @submit="handleFocusSubmit"
      @exit="handleExit"
    />

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
import { Loading } from '@element-plus/icons-vue'
import ExamFocusWorkspace from '@/components/ExamFocusWorkspace.vue'
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

let sessionData = null

const answeredCount = computed(() =>
  answers.value.filter(a => a).length
)

const unansweredCount = computed(() =>
  questions.value.length - answeredCount.value
)

const focusQuestions = computed(() => questions.value.map((question) => {
  const options = Array.isArray(question.options)
    ? question.options.map((option, index) => ({
        key: option.key || option.value || String.fromCharCode(65 + index),
        text: option.text || option.label || ''
      }))
    : ['A', 'B', 'C', 'D']
        .map((key) => ({ key, text: question[`option${key}`] }))
        .filter((option) => option.text)
  return {
    id: question.id ?? question.questionId,
    category: question.categoryName || question.category || '行测单选',
    difficulty: question.difficulty || question.difficultyLevel,
    stem: question.questionStem || question.stem || '',
    options
  }
}))

const initialAnswers = computed(() => Object.fromEntries(
  questions.value.map((question, index) => [question.id ?? question.questionId, answers.value[index] || ''])
))

function draftKey() {
  return `honghu-exam-draft:${sessionData?.examId || route.params.examId}`
}

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

    // Get questions from session data
    questions.value = sessionData.questions || []
    answers.value = new Array(questions.value.length).fill('')
    const savedDraft = localStorage.getItem(draftKey())
    if (savedDraft) {
      const draft = JSON.parse(savedDraft)
      answers.value = questions.value.map((question) => draft.answers?.[question.id ?? question.questionId] || '')
    }

    if (questions.value.length === 0) {
      error.value = '试卷加载失败'
      return
    }

    // Calculate time left
    const elapsed = Math.floor((Date.now() - sessionData.startTime) / 1000)
    timeLeft.value = Math.max(0, sessionData.duration * 60 - elapsed)

    if (timeLeft.value === 0) {
      error.value = '考试时间已结束'
      await autoSubmit()
      return
    }

  } catch (err) {
    console.error('Failed to load exam:', err)
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
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
  if (sessionData) {
    timeLeft.value = Math.max(0, sessionData.duration * 60 - Math.floor((Date.now() - sessionData.startTime) / 1000))
  }
  showSubmitDialog.value = true
}

function handleAutosave(payload) {
  const answerMap = payload?.answers || Object.fromEntries(
    Object.entries(payload || {}).filter(([key]) => key !== 'autoSubmitted')
  )
  answers.value = questions.value.map((question) => answerMap[question.id ?? question.questionId] || '')
  localStorage.setItem(draftKey(), JSON.stringify({ answers: answerMap, savedAt: Date.now() }))
}

function handleFocusSubmit(payload) {
  handleAutosave(payload)
  if (payload?.autoSubmitted) {
    ElMessage.warning('考试时间到，自动提交')
    autoSubmit()
    return
  }
  handleSubmit()
}

function handleExit() {
  router.push('/exams')
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

    const duration = Math.floor((Date.now() - sessionData.startTime) / 1000)

    await examApi.submit(sessionData.examId, answerList, duration)

    ElMessage.success('提交成功')

    // Clear session
    sessionStorage.removeItem('exam-session')
    localStorage.removeItem(draftKey())
    showSubmitDialog.value = false

    router.push({ name: 'exam-history' })

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

    const duration = Math.floor((Date.now() - sessionData.startTime) / 1000)

    await examApi.submit(sessionData.examId, answerList, duration)
    sessionStorage.removeItem('exam-session')
    localStorage.removeItem(draftKey())

    ElMessageBox.alert('考试时间已到，已自动提交', '提示', {
      confirmButtonText: '查看成绩',
      callback: () => {
        router.push({ name: 'exam-history' })
      }
    })

  } catch (err) {
    console.error('Auto submit failed:', err)
    ElMessage.error('自动提交失败')
  }
}

const handleBeforeUnload = (e) => {
  e.preventDefault()
  e.returnValue = ''
}

onMounted(() => {
  loadExam()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
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
