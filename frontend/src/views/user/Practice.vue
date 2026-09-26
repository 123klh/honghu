<template>
  <div class="page">
    <h2 class="page-title">模块刷题</h2>
    <el-row :gutter="12">
      <el-col :xs="12" :sm="8" v-for="t in types" :key="t.name">
        <el-card class="t" shadow="hover" :class="{ on: form.questionType === t.name }"
                 @click="form.questionType = t.name">
          <div class="tn">{{ t.name }}</div>
          <div class="muted">{{ t.count }} 题</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="opt">
      <el-form inline>
        <el-form-item label="题量">
          <el-input-number v-model="form.count" :min="1" :max="100" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="startModule">开始练习</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <h2 class="page-title">智能推荐练习</h2>
    <p class="muted">依据 IRT 能力值与 SM-2 复习到期时间组卷，优先补薄弱知识点。</p>
    <el-card>
      <el-radio-group v-model="mode" class="modes">
        <el-radio-button label="WEAK">薄弱优先（略简单）</el-radio-button>
        <el-radio-button label="FOCUS">重点突破（同水平）</el-radio-button>
        <el-radio-button label="SECURE">冲刺提分（略难）</el-radio-button>
      </el-radio-group>
      <div class="row">
        <el-input-number v-model="form.count" :min="1" :max="100" />
        <el-button type="success" :loading="loading" @click="startPersonalized">生成练习</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { practiceApi } from '../../api'

const router = useRouter()
const loading = ref(false)
const mode = ref('WEAK')
const form = reactive({ questionType: '常识判断', count: 10 })

// 题量取自题库实际分布（行测五大模块）
const types = [
  { name: '常识判断', count: 2760 },
  { name: '言语理解与表达', count: 2200 },
  { name: '判断推理', count: 1536 },
  { name: '数量关系', count: 376 },
  { name: '资料分析', count: 317 }
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
.t { text-align: center; cursor: pointer; margin-bottom: 12px; }
.t.on { border-color: var(--hh-primary); box-shadow: 0 0 0 1px var(--hh-primary) inset; }
.tn { font-weight: 600; }
.opt { margin: 8px 0 20px; }
.modes { flex-wrap: wrap; }
.row { display: flex; gap: 12px; align-items: center; margin-top: 14px; flex-wrap: wrap; }
</style>
