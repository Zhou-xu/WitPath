<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterView } from 'vue-router'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') ui.closeMobileNav()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="shell" :class="{ 'shell--collapsed': ui.sidebarCollapsed }">
    <AppSidebar />
    <Transition name="backdrop">
      <div v-if="ui.mobileNavOpen" class="shell__backdrop" aria-hidden="true" @click="ui.closeMobileNav()" />
    </Transition>
    <div class="shell__main">
      <AppTopbar />
      <main class="shell__content">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--sidebar-w) 1fr;
  /* 整页锁定视口：侧边栏/顶栏固定，仅内容区内部滚动 */
  height: 100vh;
  overflow: hidden;
  background: var(--c-bg);
  transition: grid-template-columns var(--dur) var(--ease);
}

.shell--collapsed {
  grid-template-columns: var(--sidebar-w-collapsed) 1fr;
}

.shell__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
}

.shell__content {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--sp-6) var(--sp-8) var(--sp-12);
}

.shell__backdrop {
  display: none;
}

@media (max-width: 900px) {
  /* 移动端：侧边栏变为抽屉，主栏占满整行 */
  .shell,
  .shell--collapsed {
    grid-template-columns: 1fr;
  }

  .shell__content {
    padding: var(--sp-4) var(--sp-4) var(--sp-10);
  }

  .shell__backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 70;
    background: rgba(33, 29, 23, 0.38);
    backdrop-filter: blur(2px);
  }
}

.backdrop-enter-active,
.backdrop-leave-active {
  transition: opacity var(--dur) var(--ease);
}

.backdrop-enter-from,
.backdrop-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .backdrop-enter-active,
  .backdrop-leave-active {
    transition: none;
  }
}
</style>
