<script setup>
import { computed, watch } from "vue";
import Button from "../ui/Button.vue";
const props = defineProps({
  total: Number,
  size: { type: Number, default: 12 },
});
const page = defineModel({ default: 1 });
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)));
watch(pages, (n) => {
  if (page.value > n) page.value = n;
});
</script>
<template>
  <nav class="pagination" aria-label="分页">
    <span>共 {{ total }} 项 · 第 {{ page }} / {{ pages }} 页</span>
    <div class="toolbar-actions">
      <Button variant="outline" size="sm" :disabled="page <= 1" @click="page--"
        >上一页</Button
      >
      <Button
        variant="outline"
        size="sm"
        :disabled="page >= pages"
        @click="page++"
        >下一页</Button
      >
    </div>
  </nav>
</template>
