/**
 * 格式化文件大小
 * @param {number} bytes
 * @returns {string}
 */
export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/**
 * 格式化日期
 * @param {string|Date} date
 * @param {string} format - 'full' | 'date' | 'time' | 'relative'
 * @returns {string}
 */
export function formatDate(date, format = 'date') {
  if (!date) return ''
  const d = new Date(date)

  if (format === 'relative') {
    const now = Date.now()
    const diff = now - d.getTime()
    const minutes = Math.floor(diff / 60000)
    const hours = Math.floor(diff / 3600000)
    const days = Math.floor(diff / 86400000)

    if (minutes < 1) return '刚刚'
    if (minutes < 60) return `${minutes} 分钟前`
    if (hours < 24) return `${hours} 小时前`
    if (days < 7) return `${days} 天前`
  }

  if (format === 'time') {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  if (format === 'full') {
    return d.toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit'
    })
  }

  return d.toLocaleDateString('zh-CN', {
    year: 'numeric', month: 'short', day: 'numeric'
  })
}

/**
 * 获取文件扩展名图标
 * @param {string} filename
 * @returns {string}
 */
export function getFileIcon(filename) {
  const ext = filename.split('.').pop()?.toLowerCase()
  const icons = {
    pdf: '📕',
    doc: '📄', docx: '📄',
    ppt: '📊', pptx: '📊',
    xls: '📈', xlsx: '📈',
    txt: '📝', md: '📝',
    csv: '📋',
    zip: '📦', rar: '📦', '7z': '📦',
    jpg: '🖼️', jpeg: '🖼️', png: '🖼️', gif: '🖼️', svg: '🖼️'
  }
  return icons[ext] || '📎'
}

/**
 * 防抖
 * @param {Function} fn
 * @param {number} delay
 * @returns {Function}
 */
export function debounce(fn, delay = 300) {
  let timer = null
  return function (...args) {
    clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/**
 * 节流
 * @param {Function} fn
 * @param {number} delay
 * @returns {Function}
 */
export function throttle(fn, delay = 300) {
  let last = 0
  return function (...args) {
    const now = Date.now()
    if (now - last >= delay) {
      last = now
      fn.apply(this, args)
    }
  }
}