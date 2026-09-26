<template>
  <div class="favorites-page">
    <div class="page-header">
      <h2>我的收藏</h2>
      <p class="subtitle">重点题目收藏，方便随时复习</p>
    </div>

    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else class="content">
      <!-- Filter Bar -->
      <div class="toolbar">
        <el-select
          v-model="filterType"
          placeholder="题目类型"
          clearable
          style="width: 200px"
          @change="loadFavorites"
        >
          <el-option label="全部类型" value="" />
          <el-option label="常识判断" value="常识判断" />
          <el-option label="言语理解与表达" value="言语理解与表达" />
          <el-option label="判断推理" value="判断推理" />
          <el-option label="数量关系" value="数量关系" />
          <el-option label="资料分析" value="资料分析" />
        </el-select>

        <div class="stats">
          <span>共收藏 {{ total }} 题</span>
        </div>
      </div>

      <!-- Favorites List -->
      <div v-if="favorites.length === 0" class="empty-state">
        <el-empty description="还没有收藏题目">
          <el-button type="primary" @click="$router.push('/practice')">
            去练习
          </el-button>
        </el-empty>
      </div>

      <div v-else class="questions-list">
        <div
          v-for="(item, idx) in favorites"
          :key="item.id"
          class="question-item"
        >
          <div class="question-header">
            <div class="header-left">
              <span class="question-number"># {{ (currentPage - 1) * pageSize + idx + 1 }}</span>
              <el-tag size="small">{{ item.question.questionType }}</el-tag>
            </div>
            <div class="header-right">
              <span class="favorite-time">{{ formatDate(item.createdAt) }}</span>
              <el-button
                text
                type="danger"
                @click="removeFavorite(item.questionId)"
              >
                <el-icon><StarFilled /></el-icon>
                取消收藏
              </el-button>
            </div>
          </div>

          <QuestionCard
            :question="item.question"
            :index="idx"
            model-value=""
            :show-favorite="false"
          />

          <div v-if="item.note" class="note-section">
            <div class="note-header">
              <el-icon><Document /></el-icon>
              <span>我的笔记</span>
            </div>
            <div class="note-content">{{ item.note }}</div>
          </div>

          <div class="question-footer">
            <el-button
              size="small"
              @click="editNote(item)"
            >
              <el-icon><Edit /></el-icon>
              {{ item.note ? '编辑笔记' : '添加笔记' }}
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
          @current-change="loadFavorites"
        />
      </div>
    </div>

    <!-- Note Edit Dialog -->
    <el-dialog
      v-model="showNoteDialog"
      title="编辑笔记"
      width="600px"
    >
      <el-input
        v-model="editingNote"
        type="textarea"
        :rows="6"
        placeholder="记录你对这道题的理解、易错点、解题技巧等..."
        maxlength="500"
        show-word-limit
      />

      <template #footer>
        <el-button @click="showNoteDialog = false">取消</el-button>
        <el-button type="primary" @click="saveNote" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Loading,
  StarFilled,
  Document,
  Edit
} from '@element-plus/icons-vue'
import QuestionCard from '@/components/QuestionCard.vue'
import { favoriteApi } from '@/api'

const router = useRouter()

const loading = ref(false)
const favorites = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filterType = ref('')

const showNoteDialog = ref(false)
const editingItem = ref(null)
const editingNote = ref('')
const saving = ref(false)

async function loadFavorites() {
  try {
    loading.value = true

    const params = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (filterType.value) {
      params.questionType = filterType.value
    }

    const data = await favoriteApi.list(params)
    favorites.value = data.items || []
    total.value = data.total || 0

  } catch (err) {
    console.error('Failed to load favorites:', err)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function removeFavorite(questionId) {
  try {
    await ElMessageBox.confirm(
      '确定要取消收藏这道题吗？',
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await favoriteApi.remove(questionId)
    ElMessage.success('已取消收藏')
    loadFavorites()

  } catch (err) {
    if (err === 'cancel') return
    console.error('Failed to remove favorite:', err)
    ElMessage.error('操作失败')
  }
}

function editNote(item) {
  editingItem.value = item
  editingNote.value = item.note || ''
  showNoteDialog.value = true
}

async function saveNote() {
  if (!editingItem.value) return

  try {
    saving.value = true

    await favoriteApi.updateNote(
      editingItem.value.questionId,
      editingNote.value
    )

    editingItem.value.note = editingNote.value
    ElMessage.success('笔记已保存')
    showNoteDialog.value = false

  } catch (err) {
    console.error('Failed to save note:', err)
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

onMounted(() => {
  loadFavorites()
})
</script>

<style scoped>
.favorites-page {
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

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  gap: 16px;
}

.stats {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.empty-state {
  min-height: 400px;
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

.favorite-time {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.note-section {
  margin-top: 16px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border-left: 4px solid var(--el-color-warning);
}

.note-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-warning);
}

.note-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

.question-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .favorites-page {
    padding: 12px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar > * {
    width: 100% !important;
  }

  .stats {
    text-align: center;
  }

  .question-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
