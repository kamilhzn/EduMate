<template>
  <div class="reader-page fade-in-up" v-loading="loading">
    <div class="reader-header">
      <el-button text class="back-btn" @click="goBack">
        <span class="back-arrow">←</span> 返回章节列表
      </el-button>
      <el-breadcrumb separator="›" class="breadcrumb">
        <el-breadcrumb-item :to="{ path: '/knowledge' }">知识库</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: `/knowledge/${courseId}` }">{{ courseName }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ chapterTitle }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ sectionTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="read-progress">
        <div class="progress-bar" :style="{ width: progressPercent + '%' }"></div>
      </div>
    </div>

    <div class="reader-content" ref="contentRef" @scroll="onScroll">
      <MarkdownViewer :content="sectionContent" />
    </div>

    <div class="reader-footer" v-if="!loading && sectionContent">
      <div class="quiz-section">
        <!-- 节出题 -->
        <el-dropdown @command="handleSectionQuiz" placement="top">
          <el-button type="warning" class="quiz-btn">
            ✨ 我已学习本节，请为我出题 ▼
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="1">1 道题</el-dropdown-item>
              <el-dropdown-item command="3">3 道题</el-dropdown-item>
              <el-dropdown-item command="5">5 道题</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 章出题（仅最后一节显示） -->
        <el-dropdown v-if="isLastSection" @command="handleChapterQuiz" placement="top">
          <el-button type="warning" class="quiz-btn">
            📚 我已学习本章，请为我出题 ▼
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="5">5 道题（随机选节）</el-dropdown-item>
              <el-dropdown-item command="10">10 道题（随机选节）</el-dropdown-item>
              <el-dropdown-item command="20">20 道题（随机选节）</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-button v-if="hasNext" type="primary" size="large" @click="goNext" class="next-btn">
        📖 下一节：{{ nextSectionTitle }} →
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCourseStore } from '@/stores/useCourseStore'
import { useQuizStore } from '@/stores/useQuizStore'
import { storeToRefs } from 'pinia'
import MarkdownViewer from '@/components/common/MarkdownViewer.vue'
import { getSectionContent } from '@/api/course'

const route = useRoute()
const router = useRouter()
const courseStore = useCourseStore()
const quizStore = useQuizStore()
const { chapters, currentChapter, currentSection } = storeToRefs(courseStore)

const sectionContent = ref('')
const loading = ref(true)
const progressPercent = ref(0)
const contentRef = ref(null)

const courseId = computed(() => route.params.courseId)
const courseName = computed(() => courseStore.currentCourse?.name || '')
const chapterTitle = computed(() => currentChapter.value?.title || '')
const sectionTitle = computed(() => currentSection.value?.title || '')

const isLastSection = computed(() => {
  if (!currentChapter.value || !currentSection.value) return false
  const sections = currentChapter.value.sections || []
  return sections.length > 0 && sections[sections.length - 1].id === currentSection.value.id
})

const hasNext = computed(() => {
  if (!currentChapter.value || !currentSection.value) return false
  const sections = currentChapter.value.sections || []
  const idx = sections.findIndex(s => s.id === currentSection.value.id)
  if (idx >= 0 && idx < sections.length - 1) return true
  const chIdx = chapters.value.findIndex(c => c.id === currentChapter.value.id)
  return chIdx >= 0 && chIdx < chapters.value.length - 1
})

const nextSectionTitle = computed(() => {
  if (!currentChapter.value || !currentSection.value) return ''
  const sections = currentChapter.value.sections || []
  const idx = sections.findIndex(s => s.id === currentSection.value.id)
  if (idx >= 0 && idx < sections.length - 1) return sections[idx + 1].title
  const chIdx = chapters.value.findIndex(c => c.id === currentChapter.value.id)
  if (chIdx >= 0 && chIdx < chapters.value.length - 1) {
    const nextCh = chapters.value[chIdx + 1]
    return (nextCh.sections?.[0])?.title || nextCh.title
  }
  return ''
})

function onScroll() {
  const el = contentRef.value
  if (!el) return
  const pct = el.scrollTop / (el.scrollHeight - el.clientHeight)
  progressPercent.value = Math.min(Math.round(pct * 100), 100)
}

function goBack() {
  router.push(`/knowledge/${courseId.value}`)
}

function goNext() {
  const sections = currentChapter.value?.sections || []
  const idx = sections.findIndex(s => s.id === currentSection.value?.id)
  if (idx >= 0 && idx < sections.length - 1) {
    const next = sections[idx + 1]
    courseStore.selectSection(next)
    router.push(`/knowledge/${courseId.value}/${currentChapter.value.id}/${next.id}`)
  } else {
    const chIdx = chapters.value.findIndex(c => c.id === currentChapter.value?.id)
    if (chIdx >= 0 && chIdx < chapters.value.length - 1) {
      const nextCh = chapters.value[chIdx + 1]
      const firstSection = nextCh.sections?.[0]
      courseStore.selectChapter(nextCh)
      if (firstSection) {
        courseStore.selectSection(firstSection)
        router.push(`/knowledge/${courseId.value}/${nextCh.id}/${firstSection.id}`)
      } else {
        router.push(`/knowledge/${courseId.value}/${nextCh.id}`)
      }
    }
  }
}

async function handleSectionQuiz(count) {
  await quizStore.generateQuiz({
    courseName: courseName.value,
    chapter: chapterTitle.value,
    section: sectionTitle.value,
    count: Number(count)
  })
  router.push('/quiz')
}

async function handleChapterQuiz(count) {
  await quizStore.generateQuiz({
    courseName: courseName.value,
    chapter: chapterTitle.value,
    count: Number(count)
  })
  router.push('/quiz')
}

async function loadContent() {
  loading.value = true
  try {
    const data = await getSectionContent(courseId.value, route.params.sectionId)
    sectionContent.value = typeof data === 'string' ? data : data?.content || '# 暂无内容'
  } catch {
    sectionContent.value = '# 加载失败\n\n请稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await courseStore.fetchCourses()
  const course = courseStore.courses.find(c => c.id === courseId.value)
  if (course) courseStore.selectCourse(course)
  await courseStore.fetchChapters(courseId.value)

  const chapter = chapters.value.find(c => c.id === route.params.chapterId)
  if (chapter) {
    courseStore.selectChapter(chapter)
    const section = chapter.sections?.find(s => s.id === route.params.sectionId)
    if (section) courseStore.selectSection(section)
  }

  await loadContent()
})
</script>

<style lang="scss" scoped>
.reader-page {
  max-width: 800px;
  margin: 0 auto;
}

.reader-header {
  margin-bottom: 20px;
}

.back-btn {
  margin-bottom: 8px;
  font-size: 15px;

  .back-arrow {
    margin-right: 4px;
  }
}

.breadcrumb {
  margin-bottom: 12px;
}

.read-progress {
  height: 3px;
  background: var(--color-border);
  border-radius: 2px;
  margin-top: 12px;
  overflow: hidden;

  .progress-bar {
    height: 100%;
    background: linear-gradient(90deg, var(--color-accent), var(--color-primary));
    border-radius: 2px;
    transition: width 0.4s ease-out;
  }
}

.reader-content {
  background: var(--color-surface);
  border-radius: var(--radius-lg);
  padding: 32px 40px;
  box-shadow: var(--shadow-card);
  min-height: 400px;
  max-height: calc(100vh - 320px);
  overflow-y: auto;
}

.reader-footer {
  margin-top: 24px;
  text-align: center;
}

.quiz-section {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.quiz-btn {
  min-width: 240px;
}

.next-btn {
  margin-top: 8px;
}
</style>