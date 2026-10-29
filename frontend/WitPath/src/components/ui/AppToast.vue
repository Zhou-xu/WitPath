<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

/** 全局提示浮层 */
const ui = useUiStore()

const icons: Record<string, string> = {
  success: 'M4 8.5l3 3 5-6',
  error: 'M6 6l6 6M12 6l-6 6',
  warning: 'M9 5.5v5M9 13.2v.4',
  info: 'M9 8v4.5M9 5.6v.3',
}
</script>

<template>
  <Teleport to="body">
    <div class="toasts" role="status" aria-live="polite">
      <TransitionGroup name="toast">
        <div v-for="t in ui.toasts" :key="t.id" class="toast" :class="`toast--${t.type}`">
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
            <path :d="icons[t.type]" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span>{{ t.message }}</span>
          <button class="toast__close" aria-label="关闭提示" @click="ui.dismiss(t.id)">×</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toasts {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 200;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 240px;
  max-width: 360px;
  padding: 10px 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
  font-size: 13px;
  color: var(--c-text);
}

.toast--success {
  color: var(--c-success);
  border-color: rgba(78, 125, 91, 0.28);
}

.toast--error {
  color: var(--c-danger);
  border-color: rgba(176, 82, 60, 0.28);
}

.toast--warning {
  color: var(--c-warning);
  border-color: rgba(185, 133, 36, 0.28);
}

.toast--info {
  color: var(--c-info);
  border-color: rgba(74, 107, 138, 0.28);
}

.toast__close {
  margin-left: auto;
  color: var(--c-text-muted);
  font-size: 16px;
  line-height: 1;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease);
}
</style>
