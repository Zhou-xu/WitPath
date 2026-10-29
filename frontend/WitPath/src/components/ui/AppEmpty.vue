<script setup lang="ts">
/** 空状态：统一插图、说明与操作区 */
withDefaults(defineProps<{ title?: string; description?: string }>(), {
  title: '暂无数据',
  description: '',
})
</script>

<template>
  <div class="empty-state">
    <svg width="52" height="52" viewBox="0 0 52 52" fill="none" aria-hidden="true">
      <rect x="8" y="12" width="36" height="28" rx="4" stroke="currentColor" stroke-opacity="0.35" stroke-width="1.6" />
      <path d="M8 21h36" stroke="currentColor" stroke-opacity="0.25" stroke-width="1.6" />
      <path d="M17 29h8M17 34h14" stroke="currentColor" stroke-opacity="0.25" stroke-width="1.6" stroke-linecap="round" />
    </svg>
    <div class="stack" style="gap: 2px">
      <strong style="color: var(--c-text-secondary); font-weight: 500">{{ title }}</strong>
      <span v-if="description" class="text-xs text-muted">{{ description }}</span>
    </div>
    <slot />
  </div>
</template>
