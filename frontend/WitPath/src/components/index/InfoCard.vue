<template>
  <div class="info-card" :class="{ 'info-card--dark': dark }">
    <!-- 顶部渐变线：悬停时展开 -->
    <span class="info-card__bar" aria-hidden="true"></span>
    <!-- 序号水印 -->
    <span class="info-card__index" aria-hidden="true">{{ indexLabel }}</span>
    <div class="info-card-label">{{ label }}</div>
    <div class="info-card-value">
      {{ value }}
      <svg class="info-card__arrow" viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <path d="M2.5 8h11M9.5 4l4 4-4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>
    <div class="info-card-desc">{{ desc }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: String, required: true },
  desc: { type: String, required: true },
  /** 序号（1 起），展示为 01 / 02 形式 */
  index: { type: Number, default: 0 },
  dark: { type: Boolean, default: false },
})

const indexLabel = computed(() => (props.index > 0 ? String(props.index).padStart(2, '0') : ''))
</script>

<style scoped>
.info-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 30px 26px 28px;
  border-radius: var(--wp-radius-md);
  background: rgba(255, 255, 255, 0.66);
  backdrop-filter: blur(14px) saturate(1.3);
  -webkit-backdrop-filter: blur(14px) saturate(1.3);
  border: 1px solid rgba(255, 255, 255, 0.75);
  box-shadow: var(--wp-shadow-sm);
  overflow: hidden;
  cursor: pointer;
  transition:
    transform 0.4s var(--wp-ease-out),
    box-shadow 0.4s ease,
    border-color 0.4s ease,
    background 0.4s ease;
}

/* 顶部渐变线：从左展开 */
.info-card__bar {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, var(--wp-accent), var(--wp-accent-soft));
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.5s var(--wp-ease-out);
}

/* 序号水印：衬线数字，悬停点亮 */
.info-card__index {
  position: absolute;
  top: 18px;
  right: 22px;
  font-family: var(--wp-font-serif);
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  color: rgba(34, 48, 60, 0.07);
  transition: color 0.4s ease;
  user-select: none;
}

.info-card-label {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 2px;
  color: var(--wp-slate);
  margin-bottom: 14px;
  transition: color 0.4s ease;
}

.info-card-value {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18.5px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--wp-ink);
  margin-bottom: 12px;
  transition: color 0.4s ease;
}

/* 悬停时滑出的箭头 */
.info-card__arrow {
  width: 15px;
  height: 15px;
  color: var(--wp-accent);
  opacity: 0;
  transform: translateX(-4px);
  transition:
    opacity 0.35s ease,
    transform 0.35s var(--wp-ease-out);
}

.info-card-desc {
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--wp-body);
  transition: color 0.4s ease;
}

/* ---------- 悬停态 ---------- */
.info-card:hover {
  transform: translateY(-6px);
  border-color: rgba(201, 106, 69, 0.28);
  background: rgba(255, 255, 255, 0.85);
  box-shadow: var(--wp-shadow-lg);
}

.info-card:hover .info-card__bar {
  transform: scaleX(1);
}

.info-card:hover .info-card__index {
  color: rgba(201, 106, 69, 0.22);
}

.info-card:hover .info-card-label {
  color: var(--wp-accent);
}

.info-card:hover .info-card__arrow {
  opacity: 1;
  transform: translateX(0);
}

/* ---------- 深色变体（供后续深色区块复用） ---------- */
.info-card--dark {
  background: linear-gradient(165deg, rgba(51, 80, 95, 0.92), rgba(34, 48, 60, 0.94));
  border-color: rgba(255, 255, 255, 0.14);
  box-shadow: var(--wp-shadow-md);
}

.info-card--dark .info-card-label {
  color: rgba(255, 255, 255, 0.6);
}

.info-card--dark .info-card-value {
  color: #fff;
}

.info-card--dark .info-card-desc {
  color: rgba(255, 255, 255, 0.72);
}

.info-card--dark .info-card__index {
  color: rgba(255, 255, 255, 0.1);
}

.info-card--dark:hover .info-card__index {
  color: rgba(232, 163, 126, 0.3);
}

.info-card--dark:hover .info-card-label {
  color: var(--wp-accent-soft);
}

.info-card--dark:hover .info-card-desc {
  color: rgba(255, 255, 255, 0.85);
}
</style>
