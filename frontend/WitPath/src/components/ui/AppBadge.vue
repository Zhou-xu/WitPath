<script setup lang="ts">
import { computed } from 'vue'

/** 状态标签：按语义自动匹配配色 */
const props = withDefaults(defineProps<{ tone?: string; text?: string }>(), { tone: 'default' })

const toneMap: Record<string, string> = {
  default: '',
  success: 'badge--success',
  warning: 'badge--warning',
  danger: 'badge--danger',
  info: 'badge--info',
  accent: 'badge--accent',
  violet: 'badge--violet',
}

const cls = computed(() => toneMap[props.tone] ?? '')
</script>

<template>
  <span class="badge" :class="cls">{{ text ?? '—' }}</span>
</template>
