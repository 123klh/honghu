<template>
  <div class="favorites-page">
    <div class="page-header">
      <h2>我的收藏</h2>
      <p class="subtitle">重点题目收藏，方便随时复习</p>
    </div>

    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else-if="error" class="error-state">
      <el-alert :title="error" type="error" :closable="false" show-icon />
      <el-button @click="loadFavorites">重新加载</el-button>
    </div>

    <div v-else class="content">
      <!-- Filter Bar -->
      <div class="toolbar">
        <el-select
          v-model="filterType"
          placeholder="题目类型"
          clearable
          style="width: 200px"
          @change="changeFilter"
        >
          <el-option label="全部类型" value="" />
          <el-option label="常识判断" value="常识判断" />
          <el-option label="言语理解与表达" value="言语理解与表达" />
          <el-option label="判断推理" value="判断推理" />
          <el-option label="数量关系" value="数量关系" />
          <el-option label="资料分析" value="资料分析" />
        </el-select>

        <div class="stats">
          <span>共收藏 {{ allFavorites.length }} 题</span>
        </div>
      </div>

      <!-- Favorites List -->
      <div v-if="favorites.length === 0" class="empty-state">
        <el-empty :description="allFavorites.length ? '没有符合该类型的收藏题目' : '还没有收藏题目'">
          <el-button v-if="!allFavorites.length" type="primary" @click="$router.push('/practice')">
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
              <el-tag v-if="item.question" size="small">{{ item.question.questionType }}</el-tag>
              <el-tag v-else size="small" type="info">暂时不可用</el-tag>
            </div>
            <div class="header-right">
              <span class="favorite-time">收藏于 {{ formatDate(item.createdAt) }}</span>
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
            v-if="item.question"
            :question="item.question"
            :index="(currentPage - 1) * pageSize + idx"
            model-value=""
            :disabled="true"
            :show-favorite="false"
          />
          <el-alert v-else title="题目已下架或暂时无法加载" type="info" :closable="false" show-icon />

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
          :pager-count="5"
          layout="prev, pager, next"
          background
        />
      </div>
    </div>

    <!-- Note Edit Dialog -->
    <el-dialog
      v-model="showNoteDialog"
      title="编辑笔记"
      width="min(600px, calc(100vw - 32px))"
    >
      <el-input
        v-model="editingNote"
        type="textarea"
        :rows="6"
        placeholder="记录你对这道题的理解、易错点、解题技巧等..."
        maxlength="200"
        aria-label="收藏题目笔记"
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, StarFilled, Document, Edit } from '@element-plus/icons-vue'
import QuestionCard from '@/components/QuestionCard.vue'
import { favoriteApi, questionApi } from '@/api'

const loading = ref(false)
const error = ref('')
const allFavorites = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const filterType = ref('')

const filteredFavorites = computed(() => allFavorites.value.filter(item =>
  !filterType.value || item.question?.questionType === filterType.value
))
const total = computed(() => filteredFavorites.value.length)
const favorites = computed(() => filteredFavorites.value.slice(
  (currentPage.value - 1) * pageSize.value,
  currentPage.value * pageSize.value
))

const showNoteDialog = ref(false)
const editingItem = ref(null)
const editingNote = ref('')
const saving = ref(false)

function changeFilter() {
  currentPage.value = 1
}

function clampPage() {
  currentPage.value = Math.min(currentPage.value, Math.max(1, Math.ceil(total.value / pageSize.value)))
}

async function loadFavorites() {
  loading.value = true
  error.value = ''
  try {
    const records = await favoriteApi.questions()
    const questions = await Promise.allSettled(records.map(record => questionApi.get(record.questionId)))
    allFavorites.value = records.map((record, index) => ({
      ...record,
      note: record.notes || '',
      question: questions[index].status === 'fulfilled' ? questions[index].value : null
    }))
    clampPage()
  } catch (err) {
    console.error('Failed to load favorites:', err)
    error.value = '收藏暂时无法加载，请重试。'
  } finally {
    loading.value = false
  }
}

async function removeFavorite(questionId) {
  try {
    await ElMessageBox.confirm('确定要取消收藏这道题吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await favoriteApi.removeQuestion(questionId)
    allFavorites.value = allFavorites.value.filter(item => item.questionId !== questionId)
    clampPage()
    ElMessage.success('已取消收藏')
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
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
  saving.value = true
  try {
    await favoriteApi.addQuestion(editingItem.value.questionId, {
      folder: editingItem.value.folder,
      notes: editingNote.value
    })
    editingItem.value.notes = editingNote.value
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
  return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

onMounted(loadFavorites)
</script>

<style scoped>
.favorites-page { width: 100%; max-width: 1200px; margin: 0 auto; padding: 0; }
.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0 0 8px; font-size: 24px; font-weight: 650; color: #273044; }
.subtitle { margin: 0; font-size: 14px; color: #8491a5; line-height: 1.7; }
.loading-container { display: flex; justify-content: center; align-items: center; min-height: 320px; }
.loading-container .el-icon { font-size: 40px; color: #646cff; }
.error-state { display: flex; flex-direction: column; align-items: flex-start; gap: 18px; }
.content, .questions-list { display: flex; flex-direction: column; gap: 20px; }
.toolbar, .question-item, .empty-state, .pagination { background: #fff; border: 1px solid #edf0f6; border-radius: 12px; box-shadow: 0 3px 14px rgb(39 48 68 / 3%); }
.toolbar { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px; gap: 16px; }
.stats { font-size: 13px; color: #8491a5; }
.empty-state { min-height: 300px; display: flex; align-items: center; justify-content: center; }
.question-item { padding: 24px; }
.question-header { display: flex; justify-content: space-between; align-items: center; gap: 18px; margin-bottom: 22px; padding-bottom: 18px; border-bottom: 1px solid #edf0f6; }
.header-left, .header-right { display: flex; align-items: center; gap: 12px; }
.question-number { font-size: 14px; font-weight: 600; color: #646cff; }
.favorite-time { font-size: 12px; color: #8491a5; }
.question-item :deep(.question-card) { border: 0; border-radius: 0; padding: 0; box-shadow: none; }
.question-item :deep(.question-card > .question-header) { display: none; }
.note-section { margin-top: 22px; padding: 18px; background: #f5f5ff; border-radius: 9px; border-left: 3px solid #646cff; }
.note-header { display: flex; align-items: center; gap: 7px; margin-bottom: 10px; font-size: 13px; font-weight: 600; color: #646cff; }
.note-content { font-size: 14px; line-height: 1.9; color: #465168; white-space: pre-wrap; overflow-wrap: anywhere; }
.question-footer { display: flex; justify-content: flex-end; margin-top: 20px; padding-top: 16px; border-top: 1px solid #edf0f6; }
.question-footer :deep(.el-icon) { margin-right: 5px; }
.pagination { display: flex; justify-content: center; padding: 20px; }
@media (max-width: 768px) {
  .page-header h2 { font-size: 22px; }
  .toolbar { flex-direction: column; align-items: stretch; padding: 18px; }
  .toolbar :deep(.el-select) { width: 100% !important; }
  .stats { font-size: 12px; }
  .question-item { padding: 20px 18px; }
  .question-header { flex-direction: column; align-items: flex-start; gap: 12px; }
  .header-right { width: 100%; justify-content: space-between; gap: 8px; }
  .favorite-time { font-size: 11px; }
}
</style>
