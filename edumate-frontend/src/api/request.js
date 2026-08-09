import axios from 'axios'
import { ElMessage } from 'element-plus'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 120000
})

service.interceptors.response.use(
  response => response.data,
  error => {
    const msg = error.response?.data?.message || '请求失败，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default service