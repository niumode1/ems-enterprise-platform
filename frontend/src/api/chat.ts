import http, { request } from './http'
import type {
  ChatAttachmentCapabilities,
  ChatAttachmentView,
  ChatConversationDetail,
  ChatConversationView,
  ChatMemberView,
  ChatMessageView,
  PageResult,
} from '@/types'

export interface CreateConversationPayload {
  type: 'DIRECT' | 'GROUP'
  name?: string
  employeeIds: string[]
  burnAfterRead?: boolean
  antiRecall?: boolean
  retentionDays?: number | null
}

export interface ConversationSettingsPayload {
  name?: string
  burnAfterRead?: boolean
  antiRecall?: boolean
  retentionDays?: number | null
}

export function listChatConversations() {
  return request<ChatConversationView[]>({ url: '/chat/conversations' })
}

export function createChatConversation(payload: CreateConversationPayload) {
  return request<ChatConversationDetail>({ url: '/chat/conversations', method: 'post', data: payload })
}

export function getChatConversation(id: string) {
  return request<ChatConversationDetail>({ url: `/chat/conversations/${id}` })
}

export function updateChatConversation(id: string, payload: ConversationSettingsPayload) {
  return request<ChatConversationDetail>({
    url: `/chat/conversations/${id}/settings`,
    method: 'put',
    data: payload,
  })
}

export function addChatMember(conversationId: string, employeeId: string) {
  return request<ChatMemberView>({
    url: `/chat/conversations/${conversationId}/members`,
    method: 'post',
    data: { employeeId },
  })
}

export function removeChatMember(conversationId: string, employeeId: string) {
  return request<void>({
    url: `/chat/conversations/${conversationId}/members/${employeeId}`,
    method: 'delete',
  })
}

export function pageChatMessages(conversationId: string, page = 1, size = 30) {
  return request<PageResult<ChatMessageView>>({
    url: `/chat/conversations/${conversationId}/messages`,
    params: { page, size },
  })
}

export function sendChatMessage(conversationId: string, content: string, attachmentId?: string) {
  return request<ChatMessageView>({
    url: `/chat/conversations/${conversationId}/messages`,
    method: 'post',
    data: attachmentId ? { content, attachmentId } : { content },
  })
}

export function getChatCapabilities() {
  return request<ChatAttachmentCapabilities>({ url: '/chat/capabilities' })
}

export function uploadChatAttachment(conversationId: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<ChatAttachmentView>({
    url: `/chat/conversations/${conversationId}/attachments`,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export async function downloadChatAttachment(attachmentId: string) {
  return http.get<unknown, Blob>(`/chat/attachments/${attachmentId}/download`, {
    responseType: 'blob',
  })
}

export function markChatRead(conversationId: string) {
  return request<void>({ url: `/chat/conversations/${conversationId}/read`, method: 'post' })
}

export function recallChatMessage(messageId: string) {
  return request<ChatMessageView>({ url: `/chat/messages/${messageId}/recall`, method: 'post' })
}
