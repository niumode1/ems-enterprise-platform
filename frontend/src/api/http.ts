import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResponse } from '@/types'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30_000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('ems_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '请求失败'
    if (status === 401) {
      localStorage.removeItem('ems_access_token')
      localStorage.removeItem('ems_refresh_token')
      localStorage.removeItem('ems_user')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<unknown, ApiResponse<T>>(config)
  if (response.code !== 0) {
    ElMessage.error(response.message)
    throw new Error(response.message)
  }
  return response.data
}

export default http
