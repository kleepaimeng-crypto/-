<script setup lang="ts">
import { useRouter } from 'vue-router'
import { authSession } from '../auth/session'
import FixedCanvasShell from '../components/FixedCanvasShell.vue'
import PlatformBrand from '../components/PlatformBrand.vue'
import PassengerCabinStage from '../components/passenger/PassengerCabinStage.vue'
import PassengerTrafficPanel from '../components/passenger/PassengerTrafficPanel.vue'
import { usePassengerRealtime } from '../composables/usePassengerRealtime'

const router = useRouter()
const {
  autoRefresh,
  cabinScroller,
  snapshot,
  snapshotError,
  snapshotLoading,
  toggleAutoRefresh,
  windowDisplay,
  windowError,
  windowLoading,
} = usePassengerRealtime()

async function logout(): Promise<void> {
  authSession.logout()
  await router.replace('/login')
}
</script>

<template>
  <FixedCanvasShell shell-class="passenger-shell">
      <header class="workspace-header">
      <PlatformBrand compact />
      <nav class="workspace-nav" aria-label="主导航">
        <button class="workspace-nav__item" @click="router.push('/')">数据管理</button>
        <button class="workspace-nav__item" @click="router.push('/flight-track')">飞机轨迹实时系统</button>
        <button class="workspace-nav__item" disabled>飞机轨迹回放系统</button>
        <button class="workspace-nav__item" disabled>数据统计</button>
        <button
          v-if="authSession.state.user?.roleCode === 'SUPER_ADMIN'"
          class="workspace-nav__item"
          @click="router.push('/users')"
        >用户管理</button>
        <button class="workspace-nav__item is-active">乘客实时动态</button>
      </nav>
      <div class="workspace-header__account">
        <span class="account-dot"></span>
        <span>{{ authSession.state.user?.username }}</span>
        <button class="text-action" @click="logout">退出</button>
      </div>
      </header>

      <section class="passenger-layout">
        <aside class="passenger-left">
          <PassengerTrafficPanel
            :auto-refresh="autoRefresh"
            :snapshot="snapshot"
            :loading="snapshotLoading"
            :error="snapshotError"
            @toggle-auto-refresh="toggleAutoRefresh"
          />
        </aside>
        <PassengerCabinStage
          v-model:cabin-scroller="cabinScroller"
          :activities="snapshot?.passengerActivities.items ?? []"
          :activity-error="snapshotError"
          :activity-loading="snapshotLoading"
          :window-display="windowDisplay"
          :window-error="windowError"
          :window-loading="windowLoading"
        />
      </section>

      <footer class="workspace-footer">
        <span>部件号：XXXXXXXXXXXXXXXXX</span>
        <span>版本号：V0.1</span>
      </footer>
  </FixedCanvasShell>
</template>
