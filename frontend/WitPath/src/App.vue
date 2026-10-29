<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppToast from '@/components/ui/AppToast.vue'
</script>

<template>
  <RouterView v-slot="{ Component }">
    <Transition name="page" mode="out-in">
      <component :is="Component" />
    </Transition>
  </RouterView>

  <AppToast />
</template>
