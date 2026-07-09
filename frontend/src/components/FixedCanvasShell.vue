<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { calculateFixedCanvasScale } from '../utils/fixedCanvas'

defineProps<{
  shellClass?: string | string[] | Record<string, boolean>
}>()

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
</script>

<template>
  <div class="fixed-canvas-viewport">
    <main
      class="workspace-shell fixed-canvas-shell"
      :class="shellClass"
      :style="{ transform: `translate(-50%, -50%) scale(${canvasScale})` }"
    >
      <slot />
    </main>
  </div>
</template>
