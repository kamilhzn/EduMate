<template>
  <div class="chapter-list">
    <div
      v-for="chapter in chapters"
      :key="chapter.id"
      class="chapter-group"
    >
      <div class="chapter-header">
        <div class="chapter-main" @click="$emit('select-chapter', chapter)">
          <span class="chapter-title">{{ chapter.title }}</span>
          <span class="chapter-meta" v-if="chapter.sections?.length">
            {{ chapter.sections.length }} 小节
          </span>
        </div>
        <button
          class="chapter-toggle"
          @click.stop="toggleChapter(chapter.id)"
          v-if="chapter.sections?.length"
        >
          <span class="toggle-icon" :class="{ open: expandedChapters.has(chapter.id) }">
            ▼
          </span>
        </button>
      </div>
      <transition name="expand">
        <div v-if="expandedChapters.has(chapter.id)" class="chapter-sections">
          <SectionItem
            v-for="section in chapter.sections"
            :key="section.id"
            :title="section.title"
            :isActive="activeSection?.id === section.id"
            @click="$emit('select-section', section, chapter)"
          />
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SectionItem from './SectionItem.vue'

defineProps({
  chapters: { type: Array, default: () => [] },
  activeSection: { type: Object, default: null }
})

defineEmits(['select-chapter', 'select-section'])

const expandedChapters = ref(new Set())

function toggleChapter(id) {
  const newSet = new Set(expandedChapters.value)
  if (newSet.has(id)) {
    newSet.delete(id)
  } else {
    newSet.add(id)
  }
  expandedChapters.value = newSet
}
</script>

<style lang="scss" scoped>
.chapter-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chapter-group {
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.chapter-header {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border-radius: var(--radius-sm);
  transition: all 0.2s;
  box-shadow: var(--shadow-sm);

  &:hover {
    box-shadow: var(--shadow-card);
  }
}

.chapter-main {
  flex: 1;
  padding: 14px 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 12px;
}

.chapter-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--color-text);
}

.chapter-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  background: var(--color-bg);
  padding: 2px 8px;
  border-radius: var(--radius-full);
}

.chapter-toggle {
  padding: 14px 16px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: color 0.2s;

  &:hover {
    color: var(--color-primary);
  }
}

.toggle-icon {
  display: inline-block;
  font-size: 10px;
  transition: transform 0.3s;

  &.open {
    transform: rotate(180deg);
  }
}

.chapter-sections {
  background: var(--color-bg);
  border-radius: 0 0 var(--radius-sm) var(--radius-sm);
  overflow: hidden;
}
</style>