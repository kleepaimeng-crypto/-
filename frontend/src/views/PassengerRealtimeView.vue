<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authSession } from '../auth/session'
import PlatformBrand from '../components/PlatformBrand.vue'
import PassengerCabinStage from '../components/passenger/PassengerCabinStage.vue'
import { usePassengerRealtime } from '../composables/usePassengerRealtime'
import { calculateFixedCanvasScale } from '../utils/fixedCanvas'

const router = useRouter()
const {
  cabinScroller,
  windowDisplay,
  windowError,
  windowLoading,
} = usePassengerRealtime()
const canvasScale = ref(1)

function updateCanvasScale(): void {
  canvasScale.value = calculateFixedCanvasScale(window.innerWidth, window.innerHeight)
}

onMounted(() => {
  updateCanvasScale()
  window.addEventListener('resize', updateCanvasScale)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateCanvasScale)
})

async function logout(): Promise<void> {
  authSession.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="passenger-fixed-viewport">
    <main
      class="workspace-shell passenger-shell"
      :style="{ transform: `translate(-50%, -50%) scale(${canvasScale})` }"
    >
      <header class="workspace-header">
      <PlatformBrand compact />
      <nav class="workspace-nav" aria-label="主导航">
        <button class="workspace-nav__item" @click="router.push('/')">数据管理</button>
        <button class="workspace-nav__item" disabled>飞机轨迹实时系统</button>
        <button class="workspace-nav__item" disabled>飞机轨迹回放系统</button>
        <button class="workspace-nav__item" disabled>数据统计</button>
        <button class="workspace-nav__item" disabled>用户管理</button>
        <button class="workspace-nav__item is-active">乘客实时动态</button>
      </nav>
      <div class="workspace-header__account">
        <span class="account-dot"></span>
        <span>{{ authSession.state.user?.username }}</span>
        <button class="text-action" @click="logout">退出</button>
      </div>
      </header>

      <section class="passenger-layout">
        <PassengerCabinStage
          v-model:cabin-scroller="cabinScroller"
          :window-display="windowDisplay"
          :window-error="windowError"
          :window-loading="windowLoading"
        />
      </section>

      <footer class="workspace-footer">
        <span>部件号：XXXXXXXXXXXXXXXXX</span>
        <span>版本号：V0.1</span>
      </footer>
    </main>
  </div>
</template>
