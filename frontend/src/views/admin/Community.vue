<template>
  <div class="admin-community">
    <div class="page-header">
      <h2>社区管理</h2>
      <el-button @click="loadDiscussions" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <div class="content">
      <!-- Filter Bar -->
      <div class="filter-bar">
        <el-select
          v-model="filterStatus"
          placeholder="状态筛选"
          clearable
          style="width: 150px"
          @change="loadDiscussions"
        >
          <el-option label="全部" value="" />
          <el-option label="置顶" value="pinned" />
          <el-option label="普通" value="normal" />
        </el-select>

        <el-input
          v-model="searchKeyword"
          placeholder="搜索标题或内容"
          clearable
          style="width: 300px"
          @change="loadDiscussions"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- Discussions Table -->
      <div class="table-card">
        <el-table
          :data="discussions"
          v-loading="loading"
          stripe
          style="width: 100%"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.pinned" type="warning" size="small">
                <el-icon><Star /></el-icon>
                置顶
              </el-tag>
              <el-tag v-else type="info" size="small">普通</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="250">
            <template #default="{ row }">
              <div class="discussion-title">{{ row.title }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="authorName" label="作者" width="120" />
          <el-table-column label="统计" width="140">
            <template #default="{ row }">
              <div class="stats-cell">
                <span><el-icon><View /></el-icon> {{ row.viewCount }}</span>
                <span><el-icon><ChatDotRound /></el-icon> {{ row.replyCount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="发布时间" width="160">
            <template #default="{ row }">
              {{ formatDate(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="viewDiscussion(row)">
                查看
              </el-button>
              <el-button
                v-if="!row.pinned"
                size="small"
                text
                type="warning"
                @click="pinDiscussion(row)"
              >
                置顶
              </el-button>
              <el-button
                v-else
                size="small"
                text
                @click="unpinDiscussion(row)"
              >
                取消置顶
              </el-button>
              <el-button
                size="small"
                text
                type="danger"
                @click="deleteDiscussion(row)"
              >
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
            @current-change="loadDiscussions"
          />
        </div>
      </div>
    </div>

    <!-- View Dialog -->
    <el-dialog
      v-model="showViewDialog"
      title="讨论详情"
      width="800px"
    >
      <div v-if="viewingDiscussion" class="discussion-detail">
        <div class="detail-header">
          <h3>{{ viewingDiscussion.title }}</h3>
          <el-tag v-if="viewingDiscussion.pinned" type="warning">置顶</el-tag>
        </div>

        <div class="detail-meta">
          <span>作者: {{ viewingDiscussion.authorName }}</span>
          <span>发布时间: {{ formatDate(viewingDiscussion.createdAt) }}</span>
          <span>浏览: {{ viewingDiscussion.viewCount }}</span>
          <span>回复: {{ viewingDiscussion.replyCount }}</span>
        </div>

        <div class="detail-content">
          <div class="content-label">内容:</div>
          <div class="content-text">{{ viewingDiscussion.content }}</div>
        </div>

        <div v-if="replies.length > 0" class="replies-section">
          <div class="replies-header">回复列表 ({{ replies.length }})</div>
          <div class="replies-list">
            <div
              v-for="reply in replies"
              :key="reply.id"
              class="reply-item"
            >
              <div class="reply-header">
                <span class="reply-author">{{ reply.authorName }}</span>
                <span class="reply-time">{{ formatDate(reply.createdAt) }}</span>
                <el-button
                  size="small"
                  text
                  type="danger"
                  @click="deleteReply(reply)"
                >
                  删除
                </el-button>
              </div>
              <div class="reply-content">{{ reply.content }}</div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  Search,
  Star,
  View,
  ChatDotRound
} from '@element-plus/icons-vue'
import { communityApi } from '@/api'

const loading = ref(false)
const discussions = ref([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const filterStatus = ref('')
const searchKeyword = ref('')

const showViewDialog = ref(false)
const viewingDiscussion = ref(null)
const replies = ref([])

async function loadDiscussions() {
  try {
    loading.value = true

    const params = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (filterStatus.value === 'pinned') params.pinned = true
    if (filterStatus.value === 'normal') params.pinned = false
    if (searchKeyword.value) params.keyword = searchKeyword.value

    const data = await communityApi.adminList(params)
    discussions.value = data.items || []
    total.value = data.total || 0

  } catch (err) {
    console.error('Failed to load discussions:', err)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

async function viewDiscussion(discussion) {
  try {
    const data = await communityApi.detail(discussion.id)
    viewingDiscussion.value = data.discussion
    replies.value = data.replies || []
    showViewDialog.value = true
  } catch (err) {
    console.error('Failed to load discussion detail:', err)
    ElMessage.error('加载详情失败')
  }
}

async function pinDiscussion(discussion) {
  try {
    await communityApi.pin(discussion.id)
    ElMessage.success('置顶成功')
    loadDiscussions()
  } catch (err) {
    console.error('Failed to pin discussion:', err)
    ElMessage.error('置顶失败')
  }
}

async function unpinDiscussion(discussion) {
  try {
    await communityApi.unpin(discussion.id)
    ElMessage.success('已取消置顶')
    loadDiscussions()
  } catch (err) {
    console.error('Failed to unpin discussion:', err)
    ElMessage.error('操作失败')
  }
}

async function deleteDiscussion(discussion) {
  try {
    await ElMessageBox.confirm(
      `确定要删除讨论"${discussion.title}"吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await communityApi.adminDelete(discussion.id)
    ElMessage.success('删除成功')
    loadDiscussions()

  } catch (err) {
    if (err === 'cancel') return
    console.error('Failed to delete discussion:', err)
    ElMessage.error('删除失败')
  }
}

async function deleteReply(reply) {
  try {
    await ElMessageBox.confirm(
      '确定要删除这条回复吗？此操作不可恢复。',
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await communityApi.adminDeleteReply(reply.id)
    ElMessage.success('删除成功')

    // Reload discussion detail
    if (viewingDiscussion.value) {
      const data = await communityApi.detail(viewingDiscussion.value.id)
      replies.value = data.replies || []
    }

  } catch (err) {
    if (err === 'cancel') return
    console.error('Failed to delete reply:', err)
    ElMessage.error('删除失败')
  }
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
  loadDiscussions()
})
</script>

<style scoped>
.admin-community {
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

.discussion-title {
  line-height: 1.6;
  word-break: break-word;
}

.stats-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stats-cell span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.discussion-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 2px solid var(--el-border-color);
}

.detail-header h3 {
  margin: 0;
  font-size: 20px;
  color: var(--el-text-color-primary);
  flex: 1;
}

.detail-meta {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.content-label {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.content-text {
  line-height: 1.8;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.replies-section {
  border-top: 1px solid var(--el-border-color);
  padding-top: 20px;
}

.replies-header {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 16px;
}

.replies-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reply-item {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border-left: 3px solid var(--el-color-primary);
}

.reply-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
}

.reply-author {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.reply-time {
  color: var(--el-text-color-secondary);
  flex: 1;
}

.reply-content {
  line-height: 1.6;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

@media (max-width: 768px) {
  .admin-community {
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

  .detail-meta {
    flex-direction: column;
    gap: 8px;
  }
}
</style>
