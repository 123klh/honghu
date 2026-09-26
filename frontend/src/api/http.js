import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store/auth'
import router from '../router'

/**
 * 统一 HTTP 客户端。
 * 后端 GlobalExceptionHandler 对业务异常返回 HTTP 200 + 信封里的 code，
 * 所以不能只看 HTTP 状态，必须检查 body.code。
 */
const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) config.headers.Authorization = `Bearer ${auth.token}`
  return config
})

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 200) return body.data
      if (body.code === 401) {
        useAuthStore().logout()
        router.push({ name: 'login' })
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || `code ${body.code}`))
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      useAuthStore().logout()
      router.push({ name: 'login' })
    }
    const msg = err.response?.data?.message || err.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(err)
  }
)

export default http
