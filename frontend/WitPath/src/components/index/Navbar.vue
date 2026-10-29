<template>
  <nav class="navbar" :class="{ 'navbar--scrolled': scrolled }">
    <div class="navbar-inner">
      <a class="navbar-brand" href="#top">
        <img class="logo" src="@/assets/gradient-modern-mountain.svg" alt="WitPath Logo" />
        <span class="brand-word">WitPath</span>
        <span class="brand-divider"></span>
        <span class="brand-tagline">知途知行 · AI 学习协作平台</span>
      </a>
      <div class="navbar-right">
        <!-- 四个导航项改为按钮，点击逻辑由后续自行绑定（@click） -->
        <ul class="navbar-links">
          <li><button type="button">学习规划</button></li>
          <li><button type="button">资源库</button></li>
          <li><button type="button">提出建议</button></li>
          <li><button type="button">关于我们</button></li>
        </ul>
        <button class="navbar-cta" type="button" @click="$router.push('/login')">
          开启学途
          <svg class="cta-arrow" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M2.5 8h11M9.5 4l4 4-4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
      </div>
    </div>
  </nav>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'

const scrolled = ref(false)

function onScroll() {
  scrolled.value = window.scrollY > 12
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.navbar {
  position: sticky;
  top: 0;
  z-index: 50;
  height: var(--wp-nav-h);
  display: flex;
  align-items: center;
  border-bottom: 1px solid transparent;
  background: rgba(250, 247, 242, 0.72);
  backdrop-filter: blur(14px) saturate(1.5);
  -webkit-backdrop-filter: blur(14px) saturate(1.5);
  transition:
    border-color 0.35s ease,
    box-shadow 0.35s ease,
    background 0.35s ease;
}

.navbar--scrolled {
  border-bottom-color: var(--wp-line);
  box-shadow: 0 4px 24px rgba(16, 24, 40, 0.05);
}

.navbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  max-width: var(--wp-max-w);
  margin: 0 auto;
  padding: 0 var(--wp-gutter);
}

/* ---------- 品牌区 ---------- */
.navbar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  text-decoration: none;
  color: var(--wp-ink);
}

.logo {
  width: 34px;
  height: 34px;
  object-fit: contain;
  transition: transform 0.4s var(--wp-ease-out);
}

.navbar-brand:hover .logo {
  transform: rotate(-6deg) scale(1.06);
}

.brand-word {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.brand-divider {
  width: 1px;
  height: 16px;
  background: var(--wp-line-strong);
}

.brand-tagline {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--wp-muted);
  white-space: nowrap;
}

/* ---------- 链接区 ---------- */
.navbar-right {
  display: flex;
  align-items: center;
  gap: 36px;
}

.navbar-links {
  display: flex;
  list-style: none;
  gap: 30px;
  margin: 0;
  padding: 0;
}

.navbar-links a,
.navbar-links button {
  position: relative;
  display: inline-block;
  padding: 4px 2px;
  margin: 0;
  border: 0;
  background: none;
  font-family: inherit;
  font-size: 14.5px;
  letter-spacing: 0.5px;
  color: var(--wp-ink-soft);
  text-decoration: none;
  cursor: pointer;
  transition: color 0.25s ease;
}

/* 中心展开下划线 */
.navbar-links a::after,
.navbar-links button::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 0;
  height: 2px;
  border-radius: 2px;
  background: linear-gradient(90deg, var(--wp-accent), var(--wp-accent-soft));
  transform: translateX(-50%);
  transition: width 0.3s var(--wp-ease-out);
}

.navbar-links a:hover,
.navbar-links button:hover {
  color: var(--wp-ink);
}

.navbar-links a:hover::after,
.navbar-links button:hover::after {
  width: 100%;
}

/* ---------- CTA ---------- */
.navbar-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 20px;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--wp-accent) 0%, var(--wp-accent-deep) 100%);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  white-space: nowrap;
  box-shadow: var(--wp-shadow-accent);
  transition:
    transform 0.25s var(--wp-ease-out),
    box-shadow 0.25s ease,
    filter 0.25s ease;
}

.navbar-cta:hover {
  transform: translateY(-1px);
  filter: brightness(1.06);
  box-shadow: 0 10px 28px rgba(201, 106, 69, 0.36);
}

.navbar-cta:active {
  transform: translateY(0);
}

.cta-arrow {
  width: 14px;
  height: 14px;
  transition: transform 0.25s var(--wp-ease-out);
}

.navbar-cta:hover .cta-arrow {
  transform: translateX(3px);
}

/* ---------- 响应式 ---------- */
@media (max-width: 960px) {
  .brand-tagline {
    display: none;
  }
}

@media (max-width: 760px) {
  .navbar-inner {
    padding: 0 24px;
  }

  .navbar-right {
    gap: 16px;
  }

  .navbar-links {
    display: none;
  }
}
</style>
