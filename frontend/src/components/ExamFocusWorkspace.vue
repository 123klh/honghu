<template>
  <div class="exam-focus-shell">
    <!-- 顶部极简专注栏：替代完整侧栏与面包屑 -->
    <header class="focus-topbar">
      <div class="focus-topbar-left">
        <el-button text @click="emit('exit')">← 返回</el-button>
        <span class="focus-title">{{ title }}</span>
        <span class="focus-progress num-tabular">
          第 {{ currentIndex + 1 }} / {{ questions.length }} 题
        </span>
      </div>

      <div class="focus-topbar-right">
        <span class="timer-pill num-tabular" title="本题用时">
          本题 {{ formatSeconds(questionSeconds) }}
        </span>
        <span
          v-if="remainingSeconds !== null"
          class="timer-pill is-countdown num-tabular"
          :class="{ 'is-urgent': remainingSeconds <= 300 }"
          title="剩余考试时间"
        >
          剩余 {{ formatSeconds(remainingSeconds) }}
        </span>
        <el-button type="primary" @click="emit('submit', answers)">交卷</el-button>
      </div>
    </header>

    <!-- 专注双栏：左侧限宽题干与选项 + 右侧吸顶答题卡 -->
    <div v-if="currentQuestion" class="exam-focus-layout">
      <main class="question-panel">
        <div class="question-meta-row">
          <span class="q-badge">{{ currentQuestion.category || '行测单选' }}</span>
          <span v-if="currentQuestion.difficulty" class="q-meta-muted">
            难度 {{ currentQuestion.difficulty }}
          </span>
          <el-button
            text
            size="small"
            class="flag-btn"
            :type="flaggedSet.has(currentQuestion.id) ? 'warning' : 'default'"
            @click="toggleFlag(currentQuestion.id)"
          >
            {{ flaggedSet.has(currentQuestion.id) ? '已标记疑难 (F)' : '标记疑难 (F)' }}
          </el-button>
        </div>

        <div class="stem">{{ currentQuestion.stem }}</div>

        <div class="options-list" role="radiogroup" aria-label="题目选项">
          <div
            v-for="(opt, idx) in currentQuestion.options"
            :key="opt.key"
            class="option-item"
            :class="{
              'is-selected': answers[currentQuestion.id] === opt.key,
              'is-eliminated': isEliminated(currentQuestion.id, opt.key)
            }"
            role="radio"
            :aria-checked="answers[currentQuestion.id] === opt.key"
            tabindex="0"
            @click="selectOption(currentQuestion.id, opt.key)"
            @contextmenu.prevent="toggleEliminate(currentQuestion.id, opt.key)"
          >
            <span class="opt-key num-tabular">{{ opt.key }}</span>
            <span class="opt-text">{{ opt.text }}</span>
            <button
              type="button"
              class="eliminate-btn"
              title="排除该选项（也可右键点击选项）"
              @click.stop="toggleEliminate(currentQuestion.id, opt.key)"
            >
              {{ isEliminated(currentQuestion.id, opt.key) ? '恢复' : '排除' }}
            </button>
          </div>
        </div>

        <footer class="question-footer">
          <span class="shortcut-hint">
            快捷键：A–D / 1–4 选答案 · ← / → 切题 · 右键排除选项 · F 标记
          </span>
          <div class="nav-actions">
            <el-button :disabled="currentIndex === 0" @click="goTo(currentIndex - 1)">
              上一题
            </el-button>
            <el-button
              type="primary"
              :disabled="currentIndex >= questions.length - 1"
              @click="goTo(currentIndex + 1)"
            >
              下一题
            </el-button>
          </div>
        </footer>
      </main>

      <!-- 右侧吸顶答题卡 -->
      <aside class="answer-sheet-aside" aria-label="答题卡">
        <div class="sheet-header">
          <span class="sheet-title">答题卡</span>
          <span class="sheet-stat num-tabular">
            已答 {{ answeredCount }}/{{ questions.length }}
          </span>
        </div>

        <div class="sheet-grid">
          <button
            v-for="(q, idx) in questions"
            :key="q.id"
            type="button"
            class="sheet-cell num-tabular"
            :class="{
              'is-current': idx === currentIndex,
              'is-answered': Boolean(answers[q.id]),
              'is-flagged': flaggedSet.has(q.id)
            }"
            @click="goTo(idx)"
          >
            {{ idx + 1 }}
          </button>
        </div>

        <div class="sheet-legend">
          <span><i class="dot is-answered" />已答</span>
          <span><i class="dot is-flagged" />疑难</span>
          <span><i class="dot" />未答</span>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

const props = defineProps({
  title: { type: String, default: '行测专项专注刷题' },
  questions: {
    type: Array,
    default: () => []
  },
  initialAnswers: {
    type: Object,
    default: () => ({})
  },
  /** 模考总倒计时（秒）；传 null 表示普通刷题不限时 */
  totalSeconds: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['autosave', 'submit', 'exit'])

const currentIndex = ref(0)
const answers = reactive({ ...props.initialAnswers })
const flaggedSet = reactive(new Set())
const eliminatedMap = reactive({})

const questionSeconds = ref(0)
const remainingSeconds = ref(props.totalSeconds)
let tickTimer = null
let autosaveTimer = null

const currentQuestion = computed(() => props.questions[currentIndex.value] || null)
const answeredCount = computed(
  () => props.questions.filter((q) => Boolean(answers[q.id])).length
)

function goTo(index) {
  if (index < 0 || index >= props.questions.length) return
  currentIndex.value = index
  questionSeconds.value = 0
}

function selectOption(questionId, optionKey) {
  answers[questionId] = optionKey
  // 若该选项此前被排除，选中时自动取消排除态
  if (eliminatedMap[questionId]?.has(optionKey)) {
    eliminatedMap[questionId].delete(optionKey)
  }
  scheduleAutosave(questionId, optionKey)
}

function toggleFlag(questionId) {
  if (flaggedSet.has(questionId)) {
    flaggedSet.delete(questionId)
  } else {
    flaggedSet.add(questionId)
  }
}

function toggleEliminate(questionId, optionKey) {
  if (!eliminatedMap[questionId]) {
    eliminatedMap[questionId] = new Set()
  }
  const set = eliminatedMap[questionId]
  if (set.has(optionKey)) {
    set.delete(optionKey)
  } else {
    set.add(optionKey)
    if (answers[questionId] === optionKey) {
      delete answers[questionId]
    }
  }
}

function isEliminated(questionId, optionKey) {
  return Boolean(eliminatedMap[questionId]?.has(optionKey))
}

function scheduleAutosave(questionId, answer) {
  clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(() => {
    emit('autosave', {
      questionId,
      answer,
      answers: { ...answers },
      spentSeconds: questionSeconds.value
    })
  }, 400)
}

function formatSeconds(sec) {
  const s = Math.max(0, Number(sec) || 0)
  const mm = String(Math.floor(s / 60)).padStart(2, '0')
  const ss = String(s % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function onKeydown(e) {
  const tag = e.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || e.metaKey || e.ctrlKey || e.altKey) {
    return
  }
  const q = currentQuestion.value
  if (!q) return

  const key = e.key.toUpperCase()
  const keyIndexMap = { '1': 0, '2': 1, '3': 2, '4': 3, A: 0, B: 1, C: 2, D: 3 }

  if (key in keyIndexMap) {
    const opt = q.options?.[keyIndexMap[key]]
    if (opt) {
      e.preventDefault()
      selectOption(q.id, opt.key)
    }
  } else if (e.key === 'ArrowRight' || e.key === 'Enter') {
    if (currentIndex.value < props.questions.length - 1) {
      e.preventDefault()
      goTo(currentIndex.value + 1)
    }
  } else if (e.key === 'ArrowLeft') {
    if (currentIndex.value > 0) {
      e.preventDefault()
      goTo(currentIndex.value - 1)
    }
  } else if (key === 'F') {
    e.preventDefault()
    toggleFlag(q.id)
  }
}

watch(
  () => props.totalSeconds,
  (val) => {
    remainingSeconds.value = val
  }
)

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  tickTimer = setInterval(() => {
    questionSeconds.value += 1
    if (remainingSeconds.value !== null && remainingSeconds.value > 0) {
      remainingSeconds.value -= 1
      if (remainingSeconds.value === 0) {
        emit('submit', { ...answers, autoSubmitted: true })
      }
    }
  }, 1000)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  clearInterval(tickTimer)
  clearTimeout(autosaveTimer)
})
</script>

<style scoped>
.exam-focus-shell {
  min-height: 100vh;
  background: var(--hh-bg);
  padding: 0 20px 48px;
}

.focus-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  max-width: 1124px;
  height: 58px;
  margin: 0 auto 24px;
  background: var(--hh-bg);
  border-bottom: 1px solid var(--hh-border);
}

.focus-topbar-left,
.focus-topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.focus-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--hh-text);
}

.focus-progress {
  font-size: 13px;
  color: var(--hh-muted);
}

.timer-pill {
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--hh-border);
  background: var(--hh-surface);
  font-size: 13px;
  color: var(--hh-text-secondary);
}

.timer-pill.is-urgent {
  border-color: var(--hh-danger);
  color: var(--hh-danger);
  font-weight: 600;
}

.question-meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.q-badge {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--hh-tint);
  color: var(--hh-primary);
  font-size: 12px;
  font-weight: 600;
}

.q-meta-muted {
  font-size: 12px;
  color: var(--hh-muted);
}

.flag-btn {
  margin-left: auto;
}

.options-list {
  margin-top: 22px;
}

.opt-key {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  line-height: 22px;
  text-align: center;
  border-radius: 6px;
  border: 1px solid var(--hh-border-strong);
  font-size: 13px;
  font-weight: 600;
  color: var(--hh-text-secondary);
}

.option-item.is-selected .opt-key {
  background: var(--hh-primary);
  border-color: var(--hh-primary);
  color: #ffffff;
}

.opt-text {
  flex: 1;
  font-size: 15.5px;
  line-height: 1.65;
}

.eliminate-btn {
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: var(--hh-muted);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.eliminate-btn:hover {
  background: var(--hh-hover-bg);
  color: var(--hh-text);
}

.question-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 28px;
  padding-top: 18px;
  border-top: 1px solid var(--hh-border);
}

.shortcut-hint {
  font-size: 12px;
  color: var(--hh-muted);
}

.nav-actions {
  display: flex;
  gap: 10px;
}

.sheet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.sheet-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--hh-text);
}

.sheet-stat {
  font-size: 12px;
  color: var(--hh-muted);
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.sheet-cell {
  height: 34px;
  border: 1px solid var(--hh-border);
  border-radius: 8px;
  background: var(--hh-surface);
  color: var(--hh-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.sheet-cell.is-answered {
  background: var(--hh-tint);
  border-color: var(--hh-primary);
  color: var(--hh-primary);
  font-weight: 600;
}

.sheet-cell.is-flagged {
  border-color: var(--hh-warning);
  box-shadow: inset 0 0 0 1px var(--hh-warning);
}

.sheet-cell.is-current {
  background: var(--hh-primary);
  border-color: var(--hh-primary);
  color: #ffffff;
  font-weight: 600;
}

.sheet-legend {
  display: flex;
  gap: 14px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--hh-border);
  font-size: 12px;
  color: var(--hh-muted);
}

.sheet-legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 2px;
  margin-right: 5px;
  border: 1px solid var(--hh-border-strong);
  vertical-align: middle;
}

.sheet-legend .dot.is-answered {
  background: var(--hh-primary);
  border-color: var(--hh-primary);
}

.sheet-legend .dot.is-flagged {
  background: var(--hh-warning);
  border-color: var(--hh-warning);
}

@media (max-width: 720px) {
  .shortcut-hint {
    display: none;
  }
}
</style>
