import { request } from './http'
import type { LoginResponse, UserInfo } from '@/types'

export function login(username: string, password: string) {
  return request<LoginResponse>({
    url: '/auth/login',
    method: 'post',
    data: { username, password },
  })
}

export function me() {
  return request<UserInfo>({ url: '/auth/me' })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

export function changePassword(currentPassword: string, newPassword: string) {
  return request<void>({
    url: '/users/me/password',
    method: 'put',
    data: { currentPassword, newPassword },
  })
}
