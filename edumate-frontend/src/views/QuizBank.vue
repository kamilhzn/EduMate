<template>
  <div class="quiz-bank">
    <h1 class="page-title">智能题库</h1>

    <LoadingSpinner v-if="loading && quizzes.length === 0" text="加载题库中..." />

    <div v-else-if="quizzes.length === 0" class="empty-state">
      <EmptyState
        icon="📝"
        title="暂无题库"
        desc="请先到知识库中学习并出题"
      >
        <el-button type="primary" @click="$router.push('/knowledge')">前往知识库</el-button>
      </EmptyState>
    </div>

    <div v-else>
      <div v-for="(quizList, courseName) in groupedQuizzes" :key="courseName">
        <CourseDivider
          :courseName="courseName"
          :count="quizList.length"
          :collapsed="collapsedCourses.has(courseName)"
          :dotColor="getCourseColor(courseName)"
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
    </div>

    <el-dialog
      v-model="showQuizDialog"
      :title="currentQuiz?.name"
      width="80%"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="quiz-questions" v-if="currentQuiz">
        <div
          v-for="(q, qi) in currentQuiz.questions"
          :key="qi"
          class="question-item"
          :class="{ checked: checkedAnswers[qi] }"
        >
          <!-- 题头：题号 + 题型标签 -->
          <div class="question-header">
            <span class="question-num">第 {{ qi + 1 }} 题</span>
            <el-tag size="small" :type="getTypeTagType(q.type)">
              {{ getTypeLabel(q.type) }}
            </el-tag>
          </div>

          <!-- 题干 -->
          <p class="question-stem">{{ q.stem }}</p>

          <!-- 单选题 -->
          <div v-if="q.type === 'single_choice' && q.options?.length" class="question-options">
            <div
              v-for="(opt, oi) in q.options"
              :key="oi"
              class="option-item"
              :class="{
                selected: isOptionSelected(qi, optLetter(oi)),
                correct: checkedAnswers[qi] && isCorrectOption(q, optLetter(oi)),
                wrong: checkedAnswers[qi] && isOptionSelected(qi, optLetter(oi)) && !isCorrectOption(q, optLetter(oi))
              }"
              @click="!checkedAnswers[qi] && selectSingle(qi, optLetter(oi))"
            >
              <span class="option-letter">{{ optLetter(oi) }}</span>
              <span class="option-text">{{ opt }}</span>
              <span v-if="checkedAnswers[qi] && isCorrectOption(q, optLetter(oi))" class="option-icon correct">✓</span>
              <span v-if="checkedAnswers[qi] && isOptionSelected(qi, optLetter(oi)) && !isCorrectOption(q, optLetter(oi))" class="option-icon wrong">✗</span>
            </div>
          </div>

          <!-- 多选题 -->
          <div v-else-if="q.type === 'multiple_choice' && q.options?.length" class="question-options">
            <div
              v-for="(opt, oi) in q.options"
              :key="oi"
              class="option-item"
              :class="{
                selected: isOptionSelected(qi, optLetter(oi)),
                correct: checkedAnswers[qi] && isCorrectOption(q, optLetter(oi)),
                wrong: checkedAnswers[qi] && isOptionSelected(qi, optLetter(oi)) && !isCorrectOption(q, optLetter(oi))
              }"
              @click="!checkedAnswers[qi] && toggleMultiple(qi, optLetter(oi))"
            >
              <span class="option-checkbox">
                <span v-if="isOptionSelected(qi, optLetter(oi))" class="checked">☑</span>
                <span v-else class="unchecked">☐</span>
              </span>
              <span class="option-letter">{{ optLetter(oi) }}</span>
              <span class="option-text">{{ opt }}</span>
              <span v-if="checkedAnswers[qi] && isCorrectOption(q, optLetter(oi))" class="option-icon correct">✓</span>
              <span v-if="checkedAnswers[qi] && isOptionSelected(qi, optLetter(oi)) && !isCorrectOption(q, optLetter(oi))" class="option-icon wrong">✗</span>
            </div>
          </div>

          <!-- 填空题 -->
          <div v-else-if="q.type === 'fill_blank'" class="fill-blank-area">
            <div class="input-row">
              <el-input
                v-model="userAnswers[qi]"
                placeholder="请输入你的答案"
                :disabled="checkedAnswers[qi]"
                clearable
                class="fill-input"
              />
            </div>
            <div v-if="checkedAnswers[qi]" class="fill-blank-result">
              <span v-if="isAnswerCorrect(q, qi)" class="result-correct">✓ 回答正确</span>
              <span v-else class="result-wrong">
                ✗ 回答错误，参考答案：
                <span class="correct-answers">{{ formatAnswers(q.answers || q.answer) }}</span>
              </span>
            </div>
          </div>

          <!-- 简答题 -->
          <div v-else-if="q.type === 'short_answer'" class="short-answer-area">
            <p class="short-answer-hint">请在纸上完成作答，然后点击下方按钮核对答案查看参考答案与解析。</p>
            <div v-if="checkedAnswers[qi]" class="short-answer-refs">
              <div class="ref-title">参考答案要点：</div>
              <ul class="ref-list">
                <li v-for="(ref, ri) in (q.answers || [q.answer])" :key="ri">{{ ref }}</li>
              </ul>
            </div>
          </div>

          <!-- 解析区域 -->
          <div v-if="checkedAnswers[qi] && q.explanation" class="question-explanation">
            <span class="explanation-label">💡 解析：</span>
            <span>{{ q.explanation }}</span>
          </div>

          <!-- 操作按钮 -->
          <div class="question-actions">
            <el-button
              v-if="!checkedAnswers[qi]"
              size="small"
              type="primary"
              :disabled="!canCheck(qi, q)"
              @click="checkAnswer(qi)"
            >
              核对答案
            </el-button>
            <el-tag
              v-else
              :type="isQuestionCorrect(q, qi) ? 'success' : 'danger'"
              size="small"
            >
              {{ isQuestionCorrect(q, qi) ? '✓ 回答正确' : '✗ 回答错误' }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 统计栏 -->
      <div v-if="currentQuiz && hasChecked" class="quiz-stats">
        <el-tag type="info" size="large">
          已核对 {{ checkedCount }} / {{ currentQuiz.questions?.length || 0 }} 题
          &nbsp;|&nbsp;
          正确 {{ correctCount }} 题
          &nbsp;|&nbsp;
          错误 {{ wrongCount }} 题
        </el-tag>
      </div>

      <template #footer>
        <el-button @click="showQuizDialog = false">关闭</el-button>
        <el-button type="primary" @click="checkAll">全部核对</el-button>
        <el-button @click="resetAll">重置答题</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useQuizStore } from '@/stores/useQuizStore'
import QuizCard from '@/components/common/QuizCard.vue'
import CourseDivider from '@/components/course/CourseDivider.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const quizStore = useQuizStore()

const collapsedCourses = ref(new Set())
const showQuizDialog = ref(false)
const currentQuiz = ref(null)
const userAnswers = reactive({})
const checkedAnswers = reactive({})

const loading = computed(() => quizStore.loading)
const quizzes = computed(() => quizStore.quizzes)
const groupedQuizzes = computed(() => quizStore.groupedQuizzes)

const hasChecked = computed(() => Object.values(checkedAnswers).some(v => v))
const checkedCount = computed(() => Object.values(checkedAnswers).filter(v => v).length)
const correctCount = computed(() => {
  if (!currentQuiz.value?.questions) return 0
  return currentQuiz.value.questions.reduce((sum, q, qi) => {
    return sum + (checkedAnswers[qi] && isQuestionCorrect(q, qi) ? 1 : 0)
  }, 0)
})
const wrongCount = computed(() => checkedCount.value - correctCount.value)

onMounted(() => {
  quizStore.fetchQuizzes()
})

const COURSE_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#8e44ad']

function getCourseColor(courseName) {
  const keys = Object.keys(groupedQuizzes.value)
  const idx = keys.indexOf(courseName)
  return COURSE_COLORS[idx % COURSE_COLORS.length]
}

function toggleCourse(courseName) {
  const s = new Set(collapsedCourses.value)
  if (s.has(courseName)) {
    s.delete(courseName)
  } else {
    s.add(courseName)
  }
  collapsedCourses.value = s
}

function openQuiz(quiz) {
  currentQuiz.value = quiz
  resetAnswers()
  showQuizDialog.value = true
}

function resetAnswers() {
  Object.keys(userAnswers).forEach(k => delete userAnswers[k])
  Object.keys(checkedAnswers).forEach(k => delete checkedAnswers[k])
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定要删除这个题库吗？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await quizStore.deleteQuiz(id)
    ElMessage.success('删除成功')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

/* ---------- 题型工具 ---------- */
function getTypeLabel(type) {
  const map = {
    single_choice: '单选题',
    multiple_choice: '多选题',
    fill_blank: '填空题',
    short_answer: '简答题'
  }
  return map[type] || type || '选择题'
}

function getTypeTagType(type) {
  const map = {
    single_choice: '',
    multiple_choice: 'warning',
    fill_blank: 'success',
    short_answer: 'info'
  }
  return map[type] || 'info'
}

function optLetter(index) {
  return String.fromCharCode(65 + index)
}

/* ---------- 答题交互 ---------- */
function selectSingle(qi, letter) {
  userAnswers[qi] = letter
}

function toggleMultiple(qi, letter) {
  const current = userAnswers[qi] || []
  const idx = current.indexOf(letter)
  if (idx >= 0) {
    current.splice(idx, 1)
  } else {
    current.push(letter)
  }
  userAnswers[qi] = [...current]
}

function isOptionSelected(qi, letter) {
  const ans = userAnswers[qi]
  if (Array.isArray(ans)) {
    return ans.includes(letter)
  }
  return ans === letter
}

/* ---------- 核对逻辑 ---------- */
function canCheck(qi, q) {
  if (q.type === 'single_choice') {
    return !!userAnswers[qi]
  }
  if (q.type === 'multiple_choice') {
    const ans = userAnswers[qi]
    return Array.isArray(ans) && ans.length > 0
  }
  if (q.type === 'fill_blank') {
    return !!userAnswers[qi]?.trim()
  }
  if (q.type === 'short_answer') {
    return true
  }
  return false
}

function checkAnswer(qi) {
  checkedAnswers[qi] = true
}

function checkAll() {
  currentQuiz.value?.questions?.forEach((q, qi) => {
    if (!checkedAnswers[qi]) {
      checkedAnswers[qi] = true
    }
  })
}

function resetAll() {
  currentQuiz.value?.questions?.forEach((_, qi) => {
    delete checkedAnswers[qi]
    delete userAnswers[qi]
  })
}

/* ---------- 答案判断 ---------- */
function getCorrectAnswers(q) {
  if (q.answers && q.answers.length > 0) {
    return q.answers.map(a => String(a).trim())
  }
  if (q.answer) {
    return [String(q.answer).trim()]
  }
  return []
}

function isCorrectOption(q, letter) {
  const correct = getCorrectAnswers(q)
  return correct.includes(letter)
}

function isAnswerCorrect(q, qi) {
  if (q.type === 'fill_blank') {
    const userAns = String(userAnswers[qi] || '').trim().toLowerCase()
    const correct = getCorrectAnswers(q).map(a => String(a).trim().toLowerCase())
    return correct.includes(userAns)
  }
  return false
}

function isQuestionCorrect(q, qi) {
  if (q.type === 'single_choice' || q.type === 'multiple_choice') {
    const userAns = userAnswers[qi]
    const correct = getCorrectAnswers(q)
    if (q.type === 'single_choice') {
      return correct.includes(String(userAns).trim())
    }
    const userSet = (userAns || []).map(a => a.trim()).sort().join(',')
    const correctSet = correct.map(a => a.trim()).sort().join(',')
    return userSet === correctSet
  }
  if (q.type === 'fill_blank') {
    return isAnswerCorrect(q, qi)
  }
  if (q.type === 'short_answer') {
    return true
  }
  return false
}

function formatAnswers(answers) {
  if (Array.isArray(answers)) {
    return answers.join(' / ')
  }
  return String(answers || '')
}
</script>

<style scoped>
.quiz-bank {
  width: 100%;
}

.quiz-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-bottom: 8px;
}

.empty-state {
  padding: 60px 0;
}

.expand-enter-active,
.expand-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.expand-enter-from,
.expand-leave-to {
  opacity: 0;
  max-height: 0;
}

.expand-enter-to,
.expand-leave-from {
  opacity: 1;
  max-height: 2000px;
}

.quiz-questions {
  max-height: 65vh;
  overflow-y: auto;
}

.question-item {
  padding: 16px;
  margin-bottom: 12px;
  background: var(--color-bg, #f5f7fa);
  border-radius: 8px;
  border: 1px solid var(--color-border, #e4e7ed);
  transition: border-color 0.2s;
}

.question-item.checked {
  border-color: var(--color-primary, #409eff);
}

.question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.question-num {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary, #409eff);
}

.question-stem {
  font-size: 15px;
  margin: 0 0 12px;
  line-height: 1.6;
  color: var(--color-text, #303133);
}

.question-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.option-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--color-border, #e4e7ed);
  background: white;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.option-item:hover:not(.correct):not(.wrong) {
  border-color: var(--color-primary, #409eff);
  background: var(--color-primary-light, #ecf5ff);
}

.option-item.selected {
  border-color: var(--color-primary, #409eff);
  background: var(--color-primary-light, #ecf5ff);
}

.option-item.correct {
  border-color: #67c23a;
  background: #f0f9eb;
}

.option-item.wrong {
  border-color: #f56c6c;
  background: #fef0f0;
}

.option-letter {
  font-weight: 600;
  color: var(--color-text-secondary, #606266);
  min-width: 20px;
}

.option-text {
  font-size: 14px;
  flex: 1;
  color: var(--color-text, #303133);
}

.option-checkbox {
  font-size: 16px;
  color: var(--color-primary, #409eff);
}

.option-checkbox .unchecked {
  color: var(--color-text-secondary, #909399);
}

.option-icon {
  font-weight: 600;
  font-size: 16px;
  margin-left: auto;
}

.option-icon.correct {
  color: #67c23a;
}

.option-icon.wrong {
  color: #f56c6c;
}

.fill-blank-area {
  margin-bottom: 12px;
}

.input-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.fill-input {
  max-width: 400px;
}

.fill-blank-result {
  font-size: 14px;
  margin-top: 6px;
}

.result-correct {
  color: #67c23a;
  font-weight: 600;
}

.result-wrong {
  color: #f56c6c;
}

.correct-answers {
  font-weight: 600;
  color: #67c23a;
}

.short-answer-area {
  margin-bottom: 12px;
}

.short-answer-hint {
  font-size: 13px;
  color: var(--color-text-secondary, #909399);
  margin: 0 0 8px;
  padding: 8px 12px;
  background: var(--color-bg, #f5f7fa);
  border-radius: 6px;
}

.short-answer-refs {
  margin-top: 8px;
  padding: 10px 14px;
  background: #f0f9eb;
  border-radius: 8px;
  border: 1px solid #d9ecdb;
}

.ref-title {
  font-size: 13px;
  font-weight: 600;
  color: #67c23a;
  margin-bottom: 6px;
}

.ref-list {
  margin: 0;
  padding-left: 18px;
}

.ref-list li {
  font-size: 14px;
  color: var(--color-text, #303133);
  margin-bottom: 4px;
}

.question-explanation {
  font-size: 13px;
  color: var(--color-text-secondary, #606266);
  padding: 10px 14px;
  background: white;
  border-radius: 8px;
  margin-top: 10px;
  border: 1px solid var(--color-border, #e4e7ed);
  line-height: 1.6;
}

.explanation-label {
  font-weight: 600;
  color: var(--color-text, #303133);
}

.question-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

.quiz-stats {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border, #e4e7ed);
  text-align: center;
}
</style>
