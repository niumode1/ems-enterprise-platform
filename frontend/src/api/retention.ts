import { request } from './http'
import type { RetentionRunResult, RetentionStatusView } from '@/types'

export function getRetentionStatus() {
  return request<RetentionStatusView>({ url: '/retention/status' })
}

export function runRetention(payload: { dryRun: boolean; scopes?: string[] }) {
  return request<RetentionRunResult>({
    url: '/retention/run',
    method: 'post',
    data: payload,
  })
}
