<template>
  <div class="loading-spinner">
    <div class="spinner-ring"></div>
    <p class="spinner-text">{{ text || '加载中...' }}</p>
  </div>
</template>

<script setup>
defineProps({
  text: { type: String, default: '加载中...' }
})
</script>

<style lang="scss" scoped>
.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 40px;
}

.spinner-ring {
  width: 40px;
  height: 40px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.spinner-text {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
}
</style>