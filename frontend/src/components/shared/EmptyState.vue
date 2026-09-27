<script setup>
defineProps({
  title: { type: String, default: "暂无数据" },
  description: String,
});
</script>

<template>
  <div class="empty-state">
    <slot name="icon" />
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>
