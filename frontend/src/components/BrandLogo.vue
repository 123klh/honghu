<template>
  <span class="brand-logo">
    <span class="brand-symbol" aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none">
        <path d="M6 8.5c3.7-.3 7 .7 10 3.2 3-2.5 6.3-3.5 10-3.2v15c-3.7-.3-7 .7-10 3.2-3-2.5-6.3-3.5-10-3.2v-15Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
        <path d="M16 12v14M10 13l3 1.3M22 13l-3 1.3" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </svg>
    </span>
    <span v-if="!compact" class="brand-name">波波公考</span>
  </span>
</template>

<script setup>
defineProps({ compact: { type: Boolean, default: false } })
</script>

<style scoped>
.brand-logo { display: inline-flex; align-items: center; gap: 11px; white-space: nowrap; }
.brand-symbol { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; color: white; background: linear-gradient(145deg, #8990ff, #646cff 65%, #7961d9); box-shadow: 0 4px 12px #646cff22; flex-shrink: 0; }
.brand-symbol svg { width: 28px; height: 28px; }
.brand-name { font-size: 21px; font-weight: 700; letter-spacing: .5px; color: #545bcc; }
</style>
