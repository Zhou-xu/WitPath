<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useUiStore } from '@/stores/ui'
import AppButton from '@/components/ui/AppButton.vue'
import UserAvatar from '@/components/ui/UserAvatar.vue'
import { reminderApi } from '@/api'

const user = useUserStore()
const ui = useUiStore()
const router = useRouter()

const unread = ref(0)
const menuOpen = ref(false)

const title = computed(() => {
  const map: Record<string, string> = {
    '/app/dashboard': '学习概览',
    '/app/goals': '目标与任务',
    '/app/records': '学习记录',
    '/app/materials': '学习资料',
    '/app/assistant': 'AI 助手',
    '/app/settings': '个人设置',
  }
  return map[router.currentRoute.value.path] ?? '学途知行'
})

async function loadUnread() {
  try {
    unread.value = await reminderApi.unreadCount()
  } catch {
    unread.value = 0
  }
}

function logout() {
  user.logout()
}

onMounted(loadUnread)
</script>

<template>
  <header class="top">
    <div class="top__inner">
      <div class="top__left">
        <button class="top__burger" aria-label="打开导航" @click="ui.toggleMobileNav()">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </button>
        <div class="stack" style="gap: 2px">
          <h1 class="top__title">{{ title }}</h1>
          <span class="text-xs text-muted top__date">今天是 {{ new Date().toLocaleDateString('zh-CN') }}，保持节奏</span>
        </div>
      </div>

      <div class="top__right">
        <RouterLink to="/app/dashboard" class="top__bell" :title="`${unread} 条未读提醒`">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="none">
            <path d="M12 4a5 5 0 0 0-5 5v3l-2 3h14l-2-3V9a5 5 0 0 0-5-5zm-2 13a2 2 0 0 0 4 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
          <span v-if="unread > 0" class="top__dot" />
        </RouterLink>

        <div class="top__menu">
          <button class="top__avatar" @click="menuOpen = !menuOpen">
            <UserAvatar :url="user.profile?.avatarUrl" :name="user.displayName" :size="34" />
          </button>
          <Transition name="pop">
            <div v-if="menuOpen" class="top__dropdown" @mouseleave="menuOpen = false">
              <div class="top__dropdown-head">
                <UserAvatar :url="user.profile?.avatarUrl" :name="user.displayName" :size="30" />
                <div class="stack" style="gap: 2px; min-width: 0">
                  <strong>{{ user.displayName }}</strong>
                  <span class="text-xs text-muted truncate">
                    {{ user.profile?.email || user.profile?.username }}
                  </span>
                </div>
              </div>
              <hr class="divider" />
              <RouterLink to="/app/settings" class="top__dropdown-item" @click="menuOpen = false">个人设置</RouterLink>
              <button class="top__dropdown-item" @click="logout">退出登录</button>
            </div>
          </Transition>
        </div>

        <RouterLink to="/" class="top__ghost">
          <AppButton variant="ghost" size="sm">返回首页</AppButton>
        </RouterLink>
      </div>
    </div>
  </header>
</template>

<style scoped>
.top {
  position: sticky;
  top: 0;
  z-index: 40;
  height: var(--topbar-h);
  background: rgba(247, 244, 239, 0.86);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--c-border);
}

.top__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: 100%;
  padding: 0 var(--sp-8);
}

.top__left {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-width: 0;
}

.top__burger {
  display: none;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: var(--c-text-secondary);
  transition: background var(--dur-fast) var(--ease);
}

.top__burger:hover {
  background: var(--c-surface-muted);
  color: var(--c-text);
}

.top__title {
  font-size: 19px;
}

.top__right {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.top__bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  color: var(--c-text-secondary);
  transition: background var(--dur-fast) var(--ease);
}

.top__bell:hover {
  background: var(--c-surface);
}

.top__dot {
  position: absolute;
  top: 7px;
  right: 8px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-danger);
}

.top__menu {
  position: relative;
}

.top__avatar {
  display: grid;
  place-items: center;
  border-radius: 50%;
  transition: box-shadow var(--dur-fast) var(--ease);
}

.top__avatar:hover {
  box-shadow: 0 0 0 3px var(--c-accent-soft);
}

.top__dropdown {
  position: absolute;
  right: 0;
  top: 42px;
  width: 200px;
  padding: 12px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
}

.top__dropdown-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 4px;
}

.top__dropdown-item {
  display: block;
  width: 100%;
  padding: 7px 8px;
  border-radius: 8px;
  text-align: left;
  font-size: 13px;
  color: var(--c-text-secondary);
}

.top__dropdown-item:hover {
  background: var(--c-surface-muted);
  color: var(--c-text);
}

.top__ghost {
  display: flex;
}

@media (max-width: 900px) {
  .top__inner {
    padding: 0 var(--sp-4);
  }

  .top__burger {
    display: grid;
  }
}

@media (max-width: 640px) {
  .top__date,
  .top__ghost {
    display: none;
  }

  .top__title {
    font-size: 17px;
  }
}
</style>
