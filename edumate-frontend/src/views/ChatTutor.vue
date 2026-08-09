<template>
  <div class="chat-tutor fade-in-up">
    <div class="chat-main">
      <ChatPanel
        :messages="currentMessages"
        :isStreaming="isStreaming"
        @send="handleSend"
      />
    </div>
    <div class="chat-sidebar">
      <div class="sidebar-section">
        <h3 class="sidebar-title">选择课程</h3>
        <div class="course-list" v-if="courses.length">
          <div
            v-for="c in courses"
            :key="c.id"
            class="course-item"
            :class="{ active: currentCourseId === c.id }"
            @click="switchCourse(c.id)"
          >
            <span class="course-dot" :style="{ background: c.coverColor || 'var(--color-primary)' }"></span>
            <span class="course-name">{{ c.name }}</span>
          </div>
        </div>
        <div v-else class="no-course">
          <p>暂无课程</p>
          <el-button type="primary" size="small" @click="$router.push('/upload')">去上传</el-button>
        </div>
      </div>
      <div class="sidebar-section">
        <el-button
          class="new-chat-btn"
          @click="handleNewChat"
          :disabled="!currentCourseId"
          plain
        >
          💬 新建对话
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useCourseStore } from '@/stores/useCourseStore'
import { useChatStore } from '@/stores/useChatStore'
import { storeToRefs } from 'pinia'
import ChatPanel from '@/components/common/ChatPanel.vue'

const courseStore = useCourseStore()
const chatStore = useChatStore()
const { courses } = storeToRefs(courseStore)
const { currentCourseId, currentMessages, isStreaming } = storeToRefs(chatStore)

onMounted(async () => {
  await courseStore.fetchCourses()
  if (courses.value.length > 0) {
    chatStore.initSession(courses.value[0].id)
  }
})

function switchCourse(courseId) {
  chatStore.initSession(courseId)
}

function handleSend(content) {
  chatStore.sendMessage(content)
}

function handleNewChat() {
  if (currentCourseId.value) {
    chatStore.clearSession(currentCourseId.value)
  }
}
</script>

<style lang="scss" scoped>
.chat-tutor {
  display: flex;
  gap: 20px;
  height: calc(100vh - 112px);
}

.chat-main {
  flex: 1;
  min-width: 0;
}

.chat-sidebar {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-section {
  background: var(--color-surface);
  border-radius: var(--radius-md);
  padding: 16px;
  box-shadow: var(--shadow-card);
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin: 0 0 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.course-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.course-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: var(--color-bg);
  }

  &.active {
    background: rgba(26, 86, 219, 0.08);
    color: var(--color-primary);
    font-weight: 500;
  }
}

.course-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.course-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.no-course {
  text-align: center;
  padding: 12px 0;
  font-size: 13px;
  color: var(--color-text-secondary);

  p { margin: 0 0 8px; }
}

.new-chat-btn {
  width: 100%;
}
</style>