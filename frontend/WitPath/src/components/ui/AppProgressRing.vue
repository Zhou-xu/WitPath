<script setup lang="ts">
import { computed } from 'vue'

/** 环形进度：用于目标完成度 */
const props = withDefaults(defineProps<{ value?: number; size?: number; label?: string }>(), {
  value: 0,
  size: 64,
})

const clamped = computed(() => Math.max(0, Math.min(100, Number(props.value) || 0)))
const stroke = computed(() => Math.max(4, props.size / 10))
const radius = computed(() => (props.size - stroke.value) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const offset = computed(() => circumference.value * (1 - clamped.value / 100))
</script>

<template>
  <div class="ring" :style="{ width: `${size}px`, height: `${size}px` }">
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`">
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        stroke="var(--c-surface-sunken)"
        :stroke-width="stroke"
      />
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        stroke="var(--c-accent)"
        :stroke-width="stroke"
        stroke-linecap="round"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="offset"
        :transform="`rotate(-90 ${size / 2} ${size / 2})`"
        style="transition: stroke-dashoffset var(--dur-slow) var(--ease)"
      />
    </svg>
    <div class="ring__text">
      <strong>{{ Math.round(clamped) }}</strong>
      <span v-if="label">{{ label }}</span>
    </div>
  </div>
</template>

<style scoped>
.ring {
  position: relative;
  display: grid;
  place-items: center;
}

.ring__text {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.1;
}

.ring__text strong {
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.ring__text span {
  font-size: 10px;
  color: var(--c-text-muted);
}
</style>
