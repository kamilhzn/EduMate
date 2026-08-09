import request from './request'

export function search(query, topK = 5) {
  return request.post('/api/search', { query, topK })
}