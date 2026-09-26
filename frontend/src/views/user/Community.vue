<template>
  <div class="community-page">
    <div class="page-header">
      <h2>学习社区</h2>
      <p class="subtitle">交流学习心得，分享备考经验</p>
    </div>

    <div class="content">
      <!-- Action Bar -->
      <div class="action-bar">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Edit /></el-icon>
          发布讨论
        </el-button>
      </div>

      <!-- Discussions List -->
      <div v-if="loading" class="loading-container">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>

      <div v-else-if="discussions.length === 0" class="empty-state">
        <el-empty description="还没有讨论，来发表第一条吧！" />
      </div>

      <div v-else class="discussions-list">
        <div
          v-for="discussion in discussions"
          :key="discussion.id"
          class="discussion-card"
          @click="goToDiscussion(discussion.id)"
        >
          <div v-if="discussion.pinned" class="pinned-badge">
            <el-icon><Star /></el-icon>
            置顶
          </div>

          <div class="discussion-header">
            <h3>{{ discussion.title }}</h3>
          </div>

          <div class="discussion-content">
            {{ getTruncatedContent(discussion.content) }}
          </div>

          <div class="discussion-meta">
            <div class="meta-left">
              <div class="author">
                <el-icon><User /></el-icon>
                <span>{{ discussion.authorName }}</span>
              </div>
              <div class="time">
                <el-icon><Clock /></el-icon>
                <span>{{ formatDate(discussion.createdAt) }}</span>
              </div>
            </div>

            <div class="meta-right">
              <div class="stat-item">
                <el-icon><View /></el-icon>
                <span>{{ discussion.viewCount }}</span>
              </div>
              <div class="stat-item">
                <el-icon><ChatDotRound /></el-icon>
                <span>{{ discussion.replyCount }}</span>
              </div>
            </div>
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
          @current-change="loadDiscussions"
        />
      </div>
    </div>

    <!-- Create Discussion Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="发布讨论"
      width="700px"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item label="标题" prop="title">
          <el-input
            v-model="form.title"
            placeholder="请输入讨论标题"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="10"
            placeholder="分享你的学习心得、备考经验、疑难问题..."
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitDiscussion" :loading="submitting">
          发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Loading,
  Edit,
  Star,
  User,
  Clock,
  View,
  ChatDotRound
} from '@element-plus/icons-vue'
import { communityApi } from '@/api'

const router = useRouter()

const loading = ref(false)
const discussions = ref([])
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

const showCreateDialog = ref(false)
const submitting = ref(false)
const formRef = ref(null)

const form = reactive({
  title: '',
  content: ''
})

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { min: 5, max: 100, message: '标题长度应在 5-100 字符之间', trigger: 'blur' }
  ],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' },
    { min: 10, max: 2000, message: '内容长度应在 10-2000 字符之间', trigger: 'blur' }
  ]
}

async function loadDiscussions() {
  try {
    loading.value = true

    const data = await communityApi.list({
      page: currentPage.value,
      size: pageSize.value
    })

    discussions.value = data.items || []
    total.value = data.total || 0

  } catch (err) {
    console.error('Failed to load discussions:', err)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function goToDiscussion(id) {
  router.push({ name: 'community-detail', params: { id } })
}

async function submitDiscussion() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  try {
    submitting.value = true

    await communityApi.create({
      title: form.title,
      content: form.content
    })

    ElMessage.success('发布成功')
    showCreateDialog.value = false
    form.title = ''
    form.content = ''

    // Reload discussions
    currentPage.value = 1
    loadDiscussions()

  } catch (err) {
    console.error('Failed to create discussion:', err)
    ElMessage.error('发布失败：' + (err.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

function getTruncatedContent(content) {
  if (!content) return ''
  return content.length > 150 ? content.substring(0, 150) + '...' : content
}

function formatDate(dateStr) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now - date

  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 7) return `${days} 天前`

  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  })
}

onMounted(() => {
  loadDiscussions()
})
</script>

<style scoped>
.community-page {
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

.content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  padding: 20px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
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

.empty-state {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 8px;
}

.discussions-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.discussion-card {
  position: relative;
  background: white;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition: all 0.3s;
}

.discussion-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.pinned-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: linear-gradient(135deg, #ffd700, #ffed4e);
  color: #b8860b;
  font-size: 12px;
  font-weight: 600;
  border-radius: 12px;
}

.discussion-header h3 {
  margin: 0 0 12px 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.discussion-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.discussion-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);
}

.meta-left,
.meta-right {
  display: flex;
  gap: 20px;
  align-items: center;
}

.author,
.time,
.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-item {
  font-weight: 500;
}

.pagination {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: white;
  border-radius: 8px;
}

@media (max-width: 768px) {
  .community-page {
    padding: 12px;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .action-bar {
    padding: 16px;
  }

  .discussion-card {
    padding: 16px;
  }

  .discussion-header h3 {
    font-size: 16px;
    padding-right: 80px;
  }

  .discussion-meta {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .meta-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
