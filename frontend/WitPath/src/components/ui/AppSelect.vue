<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface AppSelectOption {
  label: string
  value: string | number | null
}

const props = withDefaults(
  defineProps<{
    modelValue: string | number | null
    options: AppSelectOption[]
    placeholder?: string
    disabled?: boolean
    /** 控件尺寸：sm 对应 control--sm，默认 md 与 .control 一致 */
    size?: 'sm' | 'md'
    /** 选项较多时开启顶部搜索框（如模型列表），默认关闭以保持原有交互 */
    searchable?: boolean
    searchPlaceholder?: string
  }>(),
  { placeholder: '请选择', size: 'md', searchable: false, searchPlaceholder: '搜索…' },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number | null): void
  (e: 'change', value: string | number | null): void
}>()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const highlighted = ref(0)
const keyword = ref('')
const searchInput = ref<HTMLInputElement | null>(null)

const selected = computed(() => props.options.find((o) => o.value === props.modelValue))
const displayText = computed(() => selected.value?.label ?? '')

/** 搜索开启时按关键字过滤（忽略大小写），未开启则原样返回 */
const visibleOptions = computed(() => {
  const list = props.options ?? []
  const kw = keyword.value.trim().toLowerCase()
  if (!props.searchable || !kw) return list
  return list.filter((o) => String(o.label ?? '').toLowerCase().includes(kw))
})

function syncHighlight() {
  const idx = visibleOptions.value.findIndex((o) => o.value === props.modelValue)
  highlighted.value = idx >= 0 ? idx : 0
}

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    keyword.value = ''
    syncHighlight()
    if (props.searchable) {
      nextTick(() => searchInput.value?.focus())
    }
  }
}

function choose(option: AppSelectOption) {
  open.value = false
  if (option.value === props.modelValue) return
  emit('update:modelValue', option.value)
  emit('change', option.value)
}

function move(delta: number) {
  if (!open.value) return
  const total = visibleOptions.value.length
  if (total === 0) return
  highlighted.value = (highlighted.value + delta + total) % total
}

function onKeydown(event: KeyboardEvent) {
  if (props.disabled) return
  switch (event.key) {
    case 'Enter':
    case ' ':
      // 搜索框内空格需要正常输入，不拦截
      if (props.searchable && open.value && event.target === searchInput.value && event.key === ' ') return
      event.preventDefault()
      if (open.value) {
        const option = visibleOptions.value[highlighted.value]
        if (option) choose(option)
      }
      else open.value = true
      break
    case 'Escape':
      open.value = false
      break
    case 'ArrowDown':
      event.preventDefault()
      if (open.value) move(1)
      else open.value = true
      break
    case 'ArrowUp':
      event.preventDefault()
      if (open.value) move(-1)
      else open.value = true
      break
    case 'Tab':
      open.value = false
      break
  }
}

function onDocClick(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
}

watch(
  () => props.modelValue,
  () => {
    if (open.value) syncHighlight()
  },
)

// 关键字变化后重新对齐高亮项，避免指向被过滤掉的选项
watch(keyword, syncHighlight)

onMounted(() => document.addEventListener('mousedown', onDocClick))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocClick))
</script>

<template>
  <div ref="root" class="asel" :class="[`asel--${size}`, { 'asel--open': open }]">
    <button
      type="button"
      class="control asel__trigger"
      :disabled="disabled"
      :aria-haspopup="'listbox'"
      :aria-expanded="open"
      @click="toggle"
      @keydown="onKeydown"
    >
      <span class="asel__text" :class="{ 'asel__text--placeholder': !selected }">
        {{ selected ? displayText : placeholder }}
      </span>
      <svg class="asel__chevron" width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
        <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>

    <Transition name="asel-pop">
      <ul v-if="open" class="asel__menu" role="listbox">
        <li v-if="searchable" class="asel__search" @mousedown.prevent>
          <input
            ref="searchInput"
            v-model="keyword"
            class="asel__search-input"
            type="text"
            :placeholder="searchPlaceholder"
            @keydown="onKeydown"
          />
        </li>
        <li
          v-for="(option, index) in visibleOptions"
          :key="String(option.value)"
          class="asel__option"
          :class="{
            'asel__option--active': index === highlighted,
            'asel__option--selected': option.value === modelValue,
          }"
          role="option"
          :aria-selected="option.value === modelValue"
          @mouseenter="highlighted = index"
          @mousedown.prevent
          @click="choose(option)"
        >
          <span class="asel__option-label">{{ option.label }}</span>
          <svg v-if="option.value === modelValue" class="asel__check" width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
            <path d="M2 6.2 4.8 9 10 3.4" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </li>
        <li v-if="searchable && visibleOptions.length === 0" class="asel__empty">无匹配选项</li>
      </ul>
    </Transition>
  </div>
</template>

<style scoped>
.asel {
  position: relative;
  width: 100%;
}

.asel__trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  text-align: left;
  cursor: pointer;
  padding-right: 10px;
}

.asel__trigger:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.asel--open .asel__trigger {
  border-color: var(--c-accent-ring);
  box-shadow: 0 0 0 3px var(--c-accent-ring);
}

.asel--sm .asel__trigger {
  padding: 6px 10px;
  font-size: 13px;
}

.asel__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asel__text--placeholder {
  color: var(--c-text-muted, rgba(33, 29, 23, 0.45));
}

.asel__chevron {
  flex: none;
  color: rgba(33, 29, 23, 0.45);
  transition: transform var(--dur-fast) var(--ease);
}

.asel--open .asel__chevron {
  transform: rotate(180deg);
}

.asel__menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 60;
  max-height: 248px;
  overflow-y: auto;
  margin: 0;
  padding: 6px;
  list-style: none;
  background: var(--c-surface);
  border: 1px solid var(--c-border-strong);
  border-radius: var(--r-control);
  box-shadow: var(--shadow-md);
}

.asel__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: calc(var(--r-control) - 4px);
  font-size: 14px;
  color: var(--c-text);
  cursor: pointer;
  user-select: none;
}

/* 搜索框：吸在弹层顶部，随列表一起滚动会挡视线，因此用 sticky */
.asel__search {
  position: sticky;
  top: 0;
  padding: 6px;
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.asel__search-input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--c-border);
  border-radius: calc(var(--r-control) - 4px);
  background: var(--c-bg-subtle);
  font-size: 14px;
  color: var(--c-text);
}

.asel__search-input:focus {
  outline: none;
  border-color: var(--c-accent);
  background: var(--c-surface);
}

.asel__empty {
  padding: 10px;
  text-align: center;
  font-size: 13px;
  color: var(--c-text-muted);
}

.asel__option-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asel__option--active {
  background: var(--c-surface-muted);
}

.asel__option--selected {
  color: var(--c-accent);
  font-weight: 600;
  background: var(--c-accent-soft);
}

.asel__option--selected.asel__option--active {
  background: var(--c-accent-soft);
}

.asel__check {
  flex: none;
  color: var(--c-accent);
}

.asel-pop-enter-active,
.asel-pop-leave-active {
  transition: opacity 0.14s var(--ease), transform 0.14s var(--ease);
}

.asel-pop-enter-from,
.asel-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (prefers-reduced-motion: reduce) {
  .asel-pop-enter-active,
  .asel-pop-leave-active {
    transition: none;
  }

  .asel__chevron {
    transition: none;
  }
}
</style>
