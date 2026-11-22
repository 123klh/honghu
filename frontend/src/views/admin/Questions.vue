<template>
  <div class="admin-questions">
    <div class="page-header">
      <h2>题库管理</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon>
        添加题目
      </el-button>
    </div>

    <div class="content">
      <!-- Filter Bar -->
      <div class="filter-bar">
        <el-select
          v-model="filterType"
          placeholder="题目类型"
          clearable
          style="width: 200px"
          @change="loadQuestions"
        >
          <el-option label="全部类型" value="" />
          <el-option label="常识判断" value="常识判断" />
          <el-option label="言语理解与表达" value="言语理解与表达" />
          <el-option label="判断推理" value="判断推理" />
          <el-option label="数量关系" value="数量关系" />
          <el-option label="资料分析" value="资料分析" />
        </el-select>

        <el-input
          v-model="searchKeyword"
          placeholder="搜索题干关键词"
          clearable
          style="width: 300px"
          @change="loadQuestions"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>

        <el-button @click="loadQuestions" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>

      <!-- Questions Table -->
      <div class="table-card">
        <el-table
          :data="questions"
          v-loading="loading"
          stripe
          style="width: 100%"
        >
          <el-table-column prop="questionId" label="ID" width="80" />
          <el-table-column prop="questionCode" label="题目编号" width="140" />
          <el-table-column prop="questionType" label="类型" width="140">
            <template #default="{ row }">
              <el-tag size="small">{{ row.questionType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="questionStem" label="题干" min-width="300">
            <template #default="{ row }">
              <div class="question-stem">{{ truncate(row.questionStem, 80) }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="answer" label="答案" width="80" align="center" />
          <el-table-column label="统计" width="120">
            <template #default="{ row }">
              <div class="stats-cell">
                <span>答题: {{ row.answerCount || 0 }}</span>
                <span>收藏: {{ row.favoriteCount || 0 }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="viewQuestion(row)">
                查看
              </el-button>
              <el-button size="small" text type="primary" @click="editQuestion(row)">
                编辑
              </el-button>
              <el-button size="small" text type="danger" @click="deleteQuestion(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next, jumper"
            @current-change="loadQuestions"
          />
        </div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      :title="editingQuestion ? '编辑题目' : '添加题目'"
      width="800px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="题目编号" prop="questionCode">
          <el-input v-model="form.questionCode" placeholder="如: 2023-GK-001" />
        </el-form-item>

        <el-form-item label="题目类型" prop="questionType">
          <el-select v-model="form.questionType" style="width: 100%">
            <el-option label="常识判断" value="常识判断" />
            <el-option label="言语理解与表达" value="言语理解与表达" />
            <el-option label="判断推理" value="判断推理" />
            <el-option label="数量关系" value="数量关系" />
            <el-option label="资料分析" value="资料分析" />
          </el-select>
        </el-form-item>

        <el-form-item label="题干" prop="questionStem">
          <el-input
            v-model="form.questionStem"
            type="textarea"
            :rows="4"
            placeholder="请输入题干"
          />
        </el-form-item>

        <el-form-item label="选项A" prop="optionA">
          <el-input v-model="form.optionA" />
        </el-form-item>

        <el-form-item label="选项B" prop="optionB">
          <el-input v-model="form.optionB" />
        </el-form-item>

        <el-form-item label="选项C" prop="optionC">
          <el-input v-model="form.optionC" />
        </el-form-item>

        <el-form-item label="选项D" prop="optionD">
          <el-input v-model="form.optionD" />
        </el-form-item>

        <el-form-item label="正确答案" prop="answer">
          <el-radio-group v-model="form.answer">
            <el-radio label="A">A</el-radio>
            <el-radio label="B">B</el-radio>
            <el-radio label="C">C</el-radio>
            <el-radio label="D">D</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="解析" prop="explanation">
          <el-input
            v-model="form.explanation"
            type="textarea"
            :rows="3"
            placeholder="请输入题目解析"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">
          {{ editingQuestion ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- View Dialog -->
    <el-dialog
      v-model="showViewDialog"
      title="题目详情"
      width="700px"
    >
      <div v-if="viewingQuestion" class="question-detail">
        <div class="detail-row">
          <span class="label">题目编号:</span>
          <span>{{ viewingQuestion.questionCode }}</span>
        </div>
        <div class="detail-row">
          <span class="label">题目类型:</span>
          <el-tag size="small">{{ viewingQuestion.questionType }}</el-tag>
        </div>
        <div class="detail-section">
          <div class="label">题干:</div>
          <div class="content">{{ viewingQuestion.questionStem }}</div>
        </div>
        <div class="detail-section">
          <div class="label">选项:</div>
          <div class="options">
            <div v-if="viewingQuestion.optionA">A. {{ viewingQuestion.optionA }}</div>
            <div v-if="viewingQuestion.optionB">B. {{ viewingQuestion.optionB }}</div>
            <div v-if="viewingQuestion.optionC">C. {{ viewingQuestion.optionC }}</div>
            <div v-if="viewingQuestion.optionD">D. {{ viewingQuestion.optionD }}</div>
          </div>
        </div>
        <div class="detail-row">
          <span class="label">正确答案:</span>
          <el-tag type="success">{{ viewingQuestion.answer }}</el-tag>
        </div>
        <div class="detail-section">
          <div class="label">解析:</div>
          <div class="content">{{ viewingQuestion.explanation }}</div>
        </div>
        <div class="detail-row">
          <span class="label">答题次数:</span>
          <span>{{ viewingQuestion.answerCount || 0 }}</span>
        </div>
        <div class="detail-row">
          <span class="label">收藏次数:</span>
          <span>{{ viewingQuestion.favoriteCount || 0 }}</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Search,
  Refresh
} from '@element-plus/icons-vue'
import { questionApi } from '@/api'

const loading = ref(false)
const questions = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const filterType = ref('')
const searchKeyword = ref('')

const showCreateDialog = ref(false)
const showViewDialog = ref(false)
const submitting = ref(false)
const formRef = ref(null)
const editingQuestion = ref(null)
const viewingQuestion = ref(null)

const form = reactive({
  questionCode: '',
  questionType: '',
  questionStem: '',
  optionA: '',
  optionB: '',
  optionC: '',
  optionD: '',
  answer: '',
  explanation: ''
})

const rules = {
  questionCode: [
    { required: true, message: '请输入题目编号', trigger: 'blur' }
  ],
  questionType: [
    { required: true, message: '请选择题目类型', trigger: 'change' }
  ],
  questionStem: [
    { required: true, message: '请输入题干', trigger: 'blur' }
  ],
  optionA: [
    { required: true, message: '请输入选项A', trigger: 'blur' }
  ],
  optionB: [
    { required: true, message: '请输入选项B', trigger: 'blur' }
  ],
  answer: [
    { required: true, message: '请选择正确答案', trigger: 'change' }
  ]
}

async function loadQuestions() {
  try {
    loading.value = true

    const params = {
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }

    if (filterType.value) params.questionType = filterType.value
    if (searchKeyword.value) params.keyword = searchKeyword.value

    const data = await questionApi.adminList(params)
    questions.value = data.list || []
    total.value = data.total || 0

  } catch (err) {
    console.error('Failed to load questions:', err)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function viewQuestion(question) {
  viewingQuestion.value = question
  showViewDialog.value = true
}

function editQuestion(question) {
  editingQuestion.value = question
  Object.assign(form, {
    questionCode: question.questionCode,
    questionType: question.questionType,
    questionStem: question.questionStem,
    optionA: question.optionA,
    optionB: question.optionB,
    optionC: question.optionC,
    optionD: question.optionD,
    answer: question.answer,
    explanation: question.explanation
  })
  showCreateDialog.value = true
}

async function submitForm() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    submitting.value = true

    if (editingQuestion.value) {
      await questionApi.adminUpdate(editingQuestion.value.questionId, {
        ...editingQuestion.value,
        ...form
      })
      ElMessage.success('更新成功')
    } else {
      await questionApi.adminCreate(form)
      ElMessage.success('创建成功')
    }

    showCreateDialog.value = false
    resetForm()
    loadQuestions()

  } catch (err) {
    console.error('Failed to submit question:', err)
    ElMessage.error(editingQuestion.value ? '更新失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

async function deleteQuestion(question) {
  try {
    await ElMessageBox.confirm(
      `确定要删除题目"${question.questionCode}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await questionApi.adminDelete(question.questionId)
    ElMessage.success('删除成功')
    loadQuestions()

  } catch (err) {
    if (err === 'cancel') return
    console.error('Failed to delete question:', err)
    ElMessage.error('删除失败')
  }
}

function resetForm() {
  editingQuestion.value = null
  Object.assign(form, {
    questionCode: '',
    questionType: '',
    questionStem: '',
    optionA: '',
    optionB: '',
    optionC: '',
    optionD: '',
    answer: '',
    explanation: ''
  })
  formRef.value?.resetFields()
}

function truncate(text, length) {
  if (!text) return ''
  return text.length > length ? text.substring(0, length) + '...' : text
}

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped>
.admin-questions {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
}

.content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  flex-wrap: wrap;
}

.table-card {
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.question-stem {
  line-height: 1.6;
  word-break: break-word;
}

.stats-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.question-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 80px;
}

.content {
  line-height: 1.6;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

.options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 12px;
}

.options > div {
  line-height: 1.6;
}

@media (max-width: 768px) {
  .admin-questions {
    padding: 12px;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .filter-bar {
    flex-direction: column;
  }

  .filter-bar > * {
    width: 100% !important;
  }
}
</style>
