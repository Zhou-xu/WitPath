<script setup lang="ts">
import { computed, ref } from 'vue'

/** 统一头像展示：有地址显示图片，否则回退为昵称/用户名首字 */
const props = withDefaults(
  defineProps<{
    url?: string
    name?: string
    size?: number
    /** 圆形（默认）或圆角方形 */
    square?: boolean
  }>(),
  { size: 34, square: false },
)

const failed = ref(false)

const showImage = computed(() => Boolean(props.url) && !failed.value)
const initial = computed(() => (props.name || '同学').trim().charAt(0) || '同')
const diameter = computed(() => `${props.size}px`)
</script>

<template>
  <span
    class="avatar"
    :class="{ 'avatar--square': square }"
    :style="{ width: diameter, height: diameter, fontSize: `${Math.max(11, Math.round(size * 0.38))}px` }"
  >
    <img v-if="showImage" :src="url" :alt="name || '头像'" @error="failed = true" />
    <template v-else>{{ initial }}</template>
  </span>
</template>

<style scoped>
.avatar {
  display: grid;
  place-items: center;
  flex: none;
  overflow: hidden;
  border-radius: 50%;
  background: var(--c-accent);
  color: #fffaf4;
  font-weight: 600;
  line-height: 1;
  user-select: none;
}

.avatar--square {
  border-radius: var(--r-control);
  background: var(--c-accent-soft);
  color: var(--c-accent-hover);
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
