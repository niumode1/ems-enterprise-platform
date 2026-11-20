import { request } from './http'
import type { FieldPermission } from '@/types'

export interface FieldPermissionPayload {
  entityType: string
  fieldCode: string
  roleId?: string | number
  scopeType?: string
  visibility: string
  maskRule?: string
  selfEdit?: boolean
  exportable?: boolean
  aiReadable?: boolean
  sensitive?: boolean
  keyField?: boolean
}

export function listFieldPermissions(entityType = 'EMPLOYEE') {
  return request<FieldPermission[]>({ url: '/field-permissions', params: { entityType } })
}

export function createFieldPermission(payload: FieldPermissionPayload) {
  return request<FieldPermission>({ url: '/field-permissions', method: 'post', data: payload })
}

export function updateFieldPermission(id: string, payload: FieldPermissionPayload) {
  return request<FieldPermission>({ url: `/field-permissions/${id}`, method: 'put', data: payload })
}
