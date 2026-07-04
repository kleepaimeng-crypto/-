export function authGuardDestination(
  requiresAuth: boolean,
  isAuthenticated: boolean,
  targetPath: string,
): true | { name: string; query?: Record<string, string> } {
  if (requiresAuth && !isAuthenticated) {
    return { name: 'login', query: { redirect: targetPath } }
  }
  if (!requiresAuth && isAuthenticated) {
    return { name: 'workspace' }
  }
  return true
}
