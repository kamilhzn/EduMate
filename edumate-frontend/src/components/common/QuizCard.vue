<template>
  <div class="quiz-card" @click="$emit('click')">
    <div class="quiz-header">
      <span class="quiz-type-badge">{{ source }}</span>
      <el-button
        class="delete-btn"
        :icon="'Close'"
        circle
        size="small"
        @click.stop="$emit('delete')"
      />
    </div>
    <h3 class="quiz-name">{{ name }}</h3>
    <div class="quiz-meta">
      <span class="quiz-count">{{ count }} 题</span>
      <span class="quiz-date">{{ formatDate(createdAt) }}</span>
    </div>
  </div>
</template>

<script setup>
defineProps({
  name: { type: String, required: true },
  source: { type: String, default: '' },
  count: { type: Number, default: 0 },
  createdAt: { type: String, default: '' }
})
defineEmits(['click', 'delete'])

function formatDate(dateStr) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric'
  })
}
</script>

<style lang="scss" scoped>
.quiz-card {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: all 0.25s ease-out;
  border: 1px solid transparent;
  position: relative;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-hover);
    border-color: var(--color-accent);

    .delete-btn {
      opacity: 1;
    }
  }
}

.quiz-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.quiz-type-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--color-accent-light);
  color: var(--color-accent-dark);
  font-weight: 500;
}

.delete-btn {
  opacity: 0;
  transition: opacity 0.2s;
  --el-button-bg-color: rgba(217, 64, 64, 0.1);
  --el-button-border-color: transparent;
  --el-button-text-color: var(--color-danger);
  --el-button-hover-bg-color: var(--color-danger);
  --el-button-hover-border-color: var(--color-danger);
  --el-button-hover-text-color: #fff;
}

.quiz-name {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 10px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quiz-meta {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.quiz-count {
  color: var(--color-primary);
  font-weight: 500;
}
</style>