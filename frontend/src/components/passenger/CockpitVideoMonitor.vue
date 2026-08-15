<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getCockpitVideoConfig } from '../../api/passengerRealtime'
import type { CockpitVideoConfigDto } from '../../api/types'
import { toMessage } from '../../utils/displayFormatters'

const config = ref<CockpitVideoConfigDto | null>(null)
const loading = ref(true)
const error = ref('')
const playerLoaded = ref(false)
const playerKey = ref(0)

const playerUrl = computed(() => {
  if (!config.value?.enabled || !config.value.playbackUrl) return ''

  const url = new URL(config.value.playbackUrl)
  url.searchParams.set('controls', 'true')
  url.searchParams.set('muted', 'true')
  url.searchParams.set('autoplay', 'true')
  url.searchParams.set('playsInline', 'true')
  url.searchParams.set('disablepictureinpicture', 'false')
  return url.toString()
})

const headerStatus = computed(() => {
  if (loading.value) return '读取视频配置中'
  if (error.value) return '视频配置读取失败'
  if (!config.value?.enabled) return '视频监控未启用'
  return playerLoaded.value ? '' : 'WebRTC 视频已配置'
})

onMounted(() => {
  void loadConfig()
})

async function loadConfig(): Promise<void> {
  loading.value = true
  error.value = ''
  playerLoaded.value = false
  try {
    config.value = await getCockpitVideoConfig()
  } catch (requestError) {
    config.value = null
    error.value = toMessage(requestError)
  } finally {
    loading.value = false
  }
}

function reload(): void {
  if (!config.value?.enabled || !playerUrl.value) {
    void loadConfig()
    return
  }

  playerLoaded.value = false
  playerKey.value += 1
}
</script>

<template>
  <section class="cockpit-card">
    <div class="cockpit-card__heading">
      <h3>驾驶舱实时监控</h3>
      <div class="cockpit-card__actions">
        <span v-if="headerStatus">{{ headerStatus }}</span>
        <button
          v-if="!loading"
          class="cockpit-video-reload"
          type="button"
          @click="reload"
        >重新加载</button>
      </div>
    </div>

    <div class="cockpit-monitor" :class="{ 'has-player': Boolean(playerUrl) }">
      <template v-if="loading">
        <span>正在读取驾驶舱视频配置</span>
        <strong>请稍候</strong>
      </template>

      <template v-else-if="error">
        <span>驾驶舱视频配置读取失败</span>
        <strong>{{ error }}</strong>
      </template>

      <template v-else-if="!config?.enabled || !playerUrl">
        <span>驾驶舱监控未启用</span>
        <strong>请配置 WebRTC 播放地址</strong>
      </template>

      <template v-else>
        <iframe
          :key="playerKey"
          class="cockpit-video-frame"
          :src="playerUrl"
          title="驾驶舱实时监控视频"
          allow="autoplay; fullscreen; picture-in-picture"
          allowfullscreen
          scrolling="no"
          @load="playerLoaded = true"
        ></iframe>
        <div v-if="!playerLoaded" class="cockpit-video-loading">正在加载 WebRTC 播放器</div>
      </template>
    </div>
  </section>
</template>
