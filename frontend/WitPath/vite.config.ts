import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// 后端地址：可用 WITPATH_BACKEND 覆盖（默认本机 8080）
const backend = process.env.WITPATH_BACKEND ?? 'http://127.0.0.1:8080'

// /login 与 /register 同时是前端路由和后端接口路径：
// 浏览器刷新/直达（GET + Accept: text/html）时交回 SPA 渲染，避免被代理到后端 405；
// axios 接口请求（JSON Accept）照常转发。
const spaBypass = (req: { method?: string; headers: Record<string, unknown> }) => {
  const accept = String(req.headers.accept ?? '')
  if (req.method === 'GET' && accept.includes('text/html')) return '/index.html'
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    open: true,
    // 开发态直连 Spring Boot，前后端真正打通（生产由 Nginx 反代同源）
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/login': { target: backend, changeOrigin: true, bypass: spaBypass },
      '/register': { target: backend, changeOrigin: true, bypass: spaBypass },
      '/public': { target: backend, changeOrigin: true },
      '/files': { target: backend, changeOrigin: true },
    },
  },
})
