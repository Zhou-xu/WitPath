<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import { useUserStore } from '@/stores/user'
import UserAvatar from '@/components/ui/UserAvatar.vue'

const route = useRoute()
const ui = useUiStore()
const user = useUserStore()

/* 移动端(≤900px)侧边栏是抽屉：始终显示文字标签，路由跳转后自动收起 */
const isMobile = ref(false)
let mq: MediaQueryList | null = null

function onMediaChange(event: MediaQueryListEvent) {
  isMobile.value = event.matches
  if (!event.matches) ui.closeMobileNav()
}

onMounted(() => {
  mq = window.matchMedia('(max-width: 900px)')
  isMobile.value = mq.matches
  mq.addEventListener('change', onMediaChange)
})

onBeforeUnmount(() => mq?.removeEventListener('change', onMediaChange))

watch(
  () => route.path,
  () => {
    if (isMobile.value) ui.closeMobileNav()
  },
)

const showLabels = computed(() => isMobile.value || !ui.sidebarCollapsed)

interface NavItem {
  to: string
  label: string
  path: string
}

const items: NavItem[] = [
  { to: '/app/dashboard', label: '学习概览', path: 'M4 10.5L12 5l8 5.5V19a1 1 0 0 1-1 1h-5v-6H10v6H5a1 1 0 0 1-1-1z' },
  { to: '/app/goals', label: '目标与任务', path: 'M12 4v16M4 12h16' },
  { to: '/app/records', label: '学习记录', path: 'M6 4h9l4 4v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1zm3 8h7m-7 4h7' },
  { to: '/app/materials', label: '学习资料', path: 'M4 7h16v12a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1zM4 7l8-3 8 3' },
  { to: '/app/assistant', label: 'AI 助手', path: 'M12 4a6 6 0 0 1 6 6c0 3-2 4-2 6H8c0-2-2-3-2-6a6 6 0 0 1 6-6zm-3 15h6' },
  { to: '/app/settings', label: '个人设置', path: 'M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zm8 3c0 .6-.05 1.2-.15 1.8l1.9 1.5-1.9 3.3-2.25-.9a8 8 0 0 1-3.1 1.8L14.2 22h-3.8l-.3-2.5a8 8 0 0 1-3.1-1.8l-2.25.9-1.9-3.3 1.9-1.5A8.6 8.6 0 0 1 4 12c0-.6.05-1.2.15-1.8l-1.9-1.5 1.9-3.3 2.25.9a8 8 0 0 1 3.1-1.8L10.4 2h3.8l.3 2.5a8 8 0 0 1 3.1 1.8l2.25-.9 1.9 3.3-1.9 1.5c.1.6.15 1.2.15 1.8z' },
]

const active = computed(() => route.path)
</script>

<template>
  <aside class="side" :class="{ 'side--open': isMobile && ui.mobileNavOpen }">
    <div class="side__brand">
      <span class="side__logo">Wit</span>
      <span v-if="showLabels" class="side__name">知途知行</span>
    </div>

    <nav class="side__nav">
      <RouterLink
        v-for="item in items"
        :key="item.to"
        :to="item.to"
        class="side__link"
        :class="{ 'is-active': active.startsWith(item.to) }"
        :title="item.label"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path :d="item.path" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span v-if="showLabels">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="side__foot">
      <button class="side__toggle" @click="isMobile ? ui.closeMobileNav() : ui.toggleSidebar()">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
          <path d="M14 7l-5 5 5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
        <span v-if="showLabels">{{ isMobile ? '收起' : '收起导航' }}</span>
      </button>
      <div v-if="showLabels" class="side__user">
        <UserAvatar :url="user.profile?.avatarUrl" :name="user.displayName" :size="24" />
        <span class="truncate">{{ user.displayName }}</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.side {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  padding: var(--sp-5) var(--sp-3);
  background: var(--c-surface);
  border-right: 1px solid var(--c-border);
  overflow: hidden;
}

.side__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 8px;
  min-height: 32px;
}

.side__logo {
  font-family: var(--font-brand);
  /* 草书字形视觉尺寸偏小，字号需比普通字体大一档才能对齐观感 */
  font-size: 30px;
  color: var(--c-accent);
  line-height: 0.9;
}

.side__name {
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.side__nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--sp-2);
}

.side__link {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 38px;
  padding: 0 10px;
  border-radius: var(--r-control);
  color: var(--c-text-secondary);
  font-size: 13.5px;
  white-space: nowrap;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}

.side__link:hover {
  background: var(--c-surface-muted);
  color: var(--c-text);
}

.side__link.is-active {
  background: var(--c-accent-soft);
  color: var(--c-accent-hover);
  font-weight: 500;
}

.side__foot {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.side__toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  border-radius: var(--r-control);
  color: var(--c-text-muted);
  font-size: 12.5px;
  transition: background var(--dur-fast) var(--ease), transform var(--dur) var(--ease);
}

.side__toggle:hover {
  background: var(--c-surface-muted);
  color: var(--c-text);
}

.side__user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--r-control);
  background: var(--c-bg-subtle);
  font-size: 12.5px;
  color: var(--c-text-secondary);
}

@media (max-width: 900px) {
  /* 抽屉模式：固定定位 + 滑入滑出，始终展示完整标签 */
  .side {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 80;
    width: 264px;
    padding: var(--sp-5) var(--sp-4);
    transform: translateX(-100%);
    transition: transform var(--dur) var(--ease);
    box-shadow: none;
    overflow-y: auto;
  }

  .side--open {
    transform: none;
    box-shadow: var(--shadow-lg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .side {
    transition: none;
  }
}
</style>
