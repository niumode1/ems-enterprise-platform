import { request } from './http'
import type { BossAccessGrantView, BossAccessRequestResult, BossAccessStatusView } from '@/types'

export function getBossAccessStatus() {
  return request<BossAccessStatusView>({ url: '/boss-access/status' })
}

export function requestBossAccess(payload: {
  employeeId?: string | null
  reason: string
  durationMinutes?: number | null
}) {
  return request<BossAccessRequestResult>({
    url: '/boss-access/requests',
    method: 'post',
    data: payload,
  })
}

export function listBossAccessGrants(all = false) {
  return request<BossAccessGrantView[]>({
    url: '/boss-access/grants',
    params: { all },
  })
}

export function revokeBossAccessGrant(id: string) {
  return request<BossAccessGrantView>({
    url: `/boss-access/grants/${id}/revoke`,
    method: 'post',
  })
}
