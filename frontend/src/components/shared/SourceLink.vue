<script setup>
import { computed } from "vue";
import { ExternalLink } from "lucide-vue-next";
import { safeUrl } from "../../lib/display.js";
const props = defineProps({
  url: String,
  title: { type: String, default: "打开原文" },
});
const href = computed(() => safeUrl(props.url));
</script>
<template>
  <a
    v-if="href"
    :href="href"
    target="_blank"
    rel="noopener noreferrer"
    class="source-link"
    >{{ title }}<ExternalLink :size="14"
  /></a>
  <span v-else class="muted">未提供原文链接</span>
</template>
