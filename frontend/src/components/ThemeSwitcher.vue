<template>
  <el-dropdown trigger="click" @command="setTheme">
    <span
      class="theme-trigger"
      :title="`当前主题：${currentLabel}（点击切换）`"
      role="button"
      tabindex="0"
    >
      <el-icon :size="17">
        <component :is="currentIcon" />
      </el-icon>
      <span class="theme-trigger-text">{{ currentLabel }}</span>
    </span>

    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="item in options"
          :key="item.value"
          :command="item.value"
          :class="{ 'is-active-theme': theme === item.value }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Reading, Sunny } from '@element-plus/icons-vue'

const STORAGE_KEY = 'hh-theme'

const options = [
  { value: 'light', label: '浅色标准', icon: Sunny },
  { value: 'paper', label: '纸感护眼', icon: Reading }
]

const theme = ref('light')

function setTheme(mode) {
  const next = mode === 'paper' ? 'paper' : 'light'
  theme.value = next

  const root = document.documentElement
  if (next === 'light') {
    delete root.dataset.theme
  } else {
    root.dataset.theme = next
  }

  localStorage.setItem(STORAGE_KEY, next)
}

const currentOption = computed(
  () => options.find((item) => item.value === theme.value) || options[0]
)
const currentLabel = computed(() => currentOption.value.label)
const currentIcon = computed(() => currentOption.value.icon)

onMounted(() => {
  setTheme(localStorage.getItem(STORAGE_KEY))
})
</script>

<style scoped>
.theme-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 10px;
  border-radius: var(--hh-radius-sm, 8px);
  color: var(--hh-text-secondary, #4b5565);
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.theme-trigger:hover {
  background-color: var(--hh-hover-bg, #f4f6fc);
  color: var(--hh-text, #182030);
}

:deep(.is-active-theme) {
  color: var(--hh-primary, #4353e8);
  font-weight: 600;
}

@media (max-width: 640px) {
  .theme-trigger-text {
    display: none;
  }
}
</style>
