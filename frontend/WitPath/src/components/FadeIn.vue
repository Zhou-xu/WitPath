<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

type FadeDirection = 'up' | 'down' | 'left' | 'right' | 'none'

const props = withDefaults(
  defineProps<{
    /** 渐入延迟（毫秒），用于错峰编排 */
    delay?: number
    /** 动画时长（毫秒） */
    duration?: number
    /** 渐入方向 */
    from?: FadeDirection
    /** 位移距离（像素） */
    distance?: number
    /** 缓动函数 */
    easing?: string
    /** 渲染的 HTML 标签，默认 div */
    as?: string
  }>(),
  {
    delay: 0,
    duration: 600,
    from: 'up',
    distance: 24,
    easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
    as: 'div',
  },
)

const visible = ref(false)
const done = ref(false)
let cleanupTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  // 双 requestAnimationFrame：确保浏览器先绘制初始隐藏态，
  // 再切换到可见态，过渡动画才会真正播放
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      visible.value = true
    })
  })
  // 动画结束后卸掉 will-change 与 transform，避免长期占用合成层，
  // 同时消除对内部 backdrop-filter 等效果的潜在影响
  cleanupTimer = setTimeout(() => {
    done.value = true
  }, props.delay + props.duration + 50)
})

onUnmounted(() => {
  if (cleanupTimer) {
    clearTimeout(cleanupTimer)
    cleanupTimer = null
  }
})

/** 隐藏态下的位移，由方向与距离决定 */
const hiddenTransform = computed(() => {
  const d = props.distance
  switch (props.from) {
    case 'down':
      return `translateY(-${d}px)`
    case 'left':
      return `translateX(${d}px)`
    case 'right':
      return `translateX(-${d}px)`
    case 'none':
      return 'none'
    default:
      return `translateY(${d}px)`
  }
})

const styleVars = computed(() => ({
  '--fade-delay': `${props.delay}ms`,
  '--fade-duration': `${props.duration}ms`,
  '--fade-easing': props.easing,
  '--fade-transform': hiddenTransform.value,
}))
</script>

<template>
  <component
    :is="as"
    class="fade-in"
    :class="{ 'fade-in--visible': visible, 'fade-in--done': done }"
    :style="styleVars"
  >
    <slot />
  </component>
</template>
