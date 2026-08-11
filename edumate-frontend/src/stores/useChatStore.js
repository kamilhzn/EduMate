import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { streamChat } from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref({})
  const currentCourseId = ref(null)
  const isStreaming = ref(false)

  const currentMessages = computed(() => {
    if (!currentCourseId.value) return []
    return sessions.value[currentCourseId.value]?.messages || []
  })

  function initSession(courseId) {
    if (!sessions.value[courseId]) {
      sessions.value[courseId] = {
        messages: [{
          role: 'assistant',
          content: '你好！我是 EduMate 学习助手，请随时向我提问课程相关问题。',
          references: null,
          timestamp: Date.now()
        }],
        sessionId: null
      }
    }
    currentCourseId.value = courseId
  }

  async function sendMessage(content) {
    if (!currentCourseId.value || !content.trim()) return

    const session = sessions.value[currentCourseId.value]
    if (!session) return

    session.messages.push({ role: 'user', content: content.trim(), references: null, timestamp: Date.now() })

    const aiMsg = { role: 'assistant', content: '', references: null, timestamp: Date.now() }
    session.messages.push(aiMsg)

    isStreaming.value = true

    try {
      for await (const { event, data } of streamChat(content.trim(), session.sessionId)) {
        if (event === 'message') {
          aiMsg.content += data
        } else if (event === 'references') {
          try {
            const refs = JSON.parse(data)
            if (Array.isArray(refs) && refs.length > 0) {
              aiMsg.references = refs
            }
          } catch (e) {
            console.error('解析参考资料失败:', e)
          }
        } else if (event === 'done') {
          try {
            const doneData = JSON.parse(data)
            if (doneData.sessionId) {
              session.sessionId = doneData.sessionId
            }
          } catch (e) {
            console.error('解析完成事件失败:', e)
          }
        } else if (event === 'error') {
          aiMsg.content = data
        }
      }
    } catch {
      if (aiMsg.content === '') {
        aiMsg.content = '抱歉，请求失败，请稍后重试。'
      }
    } finally {
      isStreaming.value = false
    }
  }

  function clearSession(courseId) {
    const id = courseId || currentCourseId.value
    if (id && sessions.value[id]) {
      sessions.value[id] = {
        messages: [{
          role: 'assistant',
          content: '你好！我是 EduMate 学习助手，请随时向我提问课程相关问题。',
          references: null,
          timestamp: Date.now()
        }],
        sessionId: null
      }
    }
  }

  return {
    sessions, currentCourseId, isStreaming, currentMessages,
    initSession, sendMessage, clearSession
  }
})
