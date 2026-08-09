<template>
  <div class="document-upload-page fade-in-up">
    <div class="hero-section">
      <h1 class="page-title hero-title">📚 开始构建你的知识库</h1>
      <p class="hero-subtitle">上传课程资料，开启智能学习之旅</p>
    </div>

    <div class="course-section">
      <CourseSelector @select="onCourseSelect" />
    </div>

    <div class="upload-section">
      <FileUploader
        :courseName="selectedCourse?.name"
        @uploaded="onFileUploaded"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import CourseSelector from '@/components/common/CourseSelector.vue'
import FileUploader from '@/components/common/FileUploader.vue'

const selectedCourse = ref(null)

function onCourseSelect(course) {
  selectedCourse.value = course
}

function onFileUploaded(file) {
  console.log('Uploaded:', file.name)
}
</script>

<style lang="scss" scoped>
.document-upload-page {
  max-width: 720px;
  margin: 0 auto;
}

.hero-section {
  text-align: center;
  margin-bottom: 32px;
}

.hero-title {
  margin-bottom: 8px;
}

.hero-subtitle {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin: 0;
}

.course-section {
  margin-bottom: 24px;
}
</style>