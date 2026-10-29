import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type LoginVO, type UserVO } from '@/api'
import { clearToken, getToken, setToken } from '@/api/request'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const profile = ref<UserVO | null>(null)
  const loaded = ref(false)

  const isLogin = computed(() => Boolean(token.value))
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '同学')
  const isAdmin = computed(() => profile.value?.role === 2)

  function applyLogin(login: LoginVO) {
    token.value = login.token
    setToken(login.token)
  }

  async function loadProfile(force = false) {
    if (!token.value) return null
    if (profile.value && !force && loaded.value) return profile.value
    const data = await authApi.profile()
    profile.value = data
    loaded.value = true
    return data
  }

  function logout(redirect = true) {
    token.value = ''
    profile.value = null
    loaded.value = false
    clearToken()
    if (redirect && !location.pathname.startsWith('/login')) {
      location.href = '/login'
    }
  }

  return { token, profile, loaded, isLogin, displayName, isAdmin, applyLogin, loadProfile, logout }
})
