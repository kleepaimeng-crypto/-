import { createRouter, createWebHistory } from 'vue-router'
import { authSession } from '../auth/session'
import { authGuardDestination } from './authGuard'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      name: 'workspace',
      component: () => import('../views/WorkspaceView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/passenger-realtime',
      name: 'passenger-realtime',
      component: () => import('../views/PassengerRealtimeView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/flight-track',
      name: 'flight-track',
      component: () => import('../views/FlightTrackView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('../views/UserManagementView.vue'),
      meta: { requiresAuth: true, requiredRole: 'SUPER_ADMIN' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  await authSession.restore()
  const requiredRole = to.meta.requiredRole === 'SUPER_ADMIN' ? 'SUPER_ADMIN' : undefined
  return authGuardDestination(
    Boolean(to.meta.requiresAuth),
    authSession.isAuthenticated.value,
    to.fullPath,
    requiredRole,
    authSession.state.user?.roleCode,
  )
})
