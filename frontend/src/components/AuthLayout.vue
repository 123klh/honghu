<template>
  <main class="auth-page">
    <section class="auth-panel" :aria-labelledby="headingId">
      <div class="auth-brand"><BrandLogo /></div>
      <header class="auth-heading">
        <h1 :id="headingId">{{ title }}</h1>
        <p>{{ description }}</p>
      </header>
      <slot />
    </section>
    <footer class="auth-footer">公考刷题 · 模拟考试 · 申论评分 · 学习社区</footer>
  </main>
</template>

<script setup>
import { useId } from 'vue'
import BrandLogo from './BrandLogo.vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, required: true }
})

const headingId = useId()
</script>

<style scoped>
.auth-page {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  min-height: 100dvh;
  padding: 36px 20px 20px;
  overflow: hidden;
  background: #eef0ff;
}

.auth-page::before,
.auth-page::after {
  position: absolute;
  z-index: -1;
  width: min(900px, 85vw);
  aspect-ratio: 1;
  border-radius: 42% 58% 65% 35%;
  content: '';
  pointer-events: none;
}

.auth-page::before {
  top: -48%;
  right: -16%;
  transform: rotate(-25deg);
  background: linear-gradient(135deg, #c2c5ff, #9c9df5);
}

.auth-page::after {
  bottom: -54%;
  left: -20%;
  transform: rotate(18deg);
  background: linear-gradient(135deg, #969bfb, #cbd3ff);
}

.auth-panel {
  width: 100%;
  max-width: 440px;
  padding: 34px 36px 30px;
  border: 1px solid rgba(255, 255, 255, 0.85);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 18px 60px rgba(68, 69, 139, 0.1);
}

.auth-brand {
  display: flex;
  justify-content: center;
  margin-bottom: 30px;
}

.auth-heading { margin-bottom: 24px; }
.auth-heading h1 {
  margin: 0 0 7px;
  color: var(--hh-text, #273044);
  font-size: 23px;
  font-weight: 650;
  line-height: 1.4;
}

.auth-heading p {
  margin: 0;
  color: #738097;
  font-size: 13px;
  line-height: 1.7;
}

.auth-panel :deep(.el-form-item) { margin-bottom: 20px; }
.auth-panel :deep(.el-form-item__label) {
  margin-bottom: 7px;
  padding: 0;
  color: #445169;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
}

.auth-panel :deep(.el-input__wrapper) {
  min-height: 46px;
  padding: 0 14px;
  border-radius: 9px;
  background: #fafbfe;
}

.auth-panel :deep(.el-input__inner) { font-size: 14px; }
.auth-panel :deep(.el-input__prefix) { color: #8491a5; }
.auth-panel :deep(.el-input__wrapper.is-focus) { background: #fff; }
.auth-footer {
  margin-top: 24px;
  color: #697590;
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
}

@media (max-width: 480px) {
  .auth-page { padding: 24px 16px 18px; }
  .auth-panel { padding: 28px 24px 24px; border-radius: 16px; }
  .auth-brand { margin-bottom: 24px; }
  .auth-page::before { top: -18%; right: -55%; width: 100vw; }
  .auth-page::after { bottom: -15%; left: -65%; width: 110vw; }
}
</style>
