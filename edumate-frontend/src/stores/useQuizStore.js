import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { generateQuiz as apiGenerateQuiz, getQuizzes, deleteQuiz as apiDeleteQuiz } from '@/api/quiz'
import { ElMessage } from 'element-plus'

export const useQuizStore = defineStore('quiz', () => {
  const quizzes = ref([])
  const currentQuiz = ref(null)
  const loading = ref(false)

  const groupedQuizzes = computed(() => {
    const groups = {}
    quizzes.value.forEach(q => {
      const course = q.courseName || '未分类'
      if (!groups[course]) groups[course] = []
      groups[course].push(q)
    })
    const result = {}
    Object.entries(groups).forEach(([key, val]) => {
      result[key] = val.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    })
    return result
  })

  async function fetchQuizzes() {
    loading.value = true
    try {
      const data = await getQuizzes()
      quizzes.value = data || []
    } catch {
      quizzes.value = []
    } finally {
      loading.value = false
    }
  }

  async function generateQuiz(params) {
    loading.value = true
    try {
      const questions = await apiGenerateQuiz(params)
      const name = buildQuizName(params)
      const quiz = {
        id: 'quiz_' + Date.now(),
        name,
        courseName: params.courseName,
        source: params.chapter
          ? (params.section ? '节测试' : '章测试')
          : '课程测试',
        questions: questions || [],
        createdAt: new Date().toISOString(),
        count: (questions || []).length
      }
      quizzes.value.unshift(quiz)
      ElMessage.success(`出题完成：${name}（${quiz.count} 题）`)
      return quiz
    } catch {
      ElMessage.error('出题失败，请稍后重试')
    } finally {
      loading.value = false
    }
  }

  function buildQuizName(params) {
    if (params.section) {
      return `${params.courseName}-${params.chapter}-${params.section}-节测试`
    }
    if (params.chapter) {
      return `${params.courseName}-${params.chapter}-章测试`
    }
    return `${params.courseName}-综合测试`
  }

  async function deleteQuiz(id) {
    try {
      await apiDeleteQuiz(id)
    } catch {
      // 后端可能未实现，直接前端删除
    }
    quizzes.value = quizzes.value.filter(q => q.id !== id)
    ElMessage.success('题库已删除')
  }

  function selectQuiz(quiz) {
    currentQuiz.value = quiz
  }

  return {
    quizzes, currentQuiz, loading, groupedQuizzes,
    fetchQuizzes, generateQuiz, deleteQuiz, selectQuiz
  }
})