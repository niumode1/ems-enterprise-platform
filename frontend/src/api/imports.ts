import { request } from './http'
import type {
  CommitResult,
  ImportBatchView,
  ImportRowView,
  PageResult,
  PushResult,
} from '@/types'

export function pageImports(query: { page?: number; size?: number; status?: string; keyword?: string }) {
  return request<PageResult<ImportBatchView>>({ url: '/imports', params: query })
}

export function getImport(id: string) {
  return request<ImportBatchView>({ url: `/imports/${id}` })
}

export function uploadRoster(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request<ImportBatchView>({
    url: '/imports',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function parseImport(id: string) {
  return request<ImportBatchView>({ url: `/imports/${id}/parse`, method: 'post' })
}

export function pageImportRows(
  id: string,
  query: { page?: number; size?: number; status?: string },
) {
  return request<PageResult<ImportRowView>>({ url: `/imports/${id}/rows`, params: query })
}

export function reviewImportRow(
  batchId: string,
  rowId: string,
  payload: { values?: Record<string, unknown>; confirm?: boolean },
) {
  return request<ImportRowView>({
    url: `/imports/${batchId}/rows/${rowId}`,
    method: 'put',
    data: payload,
  })
}

export function pushImport(id: string) {
  return request<PushResult>({ url: `/imports/${id}/push`, method: 'post' })
}

export function myImportConfirmations() {
  return request<ImportRowView[]>({ url: '/imports/my-confirmations' })
}

export function myConfirmationFields() {
  return request<string[]>({ url: '/imports/my-confirmations/fields' })
}

export function confirmImportRow(
  rowId: string,
  payload: { values?: Record<string, unknown>; comment?: string },
) {
  return request<ImportRowView>({
    url: `/imports/rows/${rowId}/confirm`,
    method: 'put',
    data: payload,
  })
}

export function rejectImportRow(rowId: string, reason: string) {
  return request<ImportRowView>({
    url: `/imports/rows/${rowId}/reject`,
    method: 'post',
    data: { reason },
  })
}

export function commitImport(id: string) {
  return request<CommitResult>({ url: `/imports/${id}/commit`, method: 'post' })
}

export function rollbackImport(id: string) {
  return request<ImportBatchView>({ url: `/imports/${id}/rollback`, method: 'post' })
}
