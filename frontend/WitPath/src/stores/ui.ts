import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Toast {
  id: number
  type: 'success' | 'error' | 'warning' | 'info'
  message: string
}

export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false)
  const mobileNavOpen = ref(false)
  const toasts = ref<Toast[]>([])
  const globalLoading = ref(false)

  let seed = 0

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function toggleMobileNav() {
    mobileNavOpen.value = !mobileNavOpen.value
  }

  function closeMobileNav() {
    mobileNavOpen.value = false
  }

  function toast(message: string, type: Toast['type'] = 'info') {
    const id = ++seed
    toasts.value.push({ id, type, message })
    setTimeout(() => dismiss(id), type === 'error' ? 5000 : 3200)
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((item) => item.id !== id)
  }

  return { sidebarCollapsed, mobileNavOpen, toasts, globalLoading, toggleSidebar, toggleMobileNav, closeMobileNav, toast, dismiss }
})
