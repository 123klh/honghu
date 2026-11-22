<template>
  <div class="home-page">
    <section class="welcome-panel surface">
      <div class="welcome-person">
        <span class="welcome-avatar">{{ auth.displayName.charAt(0).toUpperCase() }}</span>
        <div>
          <h1>你好，{{ auth.displayName }}</h1>
          <p>今天也要坚持刷题，上岸就在前方。</p>
        </div>
      </div>
      <div class="welcome-actions">
        <span class="welcome-date">{{ today }}</span>
        <el-button type="primary" @click="$router.push('/practice')"><el-icon><EditPen /></el-icon>开始练习</el-button>
      </div>
    </section>

    <HomeKpiStrip
      :total-answered="stats?.totalAnswered ?? 0"
      :today-answered="todayAnswered"
      :accuracy-rate="stats?.accuracy ?? 0"
      :submitted-exams="stats?.examsTaken ?? 0"
      :last-exam-score="lastExamScore"
      :due-wrong-count="wrong?.wrongCount ?? 0"
      @review-wrong="$router.push('/wrong')"
    />

    <div class="home-panels">
      <section class="surface chart-panel">
        <div class="panel-heading"><h2>近30天答题趋势</h2><router-link to="/stats">学习统计<el-icon><ArrowRight /></el-icon></router-link></div>
        <LearningChart v-if="stats" kind="activity" :data="stats.dailyActivity || []" compact />
        <div v-else class="chart-placeholder">{{ statsFailed ? '统计数据暂时无法加载，请刷新重试' : '正在加载答题趋势…' }}</div>
      </section>
      <section class="surface chart-panel">
        <div class="panel-heading"><h2>科目答题量分布</h2><el-icon><PieChart /></el-icon></div>
        <LearningChart v-if="stats" kind="subject" :data="stats.subjectBreakdown || []" compact />
        <div v-else class="chart-placeholder">{{ statsFailed ? '统计数据暂时无法加载，请刷新重试' : '正在加载科目分布…' }}</div>
      </section>
      <section class="surface quick-panel">
        <div class="panel-heading"><h2>快速开始</h2><span class="muted">选择今天的学习内容</span></div>
        <div class="entry-grid">
          <router-link v-for="entry in entries" :key="entry.to" :to="entry.to" class="entry-link">
            <span class="entry-icon" :class="'entry--' + entry.tone"><el-icon><component :is="entry.icon" /></el-icon></span>
            <span class="entry-copy"><strong>{{ entry.name }}</strong><span>{{ entry.desc }}</span></span>
            <el-icon class="entry-arrow"><ArrowRight /></el-icon>
          </router-link>
        </div>
      </section>
      <section class="surface community-panel">
        <div class="panel-heading"><h2>社区动态</h2><router-link to="/community">全部讨论<el-icon><ArrowRight /></el-icon></router-link></div>
        <div v-if="discussions.length" class="discussion-preview-list">
          <router-link v-for="discussion in discussions" :key="discussion.id" :to="'/community/' + discussion.id" class="discussion-preview">
            <span class="discussion-avatar">{{ discussion.authorName?.charAt(0) || '学' }}</span>
            <span class="discussion-preview-copy"><strong>{{ discussion.title }}</strong><span>{{ discussion.authorName }} · {{ formatDate(discussion.createdAt) }}<span class="reply-number">{{ discussion.replyCount }} 条回复</span></span></span>
          </router-link>
        </div>
        <div v-else class="community-empty">{{ discussionsFailed ? '社区动态暂时无法加载' : '还没有讨论，去社区交流学习心得吧。' }}</div>
        <router-link to="/community" class="community-footer"><el-icon><ChatDotRound /></el-icon>交流学习心得，分享备考经验<el-icon><ArrowRight /></el-icon></router-link>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { statsApi, practiceApi, communityApi, examApi } from '../../api'
import { useAuthStore } from '../../store/auth'
import LearningChart from '../../components/LearningChart.vue'
import HomeKpiStrip from '../../components/HomeKpiStrip.vue'

const auth = useAuthStore()
const stats = ref(null)
const wrong = ref(null)
const discussions = ref([])
const lastExamScore = ref(null)
const statsFailed = ref(false)
const discussionsFailed = ref(false)
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
const todayAnswered = computed(() => {
  const now = new Date()
  const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  return Number(stats.value?.dailyActivity?.find(item => String(item.day).slice(0, 10) === date)?.answered || 0)
})
const entries = [
  { name: '模块刷题', desc: '按题型专项训练', to: '/practice', icon: 'EditPen', tone: 'purple' },
  { name: '模拟考试', desc: '整卷计时实战', to: '/exams', icon: 'Notebook', tone: 'orange' },
  { name: '申论评分', desc: '按点给分可解释', to: '/shenlun', icon: 'Document', tone: 'green' },
  { name: '错题本', desc: '重练薄弱题目', to: '/wrong', icon: 'Collection', tone: 'pink' },
  { name: '学习社区', desc: '提问与讨论', to: '/community', icon: 'ChatDotRound', tone: 'blue' },
  { name: '学习数据', desc: '趋势与画像', to: '/stats', icon: 'DataLine', tone: 'purple' }
]
function formatDate(value) {
  return value ? new Date(value).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) : ''
}
onMounted(async () => {
  const results = await Promise.allSettled([
    statsApi.me(30), practiceApi.wrongStats(), communityApi.list({ pageNum: 1, pageSize: 3 }), examApi.history()
  ])
  if (results[0].status === 'fulfilled') stats.value = results[0].value
  else statsFailed.value = true
  if (results[1].status === 'fulfilled') wrong.value = results[1].value
  if (results[2].status === 'fulfilled') discussions.value = results[2].value.items
  else discussionsFailed.value = true
  if (results[3].status === 'fulfilled') {
    lastExamScore.value = results[3].value.find(record => record.status === 'SUBMITTED' && record.score != null)?.score ?? null
  }
})
</script>

<style scoped>
.home-page { max-width: 1440px; margin: 0 auto; display: flex; flex-direction: column; gap: 20px; }
.welcome-panel { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 22px 24px; }
.welcome-person { display: flex; align-items: center; gap: 15px; min-width: 0; }
.welcome-avatar { flex-shrink: 0; display: grid; place-items: center; width: 54px; height: 54px; border-radius: 50%; background: #eeefff; color: #646cff; font-size: 23px; font-weight: 600; border: 4px solid #f5f5ff; }
.welcome-panel h1 { margin: 0 0 5px; font-size: 23px; font-weight: 650; overflow-wrap: anywhere; }
.welcome-panel p { margin: 0; color: #8491a5; font-size: 13px; }
.welcome-actions { display: flex; align-items: center; gap: 20px; flex-shrink: 0; }
.welcome-date { color: #8491a5; font-size: 12px; }
.welcome-actions .el-icon { margin-right: 6px; }
.home-panels {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr);
  gap: 1px;
  border: 1px solid var(--hh-border-strong);
  border-radius: var(--hh-radius-sm);
  overflow: hidden;
  background: var(--hh-border-strong);
}
.home-panels > section { background: var(--hh-surface); }
.chart-panel { padding: 21px 22px 16px; min-width: 0; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-heading h2 { margin: 0; font-size: 16px; font-weight: 600; }
.panel-heading a { display: inline-flex; align-items: center; gap: 3px; font-size: 12px; white-space: nowrap; }
.panel-heading > .el-icon { color: #a2a8d9; }
.chart-placeholder { display: grid; place-items: center; height: 255px; color: #8491a5; font-size: 13px; }
.quick-panel, .community-panel { padding: 21px 22px; min-width: 0; }
.entry-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px 14px; }
.entry-link { display: flex; align-items: center; gap: 12px; padding: 16px 8px; border-radius: 9px; color: #273044; transition: background .15s; min-width: 0; }
.entry-link:hover { background: #f8f9ff; }
.entry-icon { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 11px; flex-shrink: 0; font-size: 20px; }
.entry--purple { color: #646cff; background: #eeefff; }.entry--orange { color: #c68a37; background: #fff3df; }.entry--green { color: #279e86; background: #eaf7f2; }.entry--pink { color: #cd669a; background: #fceef4; }.entry--blue { color: #469ecd; background: #eaf5fc; }
.entry-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.entry-copy strong { font-size: 14px; font-weight: 550; }.entry-copy > span { color: #8491a5; font-size: 11px; }
.entry-arrow { margin-left: auto; color: #c1c7d5; font-size: 12px; }
.discussion-preview { display: flex; align-items: flex-start; gap: 11px; padding: 16px 0; border-bottom: 1px solid #f0f2f7; color: #273044; }
.discussion-avatar { display: grid; place-items: center; width: 32px; height: 32px; background: #f1f1ff; color: #8887cc; font-size: 12px; border-radius: 50%; flex-shrink: 0; }
.discussion-preview-copy { display: flex; flex-direction: column; gap: 6px; min-width: 0; flex: 1; }
.discussion-preview-copy strong { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.discussion-preview-copy > span { font-size: 11px; color: #8491a5; }.reply-number { margin-left: 8px; }
.community-footer { display: flex; align-items: center; gap: 7px; color: #8491a5; font-size: 12px; padding-top: 16px; }.community-footer > :last-child { margin-left: auto; }
.community-empty { display: grid; place-items: center; min-height: 150px; text-align: center; color: #8491a5; font-size: 13px; }
@media (max-width: 1100px) { .welcome-date { display: none; }.home-panels { grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr); }.entry-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) { .home-page { gap: 16px; }.welcome-panel { padding: 20px 18px; flex-wrap: wrap; gap: 15px; }.welcome-panel h1 { font-size: 20px; }.welcome-actions { width: 100%; justify-content: flex-end; }.welcome-date { display: block; margin-right: auto; }.home-panels { grid-template-columns: minmax(0, 1fr); }.chart-panel, .quick-panel, .community-panel { padding: 18px; }.entry-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px; }.entry-link { gap: 9px; padding: 13px 2px; }.entry-icon { width: 35px; height: 35px; }.entry-arrow { display: none; }.entry-copy > span { font-size: 10px; }.panel-heading h2 { font-size: 15px; } }
@media (max-width: 360px) { .entry-grid { grid-template-columns: 1fr; } }
</style>
