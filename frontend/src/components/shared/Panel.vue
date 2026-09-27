<script setup>
defineProps({ title: String, eyebrow: String, flush: Boolean });
</script>

<template>
  <section class="panel" :class="{ 'panel-flush': flush }">
    <div v-if="title || eyebrow || $slots.actions" class="panel-head">
      <div>
        <span v-if="eyebrow" class="overline">{{ eyebrow }}</span>
        <h3 v-if="title">{{ title }}</h3>
      </div>
      <div v-if="$slots.actions" class="panel-actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="panel-body"><slot /></div>
  </section>
</template>
