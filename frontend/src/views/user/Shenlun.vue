<template>
  <div class="shenlun-page">
    <div class="page-header">
      <h2>申论练习</h2>
      <p class="subtitle">综合写作能力训练，AI智能评分</p>
    </div>

    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else class="content">
      <!-- Filter Bar -->
      <div class="filter-bar">
        <el-select
          v-model="filterYear"
          placeholder="选择年份"
          clearable
          style="width: 150px"
          @change="loadQuestions"
        >
          <el-option label="全部年份" value="" />
          <el-option label="2024年" value="2024" />
          <el-option label="2023年" value="2023" />
          <el-option label="2022年" value="2022" />
          <el-option label="2021年" value="2021" />
        </el-select>

        <el-select
          v-model="filterRegion"
          placeholder="选择地区"
          clearable
          style="width: 150px"
          @change="loadQuestions"
        >
          <el-option label="全部地区" value="" />
          <el-option label="国考" value="国考" />
          <el-option label="省考" value="省考" />
        </el-select>

        <el-input
          v-model="searchKeyword"
          placeholder="搜索题目关键词"
          clearable
          style="width: 250px"
          @change="loadQuestions"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- Questions List -->
      <div v-if="questions.length === 0" class="empty-state">
        <el-empty description="没有找到申论题目" />
      </div>

      <div v-else class="questions-grid">
        <div
          v-for="question in questions"
          :key="question.frQuestionId"
          class="question-card"
          @click="goToQuestion(question.frQuestionId)"
        >
          <div class="card-header">
            <h3>{{ question.frQuestionStem }}</h3>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>

          <div class="card-meta">
            <el-tag v-if="question.subject" size="small">
              {{ question.subject }}
            </el-tag>
            <el-tag v-if="question.frQuestionType" size="small" type="info">
              {{ question.frQuestionType }}
            </el-tag>
            <span class="word-limit">
              <el-icon><Document /></el-icon>
              {{ question.points || 0 }} 分
            </span>
          </div>

          <div v-if="question.avgScore !== null" class="last-score">
            <span class="label">平均得分</span>
            <span class="score">{{ question.avgScore }} 分</span>
          </div>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="total > pageSize" class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadQuestions"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Search, ArrowRight, Document } from '@element-plus/icons-vue'
import { shenlunApi } from '@/api'

const router = useRouter()

const loading = ref(false)
const questions = ref([])
const currentPage = ref(1)
const pageSize = ref(12)
const total = ref(0)

const filterYear = ref('')
const filterRegion = ref('')
const searchKeyword = ref('')

async function loadQuestions() {
  try {
    loading.value = true

    const params = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (filterYear.value) params.year = filterYear.value
    if (filterRegion.value) params.region = filterRegion.value
    if (searchKeyword.value) params.keyword = searchKeyword.value

    const data = await shenlunApi.list(params)
    questions.value = data.list || []
    total.value = data.total || 0

  } catch (err) {
    console.error('Failed to load shenlun questions:', err)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function goToQuestion(id) {
  router.push({ name: 'shenlun-detail', params: { id } })
}

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped>
.shenlun-page {
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

.filter-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.empty-state {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 8px;
}

.questions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
}

.question-card {
  position: relative;
  background: white;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 24px;
  cursor: pointer;
  transition: all 0.3s;
}

.question-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  border-color: var(--el-color-primary);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.arrow-icon {
  font-size: 20px;
  color: var(--el-text-color-secondary);
  transition: transform 0.3s;
}

.question-card:hover .arrow-icon {
  transform: translateX(4px);
  color: var(--el-color-primary);
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.word-limit {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.last-score {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--el-color-success-light-9);
  border-radius: 6px;
  margin-top: 12px;
}

.last-score .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.last-score .score {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-color-success);
}

.attempted-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 4px 12px;
  background: var(--el-color-primary);
  color: white;
  font-size: 12px;
  border-radius: 12px;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .shenlun-page {
    padding: 12px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .filter-bar {
    padding: 16px;
  }

  .filter-bar > * {
    width: 100% !important;
  }

  .questions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
