import { request } from './http'
import type { SearchResponseView } from '@/types'

export interface SearchQuery {
  keyword: string
  types?: string
  page?: number
  size?: number
}

export function globalSearch(query: SearchQuery) {
  return request<SearchResponseView>({ url: '/search', params: query })
}
