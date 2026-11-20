import { request } from './http'
import type {
  DocumentPrintView,
  DocumentView,
  MilestoneView,
  PageResult,
  ProgressSnapshotView,
  ProjectFolder,
  ProjectMemberView,
  ProjectProgressView,
  ProjectView,
  TaskCommentView,
  TaskView,
} from '@/types'

export interface ProjectQuery {
  page?: number
  size?: number
  keyword?: string
  status?: string
}

export interface ProjectPayload {
  name?: string
  code?: string
  description?: string
  orgId?: string | null
  startDate?: string | null
  endDate?: string | null
  retentionDays?: number | null
  downloadPolicy?: string
}

export function pageProjects(query: ProjectQuery) {
  return request<PageResult<ProjectView>>({ url: '/projects', params: query })
}

export function getProject(id: string) {
  return request<ProjectView>({ url: `/projects/${id}` })
}

export function createProject(payload: ProjectPayload) {
  return request<ProjectView>({ url: '/projects', method: 'post', data: payload })
}

export function updateProject(id: string, payload: ProjectPayload) {
  return request<ProjectView>({ url: `/projects/${id}`, method: 'put', data: payload })
}

export function deleteProject(id: string) {
  return request<void>({ url: `/projects/${id}`, method: 'delete' })
}

export function listProjectMembers(projectId: string) {
  return request<ProjectMemberView[]>({ url: `/projects/${projectId}/members` })
}

export function addProjectMember(projectId: string, employeeId: string, memberRole: string) {
  return request<ProjectMemberView>({
    url: `/projects/${projectId}/members`,
    method: 'post',
    data: { employeeId, memberRole },
  })
}

export function removeProjectMember(projectId: string, memberId: string) {
  return request<void>({ url: `/projects/${projectId}/members/${memberId}`, method: 'delete' })
}

export function listProjectFolders(projectId: string) {
  return request<ProjectFolder[]>({ url: `/projects/${projectId}/folders` })
}

export function createProjectFolder(projectId: string, name: string, parentId?: string | null) {
  return request<ProjectFolder>({
    url: `/projects/${projectId}/folders`,
    method: 'post',
    data: { name, parentId },
  })
}

export function listDocuments(projectId: string, folderId?: string | null) {
  return request<DocumentView[]>({
    url: `/projects/${projectId}/documents`,
    params: folderId ? { folderId } : {},
  })
}

export function uploadDocument(projectId: string, file: File, folderId?: string | null, name?: string) {
  const form = new FormData()
  form.append('file', file)
  if (folderId) form.append('folderId', folderId)
  if (name) form.append('name', name)
  return request<DocumentView>({
    url: `/projects/${projectId}/documents`,
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export async function downloadDocument(document: DocumentView) {
  const token = localStorage.getItem('ems_access_token')
  const response = await fetch(`/api/v1/documents/${document.id}/download`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    let message = '下载失败'
    try {
      message = (await response.json()).message || message
    } catch {
      /* keep default */
    }
    throw new Error(message)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const anchor = window.document.createElement('a')
  anchor.href = url
  anchor.download = document.originalName || document.name
  anchor.click()
  URL.revokeObjectURL(url)
}

export function printDocument(id: string) {
  return request<DocumentPrintView>({ url: `/documents/${id}/print` })
}

export function deleteDocument(id: string) {
  return request<void>({ url: `/documents/${id}`, method: 'delete' })
}

export function listDocumentPermissions(id: string) {
  return request<import('@/types').DocumentPermissionView[]>({
    url: `/documents/${id}/permissions`,
  })
}

export function setDocumentPermission(
  id: string,
  payload: {
    employeeId: string
    canView?: boolean
    canDownload?: boolean
    canPrint?: boolean
    canDelete?: boolean
  },
) {
  return request<import('@/types').DocumentPermissionView>({
    url: `/documents/${id}/permissions`,
    method: 'put',
    data: payload,
  })
}

// ---------------------------------------------------------------- tasks

export interface TaskQuery {
  status?: string
  assigneeEmployeeId?: string
  keyword?: string
}

export interface TaskPayload {
  title: string
  description?: string | null
  status?: string
  priority?: string
  assigneeEmployeeId?: string | null
  startDate?: string | null
  dueDate?: string | null
  progress?: number
  participantEmployeeIds?: string[]
  dependsOnTaskIds?: string[]
  sort?: number
}

export function listTasks(projectId: string, query: TaskQuery = {}) {
  return request<TaskView[]>({ url: `/projects/${projectId}/tasks`, params: query })
}

export function getTask(projectId: string, taskId: string) {
  return request<TaskView>({ url: `/projects/${projectId}/tasks/${taskId}` })
}

export function createTask(projectId: string, payload: TaskPayload) {
  return request<TaskView>({ url: `/projects/${projectId}/tasks`, method: 'post', data: payload })
}

export function updateTask(projectId: string, taskId: string, payload: TaskPayload) {
  return request<TaskView>({
    url: `/projects/${projectId}/tasks/${taskId}`,
    method: 'put',
    data: payload,
  })
}

export function updateTaskStatus(projectId: string, taskId: string, status: string, progress?: number) {
  return request<TaskView>({
    url: `/projects/${projectId}/tasks/${taskId}/status`,
    method: 'put',
    data: { status, progress },
  })
}

export function deleteTask(projectId: string, taskId: string) {
  return request<void>({ url: `/projects/${projectId}/tasks/${taskId}`, method: 'delete' })
}

export function listTaskComments(projectId: string, taskId: string) {
  return request<TaskCommentView[]>({ url: `/projects/${projectId}/tasks/${taskId}/comments` })
}

export function addTaskComment(projectId: string, taskId: string, content: string) {
  return request<TaskCommentView>({
    url: `/projects/${projectId}/tasks/${taskId}/comments`,
    method: 'post',
    data: { content },
  })
}

// ------------------------------------------------------------ milestones

export interface MilestonePayload {
  title: string
  description?: string | null
  dueDate?: string | null
  status?: string
}

export function listMilestones(projectId: string) {
  return request<MilestoneView[]>({ url: `/projects/${projectId}/milestones` })
}

export function createMilestone(projectId: string, payload: MilestonePayload) {
  return request<MilestoneView>({
    url: `/projects/${projectId}/milestones`,
    method: 'post',
    data: payload,
  })
}

export function updateMilestone(projectId: string, milestoneId: string, payload: MilestonePayload) {
  return request<MilestoneView>({
    url: `/projects/${projectId}/milestones/${milestoneId}`,
    method: 'put',
    data: payload,
  })
}

export function deleteMilestone(projectId: string, milestoneId: string) {
  return request<void>({
    url: `/projects/${projectId}/milestones/${milestoneId}`,
    method: 'delete',
  })
}

// -------------------------------------------------------------- progress

export function getProjectProgress(projectId: string) {
  return request<ProjectProgressView>({ url: `/projects/${projectId}/progress` })
}

export function updateProjectProgress(projectId: string, progress: number, note?: string) {
  return request<ProjectProgressView>({
    url: `/projects/${projectId}/progress`,
    method: 'put',
    data: { progress, note },
  })
}

export function listProgressSnapshots(projectId: string) {
  return request<ProgressSnapshotView[]>({ url: `/projects/${projectId}/progress/snapshots` })
}
