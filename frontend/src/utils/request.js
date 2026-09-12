import axios from 'axios'
import { ElMessage } from 'element-plus'
import bus from './bus'
import { useUserStore } from '@/store/user'

/**
 * 统一请求封装
 *   baseURL 走 VITE_API_BASE（默认 /api，由 Nginx 反向代理到后端）
 *   code=1  成功，直接返回 data
 *   code=401 登录过期 -> 清登录态并跳登录页
 *   code=403 无权限（跨端访问）-> 提示并回自己的端
 */
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

function userStore() {
  return useUserStore()
}

request.interceptors.request.use((config) => {
  const user = userStore()
  if (user.token) {
    config.headers.Authorization = `Bearer ${user.token}`
  }
  return config
})

function gotoLogin() {
  const current = window.location.pathname + window.location.search
  window.location.href = `/login?redirect=${encodeURIComponent(current)}`
}

request.interceptors.response.use(
  (res) => {
    // 二级拦截器签发的续期 Token
    const refreshed = res.headers['x-auth-refresh']
    if (refreshed && userStore().token) {
      userStore().setToken(refreshed)
    }
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 1) return body.data
      if (body.code === 401) {
        userStore().logout()
        bus.toast('登录已过期，请重新登录', 'warning')
        gotoLogin()
        return Promise.reject(new Error(body.msg))
      }
      if (body.code === 403) {
        bus.toast(body.msg || '无权访问该端接口', 'error')
        // 跨端越权：回到自己角色的首页
        const store = userStore()
        window.location.href = store.landing || '/user/home'
        return Promise.reject(new Error(body.msg))
      }
      bus.toast(body.msg || '请求失败', 'error')
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    return body
  },
  (err) => {
    const status = err?.response?.status
    const msg = err?.response?.data?.msg || err.message || '网络异常'
    if (status === 401) {
      userStore().logout()
      gotoLogin()
    } else if (status === 403) {
      ElMessage.error(msg)
    } else if (!err.config?.silent) {
      ElMessage.error(msg)
    }
    return Promise.reject(err)
  }
)

export default request
