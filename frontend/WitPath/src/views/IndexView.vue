<template>
  <div class="page-wrapper">
    <!-- 背景氛围层：柔光球 + 颗粒质感 -->
    <div class="glow-bg">
      <div ref="orb1" class="glow-orb glow-orb-1"></div>
      <div ref="orb2" class="glow-orb glow-orb-2"></div>
      <div ref="orb3" class="glow-orb glow-orb-3"></div>
    </div>
    <div class="grain" aria-hidden="true"></div>

    <FadeIn from="down" :delay="0" :duration="500" :distance="16">
      <Navbar />
    </FadeIn>
    <HeroSection />
    <InfoCards />
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import '../assets/base.css'
import '../assets/animations.css'
import Navbar from '../components/index/Navbar.vue'
import HeroSection from '../components/index/HeroSection.vue'
import InfoCards from '../components/index/InfoCards.vue'
import FadeIn from '../components/FadeIn.vue'

const orb1 = ref(null)
const orb2 = ref(null)
const orb3 = ref(null)

const orbIntervals = []

function animateOrb(element, speed) {
  let x = Math.random() * (window.innerWidth - 200) + 100
  let y = Math.random() * (window.innerHeight - 200) + 100
  let vx = (Math.random() - 0.5) * speed * 2
  let vy = (Math.random() - 0.5) * speed * 2

  const intervalId = setInterval(() => {
    x += vx
    y += vy

    if (x <= 0 || x >= window.innerWidth - 100) {
      vx = -vx + (Math.random() - 0.5) * 0.5
      x = Math.max(0, Math.min(x, window.innerWidth - 100))
    }
    if (y <= 0 || y >= window.innerHeight - 100) {
      vy = -vy + (Math.random() - 0.5) * 0.5
      y = Math.max(0, Math.min(y, window.innerHeight - 100))
    }

    vx += (Math.random() - 0.5) * 0.1
    vy += (Math.random() - 0.5) * 0.1

    const currentSpeed = Math.sqrt(vx * vx + vy * vy)
    if (currentSpeed > speed * 1.5) {
      vx = (vx / currentSpeed) * speed * 1.5
      vy = (vy / currentSpeed) * speed * 1.5
    }

    element.style.transform = `translate(${x}px, ${y}px)`
  }, 16)

  orbIntervals.push(intervalId)
}

onMounted(() => {
  // 首页独占一屏滚动模式，离开页面时移除，避免影响工作台滚动
  document.documentElement.classList.add('wp-index')
  document.body.classList.add('wp-index')
  // 尊重系统「减少动态效果」偏好
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  animateOrb(orb1.value, 1.1)
  animateOrb(orb2.value, 0.8)
  animateOrb(orb3.value, 0.9)
})

onUnmounted(() => {
  document.documentElement.classList.remove('wp-index')
  document.body.classList.remove('wp-index')
  orbIntervals.forEach((id) => clearInterval(id))
  orbIntervals.length = 0
})
</script>

<style scoped>
.page-wrapper {
  position: relative;
  /* 一屏模式：页面本身不滚动，第二屏以固定抽屉滑入 */
  height: 100%;
  /* 用 clip 而非 hidden：避免创建滚动容器而破坏吸顶导航的 sticky 行为 */
  overflow-x: clip;
}

/* ---------- 柔光背景 ---------- */
.glow-bg {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  pointer-events: none;
  animation: fade-in 1.5s ease both;
}

.glow-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(56px);
  opacity: 0.8;
  will-change: transform;
}

.glow-orb-1 {
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, rgba(201, 106, 69, 0.62) 0%, transparent 70%);
  top: -260px;
  left: -260px;
}

.glow-orb-2 {
  width: 640px;
  height: 640px;
  background: radial-gradient(circle, rgba(78, 107, 126, 0.55) 0%, transparent 70%);
  top: -320px;
  left: -320px;
}

.glow-orb-3 {
  width: 460px;
  height: 460px;
  background: radial-gradient(circle, rgba(180, 140, 100, 0.52) 0%, transparent 70%);
  top: -230px;
  left: -230px;
}

/* ---------- 纸质颗粒 ---------- */
.grain {
  position: fixed;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  opacity: 0.045;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='160' height='160' filter='url(%23n)'/%3E%3C/svg%3E");
}

@media (prefers-reduced-motion: reduce) {
  .glow-bg {
    animation: none;
  }
}
</style>
