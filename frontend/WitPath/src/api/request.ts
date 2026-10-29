import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { useUserStore } from '@/stores/user'

const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'
const TOKEN_KEY = 'witpath_token'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

/**
 * 统一响应体：{ code, msg, data }
 */
export interface ApiResult<T> {
  code: number
  msg?: string
  data: T
}

export const http = axios.create({
  baseURL: API_BASE,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

function attachToken(config: InternalAxiosRequestConfig) {
  const token = getToken()
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  return config
}

/** 根路径实例：登录 / 注册 / 健康检查等不在 /api 前缀下的接口 */
export const httpRoot = axios.create({
  baseURL: '/',
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use(attachToken)
httpRoot.interceptors.request.use(attachToken)

function onError(error: AxiosError<ApiResult<unknown>>) {
  const status = error.response?.status
  const msg = error.response?.data?.msg ?? error.message ?? '请求失败'

  if (status === 401) {
    // Token 失效：清理本地状态并跳转登录
    try {
      const user = useUserStore()
      user.logout(false)
    } catch {
      clearToken()
    }
    if (!location.pathname.startsWith('/login')) {
      location.href = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
    }
  }
  return Promise.reject(new Error(msg))
}

http.interceptors.response.use((response) => response, onError)
httpRoot.interceptors.response.use((response) => response, onError)

/** 解包统一响应体，失败时抛出可直接展示的错误信息 */
export async function unwrap<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const res = await promise
  const body = res.data
  if (body.code !== 200) {
    throw new Error(body.msg ?? '请求失败')
  }
  return body.data
}

export function qs(params: Record<string, unknown> = {}): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    search.append(key, String(value))
  })
  const str = search.toString()
  return str ? `?${str}` : ''
}

export { API_BASE }
