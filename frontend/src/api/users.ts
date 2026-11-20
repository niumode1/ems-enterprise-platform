import { request } from './http'
import type { PageResult, RoleAssignmentView, RoleView, UserView } from '@/types'

export interface UserQuery {
  page?: number
  size?: number
  keyword?: string
  status?: string
}

export function pageUsers(query: UserQuery) {
  return request<PageResult<UserView>>({ url: '/users', params: query })
}

export function createUser(payload: {
  username: string
  password: string
  displayName: string
  employeeId?: string
  roleCodes: string[]
}) {
  return request<UserView>({ url: '/users', method: 'post', data: payload })
}

export function updateUser(id: string, payload: { displayName?: string; employeeId?: string; status?: string }) {
  return request<UserView>({ url: `/users/${id}`, method: 'put', data: payload })
}

export interface RoleAssignmentPayload {
  roleCode: string
  startDate?: string | null
  endDate?: string | null
  note?: string | null
}

export function assignRoles(id: string, assignments: RoleAssignmentPayload[]) {
  return request<UserView>({ url: `/users/${id}/roles`, method: 'put', data: { assignments } })
}

export function listUserRoles(id: string) {
  return request<RoleAssignmentView[]>({ url: `/users/${id}/roles` })
}

export function disableUser(id: string) {
  return request<void>({ url: `/users/${id}/disable`, method: 'post' })
}

export function resetPassword(id: string, password: string) {
  return request<void>({ url: `/users/${id}/reset-password`, method: 'post', data: { password } })
}

export function listRoles() {
  return request<RoleView[]>({ url: '/roles' })
}
