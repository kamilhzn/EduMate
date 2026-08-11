const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

/**
 * SSE 流式聊天
 * @returns {AsyncGenerator<{event: string, data: string}>}
 */
export async function* streamChat(query, sessionId) {
  const response = await fetch(`${BASE_URL}/api/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, sessionId: sessionId || undefined })
  })

  if (!response.ok) {
    throw new Error(`Chat request failed: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = null
  let currentDataLines = []

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || '' // 不完整的行留在 buffer

    for (const line of lines) {
      if (line.startsWith('event:')) {
        // 新事件开始，先提交上一个事件（如果有）
        if (currentEvent && currentDataLines.length > 0) {
          yield { event: currentEvent, data: currentDataLines.join('\n') }
        }
        currentEvent = line.slice(6).trim()
        currentDataLines = []
      } else if (line.startsWith('data:')) {
        currentDataLines.push(line.slice(5))
      } else if (line === '' || line === '\r') {
        // 空行表示事件结束
        if (currentEvent && currentDataLines.length > 0) {
          yield { event: currentEvent, data: currentDataLines.join('\n') }
        }
        currentEvent = null
        currentDataLines = []
      }
    }
  }

  // 处理 buffer 中剩余的内容
  if (buffer.trim()) {
    const lines = buffer.split('\n')
    for (const line of lines) {
      if (line.startsWith('event:')) {
        if (currentEvent && currentDataLines.length > 0) {
          yield { event: currentEvent, data: currentDataLines.join('\n') }
        }
        currentEvent = line.slice(6).trim()
        currentDataLines = []
      } else if (line.startsWith('data:')) {
        currentDataLines.push(line.slice(5))
      }
    }
    if (currentEvent && currentDataLines.length > 0) {
      yield { event: currentEvent, data: currentDataLines.join('\n') }
    }
  }
}
