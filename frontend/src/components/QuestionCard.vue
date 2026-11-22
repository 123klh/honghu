<template>
  <div class="question-card">
    <div class="question-header">
      <span class="question-number">第 {{ index + 1 }} 题</span>
      <el-button
        v-if="showFavorite"
        text
        :type="favored ? 'warning' : 'info'"
        :aria-label="favored ? '取消收藏题目' : '收藏题目'"
        @click.stop="$emit('toggle-favorite')"
      >
        <el-icon>
          <StarFilled v-if="favored" />
          <Star v-else />
        </el-icon>
        <span>{{ favored ? '已收藏' : '收藏' }}</span>
      </el-button>
    </div>

    <div class="question-stem">{{ question.questionStem || question.stem }}</div>

    <el-radio-group
      :model-value="modelValue"
      @update:model-value="$emit('update:modelValue', $event)"
      :disabled="disabled"
      class="options-group"
      aria-label="选择答案"
    >
      <el-radio
        v-for="opt in options"
        :key="opt.value"
        :label="opt.value"
        class="option-item"
        :class="{
          selected: opt.value === modelValue,
          correct: result && opt.value === result.correctAnswer,
          wrong: result && !result.correct && opt.value === modelValue
        }"
      >
        <span class="option-label">{{ opt.value }}.</span>
        <span class="option-text">{{ opt.text }}</span>
      </el-radio>
    </el-radio-group>

    <div v-if="result" class="result-section">
      <el-alert
        :type="result.correct ? 'success' : 'error'"
        :closable="false"
        show-icon
      >
        <template #title>
          <span v-if="result.correct">回答正确</span>
          <span v-else>回答错误，正确答案是 {{ result.correctAnswer }}</span>
        </template>
      </el-alert>

      <div v-if="question.explanation" class="explanation">
        <div class="explanation-header">
          <el-icon><InfoFilled /></el-icon>
          <span>解析</span>
        </div>
        <div class="explanation-content">{{ question.explanation }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Star, StarFilled, InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  question: {
    type: Object,
    required: true
  },
  index: {
    type: Number,
    required: true
  },
  modelValue: {
    type: String,
    default: ''
  },
  result: {
    type: Object,
    default: null
  },
  disabled: {
    type: Boolean,
    default: false
  },
  showFavorite: {
    type: Boolean,
    default: false
  },
  favored: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'toggle-favorite'])

const options = computed(() => {
  const opts = []
  if (props.question.optionA) opts.push({ value: 'A', text: props.question.optionA })
  if (props.question.optionB) opts.push({ value: 'B', text: props.question.optionB })
  if (props.question.optionC) opts.push({ value: 'C', text: props.question.optionC })
  if (props.question.optionD) opts.push({ value: 'D', text: props.question.optionD })
  return opts
})
</script>

<style scoped>
.question-card { background: #fff; border: 1px solid #edf0f6; border-radius: 12px; padding: 28px; box-shadow: 0 3px 14px rgb(39 48 68 / 3%); }
.question-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 22px; padding-bottom: 18px; border-bottom: 1px solid #edf0f6; }
.question-number { padding: 6px 12px; border-radius: 7px; font-size: 13px; font-weight: 600; color: #646cff; background: #eeefff; }
.question-header :deep(.el-button) { gap: 5px; }
.question-stem { font-size: 17px; line-height: 1.9; color: #273044; margin-bottom: 26px; white-space: pre-wrap; overflow-wrap: anywhere; }
.options-group { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.option-item { display: flex; align-items: flex-start; width: 100%; min-width: 0; min-height: 56px; margin-right: 0; padding: 16px 18px; border: 1px solid #e6ebf3; border-radius: 9px; background: #fff; transition: border-color .18s, background .18s; height: auto; white-space: normal; }
.option-item:not(.is-disabled):hover, .option-item.selected { border-color: #646cff; background: #f5f5ff; }
.option-item:focus-within { outline: 2px solid #bec2ff; outline-offset: 2px; }
.option-item.correct { border-color: var(--el-color-success); background: var(--el-color-success-light-9); }
.option-item.wrong { border-color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.option-item :deep(.el-radio__input) { margin-top: 4px; }
.option-item :deep(.el-radio__label) { display: flex; align-items: flex-start; gap: 10px; width: 100%; min-width: 0; white-space: normal; line-height: 1.8; font-size: 15px; }
.option-label { font-weight: 650; color: #273044; flex-shrink: 0; }
.option-text { flex: 1; min-width: 0; color: #465168; overflow-wrap: anywhere; }
.result-section { margin-top: 24px; display: flex; flex-direction: column; gap: 18px; }
.explanation { padding: 20px; background: #f7f8fe; border-radius: 9px; border-left: 3px solid #646cff; }
.explanation-header { display: flex; align-items: center; gap: 7px; margin-bottom: 10px; font-weight: 600; font-size: 14px; color: #646cff; }
.explanation-content { line-height: 1.9; font-size: 15px; color: #465168; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 768px) {
  .question-card { padding: 20px 16px; }
  .question-stem { font-size: 16px; line-height: 1.85; }
  .option-item { padding: 13px 12px; }
  .option-item :deep(.el-radio__label) { font-size: 14px; gap: 7px; }
  .explanation { padding: 16px; }
}
@media (prefers-reduced-motion: reduce) { .option-item { transition: none; } }
</style>
