import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCourses, createCourse as apiCreateCourse, getChapters } from '@/api/course'

export const useCourseStore = defineStore('course', () => {
  const courses = ref([])
  const currentCourse = ref(null)
  const chapters = ref([])
  const currentChapter = ref(null)
  const currentSection = ref(null)
  const loading = ref(false)

  const coverColors = ['#1A56DB', '#E8A817', '#D94040', '#2DA44E', '#8B5CF6', '#0F2B5E', '#E87722', '#6366F1']

  async function fetchCourses() {
    loading.value = true
    try {
      const data = await getCourses()
      courses.value = (data || []).map((c, i) => ({
        ...c,
        coverColor: c.coverColor || coverColors[i % coverColors.length]
      }))
    } catch {
      courses.value = []
    } finally {
      loading.value = false
    }
  }

  async function createNewCourse(name) {
    const course = await apiCreateCourse({ name })
    const newCourse = {
      ...course,
      coverColor: coverColors[courses.value.length % coverColors.length]
    }
    courses.value.unshift(newCourse)
    return newCourse
  }

  async function fetchChapters(courseId) {
    loading.value = true
    try {
      const data = await getChapters(courseId)
      chapters.value = data || []
    } catch {
      chapters.value = []
    } finally {
      loading.value = false
    }
  }

  function selectCourse(course) {
    currentCourse.value = course
    chapters.value = []
    currentChapter.value = null
    currentSection.value = null
  }

  function selectChapter(chapter) {
    currentChapter.value = chapter
    currentSection.value = null
  }

  function selectSection(section) {
    currentSection.value = section
  }

  return {
    courses, currentCourse, chapters, currentChapter, currentSection, loading,
    fetchCourses, createNewCourse, fetchChapters,
    selectCourse, selectChapter, selectSection
  }
})