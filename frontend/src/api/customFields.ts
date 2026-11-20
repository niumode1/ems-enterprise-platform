import { request } from './http'
import type { CustomFieldDef, CustomFieldValue } from '@/types'

export interface CustomFieldDefPayload {
  entityType?: string
  fieldCode?: string
  name?: string
  fieldType?: string
  options?: string[]
  timeline?: boolean
  visibility?: string
  maskRule?: string
  sensitive?: boolean
  selfEditable?: boolean
  required?: boolean
  sort?: number
}

export function listCustomFieldDefs(entityType = 'EMPLOYEE', includeDisabled = true) {
  return request<CustomFieldDef[]>({
    url: '/custom-fields/defs',
    params: { entityType, includeDisabled },
  })
}

export function createCustomFieldDef(payload: CustomFieldDefPayload) {
  return request<CustomFieldDef>({ url: '/custom-fields/defs', method: 'post', data: payload })
}

export function updateCustomFieldDef(id: string, payload: CustomFieldDefPayload) {
  return request<CustomFieldDef>({ url: `/custom-fields/defs/${id}`, method: 'put', data: payload })
}

export function disableCustomFieldDef(id: string) {
  return request<CustomFieldDef>({ url: `/custom-fields/defs/${id}/disable`, method: 'post' })
}

export function getCustomFieldValues(entityId: string, history = false) {
  return request<CustomFieldValue[]>({
    url: '/custom-fields/values',
    params: { entityType: 'EMPLOYEE', entityId, history },
  })
}

export function setCustomFieldValues(entityId: string, values: Record<string, string | null>, reason?: string) {
  return request<number>({
    url: '/custom-fields/values',
    method: 'put',
    data: { entityType: 'EMPLOYEE', entityId, values, reason },
  })
}
