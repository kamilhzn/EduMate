<template>
  <div class="course-chapters fade-in-up" v-loading="loading">
    <el-button text class="back-btn" @click="$router.push('/knowledge')">
      <span class="back-arrow">←</span> 返回课程列表
    </el-button>

    <div class="course-hero" :style="{ background: heroBg }">
      <div class="hero-cover">
        <span class="hero-icon">
          <svg width="72" height="72" viewBox="0 0 72 72" fill="none">
            <rect x="14" y="10" width="44" height="52" rx="6" fill="rgba(255,255,255,0.15)"/>
            <path d="M24 24h24M24 34h24M24 44h16" stroke="rgba(255,255,255,0.7)" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </span>
      </div>
      <div class="hero-info">
        <h1 class="hero-name">{{ course?.name }}</h1>
        <p class="hero-meta">
          共 {{ chapters.length }} 章 · {{ totalSections }} 小节
        </p>
      </div>
    </div>

    <div class="chapters-container" v-if="chapters.length">
      <ChapterList
        :chapters="chapters"
        :activeSection="courseStore.currentSection"
        @select-chapter="onSelectChapter"
        @select-section="onSelectSection"
      />
    </div>

    <div class="empty-state" v-else-if="!loading">
      <p class="empty-text">暂无章节内容，请先上传课程文档</p>
      <el-button type="primary" @click="$router.push('/upload')">去上传</el-button>
    </div>

    <div class="quiz-actions" v-if="chapters.length">
      <el-dropdown @command="handleQuiz" placement="top">
        <el-button type="warning" size="large" class="quiz-btn">
          📝 为本课程出题 ▼
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="10">10 道题</el-dropdown-item>
            <el-dropdown-item command="20">20 道题</el-dropdown-item>
            <el-dropdown-item command="50">50 道题</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCourseStore } from '@/stores/useCourseStore'
import { useQuizStore } from '@/stores/useQuizStore'
import { storeToRefs } from 'pinia'
import ChapterList from '@/components/common/ChapterList.vue'

const route = useRoute()
const router = useRouter()
const courseStore = useCourseStore()
const quizStore = useQuizStore()
const { chapters, loading } = storeToRefs(courseStore)

const course = ref(null)

const totalSections = computed(() =>
  chapters.value.reduce((sum, ch) => sum + (ch.sections?.length || 0), 0)
)

const heroBg = 'linear-gradient(135deg, #1A56DB 0%, #0F2B5E 100%)'

onMounted(async () => {
  await courseStore.fetchCourses()
  course.value = courseStore.courses.find(c => c.id === route.params.courseId)
  if (course.value) {
    courseStore.selectCourse(course.value)
    await courseStore.fetchChapters(course.value.id)
  }
})

function onSelectChapter(chapter) {
  courseStore.selectChapter(chapter)
  const firstSection = chapter.sections?.[0]
  if (firstSection) {
    router.push(`/knowledge/${route.params.courseId}/${chapter.id}/${firstSection.id}`)
  } else {
    router.push(`/knowledge/${route.params.courseId}/${chapter.id}`)
  }
}

function onSelectSection(section, chapter) {
  courseStore.selectChapter(chapter)
  courseStore.selectSection(section)
  router.push(`/knowledge/${route.params.courseId}/${chapter.id}/${section.id}`)
}

async function handleQuiz(count) {
  if (!course.value) return
  await quizStore.generateQuiz({
    courseName: course.value.name,
    count: Number(count)
  })
  router.push('/quiz')
}
</script>

<style lang="scss" scoped>
.course-chapters {
  max-width: 800px;
  margin: 0 auto;
}

.back-btn {
  margin-bottom: 16px;
  font-size: 15px;

  .back-arrow {
    margin-right: 4px;
  }
}

.course-hero {
  display: flex;
  align-items: center;
  gap: 28px;
  padding: 36px 32px;
  border-radius: var(--radius-lg);
  margin-bottom: 28px;
  box-shadow: var(--shadow-hover);
}

.hero-cover {
  flex-shrink: 0;
}

.hero-info {
  color: #fff;
}

.hero-name {
  font-family: "Noto Serif SC", serif;
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 4px;
}

.hero-meta {
  font-size: 14px;
  opacity: 0.75;
  margin: 0;
}

.chapters-container {
  margin-bottom: 32px;
}

.quiz-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding-top: 8px;
}

.quiz-btn {
  min-width: 220px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-text-secondary);
}

.empty-text {
  margin-bottom: 16px;
  font-size: 15px;
}
</style>