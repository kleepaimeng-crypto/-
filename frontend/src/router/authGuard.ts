import type { UserRole } from '../api/types'

export function authGuardDestination(
  requiresAuth: boolean,
  isAuthenticated: boolean,
  targetPath: string,
  requiredRole?: UserRole,
  currentRole?: UserRole,
): true | { name: string; query?: Record<string, string> } {
  if (requiresAuth && !isAuthenticated) {
    return { name: 'login', query: { redirect: targetPath } }
  }
  if (!requiresAuth && isAuthenticated) {
    return { name: 'workspace' }
  }
  if (requiredRole && currentRole !== requiredRole) {
    return { name: 'workspace', query: { denied: 'users' } }
  }
  return true
}
