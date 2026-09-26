<template>
  <div class="shenlun-detail">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
      <p>加载题目中...</p>
    </div>

    <div v-else-if="error" class="error-container">
      <el-alert type="error" :title="error" show-icon :closable="false" />
      <el-button @click="$router.push('/shenlun')" style="margin-top: 20px">
        返回列表
      </el-button>
    </div>

    <div v-else-if="question" class="detail-content">
      <!-- Question Info -->
      <div class="question-section">
        <div class="section-header">
          <h2>题目要求</h2>
          <div class="meta-tags">
            <el-tag v-if="question.year" type="info">{{ question.year }}年</el-tag>
            <el-tag v-if="question.region">{{ question.region }}</el-tag>
          </div>
        </div>

        <div class="question-content">
          <div class="question-stem">{{ question.stem }}</div>

          <div v-if="question.material" class="question-material">
            <h4>给定材料</h4>
            <div class="material-text">{{ question.material }}</div>
          </div>

          <div class="requirements">
            <div class="requirement-item">
              <el-icon><Edit /></el-icon>
              <span>字数要求：{{ question.wordLimit || 800 }} 字</span>
            </div>
            <div class="requirement-item">
              <el-icon><Star /></el-icon>
              <span>总分：{{ question.totalScore || 100 }} 分</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Answer Section -->
      <div class="answer-section">
        <div class="section-header">
          <h3>我的作答</h3>
          <div class="word-count" :class="{ warning: wordCount > (question.wordLimit || 800) }">
            已输入 {{ wordCount }} 字
            <span v-if="question.wordLimit">/ {{ question.wordLimit }} 字</span>
          </div>
        </div>

        <el-input
          v-model="userAnswer"
          type="textarea"
          :rows="20"
          placeholder="请在此输入你的答案..."
          :disabled="scoring || !!scoreResult"
          maxlength="5000"
          show-word-limit
        />

        <div class="action-buttons">
          <el-button
            v-if="!scoreResult"
            type="primary"
            size="large"
            :disabled="!userAnswer.trim() || scoring"
            :loading="scoring"
            @click="submitAnswer"
          >
            提交评分
          </el-button>

          <el-button
            v-if="scoreResult"
            size="large"
            @click="resetAnswer"
          >
            重新作答
          </el-button>
        </div>
      </div>

      <!-- Score Result -->
      <div v-if="scoreResult" class="score-section">
        <div class="score-header">
          <h3>评分结果</h3>
          <div class="total-score">
            <span class="score-number">{{ scoreResult.totalScore }}</span>
            <span class="score-total">/ {{ question.totalScore || 100 }}</span>
          </div>
        </div>

        <!-- Dimension Scores -->
        <div class="dimensions">
          <div
            v-for="dim in scoreResult.dimensions"
            :key="dim.dimension"
            class="dimension-card"
          >
            <div class="dimension-header">
              <span class="dimension-name">{{ dim.dimension }}</span>
              <span class="dimension-score">{{ dim.score }} / {{ dim.maxScore }}</span>
            </div>
            <el-progress
              :percentage="(dim.score / dim.maxScore) * 100"
              :color="getProgressColor(dim.score, dim.maxScore)"
            />
            <p v-if="dim.feedback" class="dimension-feedback">
              {{ dim.feedback }}
            </p>
          </div>
        </div>

        <!-- Overall Feedback -->
        <div v-if="scoreResult.overallFeedback" class="overall-feedback">
          <h4>综合评价</h4>
          <p>{{ scoreResult.overallFeedback }}</p>
        </div>

        <!-- Reference Answer -->
        <div v-if="question.referenceAnswer" class="reference-section">
          <el-collapse>
            <el-collapse-item title="查看参考答案" name="reference">
              <div class="reference-content">
                {{ question.referenceAnswer }}
              </div>
            </el-collapse-item>
          </el-collapse>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Edit, Star } from '@element-plus/icons-vue'
import { shenlunApi } from '@/api'

const router = useRouter()
const route = useRoute()

const loading = ref(true)
const error = ref('')
const question = ref(null)
const userAnswer = ref('')
const scoring = ref(false)
const scoreResult = ref(null)

const wordCount = computed(() => {
  return userAnswer.value.trim().length
})

async function loadQuestion() {
  try {
    loading.value = true
    error.value = ''

    const questionId = route.params.id
    if (!questionId) {
      error.value = '缺少题目ID'
      return
    }

    const data = await shenlunApi.detail(questionId)
    question.value = data

  } catch (err) {
    console.error('Failed to load question:', err)
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function submitAnswer() {
  if (!userAnswer.value.trim()) {
    ElMessage.warning('请输入答案')
    return
  }

  if (question.value.wordLimit && wordCount.value > question.value.wordLimit * 1.2) {
    ElMessage.warning(`答案字数过多，建议控制在 ${question.value.wordLimit} 字以内`)
    return
  }

  try {
    scoring.value = true

    const result = await shenlunApi.score(question.value.id, userAnswer.value)
    scoreResult.value = result

    ElMessage.success('评分完成')

  } catch (err) {
    console.error('Failed to score answer:', err)
    ElMessage.error('评分失败：' + (err.message || '未知错误'))
  } finally {
    scoring.value = false
  }
}

function resetAnswer() {
  userAnswer.value = ''
  scoreResult.value = null
}

function getProgressColor(score, maxScore) {
  const percentage = (score / maxScore) * 100
  if (percentage >= 80) return '#67c23a'
  if (percentage >= 60) return '#e6a23c'
  return '#f56c6c'
}

onMounted(() => {
  loadQuestion()
})
</script>

<style scoped>
.shenlun-detail {
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

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.question-section,
.answer-section,
.score-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color);
}

.section-header h2,
.section-header h3 {
  margin: 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
}

.meta-tags {
  display: flex;
  gap: 8px;
}

.question-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.question-stem {
  font-size: 18px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  font-weight: 500;
}

.question-material {
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border-left: 4px solid var(--el-color-primary);
}

.question-material h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  color: var(--el-color-primary);
}

.material-text {
  line-height: 2;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

.requirements {
  display: flex;
  gap: 24px;
  padding: 16px;
  background: var(--el-color-info-light-9);
  border-radius: 8px;
}

.requirement-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.requirement-item .el-icon {
  font-size: 18px;
  color: var(--el-color-primary);
}

.word-count {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.word-count.warning {
  color: var(--el-color-danger);
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
}

.action-buttons .el-button {
  min-width: 150px;
}

.score-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.total-score {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.score-number {
  font-size: 48px;
  font-weight: 700;
  color: var(--el-color-primary);
}

.score-total {
  font-size: 20px;
  color: var(--el-text-color-secondary);
}

.dimensions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.dimension-card {
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.dimension-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.dimension-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.dimension-score {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.dimension-feedback {
  margin: 12px 0 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.overall-feedback {
  padding: 20px;
  background: var(--el-color-primary-light-9);
  border-radius: 8px;
  border-left: 4px solid var(--el-color-primary);
  margin-bottom: 20px;
}

.overall-feedback h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  color: var(--el-color-primary);
}

.overall-feedback p {
  margin: 0;
  line-height: 1.8;
  color: var(--el-text-color-primary);
}

.reference-section {
  margin-top: 20px;
}

.reference-content {
  line-height: 2;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

@media (max-width: 768px) {
  .shenlun-detail {
    padding: 12px;
  }

  .question-section,
  .answer-section,
  .score-section {
    padding: 16px;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .question-stem {
    font-size: 16px;
  }

  .requirements {
    flex-direction: column;
    gap: 12px;
  }

  .dimensions {
    grid-template-columns: 1fr;
  }

  .score-number {
    font-size: 36px;
  }

  .action-buttons {
    flex-direction: column;
  }

  .action-buttons .el-button {
    width: 100%;
  }
}
</style>
