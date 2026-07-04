import { apiRequest } from './http'
import type { LoginResponseDto, UserDto } from './types'

export interface LoginPayload {
  username: string
  password: string
}

export function login(payload: LoginPayload): Promise<LoginResponseDto> {
  return apiRequest<LoginResponseDto>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getCurrentUser(): Promise<UserDto> {
  return apiRequest<UserDto>('/auth/me')
}
