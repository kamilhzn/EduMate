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
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'

const props = defineProps({
  role: { type: String, required: true },
  content: { type: String, default: '' },
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
</style>