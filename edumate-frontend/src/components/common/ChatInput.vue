<template>
  <div class="chat-input">
    <textarea
      ref="inputRef"
      v-model="text"
      class="input-area"
      placeholder="输入你的问题..."
      rows="1"
      @keydown.enter.exact.prevent="send"
      @input="autoResize"
      :disabled="disabled"
    ></textarea>
    <button
      class="send-btn"
      @click="send"
      :disabled="!text.trim() || disabled"
      :title="disabled ? '请等待回复完成' : '发送'"
    >
      <svg v-if="!disabled" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="22" y1="2" x2="11" y2="13"/>
        <polygon points="22 2 15 22 11 13 2 9 22 2"/>
      </svg>
      <span v-else class="thinking-dot">●</span>
    </button>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'

const props = defineProps({
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['send'])

const text = ref('')
const inputRef = ref(null)

function autoResize() {
  const el = inputRef.value
  if (el) {
    el.style.height = 'auto'
    el.style.height = Math.min(el.scrollHeight, 120) + 'px'
  }
}

function send() {
  const content = text.value.trim()
  if (!content || props.disabled) return
  emit('send', content)
  text.value = ''
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
    }
  })
}
</script>

<style lang="scss" scoped>
.chat-input {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 14px 18px;
  background: var(--color-surface);
  border-top: 1px solid var(--color-border);
}

.input-area {
  flex: 1;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 15px;
  font-family: inherit;
  resize: none;
  outline: none;
  line-height: 1.5;
  background: var(--color-bg);
  transition: border-color 0.2s;

  &:focus {
    border-color: var(--color-primary);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.send-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
  font-size: 18px;
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover:not(:disabled) {
    background: var(--color-primary-dark);
    transform: scale(1.08);
  }

  &:disabled {
    background: var(--color-border);
    cursor: not-allowed;
  }
}

.thinking-dot {
  animation: pulse 1.5s ease-in-out infinite;
}
</style>