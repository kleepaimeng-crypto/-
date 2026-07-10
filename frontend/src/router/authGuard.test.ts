import { describe, expect, it } from 'vitest'
import { authGuardDestination } from './authGuard'

describe('authGuardDestination', () => {
  it('redirects anonymous users to login and keeps the target path', () => {
    expect(authGuardDestination(true, false, '/?page=2')).toEqual({
      name: 'login',
      query: { redirect: '/?page=2' },
    })
  })

  it('redirects authenticated users away from login', () => {
    expect(authGuardDestination(false, true, '/login')).toEqual({ name: 'workspace' })
  })

  it('allows a matching authentication state', () => {
    expect(authGuardDestination(true, true, '/')).toBe(true)
    expect(authGuardDestination(false, false, '/login')).toBe(true)
  })

  it('protects role-specific routes', () => {
    expect(authGuardDestination(true, true, '/users', 'SUPER_ADMIN', 'ADMIN')).toEqual({
      name: 'workspace',
      query: { denied: 'users' },
    })
    expect(authGuardDestination(true, true, '/users', 'SUPER_ADMIN', 'SUPER_ADMIN')).toBe(true)
  })
})
