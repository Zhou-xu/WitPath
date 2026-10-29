// 设计令牌必须先加载，再加载组件样式
// 品牌草书字体（刘建毛草）：本地依赖打包，按 unicode-range 分片按需加载
import '@fontsource/liu-jian-mao-cao/chinese-simplified-400.css'
import '@fontsource/liu-jian-mao-cao/latin-400.css'
import './styles/tokens.css'
import './styles/ui.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')
