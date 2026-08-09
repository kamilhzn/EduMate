import request from './request'

export function uploadDocument(formData) {
  return request.post('/api/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}