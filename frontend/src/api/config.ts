import { request } from './http'
import type { ApprovalPolicy, ApprovalView, ConfigItem, ConfigUpdateResult } from '@/types'

export function listConfig(group?: string, keyword?: string) {
  return request<ConfigItem[]>({ url: '/config', params: { group, keyword } })
}

export function getConfig(key: string) {
  return request<ConfigItem>({ url: `/config/${key}` })
}

export function updateConfig(key: string, value: string, reason?: string) {
  return request<ConfigUpdateResult>({
    url: `/config/${key}`,
    method: 'put',
    data: { value, reason },
  })
}

export function configHistory(key: string) {
  return request<Array<Record<string, unknown>>>({ url: `/config/${key}/history` })
}

export function listPolicies() {
  return request<ApprovalPolicy[]>({ url: '/config/approval-policies' })
}

export function updatePolicy(
  id: string,
  payload: { quorum?: number; changeQuorum?: number; applicantIncluded?: boolean; enabled?: boolean },
) {
  return request<ApprovalView>({
    url: `/config/approval-policies/${id}`,
    method: 'put',
    data: payload,
  })
}
