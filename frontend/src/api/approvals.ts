import { request } from './http'
import type { ApprovalReminderResult, ApprovalView, PageResult, UserView } from '@/types'

export interface ApprovalQuery {
  page?: number
  size?: number
  status?: string
  mine?: boolean
  pendingForMe?: boolean
}

export function pageApprovals(query: ApprovalQuery) {
  return request<PageResult<ApprovalView>>({ url: '/approvals', params: query })
}

export function getApproval(id: string) {
  return request<ApprovalView>({ url: `/approvals/${id}` })
}

export function approve(id: string, comment?: string) {
  return request<ApprovalView>({
    url: `/approvals/${id}/approve`,
    method: 'post',
    data: { comment },
  })
}

export function reject(id: string, comment?: string) {
  return request<ApprovalView>({
    url: `/approvals/${id}/reject`,
    method: 'post',
    data: { comment },
  })
}

export function cancelApproval(id: string) {
  return request<ApprovalView>({ url: `/approvals/${id}/cancel`, method: 'post' })
}

export function submitApproval(payload: {
  policyCode: string
  entityType?: string
  entityId?: string
  payload?: Record<string, unknown>
  reason?: string
}) {
  return request<ApprovalView>({ url: '/approvals', method: 'post', data: payload })
}

export function listApprovalPolicies() {
  return request<import('@/types').ApprovalPolicy[]>({ url: '/approvals/policies' })
}

export function roleUsers() {
  return request<PageResult<UserView>>({ url: '/users', params: { page: 1, size: 200, status: 'ACTIVE' } })
}

export function runApprovalReminders() {
  return request<ApprovalReminderResult>({ url: '/approvals/reminders/run', method: 'post' })
}
