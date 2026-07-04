import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiClientError, apiRequest, clearAccessToken, saveAccessToken, setUnauthorizedHandler } from './http'

describe('apiRequest', () => {
  beforeEach(() => {
    clearAccessToken()
    vi.unstubAllGlobals()
  })

  it('injects the bearer token and unwraps successful responses', async () => {
    saveAccessToken('signed-token')
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 'OK', message: 'success', data: { username: 'admin' }, traceId: 'trace-1' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest<{ username: string }>('/auth/me')).resolves.toEqual({ username: 'admin' })
    const headers = (fetchMock.mock.calls[0]?.[1] as RequestInit).headers as Headers
    expect(headers.get('Authorization')).toBe('Bearer signed-token')
  })

  it('exposes the contract error and clears the session through the unauthorized hook', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ code: 'UNAUTHORIZED', message: '身份已过期', details: [], traceId: 'trace-2' }),
      { status: 401, headers: { 'Content-Type': 'application/json' } },
    )))

    await expect(apiRequest('/auth/me')).rejects.toMatchObject({
      status: 401,
      code: 'UNAUTHORIZED',
      traceId: 'trace-2',
    } satisfies Partial<ApiClientError>)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })
})
