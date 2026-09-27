<script setup>
defineProps({ eyebrow: String, title: String, description: String });
</script>

<template>
  <div class="section-toolbar">
    <div>
      <span v-if="eyebrow" class="overline">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="toolbar-actions">
      <slot name="actions" />
    </div>
  </div>
</template>
