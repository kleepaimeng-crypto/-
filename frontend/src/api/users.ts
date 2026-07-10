import { apiRequest } from './http'
import type { PageDto, UserRole, UserStatus, UserSummaryDto } from './types'

export interface UserQuery {
  page: number
  pageSize: 20 | 50 | 100
  sortBy: 'username' | 'roleCode' | 'email' | 'status' | 'createdAt'
  sortDirection: 'asc' | 'desc'
}

export interface UserCreatePayload {
  username: string
  email: string
  password: string
  roleCode: UserRole
  status: Extract<UserStatus, 'ACTIVE' | 'PENDING'>
}

export interface UserUpdatePayload {
  username?: string
  email?: string
  roleCode?: UserRole
  status?: Exclude<UserStatus, 'DELETED'>
  expectedVersion: number
}

export function getUsers(query: UserQuery): Promise<PageDto<UserSummaryDto>> {
  const params = new URLSearchParams({
    page: String(query.page),
    pageSize: String(query.pageSize),
    sortBy: query.sortBy,
    sortDirection: query.sortDirection,
  })
  return apiRequest<PageDto<UserSummaryDto>>(`/users?${params}`)
}

export function createUser(payload: UserCreatePayload): Promise<UserSummaryDto> {
  return apiRequest<UserSummaryDto>('/users', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updateUser(userId: string, payload: UserUpdatePayload): Promise<UserSummaryDto> {
  return apiRequest<UserSummaryDto>(`/users/${userId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  })
}

export function deleteUser(userId: string, reason: string, expectedVersion: number): Promise<null> {
  return apiRequest<null>(`/users/${userId}`, {
    method: 'DELETE',
    body: JSON.stringify({ reason, expectedVersion }),
  })
}
