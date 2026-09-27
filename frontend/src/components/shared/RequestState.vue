<script setup>
import Button from "../ui/Button.vue";
defineProps({ loading: Boolean, error: String, updatedAt: String });
defineEmits(["retry"]);
</script>
<template>
  <div v-if="error" class="feedback error" role="alert">
    <span>{{ error }}</span
    ><Button variant="outline" size="sm" @click="$emit('retry')">重试</Button>
  </div>
  <p v-else-if="loading" role="status" class="feedback">正在加载…</p>
  <p v-else-if="updatedAt" role="status" class="sync-note">数据已刷新</p>
</template>
