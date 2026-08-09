import request from './request'

export function getCourses() {
  return request.get('/api/courses')
}

export function createCourse(data) {
  return request.post('/api/courses', data)
}

export function getChapters(courseId) {
  return request.get(`/api/courses/${courseId}/chapters`)
}

export function getSectionContent(courseId, sectionId) {
  return request.get(`/api/courses/${courseId}/sections/${sectionId}`)
}