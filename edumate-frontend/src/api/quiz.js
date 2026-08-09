import request from './request'

export function generateQuiz(params) {
  return request.post('/api/quiz/generate', params)
}

export function getQuizzes() {
  return request.get('/api/quizzes')
}

export function deleteQuiz(id) {
  return request.delete(`/api/quizzes/${id}`)
}