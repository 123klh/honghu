<template>
  <div class="question-card">
    <div class="question-header">
      <span class="question-number">第 {{ index + 1 }} 题</span>
      <el-button
        v-if="showFavorite"
        text
        :type="favored ? 'warning' : 'info'"
        @click.stop="$emit('toggle-favorite')"
      >
        <el-icon>
          <StarFilled v-if="favored" />
          <Star v-else />
        </el-icon>
      </el-button>
    </div>

    <div class="question-stem">{{ question.questionStem || question.stem }}</div>

    <el-radio-group
      :model-value="modelValue"
      @update:model-value="$emit('update:modelValue', $event)"
      :disabled="disabled"
      class="options-group"
    >
      <el-radio
        v-for="opt in options"
        :key="opt.value"
        :label="opt.value"
        class="option-item"
        :class="{
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
.question-card {
  background: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color);
}

.question-number {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.question-stem {
  font-size: 16px;
  line-height: 1.8;
  color: var(--el-text-color-primary);
  margin-bottom: 20px;
  white-space: pre-wrap;
}

.options-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.option-item {
  display: flex;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  transition: all 0.3s;
  height: auto;
  white-space: normal;
}

.option-item:hover {
  border-color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}

.option-item.correct {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.option-item.wrong {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.option-item :deep(.el-radio__label) {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  width: 100%;
  white-space: normal;
  line-height: 1.6;
}

.option-label {
  font-weight: 600;
  color: var(--el-text-color-primary);
  flex-shrink: 0;
}

.option-text {
  flex: 1;
  color: var(--el-text-color-primary);
}

.result-section {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.explanation {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  border-left: 4px solid var(--el-color-primary);
}

.explanation-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.explanation-content {
  line-height: 1.8;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
}

@media (max-width: 768px) {
  .question-card {
    padding: 16px;
  }

  .question-stem {
    font-size: 15px;
  }

  .option-item {
    padding: 10px;
  }
}
</style>
