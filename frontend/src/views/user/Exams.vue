<template>
  <div class="exams-page">
    <div class="page-header">
      <h2>模拟考试</h2>
      <p class="subtitle">真实考试环境，全面检验复习成果</p>
    </div>

    <el-tabs v-model="activeTab" class="exam-tabs">
      <el-tab-pane label="可用试卷" name="available">
        <div v-if="loading" class="loading-container">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>

        <div v-else-if="availableExams.length === 0" class="empty-state">
          <el-empty description="暂无可用试卷" />
        </div>

        <div v-else class="exam-list">
          <div
            v-for="exam in availableExams"
            :key="exam.id"
            class="exam-card"
          >
            <div class="exam-header">
              <h3>{{ exam.title }}</h3>
              <el-tag v-if="exam.difficulty" :type="getDifficultyType(exam.difficulty)">
                {{ exam.difficulty }}
              </el-tag>
            </div>

            <div class="exam-info">
              <div class="info-item">
                <el-icon><DocumentCopy /></el-icon>
                <span>{{ exam.totalCount }} 题</span>
              </div>
              <div class="info-item">
                <el-icon><Clock /></el-icon>
                <span>{{ exam.duration }} 分钟</span>
              </div>
              <div class="info-item">
                <el-icon><Star /></el-icon>
                <span>总分 {{ exam.totalScore }}</span>
              </div>
            </div>

            <p v-if="exam.description" class="exam-description">
              {{ exam.description }}
            </p>

            <div class="exam-actions">
              <el-button type="primary" @click="startExam(exam)">
                开始考试
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="考试记录" name="history">
        <div v-if="historyLoading" class="loading-container">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>

        <div v-else-if="examHistory.length === 0" class="empty-state">
          <el-empty description="还没有考试记录" />
        </div>

        <div v-else class="history-list">
          <div
            v-for="record in examHistory"
            :key="record.id"
            class="history-card"
          >
            <div class="history-header">
              <h4>{{ record.examTitle }}</h4>
              <el-tag :type="getScoreType(record.scorePercentage)">
                {{ record.score }} 分
              </el-tag>
            </div>

            <div class="history-stats">
              <div class="stat">
                <span class="label">正确率</span>
                <span class="value">{{ record.accuracy }}%</span>
              </div>
              <div class="stat">
                <span class="label">用时</span>
                <span class="value">{{ record.duration }} 分钟</span>
              </div>
              <div class="stat">
                <span class="label">排名</span>
                <span class="value">{{ record.rank || '-' }}</span>
              </div>
            </div>

            <div class="history-time">
              <el-icon><Calendar /></el-icon>
              <span>{{ formatDate(record.startTime) }}</span>
            </div>

            <div class="history-actions">
              <el-button size="small" @click="viewDetail(record)">
                查看详情
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Start Exam Confirmation Dialog -->
    <el-dialog
      v-model="showStartDialog"
      title="开始考试"
      width="500px"
    >
      <div v-if="selectedExam" class="start-dialog-content">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>
            <div>考试须知</div>
          </template>
          <ul>
            <li>考试时长 {{ selectedExam.duration }} 分钟，请合理分配时间</li>
            <li>考试过程中请勿刷新页面或离开，否则视为放弃</li>
            <li>提交后不可修改，请认真作答</li>
            <li>考试结束后可查看详细解析</li>
          </ul>
        </el-alert>

        <div class="exam-summary">
          <h4>{{ selectedExam.title }}</h4>
          <div class="summary-grid">
            <div class="summary-item">
              <span class="label">题目数量</span>
              <span class="value">{{ selectedExam.totalCount }} 题</span>
            </div>
            <div class="summary-item">
              <span class="label">总分</span>
              <span class="value">{{ selectedExam.totalScore }} 分</span>
            </div>
            <div class="summary-item">
              <span class="label">考试时长</span>
              <span class="value">{{ selectedExam.duration }} 分钟</span>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="showStartDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmStart">
          确认开始
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Loading,
  DocumentCopy,
  Clock,
  Star,
  Calendar
} from '@element-plus/icons-vue'
import { examApi } from '@/api'

const router = useRouter()

const activeTab = ref('available')
const loading = ref(false)
const historyLoading = ref(false)
const availableExams = ref([])
const examHistory = ref([])
const showStartDialog = ref(false)
const selectedExam = ref(null)

async function loadAvailableExams() {
  try {
    loading.value = true
    const data = await examApi.list()
    // Map backend field names to frontend field names and filter papers with questions
    availableExams.value = (data || [])
      .filter(paper => paper.questionsCount > 0)
      .map(paper => ({
        id: paper.paperId,
        paperId: paper.paperId,
        title: paper.paperName,
        totalCount: paper.questionsCount,
        duration: paper.examDuration,
        totalScore: paper.questionsCount, // Assume 1 point per question
        description: paper.source + ' - ' + paper.debut,
        difficulty: null
      }))
  } catch (err) {
    console.error('Failed to load exams:', err)
    ElMessage.error('加载试卷失败')
  } finally {
    loading.value = false
  }
}

async function loadExamHistory() {
  try {
    historyLoading.value = true
    const data = await examApi.history()
    examHistory.value = data || []
  } catch (err) {
    console.error('Failed to load exam history:', err)
    ElMessage.error('加载考试记录失败')
  } finally {
    historyLoading.value = false
  }
}

function startExam(exam) {
  selectedExam.value = exam
  showStartDialog.value = true
}

async function confirmStart() {
  if (!selectedExam.value) return

  try {
    // Ensure paperId is a number
    const paperId = Number(selectedExam.value.paperId || selectedExam.value.id)
    const result = await examApi.start(paperId)

    // Store exam session data with questions
    sessionStorage.setItem('exam-session', JSON.stringify({
      examId: result.examId,
      examTitle: selectedExam.value.title,
      duration: selectedExam.value.duration,
      startTime: Date.now(),
      questions: result.questions
    }))

    showStartDialog.value = false
    router.push({ name: 'exam-run', params: { examId: result.examId } })
  } catch (err) {
    console.error('Failed to start exam:', err)
    ElMessage.error('开始考试失败：' + (err.message || '未知错误'))
  }
}

function viewDetail(record) {
  router.push({ name: 'exam-history', params: { id: record.id } })
}

function getDifficultyType(difficulty) {
  const map = {
    '简单': 'success',
    '中等': 'warning',
    '困难': 'danger'
  }
  return map[difficulty] || 'info'
}

function getScoreType(percentage) {
  if (percentage >= 90) return 'success'
  if (percentage >= 60) return 'warning'
  return 'danger'
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

onMounted(() => {
  loadAvailableExams()
  loadExamHistory()
})
</script>

<style scoped>
.exams-page {
  max-width: 1200px;
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

.exam-tabs {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

.loading-container .el-icon {
  font-size: 48px;
  color: var(--el-color-primary);
}

.empty-state {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.exam-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
}

.exam-card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 20px;
  transition: all 0.3s;
}

.exam-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-color: var(--el-color-primary);
}

.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.exam-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
  flex: 1;
}

.exam-info {
  display: flex;
  gap: 20px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.info-item .el-icon {
  font-size: 16px;
}

.exam-description {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
  margin: 12px 0;
}

.exam-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.history-card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 20px;
  transition: all 0.3s;
}

.history-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.history-header h4 {
  margin: 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.history-stats {
  display: flex;
  gap: 40px;
  margin-bottom: 12px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat .label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat .value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.history-time {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 12px;
}

.history-actions {
  display: flex;
  gap: 12px;
}

.start-dialog-content {
  padding: 10px 0;
}

.start-dialog-content ul {
  margin: 0;
  padding-left: 20px;
}

.start-dialog-content li {
  margin: 8px 0;
  line-height: 1.6;
}

.exam-summary {
  margin-top: 20px;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.exam-summary h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.summary-item .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary-item .value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

@media (max-width: 768px) {
  .exams-page {
    padding: 12px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .exam-tabs {
    padding: 12px;
  }

  .exam-list {
    grid-template-columns: 1fr;
  }

  .exam-info {
    gap: 12px;
  }

  .history-stats {
    gap: 20px;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
