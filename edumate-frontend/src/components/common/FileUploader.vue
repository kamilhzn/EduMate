<template>
  <div
    class="file-uploader"
    :class="{ dragging }"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @drop.prevent="handleDrop"
  >
    <input
      ref="fileInput"
      type="file"
      multiple
      hidden
      accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.md"
      @change="handleFileChange"
    />

    <div class="upload-zone" @click="$refs.fileInput.click()">
      <div class="upload-icon">
        <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
          <rect x="4" y="8" width="48" height="40" rx="6" stroke="var(--color-text-secondary)" stroke-width="2" stroke-dasharray="4 3"/>
          <path d="M20 28l8-8 8 8" stroke="var(--color-text-secondary)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <line x1="28" y1="20" x2="28" y2="38" stroke="var(--color-text-secondary)" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <p class="upload-title">拖拽文件到此处，或点击上传</p>
      <p class="upload-hint">支持 PDF / Word / PPT / Excel / TXT / Markdown</p>
      <p class="upload-hint">单个文件最大 50MB</p>
    </div>

    <div class="file-list" v-if="files.length">
      <div
        v-for="(f, i) in files"
        :key="i"
        class="file-item"
        :class="{ done: f.done, error: f.error }"
      >
        <span class="file-icon">{{ getFileIcon(f.name) }}</span>
        <div class="file-info">
          <span class="file-name">{{ f.name }}</span>
          <span class="file-size">{{ formatSize(f.size) }}</span>
        </div>
        <div class="file-status" v-if="f.uploading">
          <span class="status-text uploading">解析中...</span>
        </div>
        <el-tag v-else-if="f.done" type="success" size="small" effect="plain">解析完成</el-tag>
        <el-tag v-else-if="f.error" type="danger" size="small" effect="plain">失败</el-tag>
        <button v-else class="remove-btn" @click="removeFile(i)" title="移除">✕</button>
      </div>
    </div>

    <div class="upload-actions" v-if="files.length && hasPending">
      <el-button
        type="primary"
        size="large"
        class="upload-all-btn"
        @click="uploadAll"
        :loading="uploading"
      >
        <span v-if="!uploading">🚀 全部上传并索引</span>
        <span v-else>正在上传...</span>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { uploadDocument } from '@/api/document'
import { ElMessage } from 'element-plus'

const props = defineProps({
  courseName: { type: String, default: '' }
})

const emit = defineEmits(['uploaded'])

const files = ref([])
const dragging = ref(false)
const uploading = ref(false)
const fileInput = ref(null)

const hasPending = computed(() => files.value.some(f => !f.done && !f.uploading && !f.error))

function getFileIcon(name) {
  const ext = name.split('.').pop().toLowerCase()
  const icons = {
    pdf: '📕', doc: '📄', docx: '📄', ppt: '📊', pptx: '📊',
    xls: '📈', xlsx: '📈', txt: '📝', md: '📝'
  }
  return icons[ext] || '📎'
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function handleDrop(e) {
  dragging.value = false
  addFiles([...e.dataTransfer.files])
}

function handleFileChange(e) {
  addFiles([...e.target.files])
  e.target.value = ''
}

function addFiles(newFiles) {
  newFiles.forEach(f => {
    // 避免重复添加
    if (!files.value.some(existing => existing.name === f.name && existing.size === f.size)) {
      files.value.push({
        name: f.name,
        size: f.size,
        file: f,
        done: false,
        uploading: false,
        error: false
      })
    }
  })
}

function removeFile(i) {
  files.value.splice(i, 1)
}

async function uploadAll() {
  if (!props.courseName) {
    ElMessage.warning('请先选择或创建课程')
    return
  }

  uploading.value = true
  let successCount = 0
  let failCount = 0

  for (const f of files.value) {
    if (f.done || f.error) continue
    f.uploading = true
    try {
      const formData = new FormData()
      formData.append('file', f.file)
      formData.append('courseName', props.courseName)
      await uploadDocument(formData)
      f.done = true
      successCount++
      emit('uploaded', f)
    } catch {
      f.error = true
      failCount++
    } finally {
      f.uploading = false
    }
  }

  uploading.value = false

  if (successCount > 0) {
    ElMessage.success(`上传完成：${successCount} 个成功${failCount > 0 ? `，${failCount} 个失败` : ''}`)
  }
}
</script>

<style lang="scss" scoped>
.file-uploader {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
  transition: box-shadow 0.3s;

  &.dragging {
    box-shadow: 0 0 0 3px var(--color-accent), var(--shadow-hover);

    .upload-zone {
      border-color: var(--color-accent);
      background: rgba(232, 168, 23, 0.04);
    }
  }
}

.upload-zone {
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-lg);
  padding: 48px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;

  &:hover {
    border-color: var(--color-primary);
    background: rgba(26, 86, 219, 0.03);
  }
}

.upload-icon {
  margin-bottom: 16px;
  display: flex;
  justify-content: center;
}

.upload-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin: 0 0 8px;
}

.upload-hint {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 4px 0;
}

.file-list {
  padding: 16px;
  max-height: 280px;
  overflow-y: auto;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  background: var(--color-bg);
  margin-bottom: 8px;
  transition: all 0.3s;

  &.done {
    background: var(--color-success-light);
  }

  &.error {
    background: var(--color-danger-light);
  }

  &:last-child {
    margin-bottom: 0;
  }
}

.file-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  display: block;
  font-size: 14px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.status-text {
  font-size: 13px;

  &.uploading {
    color: var(--color-accent);
    animation: pulse 1.5s ease-in-out infinite;
  }
}

.remove-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(217, 64, 64, 0.1);
  color: var(--color-danger);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.2s;

  &:hover {
    background: var(--color-danger);
    color: #fff;
  }
}

.upload-actions {
  padding: 0 16px 16px;
}

.upload-all-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: var(--radius-md);
}
</style>