import { request } from './http'
import type {
  AccessLog,
  AuditLog,
  EntityVersionDetail,
  EntityVersionView,
  PageResult,
  RollbackResult,
} from '@/types'

export interface AuditQuery {
  page?: number
  size?: number
  category?: string
  module?: string
  actorId?: string
  entityType?: string
  entityId?: string
  from?: string
  to?: string
}

export function pageAuditLogs(query: AuditQuery) {
  return request<PageResult<AuditLog>>({ url: '/audit/logs', params: query })
}

export function pageAccessLogs(query: {
  page?: number
  size?: number
  userId?: string
  resourceType?: string
  sensitive?: boolean
}) {
  return request<PageResult<AccessLog>>({ url: '/audit/access-logs', params: query })
}

export interface EntityVersionQuery {
  page?: number
  size?: number
  entityType?: string
  entityId?: string
  operatorId?: string
  from?: string
  to?: string
}

export function pageEntityVersions(query: EntityVersionQuery) {
  return request<PageResult<EntityVersionView>>({ url: '/audit/versions', params: query })
}

export function getEntityVersion(id: string) {
  return request<EntityVersionDetail>({ url: `/audit/versions/${id}` })
}

export function rollbackEntityVersion(versionId: string, reason: string) {
  return request<RollbackResult>({
    url: '/audit/rollback',
    method: 'post',
    data: { versionId, reason },
  })
}
