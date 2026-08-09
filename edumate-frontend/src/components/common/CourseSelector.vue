<template>
  <div class="course-selector">
    <el-select
      v-model="selectedCourseId"
      placeholder="选择已有课程"
      filterable
      clearable
      size="large"
      class="course-select"
      popper-class="course-select-popper"
      @change="handleSelect"
    >
      <el-option
        v-for="c in courses"
        :key="c.id"
        :label="c.name"
        :value="c.id"
      >
        <span style="float: left">{{ c.name }}</span>
        <span style="float: right; color: var(--color-text-secondary); font-size: 13px">
          {{ c.chapterCount || 0 }} 章
        </span>
      </el-option>
    </el-select>
    <div class="divider-text">或</div>
    <el-input
      v-model="newCourseName"
      placeholder="输入新课程名称"
      size="large"
      class="course-input"
      @keyup.enter="handleCreate"
    >
      <template #append>
        <el-button @click="handleCreate" :loading="creating" :disabled="!newCourseName.trim()">
          <span class="create-btn-inner">✨ 创建</span>
        </el-button>
      </template>
    </el-input>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useCourseStore } from '@/stores/useCourseStore'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['select'])
const courseStore = useCourseStore()
const { courses } = storeToRefs(courseStore)

const selectedCourseId = ref(null)
const newCourseName = ref('')
const creating = ref(false)

onMounted(() => {
  courseStore.fetchCourses()
})

function handleSelect(courseId) {
  if (!courseId) return
  const course = courses.value.find(c => c.id === courseId)
  if (course) {
    courseStore.selectCourse(course)
    emit('select', course)
  }
}

async function handleCreate() {
  const name = newCourseName.value.trim()
  if (!name) return
  creating.value = true
  try {
    const course = await courseStore.createNewCourse(name)
    selectedCourseId.value = course.id
    courseStore.selectCourse(course)
    emit('select', course)
    newCourseName.value = ''
    ElMessage.success('课程创建成功')
  } catch {
    // 错误已在 request 拦截器处理
  } finally {
    creating.value = false
  }
}
</script>

<style lang="scss" scoped>
.course-selector {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: var(--color-surface);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
}

.course-select {
  width: 240px;
}

.divider-text {
  color: var(--color-text-secondary);
  font-size: 14px;
  flex-shrink: 0;
}

.course-input {
  width: 300px;
}

.create-btn-inner {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>

<style lang="scss">
.course-select-popper {
  border-radius: var(--radius-sm) !important;
}
</style>