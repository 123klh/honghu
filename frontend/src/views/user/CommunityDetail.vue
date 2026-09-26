<template>
  <div class="community-detail">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
      <p>加载讨论详情中...</p>
    </div>

    <div v-else-if="error" class="error-container">
      <el-alert type="error" :title="error" show-icon :closable="false" />
      <el-button @click="$router.push('/community')" style="margin-top: 20px">
        返回社区
      </el-button>
    </div>

    <div v-else-if="discussion" class="detail-content">
      <!-- Discussion Post -->
      <div class="discussion-card">
        <div class="card-header">
          <h1>{{ discussion.title }}</h1>
          <el-tag v-if="discussion.pinned" type="warning">
            <el-icon><Star /></el-icon>
            置顶
          </el-tag>
        </div>

        <div class="author-info">
          <div class="author-left">
            <el-avatar :size="40">{{ discussion.authorName?.charAt(0) }}</el-avatar>
            <div class="author-details">
              <div class="author-name">{{ discussion.authorName }}</div>
              <div class="post-time">{{ formatDate(discussion.createdAt) }}</div>
            </div>
          </div>
          <div class="stats">
            <span class="stat-item">
              <el-icon><View /></el-icon>
              {{ discussion.viewCount }}
            </span>
            <span class="stat-item">
              <el-icon><ChatDotRound /></el-icon>
              {{ discussion.replyCount }}
            </span>
          </div>
        </div>

        <div class="discussion-content">
          {{ discussion.content }}
        </div>

        <div v-if="isOwner" class="discussion-actions">
          <el-button size="small" text type="danger" @click="deleteDiscussion">
            <el-icon><Delete /></el-icon>
            删除讨论
          </el-button>
        </div>
      </div>

      <!-- Replies Section -->
      <div class="replies-section">
        <div class="section-header">
          <h3>全部回复 ({{ replies.length }})</h3>
        </div>

        <div v-if="replies.length === 0" class="no-replies">
          <el-empty description="还没有回复，快来发表第一条吧！" :image-size="120" />
        </div>

        <div v-else class="replies-list">
          <div
            v-for="reply in replies"
            :key="reply.id"
            class="reply-item"
          >
            <div class="reply-header">
              <div class="reply-author">
                <el-avatar :size="32">{{ reply.authorName?.charAt(0) }}</el-avatar>
                <div class="author-details">
                  <div class="author-name">{{ reply.authorName }}</div>
                  <div class="reply-time">{{ formatDate(reply.createdAt) }}</div>
                </div>
              </div>
              <el-button
                v-if="isReplyOwner(reply)"
                size="small"
                text
                type="danger"
                @click="deleteReply(reply.id)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>

            <div class="reply-content">
              {{ reply.content }}
            </div>
          </div>
        </div>
      </div>

      <!-- Reply Form -->
      <div class="reply-form-section">
        <h4>发表回复</h4>
        <el-input
          v-model="replyContent"
          type="textarea"
          :rows="4"
          placeholder="写下你的想法..."
          maxlength="1000"
          show-word-limit
        />
        <div class="form-actions">
          <el-button
            type="primary"
            :disabled="!replyContent.trim()"
            :loading="submitting"
            @click="submitReply"
          >
            发表回复
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Loading,
  Star,
  View,
  ChatDotRound,
  Delete
} from '@element-plus/icons-vue'
import { communityApi } from '@/api'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const loading = ref(true)
const error = ref('')
const discussion = ref(null)
const replies = ref([])
const replyContent = ref('')
const submitting = ref(false)

const isOwner = computed(() => {
  return discussion.value &&
         authStore.user &&
         discussion.value.authorId === authStore.user.id
})

function isReplyOwner(reply) {
  return authStore.user && reply.authorId === authStore.user.id
}

async function loadDiscussion() {
  try {
    loading.value = true
    error.value = ''

    const discussionId = route.params.id
    if (!discussionId) {
      error.value = '缺少讨论ID'
      return
    }

    const data = await communityApi.detail(discussionId)
    discussion.value = data.discussion
    replies.value = data.replies || []

  } catch (err) {
    console.error('Failed to load discussion:', err)
    error.value = err.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function submitReply() {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }

  try {
    submitting.value = true

    await communityApi.reply(discussion.value.id, replyContent.value)

    ElMessage.success('回复成功')
    replyContent.value = ''

    // Reload discussion to get updated replies
    await loadDiscussion()

  } catch (err) {
    console.error('Failed to submit reply:', err)
    ElMessage.error('回复失败：' + (err.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

async function deleteDiscussion() {
  try {
    await ElMessageBox.confirm(
      '确定要删除这个讨论吗？删除后无法恢复。',
      '删除讨论',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await communityApi.delete(discussion.value.id)
    ElMessage.success('删除成功')
    router.push('/community')

  } catch (err) {
    if (err === 'cancel') return
    console.error('Failed to delete discussion:', err)
    ElMessage.error('删除失败')
  }
}

async function deleteReply(replyId) {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条回复吗？',
      '删除回复',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await communityApi.deleteReply(replyId)
    ElMessage.success('删除成功')
    await loadDiscussion()

  } catch (err) {
    if (err === 'cancel') return
    console.error('Failed to delete reply:', err)
    ElMessage.error('删除失败')
  }
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

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(() => {
  loadDiscussion()
})
</script>

<style scoped>
.community-detail {
  max-width: 900px;
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
  gap: 20px;
}

.discussion-card,
.replies-section,
.reply-form-section {
  background: white;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.card-header h1 {
  margin: 0;
  font-size: 24px;
  color: var(--el-text-color-primary);
  line-height: 1.4;
  flex: 1;
}

.author-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color);
}

.author-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.author-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.author-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.post-time,
.reply-time {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stats {
  display: flex;
  gap: 16px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.discussion-content {
  font-size: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  margin-bottom: 16px;
}

.discussion-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);
}

.section-header {
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--el-border-color);
}

.section-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.no-replies {
  padding: 40px 0;
  text-align: center;
}

.replies-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.reply-item {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border-left: 3px solid var(--el-color-primary);
}

.reply-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.reply-author {
  display: flex;
  align-items: center;
  gap: 10px;
}

.reply-content {
  font-size: 15px;
  line-height: 1.6;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

.reply-form-section h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 768px) {
  .community-detail {
    padding: 12px;
  }

  .discussion-card,
  .replies-section,
  .reply-form-section {
    padding: 16px;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .card-header h1 {
    font-size: 20px;
  }

  .author-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .discussion-content {
    font-size: 15px;
  }

  .reply-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
