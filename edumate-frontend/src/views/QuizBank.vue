<template>
  <div class="quiz-bank fade-in-up" v-loading="loading">
    <h1 class="page-title">📝 智能题库</h1>

    <div v-if="Object.keys(groupedQuizzes).length === 0" class="empty-state">
      <div class="empty-icon">
        <svg width="56" height="56" viewBox="0 0 56 56" fill="none">
          <rect x="8" y="6" width="40" height="44" rx="6" stroke="var(--color-border)" stroke-width="2"/>
          <path d="M20 22h16M20 30h12M20 38h8" stroke="var(--color-border)" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </div>
      <p class="empty-title">暂无题库</p>
      <p class="empty-desc">请先到知识库中学习并出题</p>
      <el-button type="primary" @click="$router.push('/knowledge')">前往知识库</el-button>
    </div>

    <div v-for="(quizList, courseName) in groupedQuizzes" :key="courseName">
      <CourseDivider
        :courseName="courseName"
        :collapsed="collapsedCourses.has(courseName)"
        @toggle="toggleCourse(courseName)"
      />
      <transition name="expand">
        <div v-if="!collapsedCourses.has(courseName)" class="quiz-grid">
          <QuizCard
            v-for="quiz in quizList"
            :key="quiz.id"
            :name="quiz.name"
            :source="quiz.source"
            :count="quiz.count"
            :createdAt="quiz.createdAt"
            @click="openQuiz(quiz)"
            @delete="handleDelete(quiz.id)"
          />
        </div>
      </transition>
    </div>

    <!-- 做题弹窗 -->
    <el-dialog
      v-model="showQuizDialog"
      :title="currentQuiz?.name"
      width="700px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="quiz-questions" v-if="currentQuiz">
        <div
          v-for="(q, qi) in currentQuiz.questions"
          :key="qi"
          class="question-item"
          :class="{ revealed: revealedAnswers[qi] }"
        >
          <div class="question-header">
            <span class="question-num">第 {{ qi + 1 }} 题</span>
            <el-tag size="small" type="info">{{ q.type }}</el-tag>
          </div>
          <p class="question-stem">{{ q.stem }}</p>
          <div class="question-options" v-if="q.options?.length">
            <div
              v-for="(opt, oi) in q.options"
              :key="oi"
              class="option-item"
              :class="{ correct: revealedAnswers[qi] && opt === q.answer }"
            >
              <span class="option-letter">{{ String.fromCharCode(65 + oi) }}</span>
              <span class="option-text">{{ opt }}</span>
            </div>
          </div>
          <div class="question-answer" v-if="q.answer && !q.options?.length">
            <span class="answer-label">答案：</span>
            <span class="answer-value" v-if="revealedAnswers[qi]">{{ q.answer }}</span>
            <span class="answer-value hidden" v-else>点击"显示答案"查看</span>
          </div>
          <div class="question-explanation" v-if="revealedAnswers[qi] && q.explanation">
            <span class="explanation-label">解析：</span>
            <span>{{ q.explanation }}</span>
          </div>
          <el-button
            v-if="!revealedAnswers[qi]"
            size="small"
            text
            type="primary"
            @click="revealedAnswers[qi] = true"
          >
            显示答案
          </el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="showQuizDialog = false">关闭</el-button>
        <el-button type="primary" @click="revealAll">显示全部答案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useQuizStore } from '@/stores/useQuizStore'
import { storeToRefs } from 'pinia'
import CourseDivider from '@/components/course/CourseDivider.vue'
import QuizCard from '@/components/common/QuizCard.vue'

const quizStore = useQuizStore()
const { groupedQuizzes, currentQuiz, loading } = storeToRefs(quizStore)
const collapsedCourses = ref(new Set())
const showQuizDialog = ref(false)
const revealedAnswers = reactive({})

onMounted(() => {
  quizStore.fetchQuizzes()
})

function toggleCourse(name) {
  const newSet = new Set(collapsedCourses.value)
  if (newSet.has(name)) {
    newSet.delete(name)
  } else {
    newSet.add(name)
  }
  collapsedCourses.value = newSet
}

function openQuiz(quiz) {
  quizStore.selectQuiz(quiz)
  Object.keys(revealedAnswers).forEach(k => delete revealedAnswers[k])
  showQuizDialog.value = true
}

function revealAll() {
  if (currentQuiz.value?.questions) {
    currentQuiz.value.questions.forEach((_, i) => {
      revealedAnswers[i] = true
    })
  }
}

function handleDelete(id) {
  quizStore.deleteQuiz(id)
}
</script>

<style lang="scss" scoped>
.quiz-bank {
  max-width: 960px;
  margin: 0 auto;
}

.quiz-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 8px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--color-text-secondary);

  .empty-icon { margin-bottom: 16px; }
  .empty-title { font-size: 17px; font-weight: 600; color: var(--color-text); margin: 0 0 4px; }
  .empty-desc { font-size: 14px; margin: 0 0 16px; }
}

.quiz-questions {
  max-height: 60vh;
  overflow-y: auto;
}

.question-item {
  padding: 16px;
  margin-bottom: 12px;
  background: var(--color-bg);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);

  &.revealed {
    border-color: var(--color-success);
  }
}

.question-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.question-num {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
}

.question-stem {
  font-size: 15px;
  margin: 0 0 10px;
  line-height: 1.6;
}

.question-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.option-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  transition: background 0.2s;

  &.correct {
    background: var(--color-success-light);
  }
}

.option-letter {
  font-weight: 600;
  color: var(--color-text-secondary);
  min-width: 20px;
}

.option-text {
  font-size: 14px;
}

.question-answer {
  margin-bottom: 6px;
  font-size: 14px;
}

.answer-label {
  font-weight: 600;
  color: var(--color-text);
}

.answer-value {
  color: var(--color-success);
  font-weight: 500;

  &.hidden {
    color: var(--color-text-secondary);
  }
}

.question-explanation {
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 8px 10px;
  background: white;
  border-radius: 6px;
  margin-top: 6px;
}

.explanation-label {
  font-weight: 600;
  color: var(--color-text);
}
</style>