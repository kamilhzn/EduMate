<template>
  <div class="knowledge-base fade-in-up">
    <h1 class="page-title">我的课程知识库</h1>

    <div class="course-grid" v-loading="loading">
      <CourseCard
        v-for="course in courses"
        :key="course.id"
        :name="course.name"
        :chapterCount="course.chapterCount"
        :coverUrl="course.coverUrl"
        :coverColor="course.coverColor"
        @click="$router.push(`/knowledge/${course.id}`)"
      />
      <div class="course-card add-card" @click="showAddDialog = true">
        <div class="add-content">
          <span class="add-icon">
            <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
              <circle cx="18" cy="18" r="17" stroke="var(--color-text-secondary)" stroke-width="2" stroke-dasharray="4 3"/>
              <line x1="18" y1="10" x2="18" y2="26" stroke="var(--color-text-secondary)" stroke-width="2" stroke-linecap="round"/>
              <line x1="10" y1="18" x2="26" y2="18" stroke="var(--color-text-secondary)" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </span>
          <span class="add-text">添加课程</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="showAddDialog" title="创建新课程" width="420px" :close-on-click-modal="false">
      <el-input
        v-model="newCourseName"
        placeholder="请输入课程名称"
        size="large"
        @keyup.enter="handleCreate"
        maxlength="50"
        show-word-limit
      />
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :disabled="!newCourseName.trim()">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useCourseStore } from '@/stores/useCourseStore'
import { storeToRefs } from 'pinia'
import CourseCard from '@/components/common/CourseCard.vue'

const courseStore = useCourseStore()
const { courses, loading } = storeToRefs(courseStore)

const showAddDialog = ref(false)
const newCourseName = ref('')

onMounted(() => {
  courseStore.fetchCourses()
})

async function handleCreate() {
  const name = newCourseName.value.trim()
  if (!name) return
  await courseStore.createNewCourse(name)
  showAddDialog.value = false
  newCourseName.value = ''
}
</script>

<style lang="scss" scoped>
.knowledge-base {
  max-width: 960px;
  margin: 0 auto;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, 200px);
  gap: 20px;
  justify-content: center;
}

.add-card {
  width: 200px;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed var(--color-border);
  background: transparent;
  box-shadow: none;
  cursor: pointer;
  border-radius: var(--radius-md);
  transition: all 0.25s;

  &:hover {
    border-color: var(--color-accent);
    background: rgba(232, 168, 23, 0.04);
    transform: translateY(-4px);
  }
}

.add-content {
  text-align: center;
}

.add-icon {
  display: block;
  margin-bottom: 10px;
}

.add-text {
  color: var(--color-text-secondary);
  font-size: 14px;
}
</style>