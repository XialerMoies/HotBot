<script setup>
defineProps({
  label: String,
  value: [String, Number],
  hint: String,
  tone: { type: String, default: "default" },
});
</script>

<template>
  <article class="metric-card" :class="`metric-card-${tone}`">
    <span>{{ label }}</span
    ><strong>{{ value }}</strong
    ><small>{{ hint }}</small>
  </article>
</template>
