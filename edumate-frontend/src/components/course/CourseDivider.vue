<template>
  <div class="course-divider">
    <div class="divider-line"></div>
    <span class="divider-text">{{ courseName }}</span>
    <div class="divider-line"></div>
    <button class="divider-toggle" @click="$emit('toggle')">
      {{ collapsed ? '展开 ▶' : '收起 ▲' }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  courseName: { type: String, required: true },
  collapsed: { type: Boolean, default: false }
})
defineEmits(['toggle'])
</script>

<style lang="scss" scoped>
.course-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 24px 0 16px;
}

.divider-line {
  flex: 1;
  height: 1px;
  background: var(--color-border);
}

.divider-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
}

.divider-toggle {
  background: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 4px 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;

  &:hover {
    border-color: var(--color-primary);
    color: var(--color-primary);
  }
}
</style>