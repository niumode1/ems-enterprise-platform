import { request } from './http'
import type {
  NotificationItem,
  NotificationPreferencesView,
  PageResult,
} from '@/types'

export function pageNotifications(page = 1, size = 20, unreadOnly = false) {
  return request<PageResult<NotificationItem>>({
    url: '/notifications',
    params: { page, size, unreadOnly },
  })
}

export function unreadNotificationCount() {
  return request<{ count: number }>({ url: '/notifications/unread-count' })
}

export function markNotificationRead(id: string) {
  return request<void>({ url: `/notifications/${id}/read`, method: 'post' })
}

export function markAllNotificationsRead() {
  return request<void>({ url: '/notifications/read-all', method: 'post' })
}

export function getNotificationPreferences() {
  return request<NotificationPreferencesView>({ url: '/notifications/preferences' })
}

export function updateNotificationPreferences(
  preferences: Array<{ type: string; muted: boolean }>,
) {
  return request<NotificationPreferencesView>({
    url: '/notifications/preferences',
    method: 'put',
    data: { preferences },
  })
}
