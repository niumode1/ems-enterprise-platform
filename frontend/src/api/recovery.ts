import { request } from './http'
import type { RecoveryRequestResult, RecoveryRequestView, RecoveryStatusView } from '@/types'

export function getRecoveryStatus() {
  return request<RecoveryStatusView>({ url: '/recovery/status' })
}

export function listRecoveryRequests() {
  return request<RecoveryRequestView[]>({ url: '/recovery/requests' })
}

export function getRecoveryRequest(id: string) {
  return request<RecoveryRequestView>({ url: `/recovery/requests/${id}` })
}

export function createRecoveryRequest(payload: {
  action: string
  userId?: string | null
  username?: string | null
  reason?: string | null
  newPassword?: string | null
}) {
  return request<RecoveryRequestResult>({
    url: '/recovery/requests',
    method: 'post',
    data: payload,
  })
}

export function signRecoveryRequest(id: string, comment?: string) {
  return request<RecoveryRequestView>({
    url: `/recovery/requests/${id}/sign`,
    method: 'post',
    data: { comment },
  })
}

export function rejectRecoveryRequest(id: string, comment?: string) {
  return request<RecoveryRequestView>({
    url: `/recovery/requests/${id}/reject`,
    method: 'post',
    data: { comment },
  })
}

export function cancelRecoveryRequest(id: string) {
  return request<RecoveryRequestView>({
    url: `/recovery/requests/${id}/cancel`,
    method: 'post',
  })
}
