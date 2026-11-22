<template>
  <div class="page practice-page">
    <div class="page-heading">
      <h2>模块刷题</h2>
      <p>选择一个模块，从今天的练习开始。</p>
    </div>

    <div class="module-grid" aria-label="选择练习模块">
      <button
        v-for="t in types"
        :key="t.name"
        type="button"
        class="module-card"
        :class="{ selected: form.questionType === t.name }"
        :aria-pressed="form.questionType === t.name"
        @click="form.questionType = t.name"
      >
        <span class="module-icon"><el-icon><component :is="t.icon" /></el-icon></span>
        <span class="module-name">{{ t.name }}</span>
        <span class="module-detail">{{ t.detail }}</span>
        <span class="module-count">{{ t.count.toLocaleString() }} 题</span>
      </button>
    </div>

    <section class="selection-panel">
      <div class="selection-description">
        <span class="selection-label">当前模块</span>
        <strong>{{ form.questionType }}</strong>
      </div>
      <el-form inline class="practice-controls">
        <el-form-item label="题量">
          <el-input-number v-model="form.count" :min="1" :max="100" aria-label="模块练习题量" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="startModule">
            开始练习<el-icon class="button-arrow"><ArrowRight /></el-icon>
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="recommendation-panel">
      <div class="recommendation-heading">
        <span class="recommendation-icon"><el-icon><MagicStick /></el-icon></span>
        <div>
          <h2>智能推荐练习</h2>
          <p>根据你的答题情况与复习进度，优先练习薄弱知识点。</p>
        </div>
      </div>
      <el-radio-group v-model="mode" class="modes" aria-label="选择推荐模式">
        <el-radio-button label="WEAK">薄弱优先（略简单）</el-radio-button>
        <el-radio-button label="FOCUS">重点突破（同水平）</el-radio-button>
        <el-radio-button label="SECURE">冲刺提分（略难）</el-radio-button>
      </el-radio-group>
      <div class="recommendation-controls">
        <label class="count-label">题量</label>
        <el-input-number v-model="form.count" :min="1" :max="100" aria-label="推荐练习题量" />
        <el-button type="primary" plain :loading="loading" @click="startPersonalized">生成练习</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { practiceApi } from '../../api'
import { Reading, ChatLineSquare, Connection, DataAnalysis, TrendCharts, MagicStick, ArrowRight } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const mode = ref('WEAK')
const form = reactive({ questionType: '常识判断', count: 10 })

// 题量取自题库实际分布（行测五大模块）
const types = [
  { name: '常识判断', count: 2760, icon: Reading, detail: '积累知识，拓宽视野' },
  { name: '言语理解与表达', count: 2200, icon: ChatLineSquare, detail: '读懂语境，准确表达' },
  { name: '判断推理', count: 1536, icon: Connection, detail: '发现规律，理清逻辑' },
  { name: '数量关系', count: 376, icon: DataAnalysis, detail: '梳理方法，提升计算' },
  { name: '资料分析', count: 317, icon: TrendCharts, detail: '分析数据，提高效率' }
]

function go(session, title) {
  if (!session?.questions?.length) {
    ElMessage.warning('没有取到题目')
    return
  }
  sessionStorage.setItem('practice-session', JSON.stringify({ ...session, title }))
  router.push({ name: 'practice-session' })
}

async function startModule() {
  loading.value = true
  try {
    go(await practiceApi.startModule(form.questionType, form.count), form.questionType)
  } catch { /* 拦截器已提示 */ } finally { loading.value = false }
}

async function startPersonalized() {
  loading.value = true
  try {
    go(await practiceApi.startPersonalized(mode.value, form.count), '智能推荐')
  } catch { /* 同上 */ } finally { loading.value = false }
}
</script>

<style scoped>
.practice-page { width: 100%; max-width: 1200px; margin: 0 auto; padding: 0; }
.page-heading { margin-bottom: 24px; }
.page-heading h2 { margin: 0 0 8px; font-size: 24px; font-weight: 650; color: #273044; }
.page-heading p, .recommendation-heading p { margin: 0; color: #8491a5; font-size: 14px; line-height: 1.7; }
.module-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 16px; }
.module-card { display: flex; flex-direction: column; align-items: flex-start; min-width: 0; padding: 22px 18px; border: 1px solid #edf0f6; border-radius: 12px; background: #fff; color: #273044; font: inherit; text-align: left; cursor: pointer; box-shadow: 0 3px 14px rgb(39 48 68 / 3%); transition: border-color .18s, background .18s, box-shadow .18s; }
.module-card:hover { border-color: #c6c9ff; box-shadow: 0 5px 18px rgb(100 108 255 / 8%); }
.module-card:focus-visible { outline: 3px solid #b7bcff; outline-offset: 3px; }
.module-card.selected { border-color: #646cff; background: #f4f4ff; box-shadow: 0 0 0 1px #646cff; }
.module-icon, .recommendation-icon { display: inline-flex; align-items: center; justify-content: center; width: 44px; height: 44px; border-radius: 10px; background: #eeefff; color: #646cff; font-size: 23px; }
.module-card:nth-child(2) .module-icon { background: #eef8ff; color: #399acd; }
.module-card:nth-child(3) .module-icon { background: #effaf7; color: #32a387; }
.module-card:nth-child(4) .module-icon { background: #fff6e9; color: #cb8a2c; }
.module-card:nth-child(5) .module-icon { background: #fff0f5; color: #d36393; }
.module-name { margin-top: 18px; font-size: 15px; font-weight: 650; line-height: 1.6; }
.module-detail { margin-top: 6px; font-size: 12px; color: #8491a5; line-height: 1.8; }
.module-count { margin-top: 22px; font-size: 13px; color: #646cff; font-weight: 600; }
.selection-panel, .recommendation-panel { background: #fff; border: 1px solid #edf0f6; border-radius: 12px; box-shadow: 0 3px 14px rgb(39 48 68 / 3%); }
.selection-panel { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 22px 24px; margin: 20px 0 28px; }
.selection-description { display: flex; flex-direction: column; gap: 7px; }
.selection-label { font-size: 12px; color: #8491a5; }
.selection-description strong { font-size: 15px; font-weight: 600; color: #273044; }
.practice-controls { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.practice-controls :deep(.el-form-item) { margin: 0; }
.button-arrow { margin-left: 7px; }
.recommendation-panel { padding: 26px; }
.recommendation-heading { display: flex; align-items: center; gap: 14px; margin-bottom: 24px; }
.recommendation-heading h2 { margin: 0 0 6px; font-size: 18px; font-weight: 650; color: #273044; }
.recommendation-icon { flex-shrink: 0; }
.modes { display: flex; flex-wrap: wrap; gap: 10px; }
.modes :deep(.el-radio-button__inner) { padding: 12px 18px; border: 1px solid #edf0f6; border-radius: 8px; box-shadow: none; }
.modes :deep(.el-radio-button.is-active .el-radio-button__inner) { background: #eeefff; color: #555ee4; border-color: #646cff; }
.recommendation-controls { display: flex; align-items: center; gap: 12px; margin-top: 24px; flex-wrap: wrap; }
.count-label { font-size: 14px; color: #606b80; }
@media (max-width: 1100px) { .module-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 768px) {
  .page-heading h2 { font-size: 22px; }
  .module-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .module-card { padding: 18px 14px; }
  .module-name { font-size: 14px; }
  .module-detail { font-size: 11px; }
  .selection-panel { align-items: flex-start; flex-direction: column; gap: 18px; padding: 20px; }
  .practice-controls { width: 100%; gap: 14px; }
  .recommendation-panel { padding: 20px; }
  .recommendation-heading { align-items: flex-start; }
  .recommendation-heading p { font-size: 13px; }
  .modes { display: grid; grid-template-columns: 1fr; }
  .modes :deep(.el-radio-button__inner) { width: 100%; text-align: left; }
}
@media (prefers-reduced-motion: reduce) { .module-card { transition: none; } }
</style>
