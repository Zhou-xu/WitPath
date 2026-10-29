<script setup lang="ts">
/** 表单字段容器：label + 控件插槽 + 错误提示 */
defineProps<{ label?: string; error?: string; required?: boolean; hint?: string }>()
</script>

<template>
  <label class="field">
    <span v-if="label" class="field__label">
      {{ label }}
      <span v-if="required" aria-hidden="true">*</span>
    </span>
    <slot />
    <span v-if="error" class="field__error">{{ error }}</span>
    <span v-else-if="hint" class="text-xs text-muted">{{ hint }}</span>
  </label>
</template>
