<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

/** 弹窗：支持 ESC 关闭、点击遮罩关闭、进入/离开过渡 */
const props = withDefaults(defineProps<{ open: boolean; title?: string; width?: number }>(), {
  width: 520,
})
const emit = defineEmits<{ (e: 'close'): void }>()

const visible = ref(false)

watch(
  () => props.open,
  (val) => {
    if (val) {
      visible.value = true
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
  },
)

function close() {
  emit('close')
}

function onKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && props.open) close()
}

onMounted(() => window.addEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="modal" role="dialog" aria-modal="true">
        <div class="modal__mask" @click="close" />
        <Transition name="pop" appear>
          <div class="modal__panel" :style="{ width: `${width}px` }">
            <header class="modal__head">
              <h3>{{ title }}</h3>
              <button class="modal__close" aria-label="关闭" @click="close">
                <svg width="16" height="16" viewBox="0 0 16 16">
                  <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                </svg>
              </button>
            </header>
            <div class="modal__body">
              <slot />
            </div>
            <footer v-if="$slots.footer" class="modal__foot">
              <slot name="footer" />
            </footer>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.modal__mask {
  position: absolute;
  inset: 0;
  background: rgba(30, 25, 20, 0.32);
  backdrop-filter: blur(2px);
}

.modal__panel {
  position: relative;
  max-width: 100%;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-card);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--c-border);
}

.modal__close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  color: var(--c-text-muted);
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.modal__close:hover {
  background: var(--c-surface-muted);
  color: var(--c-text);
}

.modal__body {
  padding: 20px;
  overflow-y: auto;
}

.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--c-border);
  background: var(--c-bg-subtle);
}
</style>
