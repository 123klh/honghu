<template>
  <div class="community-page">
    <div class="page-header">
      <div>
        <h2>学习社区</h2>
        <p class="subtitle">交流学习心得，分享备考经验</p>
      </div>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Edit /></el-icon><span>发布讨论</span>
      </el-button>
    </div>

    <section class="community-feed">
      <div class="feed-heading">
        <h3>全部讨论</h3>
        <span>{{ total }} 篇讨论</span>
      </div>
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
          :class="{ pinned: discussion.pinned }"
          role="link"
          tabindex="0"
          :aria-label="discussion.title"
          @click="goToDiscussion(discussion.id)"
          @keydown.enter="goToDiscussion(discussion.id)"
          @keydown.space.prevent="goToDiscussion(discussion.id)"
        >
          <div v-if="discussion.pinned" class="pinned-badge"><el-icon><Star /></el-icon>置顶</div>
          <div class="discussion-header"><h3>{{ discussion.title }}</h3></div>
          <div class="discussion-content">{{ getTruncatedContent(discussion.content) }}</div>
          <div class="discussion-meta">
            <div class="meta-left">
              <div class="author">
                <el-avatar :size="28">
                  <span v-if="discussion.authorName">{{ discussion.authorName.charAt(0) }}</span>
                  <el-icon v-else><User /></el-icon>
                </el-avatar>
                <span>{{ discussion.authorName }}</span>
              </div>
              <div class="time"><el-icon><Clock /></el-icon><span>{{ formatDate(discussion.createdAt) }}</span></div>
            </div>
            <div class="stat-item"><el-icon><ChatDotRound /></el-icon><span>{{ discussion.replyCount }} 条回复</span></div>
          </div>
        </div>
      </div>
      <div v-if="total > pageSize" class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          :pager-count="5"
          layout="prev, pager, next"
          background
          @current-change="loadDiscussions"
        />
      </div>
    </section>

    <el-dialog v-model="showCreateDialog" title="发布讨论" width="min(700px, calc(100vw - 32px))">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入讨论标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="8"
            placeholder="分享你的学习心得、备考经验、疑难问题..." maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitDiscussion" :loading="submitting">发布</el-button>
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
      pageNum: currentPage.value,
      pageSize: pageSize.value
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
    await loadDiscussions()

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
.community-page { width: 100%; max-width: 1200px; margin: 0 auto; padding: 0; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
.page-header h2 { margin: 0 0 8px; font-size: 24px; font-weight: 650; color: #273044; }
.subtitle { margin: 0; color: #8491a5; font-size: 14px; line-height: 1.7; }
.page-header .el-button { flex-shrink: 0; }
.page-header .el-button :deep(.el-icon) { margin-right: 6px; }
.community-feed { background: #fff; border: 1px solid #edf0f6; border-radius: 12px; overflow: hidden; box-shadow: 0 3px 14px rgb(39 48 68 / 3%); }
.feed-heading { display: flex; align-items: center; justify-content: space-between; padding: 22px 26px; border-bottom: 1px solid #edf0f6; }
.feed-heading h3 { margin: 0; font-size: 16px; font-weight: 600; color: #273044; }
.feed-heading > span { font-size: 12px; color: #8491a5; }
.loading-container, .empty-state { display: flex; align-items: center; justify-content: center; min-height: 300px; }
.loading-container .el-icon { font-size: 40px; color: #646cff; }
.discussion-card { position: relative; padding: 26px; cursor: pointer; transition: background .18s; }
.discussion-card + .discussion-card { border-top: 1px solid #edf0f6; }
.discussion-card:hover { background: #fcfcff; }
.discussion-card:focus-visible { outline: 2px solid #646cff; outline-offset: -3px; background: #f7f7ff; }
.pinned-badge { position: absolute; top: 25px; right: 26px; display: flex; align-items: center; gap: 4px; padding: 4px 8px; background: #fff5e8; color: #b67c21; font-size: 11px; border-radius: 5px; }
.discussion-header h3 { margin: 0 0 10px; font-size: 17px; font-weight: 600; color: #273044; line-height: 1.6; overflow-wrap: anywhere; }
.discussion-card.pinned .discussion-header h3 { padding-right: 65px; }
.discussion-content { margin-bottom: 20px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; overflow-wrap: anywhere; font-size: 14px; line-height: 1.8; color: #758198; }
.discussion-meta, .meta-left, .author, .time, .stat-item { display: flex; align-items: center; }
.discussion-meta { justify-content: space-between; gap: 16px; }
.meta-left { flex-wrap: wrap; gap: 24px; min-width: 0; }
.author, .time, .stat-item { gap: 7px; font-size: 12px; color: #8491a5; }
.author { color: #606b80; min-width: 0; overflow-wrap: anywhere; }
.author :deep(.el-avatar) { flex-shrink: 0; background: #eeefff; color: #646cff; font-size: 12px; font-weight: 600; }
.stat-item { flex-shrink: 0; }
.pagination { display: flex; justify-content: center; padding: 22px; border-top: 1px solid #edf0f6; }
@media (max-width: 768px) {
  .page-header { align-items: flex-start; gap: 14px; }
  .page-header h2 { font-size: 22px; }
  .subtitle { font-size: 13px; }
  .page-header .el-button { padding-left: 12px; padding-right: 12px; }
  .feed-heading { padding: 18px; }
  .discussion-card { padding: 20px 18px; }
  .discussion-header h3 { font-size: 16px; }
  .discussion-content { font-size: 13px; }
  .pinned-badge { top: 20px; right: 18px; }
  .discussion-meta { align-items: flex-start; }
  .meta-left { gap: 10px 16px; }
  .time { width: 100%; }
  .stat-item { padding-top: 7px; font-size: 11px; }
  .pagination { padding: 18px 10px; }
}
@media (prefers-reduced-motion: reduce) { .discussion-card { transition: none; } }
</style>
