<script setup lang="ts">
const brandMarkUrl = '/assets/Frame%202.svg?v=2'

defineProps<{
  compact?: boolean
}>()
</script>

<template>
  <div class="platform-brand" :class="{ 'platform-brand--compact': compact }" aria-label="前中后舱网联数据显示平台">
    <span class="platform-brand__mark" aria-hidden="true">
      <img :src="brandMarkUrl" alt="" />
    </span>
    <span class="platform-brand__text">前中后舱网联数据显示平台</span>
  </div>
</template>
