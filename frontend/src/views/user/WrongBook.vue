<template>
  <div class="wrong-book-page">
    <div class="page-header">
      <h2>错题本</h2>
      <p class="subtitle">重点攻克薄弱环节，提升正确率</p>
    </div>

    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else class="content">
      <!-- Stats Overview -->
      <div class="stats-cards">
        <div class="stat-card">
          <div class="stat-icon" style="background: #fef0f0; color: #f56c6c">
            <el-icon><Close /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.totalWrong }}</div>
            <div class="stat-label">错题总数</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #f0f9ff; color: #409eff">
            <el-icon><Refresh /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.reviewed }}</div>
            <div class="stat-label">已复习</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #f0f9ff; color: #67c23a">
            <el-icon><Check /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.mastered }}</div>
            <div class="stat-label">已掌握</div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon" style="background: #fdf6ec; color: #e6a23c">
            <el-icon><Warning /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.needReview }}</div>
            <div class="stat-label">待复习</div>
          </div>
        </div>
      </div>

      <!-- Filter and Actions -->
      <div class="toolbar">
        <div class="filters">
          <el-select
            v-model="filterType"
            placeholder="题目类型"
            clearable
            style="width: 200px"
            @change="loadWrongQuestions"
          >
            <el-option label="全部类型" value="" />
            <el-option label="常识判断" value="常识判断" />
            <el-option label="言语理解与表达" value="言语理解与表达" />
            <el-option label="判断推理" value="判断推理" />
            <el-option label="数量关系" value="数量关系" />
            <el-option label="资料分析" value="资料分析" />
          </el-select>

          <el-select
            v-model="filterStatus"
            placeholder="掌握状态"
            clearable
            style="width: 150px"
            @change="loadWrongQuestions"
          >
            <el-option label="全部状态" value="" />
            <el-option label="待复习" value="NOT_REVIEWED" />
            <el-option label="已复习" value="REVIEWED" />
            <el-option label="已掌握" value="MASTERED" />
          </el-select>
        </div>

        <div class="actions">
          <el-button
            type="primary"
            :disabled="wrongQuestions.length === 0"
            @click="startReview"
          >
            <el-icon><Edit /></el-icon>
            开始复习
          </el-button>
        </div>
      </div>

      <!-- Wrong Questions List -->
      <div v-if="wrongQuestions.length === 0" class="empty-state">
        <el-empty description="暂无错题" />
      </div>

      <div v-else class="questions-list">
        <div
          v-for="(item, idx) in wrongQuestions"
          :key="item.id"
          class="question-item"
        >
          <div class="question-header">
            <div class="header-left">
              <span class="question-number"># {{ idx + 1 }}</span>
              <el-tag size="small">{{ item.question.questionType }}</el-tag>
              <el-tag
                :type="getStatusType(item.reviewStatus)"
                size="small"
              >
                {{ getStatusText(item.reviewStatus) }}
              </el-tag>
            </div>
            <div class="header-right">
              <span class="wrong-count">错误 {{ item.wrongCount }} 次</span>
              <el-button
                text
                type="danger"
                @click="removeFromWrongBook(item.id)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>

          <QuestionCard
            :question="item.question"
            :index="idx"
            :model-value="item.lastWrongAnswer"
            :result="{
              correct: false,
              correctAnswer: item.question.correctAnswer
            }"
            :disabled="true"
            :show-favorite="true"
            :favored="item.question.favored"
            @toggle-favorite="handleToggleFavorite(item.question)"
          />

          <div class="question-footer">
            <div class="time-info">
              <el-icon><Clock /></el-icon>
              <span>最后错误时间：{{ formatDate(item.lastWrongTime) }}</span>
            </div>
            <el-button
              size="small"
              @click="markAsReviewed(item.id)"
              v-if="item.reviewStatus === 'NOT_REVIEWED'"
            >
              标记为已复习
            </el-button>
            <el-button
              size="small"
              type="success"
              @click="markAsMastered(item.id)"
              v-if="item.reviewStatus !== 'MASTERED'"
            >
              标记为已掌握
            </el-button>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="total > pageSize" class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          @current-change="loadWrongQuestions"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Loading,
  Close,
  Refresh,
  Check,
  Warning,
  Edit,
  Delete,
  Clock
} from '@element-plus/icons-vue'
import QuestionCard from '@/components/QuestionCard.vue'
import { practiceApi, favoriteApi } from '@/api'

const router = useRouter()

const loading = ref(false)
const stats = ref({
  totalWrong: 0,
  reviewed: 0,
  mastered: 0,
  needReview: 0
})
const wrongQuestions = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filterType = ref('')
const filterStatus = ref('')

async function loadStats() {
  try {
    const data = await practiceApi.wrongStats()
    if (data) {
      stats.value = data
    }
  } catch (err) {
    console.error('Failed to load wrong stats:', err)
    // Stats are optional, don't show error to user
  }
}

async function loadWrongQuestions() {
  try {
    loading.value = true

    const data = await practiceApi.wrongList()
    let questions = data || []

    // Apply filters
    if (filterType.value) {
      questions = questions.filter(q => q.questionType === filterType.value)
    }

    // Update stats
    stats.value.totalWrong = questions.length
    stats.value.needReview = questions.length

    // Pagination
    total.value = questions.length
    const start = (currentPage.value - 1) * pageSize.value
    const end = start + pageSize.value
    wrongQuestions.value = questions.slice(start, end).map(q => ({
      id: q.id,
      question: q,
      wrongCount: 1,
      reviewStatus: 'NOT_REVIEWED',
      lastWrongAnswer: '',
      lastWrongTime: new Date().toISOString()
    }))

  } catch (err) {
    console.error('Failed to load wrong questions:', err)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function startReview() {
  if (wrongQuestions.length === 0) {
    ElMessage.warning('没有可复习的错题')
    return
  }

  // Use the wrong book practice API
  router.push({ name: 'practice', query: { mode: 'wrong' } })
}

async function markAsMastered(questionId) {
  try {
    await practiceApi.markMastered(questionId)
    ElMessage.success('已标记为已掌握')
    loadWrongQuestions()
    loadStats()
  } catch (err) {
    console.error('Failed to mark as mastered:', err)
    ElMessage.error('操作失败')
  }
}

async function removeFromWrongBook(questionId) {
  try {
    await ElMessageBox.confirm(
      '确定要从错题本中移除这道题吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await practiceApi.markMastered(questionId)
    ElMessage.success('已移除')
    loadWrongQuestions()
    loadStats()
  } catch (err) {
    if (err === 'cancel') return
    console.error('Failed to remove wrong question:', err)
    ElMessage.error('操作失败')
  }
}

async function markAsReviewed(questionId) {
  // Backend doesn't support reviewed status, use mastered instead
  ElMessage.info('请在复习后标记为已掌握')
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

function getStatusType(status) {
  const map = {
    NOT_REVIEWED: 'warning',
    REVIEWED: 'info',
    MASTERED: 'success'
  }
  return map[status] || ''
}

function getStatusText(status) {
  const map = {
    NOT_REVIEWED: '待复习',
    REVIEWED: '已复习',
    MASTERED: '已掌握'
  }
  return map[status] || status
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
  loadStats()
  loadWrongQuestions()
})
</script>

<style scoped>
.wrong-book-page {
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
  gap: 20px;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.stat-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 28px;
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1;
  margin-bottom: 6px;
}

.stat-label {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  gap: 16px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.empty-state {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 8px;
}

.questions-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.question-item {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color);
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.question-number {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.wrong-count {
  font-size: 14px;
  color: var(--el-color-danger);
  font-weight: 500;
}

.question-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);
}

.time-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .wrong-book-page {
    padding: 12px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .stat-value {
    font-size: 24px;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .filters {
    flex-direction: column;
  }

  .filters > * {
    width: 100% !important;
  }

  .actions {
    width: 100%;
  }

  .actions .el-button {
    width: 100%;
  }

  .question-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .question-footer {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
}
</style>
