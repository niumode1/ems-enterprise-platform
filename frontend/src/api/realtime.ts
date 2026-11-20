import { request } from './http'
import type { RealtimeCapabilities } from '@/types'

export function getRealtimeCapabilities() {
  return request<RealtimeCapabilities>({ url: '/realtime/capabilities' })
}
