<script setup lang="ts">
/** 骨架屏占位：支持行列表与单块两种形态 */
withDefaults(defineProps<{ lines?: number; height?: number; block?: boolean }>(), { lines: 3 })
</script>

<template>
  <div v-if="block" class="skeleton" :style="{ height: `${height ?? 200}px` }" />
  <div v-else class="stack" style="gap: 10px">
    <div
      v-for="i in lines"
      :key="i"
      class="skeleton"
      :style="{ height: '14px', width: i === lines ? '60%' : '100%' }"
    />
  </div>
</template>
