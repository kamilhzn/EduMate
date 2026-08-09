<template>
  <div class="section-item" :class="{ active: isActive }" @click="$emit('click')">
    <span class="section-dot"></span>
    <span class="section-title">{{ title }}</span>
    <span class="section-arrow">→</span>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  isActive: { type: Boolean, default: false }
})
defineEmits(['click'])
</script>

<style lang="scss" scoped>
.section-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px 10px 32px;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;

  &:hover {
    background: rgba(26, 86, 219, 0.05);
  }

  &.active {
    background: rgba(26, 86, 219, 0.08);
    color: var(--color-primary);
    font-weight: 500;

    .section-dot {
      background: var(--color-primary);
    }
  }
}

.section-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-secondary);
  flex-shrink: 0;
}

.section-title {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.section-arrow {
  font-size: 12px;
  color: var(--color-text-secondary);
  opacity: 0;
  transition: opacity 0.2s;
}

.section-item:hover .section-arrow {
  opacity: 1;
}
</style>