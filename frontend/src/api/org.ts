import { request } from './http'
import type { OrgNode, Position, ReportingNode } from '@/types'

export interface OrgPayload {
  parentId?: string | null
  name: string
  code?: string
  type?: string
  leaderEmployeeId?: string | null
  sort?: number
}

export function orgTree() {
  return request<OrgNode[]>({ url: '/org/tree' })
}

export function reportingTree() {
  return request<ReportingNode[]>({ url: '/org/reporting-tree' })
}

export function createOrg(payload: OrgPayload) {
  return request<OrgNode>({ url: '/org', method: 'post', data: payload })
}

export function updateOrg(id: string, payload: OrgPayload) {
  return request<OrgNode>({ url: `/org/${id}`, method: 'put', data: payload })
}

export function disableOrg(id: string) {
  return request<void>({ url: `/org/${id}/disable`, method: 'post' })
}

export function listPositions() {
  return request<Position[]>({ url: '/org/positions' })
}

export function createPosition(payload: { code: string; name: string; level?: string }) {
  return request<Position>({ url: '/org/positions', method: 'post', data: payload })
}
