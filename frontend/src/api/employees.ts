import { request } from './http'
import type {
  EmployeeRelations,
  EmployeeView,
  FieldVisibilityView,
  PageResult,
  UpdateResult,
} from '@/types'

export interface EmployeeQuery {
  page?: number
  size?: number
  keyword?: string
  orgId?: string
  status?: string
}

export interface EmployeePayload {
  employeeNo?: string
  name?: string
  gender?: string
  birthDate?: string
  phone?: string
  email?: string
  idCard?: string
  address?: string
  emergencyContact?: string
  hireDate?: string
  primaryOrgId?: string
  primaryPositionId?: string
  managerId?: string
  reason?: string
}

export function pageEmployees(query: EmployeeQuery) {
  return request<PageResult<EmployeeView>>({ url: '/employees', params: query })
}

export function getEmployee(id: string) {
  return request<EmployeeView>({ url: `/employees/${id}` })
}

export function getEmployeeRelations(id: string) {
  return request<EmployeeRelations>({ url: `/org/employees/${id}/relations` })
}

export function getMyEmployee() {
  return request<EmployeeView>({ url: '/employees/me' })
}

export function createEmployee(payload: EmployeePayload) {
  return request<EmployeeView>({ url: '/employees', method: 'post', data: payload })
}

export function updateEmployee(id: string, payload: EmployeePayload) {
  return request<UpdateResult>({ url: `/employees/${id}`, method: 'put', data: payload })
}

export function updateMyEmployee(payload: EmployeePayload) {
  return request<UpdateResult>({ url: '/employees/me', method: 'put', data: payload })
}

export function getMyFieldVisibility() {
  return request<FieldVisibilityView[]>({ url: '/employees/me/visibility' })
}

export function updateMyFieldVisibility(fieldCode: string, visible: boolean) {
  return request<FieldVisibilityView>({
    url: '/employees/me/visibility',
    method: 'put',
    data: { fieldCode, visible },
  })
}
