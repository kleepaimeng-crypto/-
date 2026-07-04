import { computed, reactive } from 'vue'
import { getCurrentUser, login as requestLogin } from '../api/auth'
import { clearAccessToken, readAccessToken, saveAccessToken, setUnauthorizedHandler } from '../api/http'
import type { LoginPayload } from '../api/auth'
import type { UserDto } from '../api/types'

interface AuthState {
  user: UserDto | null
  initialized: boolean
  restoring: boolean
}

const state = reactive<AuthState>({
  user: null,
  initialized: false,
  restoring: false,
})

let restorePromise: Promise<void> | undefined

function clearSession(): void {
  clearAccessToken()
  state.user = null
  state.initialized = true
}

setUnauthorizedHandler(clearSession)

export const authSession = {
  state,
  isAuthenticated: computed(() => state.user !== null && readAccessToken() !== null),

  async login(payload: LoginPayload): Promise<void> {
    const result = await requestLogin(payload)
    saveAccessToken(result.accessToken)
    state.user = result.user
    state.initialized = true
  },

  async restore(): Promise<void> {
    if (state.initialized) {
      return
    }
    if (!readAccessToken()) {
      state.initialized = true
      return
    }
    if (!restorePromise) {
      state.restoring = true
      restorePromise = getCurrentUser()
        .then((user) => {
          state.user = user
        })
        .catch(() => {
          clearSession()
        })
        .finally(() => {
          state.initialized = true
          state.restoring = false
          restorePromise = undefined
        })
    }
    await restorePromise
  },

  logout(): void {
    clearSession()
  },
}
