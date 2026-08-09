<template>
  <div class="course-card" @click="$emit('click')">
    <div class="card-cover" :style="{ background: coverBg }">
      <img v-if="coverUrl" :src="coverUrl" alt="" class="cover-img" />
      <span v-else class="cover-icon">
        <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
          <rect x="10" y="8" width="36" height="40" rx="4" fill="rgba(255,255,255,0.2)"/>
          <path d="M18 18h20M18 26h20M18 34h12" stroke="rgba(255,255,255,0.8)" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </span>
    </div>
    <div class="card-body">
      <h3 class="card-name">{{ name }}</h3>
      <p class="card-meta">{{ chapterCount || 0 }} 章</p>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  chapterCount: { type: Number, default: 0 },
  coverUrl: { type: String, default: '' },
  coverColor: { type: String, default: '#1A56DB' }
})

defineEmits(['click'])

const coverBg = computed(() => {
  return `linear-gradient(135deg, ${props.coverColor}, ${props.coverColor}dd)`
})
</script>

<style lang="scss" scoped>
.course-card {
  width: 200px;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-surface);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: all 0.25s ease-out;

  &:hover {
    transform: translateY(-6px);
    box-shadow: var(--shadow-hover);

    .card-cover {
      transform: scale(1.05);
    }
  }
}

.card-cover {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease-out;

  .cover-icon {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .cover-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.card-body {
  padding: 14px 16px;
}

.card-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 4px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
}
</style>