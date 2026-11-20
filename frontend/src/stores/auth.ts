import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import type { UserInfo } from '@/types'

function readStoredUser(): UserInfo | null {
  const raw = localStorage.getItem('ems_user')
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('ems_access_token') ?? '')
  const refreshToken = ref(localStorage.getItem('ems_refresh_token') ?? '')
  const user = ref<UserInfo | null>(readStoredUser())

  const isLoggedIn = computed(() => Boolean(token.value))
  const permissions = computed(() => user.value?.permissions ?? [])
  const roles = computed(() => user.value?.roles ?? [])

  function persistUser(value: UserInfo | null) {
    user.value = value
    if (value) {
      localStorage.setItem('ems_user', JSON.stringify(value))
    } else {
      localStorage.removeItem('ems_user')
    }
  }

  function setTokens(accessToken: string, newRefreshToken?: string) {
    token.value = accessToken
    localStorage.setItem('ems_access_token', accessToken)
    if (newRefreshToken) {
      refreshToken.value = newRefreshToken
      localStorage.setItem('ems_refresh_token', newRefreshToken)
    }
  }

  async function login(username: string, password: string) {
    const data = await authApi.login(username, password)
    setTokens(data.accessToken, data.refreshToken)
    persistUser(data.user)
    return data.user
  }

  async function fetchMe() {
    if (!token.value) return null
    const data = await authApi.me()
    persistUser(data)
    return data
  }

  function hasPermission(code: string) {
    return permissions.value.includes(code)
  }

  function hasAnyPermission(codes: string[]) {
    return codes.some((code) => hasPermission(code))
  }

  function hasRole(code: string) {
    return roles.value.includes(code)
  }

  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } catch {
      // Local logout still proceeds when the server is unreachable.
    }
    clear()
  }

  function clear() {
    token.value = ''
    refreshToken.value = ''
    persistUser(null)
    localStorage.removeItem('ems_access_token')
    localStorage.removeItem('ems_refresh_token')
  }

  return {
    token,
    refreshToken,
    user,
    isLoggedIn,
    permissions,
    roles,
    login,
    fetchMe,
    logout,
    clear,
    hasPermission,
    hasAnyPermission,
    hasRole,
  }
})
