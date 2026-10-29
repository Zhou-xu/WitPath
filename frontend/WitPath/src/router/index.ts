import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'index',
      component: () => import('@/views/IndexView.vue'),
      meta: { public: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue'),
      meta: { public: true },
    },
    {
      path: '/app',
      component: () => import('@/components/layout/AppShell.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/app/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
        },
        {
          path: 'goals',
          name: 'goals',
          component: () => import('@/views/GoalsView.vue'),
        },
        {
          path: 'records',
          name: 'records',
          component: () => import('@/views/RecordsView.vue'),
        },
        {
          path: 'materials',
          name: 'materials',
          component: () => import('@/views/MaterialsView.vue'),
        },
        {
          path: 'assistant',
          name: 'assistant',
          component: () => import('@/views/AssistantView.vue'),
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/SettingsView.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const user = useUserStore()

  // 已登录用户访问登录/注册页时直接进入工作台
  if (to.meta.public && to.name !== 'index' && user.isLogin) {
    return { path: '/app/dashboard' }
  }

  if (!to.meta.requiresAuth) return true

  if (!user.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 首次进入时补齐用户信息，失败（token 失效）则退回登录页
  if (!user.loaded) {
    try {
      await user.loadProfile(true)
    } catch {
      user.logout(false)
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  return true
})

export default router
