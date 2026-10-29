<script setup lang="ts">
/** 指标卡：图标 + 数值 + 趋势说明 */
withDefaults(
  defineProps<{
    label: string
    value: string | number
    unit?: string
    hint?: string
    tone?: 'default' | 'accent' | 'success' | 'warning' | 'danger'
    loading?: boolean
  }>(),
  { tone: 'default' },
)
</script>

<template>
  <div class="stat card card--interactive">
    <div v-if="loading" class="stack" style="gap: 10px">
      <div class="skeleton" style="height: 12px; width: 60%" />
      <div class="skeleton" style="height: 26px; width: 40%" />
    </div>
    <template v-else>
      <div class="stat__label">{{ label }}</div>
      <div class="stat__value" :class="`stat__value--${tone}`">
        <span class="stat__num">{{ value }}</span>
        <span v-if="unit" class="stat__unit">{{ unit }}</span>
      </div>
      <div v-if="hint || $slots.default" class="stat__hint">
        <slot>{{ hint }}</slot>
      </div>
    </template>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 20px;
}

.stat__label {
  font-size: 12.5px;
  color: var(--c-text-muted);
}

.stat__value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat__num {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat__unit {
  font-size: 12px;
  color: var(--c-text-muted);
}

.stat__value--accent .stat__num {
  color: var(--c-accent);
}

.stat__value--success .stat__num {
  color: var(--c-success);
}

.stat__value--warning .stat__num {
  color: var(--c-warning);
}

.stat__value--danger .stat__num {
  color: var(--c-danger);
}

.stat__hint {
  font-size: 12px;
  color: var(--c-text-muted);
}
</style>
