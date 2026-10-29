<template>
  <section class="hero" id="top">
    <div class="hero-content">
      <FadeIn from="up" :delay="100" :duration="600" :distance="16">
        <div class="hero-label">
          <span class="label-line"></span>
          <span>WitPath with you along the way</span>
          <span class="label-line"></span>
        </div>
      </FadeIn>
      <FadeIn from="up" :delay="180" :duration="700" :distance="22">
        <h1 class="hero-title">
          知途 知行<br />
          <span class="title-accent">
            靠近更好的自己
            <svg class="title-swash" viewBox="0 0 220 12" fill="none" preserveAspectRatio="none" aria-hidden="true">
              <defs>
                <linearGradient id="swashGrad" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stop-color="#c96a45" stop-opacity="0.15" />
                  <stop offset="50%" stop-color="#c96a45" stop-opacity="0.85" />
                  <stop offset="100%" stop-color="#e8a37e" stop-opacity="0.2" />
                </linearGradient>
              </defs>
              <path d="M3 8.5 C 55 2.5, 165 2.5, 217 8" stroke="url(#swashGrad)" stroke-width="3.5" stroke-linecap="round" />
            </svg>
          </span>
        </h1>
      </FadeIn>
      <FadeIn from="up" :delay="260" :duration="700" :distance="22">
        <div class="poetry-danmaku" aria-label="学途主题诗词轮播">
          <div
            v-for="(lane, i) in poetryLanes"
            :key="i"
            class="poetry-lane"
            :class="`poetry-lane--${i + 1}`"
          >
            <span v-for="p in lane" :key="p.t" class="poetry-item">
              <span class="poetry-text">{{ p.t }}</span>
              <span class="poetry-src">{{ p.s }}</span>
            </span>
            <!-- 复制一份同样的内容，配合 translateX(-50%) 实现无缝循环；读屏时隐藏 -->
            <span v-for="p in lane" :key="`copy-${p.t}`" class="poetry-item poetry-copy" aria-hidden="true">
              <span class="poetry-text">{{ p.t }}</span>
              <span class="poetry-src">{{ p.s }}</span>
            </span>
          </div>
        </div>
      </FadeIn>
      <FadeIn from="up" :delay="340" :duration="700" :distance="22">
        <div class="hero-buttons">
          <button class="btn btn-primary" type="button" @click="$router.push('/login')">
            开启学途
            <svg class="btn-arrow" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M2.5 8h11M9.5 4l4 4-4 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <!-- 核心功能：href="#features" 复用 InfoCards 的文档点击拦截，点击滑入第二屏抽屉 -->
          <a class="btn btn-secondary" href="#features">核心功能</a>
        </div>
      </FadeIn>
    </div>
    <FadeIn as="div" class="hero-visual" from="up" :delay="200" :duration="900" :distance="36">
      <MountainIllustration />
      <!-- 悬浮数据卡片：玻璃质感，叠在插画边缘制造层次 -->
      <div class="float-card float-card--focus wp-float-slow">
        <div class="float-card-head">
          <span class="float-card-title">今日专注</span>
          <span class="float-card-trend">↑ 12%</span>
        </div>
        <div class="float-card-value">2.5<em>h</em></div>
        <div class="float-card-bar">
          <span class="float-card-bar-fill" style="width: 68%"></span>
        </div>
        <div class="float-card-foot">已完成今日目标的 68%</div>
      </div>
      <!-- AI 建议小胶囊 -->
      <div class="ai-pill wp-float">
        <span class="ai-dot"></span>
        AI 学习建议已更新
      </div>
    </FadeIn>
    <!-- 上滑提示：抽屉收起时呼吸出现，抽屉打开后隐去 -->
    <div class="scroll-hint" aria-hidden="true">
      <span class="scroll-hint__text">上滑探索学途</span>
      <svg class="scroll-hint__chev" viewBox="0 0 16 16" fill="none">
        <path d="M3.5 10L8 5.5 12.5 10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>
  </section>
</template>

<script setup>
import MountainIllustration from './MountainIllustration.vue'
import FadeIn from '../FadeIn.vue'

// 首屏弹幕文案：与“学途”主题呼应的诗词句（立志 / 勤学 / 读书 / 行路）
// 前两条“知途”为项目原有文案，一并保留进轮播池
const poetryLanes = [
  [
    { t: '既明前路，何惧道远。', s: '知途' },
    { t: '路漫漫其修远兮，吾将上下而求索。', s: '屈原 · 离骚' },
    { t: '长风破浪会有时，直挂云帆济沧海。', s: '李白 · 行路难' },
    { t: '会当凌绝顶，一览众山小。', s: '杜甫 · 望岳' },
    { t: '欲穷千里目，更上一层楼。', s: '王之涣 · 登鹳雀楼' },
    { t: '山重水复疑无路，柳暗花明又一村。', s: '陆游 · 游山西村' },
    { t: '以书为舟，以勤为楫，纵山高水长，亦可抵理想之彼岸。', s: '知途' },
  ],
  [
    { t: '黑发不知勤学早，白首方悔读书迟。', s: '颜真卿 · 劝学' },
    { t: '纸上得来终觉浅，绝知此事要躬行。', s: '陆游 · 冬夜读书示子聿' },
    { t: '问渠那得清如许？为有源头活水来。', s: '朱熹 · 观书有感' },
    { t: '读书破万卷，下笔如有神。', s: '杜甫 · 奉赠韦左丞丈' },
    { t: '三更灯火五更鸡，正是男儿读书时。', s: '颜真卿 · 劝学' },
    { t: '书山有路勤为径，学海无涯苦作舟。', s: '增广贤文' },
  ],
  [
    { t: '既怀凌云之志，当有跬步之行。', s: '知途' },
    { t: '宝剑锋从磨砺出，梅花香自苦寒来。', s: '警世贤文' },
    { t: '千磨万击还坚劲，任尔东西南北风。', s: '郑燮 · 竹石' },
    { t: '业精于勤，荒于嬉；行成于思，毁于随。', s: '韩愈 · 进学解' },
    { t: '莫等闲，白了少年头，空悲切。', s: '岳飞 · 满江红' },
    { t: '少壮不努力，老大徒伤悲。', s: '汉乐府 · 长歌行' },
    { t: '不经一番寒彻骨，怎得梅花扑鼻香。', s: '黄檗 · 上堂开示颂' },
  ],
]
</script>

<style scoped>
.hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 60px;
  /* 首屏刚好一面：视口高 - 吸顶导航 */
  min-height: calc(100vh - var(--wp-nav-h));
  min-height: calc(100svh - var(--wp-nav-h));
  max-width: var(--wp-max-w);
  margin: 0 auto;
  /* 底部偏置：抽屉半开时，首屏内容尽量停留在可视上半区 */
  padding: 8px var(--wp-gutter) 9vh;
  /* 抽屉打开时首屏轻微后退，强化层次 */
  transform: scale(calc(1 - 0.018 * var(--sheet-open, 0)));
  transform-origin: 50% 0%;
}

.hero-content {
  flex: 1;
  max-width: 560px;
}

/* ---------- 视觉区 ---------- */
.hero-visual {
  position: relative;
  flex-shrink: 0;
  width: 440px;
}

/* ---------- 标签 ---------- */
.hero-label {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 26px;
}

.label-line {
  display: inline-block;
  width: 34px;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--wp-accent));
}

.label-line:last-child {
  background: linear-gradient(90deg, var(--wp-accent), transparent);
}

.hero-label span:nth-child(2) {
  font-size: 12.5px;
  letter-spacing: 3.5px;
  color: var(--wp-accent);
  font-weight: 600;
  white-space: nowrap;
}

/* ---------- 标题 ---------- */
.hero-title {
  font-size: clamp(40px, 4.6vw, 54px);
  font-weight: 700;
  line-height: 1.28;
  letter-spacing: 1.5px;
  color: var(--wp-ink);
  margin: 0 0 26px;
}

.title-accent {
  position: relative;
  display: inline-block;
  background: linear-gradient(120deg, #b25a38 10%, #d97b52 55%, #e8a37e 90%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

/* 手绘感下划线 */
.title-swash {
  position: absolute;
  left: 2%;
  bottom: -0.12em;
  width: 96%;
  height: 0.16em;
}

/* ---------- 诗词弹幕 ---------- */
.poetry-danmaku {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 0 0 38px;
  overflow: hidden;
  /* 两端渐隐，弹幕“飘出”而不是被硬切 */
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);
  mask-image: linear-gradient(90deg, transparent, #000 7%, #000 93%, transparent);
}

.poetry-lane {
  display: flex;
  align-items: baseline;
  gap: 64px;
  width: max-content;
  white-space: nowrap;
  animation: wp-danmaku var(--lane-d, 48s) linear infinite;
  animation-delay: var(--lane-delay, 0s);
}

/* 三条泳道不同速、不同相位，避免整齐划一的机械感 */
.poetry-lane--1 {
  --lane-d: 48s;
  --lane-delay: -6s;
}

.poetry-lane--2 {
  --lane-d: 64s;
  --lane-delay: -23s;
}

.poetry-lane--3 {
  --lane-d: 55s;
  --lane-delay: -41s;
}

.poetry-item {
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
}

.poetry-text {
  font-family: 'Liu Jian Mao Cao', 'Xingkai SC', 'STXingkai', 'KaiTi', 'STKaiti', cursive;
  font-size: 21px;
  line-height: 1.4;
  color: var(--wp-ink-soft);
}

.poetry-src {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--wp-muted);
}

/* 减少动态偏好：轮播退化为静态多行排版，隐藏复制副本 */
@media (prefers-reduced-motion: reduce) {
  .poetry-danmaku {
    overflow: visible;
    -webkit-mask-image: none;
    mask-image: none;
  }

  .poetry-lane {
    animation: none;
    flex-wrap: wrap;
    white-space: normal;
    width: auto;
    row-gap: 8px;
  }

  .poetry-copy {
    display: none;
  }
}

/* ---------- 按钮 ---------- */
.hero-buttons {
  display: flex;
  gap: 16px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 15px 34px;
  border-radius: 999px;
  text-decoration: none;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 2px;
  cursor: pointer;
  transition:
    transform 0.3s var(--wp-ease-out),
    box-shadow 0.3s ease,
    background 0.3s ease,
    border-color 0.3s ease,
    color 0.3s ease;
}

.btn-primary {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #cf7150 0%, #b25a38 100%);
  color: #fff;
  box-shadow: var(--wp-shadow-accent);
}

/* 按钮悬停扫光 */
.btn-primary::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 45%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  transform: translateX(-130%) skewX(-18deg);
  pointer-events: none;
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(201, 106, 69, 0.38);
}

.btn-primary:hover::after {
  animation: wp-sheen 0.9s ease;
}

.btn-primary:active {
  transform: translateY(0);
}

.btn-arrow {
  width: 15px;
  height: 15px;
  transition: transform 0.3s var(--wp-ease-out);
}

.btn-primary:hover .btn-arrow {
  transform: translateX(4px);
}

.btn-secondary,
.btn-secondary:link,
.btn-secondary:visited,
.btn-secondary:hover,
.btn-secondary:active,
.btn-secondary:focus {
  text-decoration: none;
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.5);
  color: var(--wp-ink-soft);
  border: 1px solid var(--wp-line-strong);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.btn-secondary:hover {
  border-color: var(--wp-accent);
  color: var(--wp-accent);
  background: rgba(201, 106, 69, 0.05);
  transform: translateY(-2px);
}

/* ---------- 上滑提示 ---------- */
.scroll-hint {
  position: absolute;
  left: 50%;
  bottom: 26px;
  transform: translateX(-50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  color: var(--wp-muted);
  font-size: 12px;
  letter-spacing: 2.5px;
  white-space: nowrap;
  pointer-events: none;
  opacity: calc(1 - var(--sheet-open, 0));
}

.scroll-hint__chev {
  width: 16px;
  height: 16px;
  animation: wp-hint-bounce 1.9s ease-in-out infinite;
}

@keyframes wp-hint-bounce {
  0%,
  100% {
    transform: translateY(3px);
    opacity: 0.45;
  }
  50% {
    transform: translateY(-3px);
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .scroll-hint__chev {
    animation: none;
  }
}

/* ---------- 悬浮卡片 ---------- */
.float-card {
  position: absolute;
  left: -28px;
  bottom: 34px;
  width: 178px;
  padding: 16px 18px;
  border-radius: var(--wp-radius-md);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(16px) saturate(1.4);
  -webkit-backdrop-filter: blur(16px) saturate(1.4);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: var(--wp-shadow-md);
}

.float-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.float-card-title {
  font-size: 12px;
  color: var(--wp-muted);
  letter-spacing: 1.5px;
}

.float-card-trend {
  font-size: 11px;
  font-weight: 600;
  color: #2e9e6b;
  letter-spacing: 0.5px;
}

.float-card-value {
  font-size: 26px;
  font-weight: 700;
  color: var(--wp-ink);
  line-height: 1.1;
  margin-bottom: 10px;
}

.float-card-value em {
  font-style: normal;
  font-size: 14px;
  color: var(--wp-muted);
  font-weight: 600;
  margin-left: 2px;
}

.float-card-bar {
  height: 5px;
  border-radius: 999px;
  background: rgba(34, 48, 60, 0.08);
  overflow: hidden;
  margin-bottom: 8px;
}

.float-card-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--wp-accent), var(--wp-accent-soft));
}

.float-card-foot {
  font-size: 11px;
  color: var(--wp-muted);
  letter-spacing: 0.5px;
}

/* ---------- AI 小胶囊 ---------- */
.ai-pill {
  position: absolute;
  top: 26px;
  right: -18px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  box-shadow: var(--wp-shadow-sm);
  font-size: 12px;
  font-weight: 600;
  color: var(--wp-slate-deep);
  letter-spacing: 1px;
  white-space: nowrap;
}

.ai-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #2e9e6b;
  animation: wp-breathe 2.4s ease-in-out infinite;
}

/* ---------- 响应式 ---------- */
@media (max-width: 960px) {
  .hero {
    flex-direction: column;
    justify-content: flex-start;
    gap: 26px;
    padding: 4vh 24px 28px;
  }

  .hero-content {
    max-width: 100%;
  }

  .hero-visual {
    width: 100%;
    max-width: 300px;
  }

  .poetry-text {
    font-size: 18px;
  }

  .poetry-lane {
    gap: 44px;
  }

  .float-card {
    left: 0;
    bottom: 20px;
  }

  .ai-pill {
    right: 0;
  }

  .scroll-hint {
    display: none;
  }
}

@media (max-width: 480px) {
  .hero-title {
    font-size: 34px;
    margin-bottom: 18px;
  }

  .hero-label {
    margin-bottom: 18px;
  }

  .poetry-text {
    font-size: 16px;
  }

  .poetry-src {
    font-size: 11px;
  }

  .poetry-danmaku {
    gap: 8px;
    margin-bottom: 26px;
  }

  .hero-buttons {
    flex-wrap: wrap;
  }

  .hero-visual {
    max-width: 240px;
  }

  .ai-pill {
    display: none;
  }
}

/* 矮屏：收紧留白保证首屏内容完整 */
@media (max-height: 640px) {
  .hero {
    gap: 20px;
    padding-bottom: 20px;
  }

  .hero-title {
    font-size: 36px;
    margin-bottom: 16px;
  }

  /* 矮屏只留两条泳道，压缩纵向空间 */
  .poetry-danmaku {
    gap: 8px;
    margin-bottom: 24px;
  }

  .poetry-lane--3 {
    display: none;
  }

  .scroll-hint {
    display: none;
  }
}
</style>
