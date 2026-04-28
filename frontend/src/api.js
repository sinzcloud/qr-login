import axios from 'axios'

// 1. 从环境变量获取 baseURL，如果未配置则默认为 '/api'
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const request = axios.create({ 
  baseURL, 
  timeout: 10000 
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

request.interceptors.response.use(
  res => {
    // 假设后端返回结构为 { code: 200, data: {...}, message: "..." }
    if (res.data.code !== 200) {
      return Promise.reject(new Error(res.data.message || '请求失败'))
    }
    return res.data.data
  },
  error => {
    // 2. 增加全局错误处理（可选，但推荐）
    if (error.response) {
      switch (error.response.status) {
        case 401:
          // Token 过期或未授权，清除本地存储并跳转登录
          localStorage.removeItem('token')
          window.location.href = '/login'
          break
        case 403:
          console.error('拒绝访问')
          break
        case 500:
          console.error('服务器错误')
          break
        default:
          console.error('网络错误')
      }
    }
    return Promise.reject(error)
  }
)

export default request