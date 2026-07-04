import type { ApiEnvelope, ApiErrorDetail, ApiErrorEnvelope } from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const TOKEN_KEY = 'cabin-platform.access-token'
let unauthorizedHandler: (() => void) | undefined

export class ApiClientError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details: ApiErrorDetail[],
    readonly traceId: string,
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler
}

export function readAccessToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function saveAccessToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearAccessToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json; charset=utf-8')
  }

  const token = readAccessToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  const payload: unknown = await response.json()
  if (!response.ok) {
    const error = toErrorEnvelope(payload, response.headers.get('X-Trace-Id'))
    if (response.status === 401 || response.status === 403) {
      unauthorizedHandler?.()
    }
    throw new ApiClientError(response.status, error.code, error.message, error.details, error.traceId)
  }

  const envelope = payload as ApiEnvelope<T>
  return envelope.data
}

export async function apiDownload(path: string): Promise<{ blob: Blob; fileName: string | null }> {
  const headers = new Headers({ Accept: 'application/octet-stream' })
  const token = readAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE_URL}${path}`, { headers })
  if (!response.ok) {
    const payload: unknown = await response.json().catch(() => null)
    const error = toErrorEnvelope(payload, response.headers.get('X-Trace-Id'))
    if (response.status === 401 || response.status === 403) unauthorizedHandler?.()
    throw new ApiClientError(response.status, error.code, error.message, error.details, error.traceId)
  }
  return {
    blob: await response.blob(),
    fileName: parseFileName(response.headers.get('Content-Disposition')),
  }
}

function toErrorEnvelope(payload: unknown, fallbackTraceId: string | null): ApiErrorEnvelope {
  if (isRecord(payload)) {
    return {
      code: typeof payload.code === 'string' ? payload.code : 'INTERNAL_ERROR',
      message: typeof payload.message === 'string' ? payload.message : '请求失败，请稍后重试',
      details: Array.isArray(payload.details) ? payload.details.filter(isErrorDetail) : [],
      traceId: typeof payload.traceId === 'string' ? payload.traceId : fallbackTraceId || 'unknown',
    }
  }
  return {
    code: 'INTERNAL_ERROR',
    message: '服务返回了无法识别的响应',
    details: [],
    traceId: fallbackTraceId || 'unknown',
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isErrorDetail(value: unknown): value is ApiErrorDetail {
  return isRecord(value) && typeof value.field === 'string' && typeof value.reason === 'string'
}

function parseFileName(disposition: string | null): string | null {
  if (!disposition) return null
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) return decodeURIComponent(encoded)
  return disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? null
}
