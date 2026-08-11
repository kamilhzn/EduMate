<template>
  <div class="chat-message" :class="role">
    <div class="message-avatar">
      <span v-if="role === 'assistant'">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--color-primary)" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <path d="M8 14s1.5 2 4 2 4-2 4-2"/>
          <line x1="9" y1="9" x2="9.01" y2="9"/>
          <line x1="15" y1="9" x2="15.01" y2="9"/>
        </svg>
      </span>
      <span v-else>
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--color-accent)" stroke-width="2">
          <circle cx="12" cy="8" r="4"/>
          <path d="M6 21v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2"/>
        </svg>
      </span>
    </div>
    <div class="message-bubble">
      <div class="message-content" v-html="renderedContent"></div>
      <span v-if="isStreaming && role === 'assistant'" class="typing-cursor">█</span>

      <!-- 参考资料 -->
      <div v-if="references?.length" class="message-references">
        <div class="ref-header">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          <span>参考资料</span>
        </div>
        <div class="ref-list">
          <div
            v-for="ref in references"
            :key="ref.index"
            class="ref-item"
            :title="ref.snippet"
          >
            <span class="ref-dot">{{ ref.index }}</span>
            <div class="ref-info">
              <div v-if="ref.courseName" class="ref-course">{{ ref.courseName }}</div>
              <div v-if="ref.chapterPath" class="ref-path">{{ ref.chapterPath }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  role: { type: String, required: true },
  content: { type: String, default: '' },
  references: { type: Array, default: null },
  isStreaming: { type: Boolean, default: false }
})

const renderedContent = computed(() => {
  if (!props.content) return ''
  return marked(props.content)
})
</script>

<style lang="scss" scoped>
.chat-message {
  display: flex;
  gap: 12px;
  padding: 16px 0;
  animation: fadeInUp 0.3s ease-out;

  &.user {
    flex-direction: row-reverse;

    .message-bubble {
      background: linear-gradient(135deg, var(--color-accent-light), #FDE8C8);
      border-bottom-right-radius: 4px;
    }
  }

  &.assistant .message-bubble {
    background: #EEF1F9;
    border-bottom-left-radius: 4px;
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
}

.message-bubble {
  max-width: 75%;
  padding: 14px 18px;
  border-radius: var(--radius-md);
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-text);
  position: relative;

  :deep(p) { margin: 0 0 8px; }
  :deep(p:last-child) { margin-bottom: 0; }
  :deep(code) {
    background: rgba(0,0,0,0.06);
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 14px;
    font-family: "JetBrains Mono", monospace;
  }
  :deep(pre) {
    background: rgba(0,0,0,0.04);
    border-radius: 6px;
    padding: 12px;
    overflow-x: auto;
    margin: 8px 0;
    code { background: none; padding: 0; }
  }
  :deep(ul), :deep(ol) { padding-left: 20px; margin: 4px 0; }
  :deep(li) { margin: 2px 0; }
}

.typing-cursor {
  display: inline-block;
  color: var(--color-primary);
  animation: typewriterCursor 1s infinite;
  font-weight: 700;
}

/* ---------- 参考资料 ---------- */
.message-references {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--color-border, #dcdfe6);
}

.ref-header {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary, #909399);
  margin-bottom: 8px;
}

.ref-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ref-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  background: white;
  border: 1px solid var(--color-border, #e4e7ed);
  border-radius: 16px;
  cursor: default;
  transition: all 0.2s;
  max-width: 100%;
}

.ref-item:hover {
  border-color: var(--color-primary, #409eff);
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.1);
}

.ref-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--color-primary, #409eff);
  color: white;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ref-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ref-course {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text, #303133);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ref-path {
  font-size: 11px;
  color: var(--color-text-secondary, #909399);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
