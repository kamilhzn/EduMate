<template>
  <div class="chat-panel">
    <div class="messages-container" ref="msgContainer">
      <div v-if="messages.length === 0" class="empty-chat">
        <div class="empty-icon">
          <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
            <circle cx="28" cy="28" r="24" stroke="var(--color-border)" stroke-width="2"/>
            <circle cx="22" cy="24" r="2" fill="var(--color-text-secondary)"/>
            <circle cx="34" cy="24" r="2" fill="var(--color-text-secondary)"/>
            <path d="M18 36s4 6 10 6 10-6 10-6" stroke="var(--color-text-secondary)" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
        <p class="empty-title">开始对话</p>
        <p class="empty-desc">选择一个课程，向 EduMate 提问</p>
      </div>
      <ChatMessage
        v-for="(msg, i) in messages"
        :key="i"
        :role="msg.role"
        :content="msg.content"
        :references="msg.references"
        :isStreaming="isStreaming && i === messages.length - 1 && msg.role === 'assistant'"
      />
    </div>
    <ChatInput :disabled="isStreaming" @send="$emit('send', $event)" />
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import ChatMessage from './ChatMessage.vue'
import ChatInput from './ChatInput.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  isStreaming: { type: Boolean, default: false }
})

defineEmits(['send'])

const msgContainer = ref(null)

function scrollToBottom() {
  nextTick(() => {
    if (msgContainer.value) {
      msgContainer.value.scrollTop = msgContainer.value.scrollHeight
    }
  })
}

watch(() => props.messages?.length, scrollToBottom)
watch(() => {
  if (props.messages.length > 0) {
    return props.messages[props.messages.length - 1]?.content
  }
  return null
}, scrollToBottom)
</script>

<style lang="scss" scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.empty-chat {
  text-align: center;
  padding: 80px 20px;
  color: var(--color-text-secondary);

  .empty-icon {
    margin-bottom: 16px;
  }

  .empty-title {
    font-size: 17px;
    font-weight: 600;
    color: var(--color-text);
    margin: 0 0 4px;
  }

  .empty-desc {
    font-size: 14px;
    margin: 0;
  }
}
</style>