import { defineStore } from 'pinia'

const KEY = 'honghu.auth'

function load() {
  try {
    return JSON.parse(localStorage.getItem(KEY) || 'null') || { token: '', user: null }
  } catch {
    return { token: '', user: null }
  }
}

/**
 * 登录态。token 与用户信息持久化到 localStorage，刷新不丢；
 * type 区分 user / manager，路由守卫据此拦截管理端页面。
 */
export const useAuthStore = defineStore('auth', {
  state: () => load(),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isManager: (s) => s.user?.type === 'manager',
    displayName: (s) => s.user?.userName || s.user?.name || ''
  },
  actions: {
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem(KEY, JSON.stringify({ token, user }))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(KEY)
    }
  }
})
