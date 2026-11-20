import { request } from './http'
import type {
  AiAskResult,
  AiHelpHit,
  AiHelpIndexPayload,
  AiHelpIndexView,
  AiPeopleAnswer,
  AiProviderView,
  AiQueryLogView,
  AiQuickQuestion,
  AiTestResult,
  PageResult,
} from '@/types'

export interface AiProviderPayload {
  id?: string | null
  name: string
  providerType?: string
  baseUrl: string
  apiKey?: string
  model: string
  enabled?: boolean
  isDefault?: boolean
}

export interface AiAskPayload {
  question: string
  employeeId?: string | null
}

export function listAiProviders() {
  return request<AiProviderView[]>({ url: '/ai/config' })
}

export function saveAiProvider(payload: AiProviderPayload) {
  return request<AiProviderView>({ url: '/ai/config', method: 'put', data: payload })
}

export function testAiProvider(providerId?: string | null) {
  return request<AiTestResult>({
    url: '/ai/providers/test',
    method: 'post',
    data: { providerId: providerId || null },
  })
}

export function askAi(payload: AiAskPayload) {
  return request<AiAskResult>({ url: '/ai/ask', method: 'post', data: payload })
}

export function locateHelp(question: string) {
  return request<AiHelpHit[]>({ url: '/ai/help/locate', method: 'post', data: { question } })
}

export function askPeople(question: string, employeeId?: string | null) {
  return request<AiPeopleAnswer>({
    url: '/ai/people/ask',
    method: 'post',
    data: { question, employeeId: employeeId || null },
  })
}

export function listQuickQuestions() {
  return request<AiQuickQuestion[]>({ url: '/ai/quick-questions' })
}

export function pageAiLogs(query: { page: number; size: number; feature?: string }) {
  return request<PageResult<AiQueryLogView>>({ url: '/ai/logs', params: query })
}

export function listHelpIndex() {
  return request<AiHelpIndexView[]>({ url: '/ai/help-index' })
}

export function createHelpIndex(payload: AiHelpIndexPayload) {
  return request<AiHelpIndexView>({ url: '/ai/help-index', method: 'post', data: payload })
}

export function updateHelpIndex(id: string, payload: AiHelpIndexPayload) {
  return request<AiHelpIndexView>({ url: `/ai/help-index/${id}`, method: 'put', data: payload })
}

export function deleteHelpIndex(id: string) {
  return request<void>({ url: `/ai/help-index/${id}`, method: 'delete' })
}
