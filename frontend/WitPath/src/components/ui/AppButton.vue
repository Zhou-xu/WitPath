<script setup lang="ts">
/** 通用按钮：primary / secondary / ghost / danger，支持加载态与按下反馈 */
withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    size?: 'sm' | 'md'
    loading?: boolean
    disabled?: boolean
    block?: boolean
    type?: 'button' | 'submit'
  }>(),
  { variant: 'secondary', size: 'md', type: 'button' },
)
</script>

<template>
  <button
    class="btn"
    :class="[`btn--${variant}`, `btn--${size}`, { 'btn--block': block, 'is-loading': loading }]"
    :type="type"
    :disabled="disabled || loading"
  >
    <span v-if="loading" class="btn__spinner" aria-hidden="true" />
    <slot />
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: var(--r-control);
  border: 1px solid transparent;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease),
    border-color var(--dur-fast) var(--ease), transform var(--dur-fast) var(--ease),
    box-shadow var(--dur-fast) var(--ease);
}

.btn:active:not(:disabled) {
  transform: translateY(1px);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.btn--md {
  height: 36px;
  padding: 0 16px;
  font-size: 13.5px;
}

.btn--sm {
  height: 30px;
  padding: 0 12px;
  font-size: 12.5px;
}

.btn--block {
  width: 100%;
}

.btn--primary {
  background: var(--c-accent);
  color: #fffaf4;
  box-shadow: var(--shadow-xs);
}

.btn--primary:hover:not(:disabled) {
  background: var(--c-accent-hover);
  box-shadow: var(--shadow-sm);
}

.btn--secondary {
  background: var(--c-surface);
  border-color: var(--c-border-strong);
  color: var(--c-text);
}

.btn--secondary:hover:not(:disabled) {
  background: var(--c-surface-muted);
}

.btn--ghost {
  background: transparent;
  color: var(--c-text-secondary);
}

.btn--ghost:hover:not(:disabled) {
  background: var(--c-surface-muted);
  color: var(--c-text);
}

.btn--danger {
  background: var(--c-danger-soft);
  color: var(--c-danger);
}

.btn--danger:hover:not(:disabled) {
  background: #f2d7cf;
}

.btn__spinner {
  width: 13px;
  height: 13px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: btn-spin 0.7s linear infinite;
}

@keyframes btn-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
