<template>
  <header class="top-bar">
    <div class="top-left">
      <button class="collapse-btn" @click="appStore.toggleSidebar" :title="appStore.sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <line x1="3" y1="6" x2="21" y2="6"/>
          <line x1="3" y1="12" x2="15" y2="12"/>
          <line x1="3" y1="18" x2="21" y2="18"/>
        </svg>
      </button>
      <span class="page-title-text">{{ pageTitle }}</span>
    </div>
    <div class="top-right">
      <span class="current-course" v-if="courseStore.currentCourse">
        <span class="course-dot" :style="{ background: courseStore.currentCourse.coverColor || 'var(--color-primary)' }"></span>
        {{ courseStore.currentCourse.name }}
      </span>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/useAppStore'
import { useCourseStore } from '@/stores/useCourseStore'

const route = useRoute()
const appStore = useAppStore()
const courseStore = useCourseStore()

const pageTitle = computed(() => route.meta.title || 'EduMate')
</script>

<style lang="scss" scoped>
.top-bar {
  height: 56px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}

.top-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: var(--color-text-secondary);
  padding: 6px 8px;
  border-radius: var(--radius-sm);
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: var(--color-bg);
    color: var(--color-text);
  }
}

.page-title-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
}

.top-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.current-course {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: rgba(26, 86, 219, 0.06);
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 500;
  color: var(--color-primary);
}

.course-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
</style>