<template>
  <!-- 第二屏抽屉：页面保持一屏不动，面板从底部划入，最多停靠在视口一半处 -->
  <div class="sheet" ref="sheetEl" data-state="closed">
    <div class="sheet-scrim" @click="closeSheet"></div>
    <section class="info-cards" id="features" ref="panelEl">
      <!-- 面板顶部小提手：暗示可上下拖动 -->
      <span class="info-cards__grabber" aria-hidden="true"></span>
      <div class="info-cards__inner" ref="scrollEl">
        <div class="section-head">
          <FadeIn from="up" :delay="80" :duration="600" :distance="16">
            <div class="section-eyebrow">
              <span class="eyebrow-line"></span>
              CORE FEATURES
            </div>
          </FadeIn>
          <FadeIn from="up" :delay="140" :duration="600" :distance="16">
            <h2 class="section-title">为每一步学习，提供清晰支点</h2>
          </FadeIn>
          <FadeIn from="up" :delay="200" :duration="600" :distance="16">
            <p class="section-sub">
              从目标规划到复盘沉淀，WitPath 陪你把「想学」变成「在学」「学会」。
            </p>
          </FadeIn>
        </div>
        <div class="info-cards__grid">
          <FadeIn
            v-for="(card, index) in cards"
            :key="card.label"
            class="info-cards__cell"
            from="up"
            :distance="28"
            :duration="600"
            :delay="280 + index * 120"
          >
            <InfoCard v-bind="card" :index="index + 1" />
          </FadeIn>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import InfoCard from './InfoCard.vue'
import FadeIn from '../FadeIn.vue'

const sheetEl = ref(null)
const panelEl = ref(null)
const scrollEl = ref(null)

/** 抽屉展开后顶部停靠位置：视口高度的一半（至多一半） */
const OPEN_RATIO = 0.5
/** 触摸触发全开所需位移：约 22% 屏高（封顶 180px），保证小幅度也能划出 */
const DRAG_RANGE = 0.22
const DRAG_RANGE_MAX = 180
/** 滚轮每格累计的位移占比（相对屏高） */
const WHEEL_RANGE = 0.3
/** 手势停止后多久吸附到开/关 */
const SNAP_DELAY = 140
/** 指数平滑系数（越小越顺滑） */
const SMOOTH = 0.16
/** 轻扫速度阈值（px/ms）：超过即视为快速滑动，直接开/关 */
const FLICK_VELOCITY = 0.4
/** 触摸方向锁定阈值（px）：超过才判定手势归属，避免抖动误判 */
const LOCK_THRESHOLD = 8

let target = 0 // 目标进度 0（收起）→ 1（展开）
let current = 0 // 当前进度（逐帧逼近 target）
let panelH = 0
let raf = null
let snapTimer = null
let touchY = 0
let touchBase = 0
let lastTouchY = 0
let lastTouchT = 0
let touchVelocity = 0
/** 当前触摸手势归属：'drawer' 抽屉拖拽 | 'content' 内容滚动 | null 未定 */
let gestureOwner = null

const reduceMotion =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

function measure() {
  panelH = panelEl.value ? panelEl.value.offsetHeight : 0
}

function render() {
  const panel = panelEl.value
  const sheet = sheetEl.value
  if (!panel) return
  const vh = window.innerHeight || 1
  // 收起：面板整体藏在视口下方；展开：顶边停在一半处（面板不足一半高时则完全展示）
  const openT = Math.max(panelH - vh * OPEN_RATIO, 0)
  const t = panelH - current * (panelH - openT)
  panel.style.transform = `translate3d(0, ${t.toFixed(1)}px, 0)`
  const scrim = sheet ? sheet.firstElementChild : null
  if (scrim) scrim.style.opacity = (current * 0.36).toFixed(3)
  if (sheet) sheet.dataset.state = current > 0.5 ? 'open' : 'closed'
  // 暴露给首屏：Hero 后退缩放与上滑提示的隐去
  document.documentElement.style.setProperty('--sheet-open', current.toFixed(3))
}

function tick() {
  const diff = target - current
  if (Math.abs(diff) < 0.0008) {
    current = target
    render()
    raf = null
    return
  }
  current += diff * SMOOTH
  render()
  raf = requestAnimationFrame(tick)
}

function kick() {
  if (reduceMotion) {
    current = target
    render()
    return
  }
  if (!raf) raf = requestAnimationFrame(tick)
}

function scheduleSnap() {
  clearTimeout(snapTimer)
  snapTimer = setTimeout(() => {
    target = current > 0.5 ? 1 : 0
    kick()
  }, SNAP_DELAY)
}

/** 手势持续驱动：progress 随滚轮/触摸增量连续变化 */
function nudge(delta) {
  const vh = window.innerHeight || 1
  target = Math.min(Math.max(target + delta / (vh * WHEEL_RANGE), 0), 1)
  kick()
  scheduleSnap()
}

function openSheet() {
  clearTimeout(snapTimer)
  target = 1
  kick()
}

function closeSheet() {
  clearTimeout(snapTimer)
  target = 0
  kick()
}

/* ---------- 输入源 ---------- */

/** 内容区当前是否可滚动 */
function isScrollable() {
  const scroll = scrollEl.value
  return !!scroll && scroll.scrollHeight > scroll.clientHeight + 1
}

/** 抽屉是否处于展开态 */
function isOpen() {
  return !!sheetEl.value && sheetEl.value.dataset.state === 'open'
}

function onWheel(e) {
  if (e.ctrlKey) return // 放行浏览器缩放

  // 抽屉已展开且指针在内容区：优先滚动内容，到边界才交还抽屉
  const overContent = !!(e.target.closest && e.target.closest('.info-cards__inner'))
  if (isOpen() && overContent && isScrollable()) {
    const scroll = scrollEl.value
    const atTop = scroll.scrollTop <= 0
    const atBottom = scroll.scrollTop + scroll.clientHeight >= scroll.scrollHeight - 1
    const goingUp = e.deltaY < 0
    if ((goingUp && !atTop) || (!goingUp && !atBottom)) {
      return // 内容未到边界：不拦截，原生滚动
    }
    e.preventDefault()
    if (goingUp && atTop) closeSheet() // 到顶继续上滑 → 收起抽屉
    return
  }

  e.preventDefault()
  const unit = e.deltaMode === 1 ? 33 : e.deltaMode === 2 ? window.innerHeight : 1
  nudge(e.deltaY * unit)
}

function onTouchStart(e) {
  touchY = e.touches[0].clientY
  touchBase = target
  lastTouchY = touchY
  lastTouchT = e.timeStamp || performance.now()
  touchVelocity = 0
  gestureOwner = null
  clearTimeout(snapTimer)
}

function onTouchMove(e) {
  const y = e.touches[0].clientY
  const now = e.timeStamp || performance.now()
  const dy = touchY - y // 上滑为正

  // 抽屉展开且内容可滚动时，先协商手势归属：
  // 内容未到顶 → 内容滚动；内容到顶后继续下滑 → 才交给抽屉收起
  if (isOpen() && isScrollable() && gestureOwner !== 'drawer') {
    const inPanel = !!(e.target.closest && e.target.closest('.info-cards'))
    if (!inPanel) {
      gestureOwner = 'drawer' // 点在遮罩上：直接当抽屉手势
    } else if (gestureOwner === 'content') {
      return // 已交给内容，保持原生滚动
    } else if (Math.abs(dy) < LOCK_THRESHOLD) {
      return // 方向未明，等待
    } else {
      const atTop = scrollEl.value.scrollTop <= 0
      gestureOwner = atTop && dy < 0 ? 'drawer' : 'content'
      if (gestureOwner === 'content') return
    }
  } else if (gestureOwner === 'content') {
    return
  }

  e.preventDefault()
  gestureOwner = 'drawer'

  // 记录瞬时速度（px/ms，上滑为正），用于松手时识别轻扫
  const dt = now - lastTouchT
  if (dt > 0) touchVelocity = (lastTouchY - y) / dt
  lastTouchY = y
  lastTouchT = now

  // 位移映射：缩短触发幅度，小幅度上滑即可完全打开
  const vh = window.innerHeight || 1
  const range = Math.min(vh * DRAG_RANGE, DRAG_RANGE_MAX)
  target = Math.min(Math.max(touchBase + dy / range, 0), 1)
  // 拖动期间直接跟手，不经过平滑，避免拖沓感
  current = target
  render()
}

function onTouchEnd() {
  clearTimeout(snapTimer)
  if (gestureOwner !== 'drawer') return // 内容滚动手势，与抽屉无关
  if (touchVelocity > FLICK_VELOCITY) {
    target = 1 // 快速上滑：直接打开
  } else if (touchVelocity < -FLICK_VELOCITY) {
    target = 0 // 快速下滑：直接收起
  } else {
    target = current > 0.5 ? 1 : 0 // 慢速拖动：按停留位置吸附
  }
  kick()
}

function onKeydown(e) {
  if (e.key === 'ArrowUp' || e.key === 'PageUp') {
    e.preventDefault()
    openSheet()
  } else if (e.key === 'ArrowDown' || e.key === 'PageDown' || e.key === 'Escape') {
    e.preventDefault()
    closeSheet()
  }
}

/** 页面不滚动，导航锚点改为开/关抽屉 */
function onDocClick(e) {
  const a = e.target.closest && e.target.closest('a[href]')
  if (!a) return
  const href = a.getAttribute('href')
  if (href === '#features') {
    e.preventDefault()
    openSheet()
  } else if (href === '#top') {
    e.preventDefault()
    closeSheet()
  }
}

onMounted(() => {
  measure()
  render()
  window.addEventListener('wheel', onWheel, { passive: false })
  window.addEventListener('touchstart', onTouchStart, { passive: true })
  window.addEventListener('touchmove', onTouchMove, { passive: false })
  window.addEventListener('touchend', onTouchEnd)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', () => {
    measure()
    render()
  })
  document.addEventListener('click', onDocClick)
})

onUnmounted(() => {
  window.removeEventListener('wheel', onWheel)
  window.removeEventListener('touchstart', onTouchStart)
  window.removeEventListener('touchmove', onTouchMove)
  window.removeEventListener('touchend', onTouchEnd)
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', onDocClick)
  clearTimeout(snapTimer)
  if (raf) cancelAnimationFrame(raf)
})

const cards = [
  {
    label: '智能学途规划',
    value: '目标拆解为可落地任务',
    desc: '将长期目标拆解为阶段性任务，一键生成个性化学习计划，让前行方向始终清晰。',
  },
  {
    label: '学习进度追踪',
    value: '可视化学习打卡',
    desc: '自动统计完成情况，以图表直观呈现学习轨迹，每一步进展都看得见。',
  },
  {
    label: '学习资料库',
    value: '分类沉淀学习资源',
    desc: '支持统一检索与管理，方便随时查阅回顾，让学过的内容真正留下来。',
  },
  {
    label: 'AI 学习分析',
    value: '智能诊断与建议',
    desc: '基于学习行为数据，提供个性化的学习建议与改进方案，知己知彼，因材施教。',
  },
]
</script>

<style scoped>
/* ---------- 抽屉容器：铺满视口但本身不拦截事件 ---------- */
.sheet {
  position: fixed;
  inset: 0;
  z-index: 40;
  pointer-events: none;
}

/* 遮罩：抽屉打开时压暗首屏，点击可收起 */
.sheet-scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(23, 33, 43, 0.14) 0%, rgba(23, 33, 43, 0.4) 100%);
  opacity: 0;
  pointer-events: none;
}

.sheet[data-state='open'] .sheet-scrim {
  pointer-events: auto;
}

/* ---------- 磨砂面板 ---------- */
.info-cards {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  /* JS 接管前的默认态：收起在视口下方 */
  transform: translateY(100%);
  pointer-events: auto;
  padding: 46px 0 60px;
  border-radius: 40px 40px 0 0;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.8) 0%, rgba(255, 255, 255, 0.6) 100%);
  backdrop-filter: blur(24px) saturate(1.5);
  -webkit-backdrop-filter: blur(24px) saturate(1.5);
  border-top: 1px solid rgba(255, 255, 255, 0.85);
  box-shadow: 0 -24px 64px rgba(16, 24, 40, 0.14);
  will-change: transform;
}

/* 顶部小提手 */
.info-cards__grabber {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 76px;
  height: 4px;
  border-radius: 0 0 6px 6px;
  background: linear-gradient(90deg, var(--wp-accent), var(--wp-accent-soft));
}

.info-cards__inner {
  max-width: var(--wp-max-w);
  margin: 0 auto;
  padding: 0 var(--wp-gutter);
  /* 半屏区域内滚动：滚动区高度 = 抽屉展开后可视高度 - 面板上下内边距 */
  max-height: calc(50vh - var(--wp-sheet-chrome));
  max-height: calc(50svh - var(--wp-sheet-chrome));
  overflow-y: auto;
  /* 滚动到边界时不连锁触发页面/抽屉 */
  overscroll-behavior: contain;
  scrollbar-width: none;
}

.info-cards__inner::-webkit-scrollbar {
  display: none;
}

/* ---------- 区块标题 ---------- */
.section-head {
  max-width: 560px;
  margin-bottom: 34px;
}

.section-eyebrow {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 3px;
  color: var(--wp-accent);
}

.eyebrow-line {
  width: 28px;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--wp-accent));
}

.section-title {
  font-size: clamp(24px, 2.6vw, 30px);
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--wp-ink);
  margin: 0 0 12px;
}

.section-sub {
  font-size: 14.5px;
  line-height: 1.9;
  color: var(--wp-body);
  margin: 0;
}

/* ---------- 卡片网格 ---------- */
.info-cards__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

/* FadeIn 容器作为网格单元时，让内部卡片继续撑满整行高度 */
.info-cards__cell {
  display: flex;
}

.info-cards__cell > .info-card {
  flex: 1;
}

@media (max-width: 960px) {
  .info-cards__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 560px) {
  .info-cards {
    --wp-sheet-chrome: 88px;
    padding: 40px 0 48px;
  }

  .info-cards__inner {
    padding: 0 24px;
  }

  .info-cards__grid {
    grid-template-columns: 1fr;
  }
}
</style>
