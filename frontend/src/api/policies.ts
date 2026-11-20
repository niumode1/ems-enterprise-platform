import http, { request } from './http'
import type {
  PolicyAnswer,
  PolicyCapabilities,
  PolicyDetail,
  PolicyView,
} from '@/types'

export function listPolicies(keyword?: string) {
  return request<PolicyView[]>({
    url: '/policies',
    params: keyword ? { keyword } : undefined,
  })
}

export function getPolicy(id: string) {
  return request<PolicyDetail>({ url: `/policies/${id}` })
}

export function policyCapabilities() {
  return request<PolicyCapabilities>({ url: '/policies/capabilities' })
}

export function uploadPolicy(payload: {
  title: string
  category?: string
  file: File
}) {
  const data = new FormData()
  data.append('title', payload.title)
  if (payload.category?.trim()) {
    data.append('category', payload.category.trim())
  }
  data.append('file', payload.file)
  return request<PolicyView>({ url: '/policies', method: 'post', data })
}

export function deletePolicy(id: string) {
  return request<void>({ url: `/policies/${id}`, method: 'delete' })
}

export function askPolicies(payload: {
  question: string
  policyId?: string | null
}) {
  return request<PolicyAnswer>({
    url: '/policies/ask',
    method: 'post',
    data: { question: payload.question, policyId: payload.policyId || null },
  })
}

export async function downloadPolicy(id: string, filename: string) {
  const blob = await http.get<never, Blob>(`/policies/${id}/download`, {
    responseType: 'blob',
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
