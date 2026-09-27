<script setup>
import { ArrowRight } from 'lucide-vue-next';
import SourceLink from '../shared/SourceLink.vue';
import { dateTime, safeUrl } from '../../lib/display.js';
defineProps({ article: {type:Object, required:true}, event: Object, featured: Boolean });
defineEmits(['open-event']);
</script>
<template>
  <article class="feed-item" :class="{featured}">
    <div class="feed-meta"><span class="feed-source">{{ article.source || '来源未标注' }}</span><span aria-hidden="true">·</span><time :datetime="article.publishTime">{{ dateTime(article.publishTime) }}</time><span v-if="featured" class="feed-latest">最新发布</span></div>
    <h2><a v-if="safeUrl(article.url)" :href="safeUrl(article.url)" target="_blank" rel="noopener noreferrer">{{ article.title || '无标题' }}</a><span v-else>{{ article.title || '无标题' }}</span></h2>
    <p v-if="article.summary" class="feed-summary">{{ article.summary.trim() }}</p>
    <div v-if="event?.subjects?.length" class="feed-subjects"><span>相关主体</span><b v-for="subject in event.subjects.slice(0,3)" :key="subject.name">{{ subject.name }}</b></div>
    <footer><button v-if="event" class="feed-event-link" type="button" @click="$emit('open-event',event)"><span>追踪事件</span><ArrowRight :size="14" /><span class="feed-event-title">{{ event.name }}</span></button><span v-else class="feed-unlinked">尚未归入事件</span><SourceLink :url="article.url" title="阅读原文" /></footer>
  </article>
</template>
<style scoped>
.feed-item { padding:27px 0; border-bottom:1px solid var(--surface-inset); }
.feed-meta { display:flex; flex-wrap:wrap; align-items:center; gap:9px; font-size:12px; color:var(--muted); }
.feed-source { color:var(--muted); font-weight:600; }
.feed-latest { margin-left:auto; font-size:10px; color:var(--accent-text); letter-spacing:.07em; }
h2 { margin:13px 0 12px; font-size:19px; line-height:1.65; font-weight:600; letter-spacing:-.015em; overflow-wrap:anywhere; }
h2 a { color:var(--ink); text-decoration:none; }
h2 a:hover { color:var(--accent-text); text-decoration:underline; text-underline-offset:5px; }
.featured h2 { font-size:clamp(22px,2.2vw,28px); line-height:1.55; }
.feed-summary { color:var(--muted); font-size:14px; line-height:1.95; margin:0 0 16px; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; overflow-wrap:anywhere; }
.featured .feed-summary { -webkit-line-clamp:3; }
.feed-subjects { display:flex; flex-wrap:wrap; gap:8px; align-items:center; margin:16px 0; font-size:11px; }
.feed-subjects > span { color:var(--muted); margin-right:3px; }
.feed-subjects b { padding:3px 8px; border-radius:4px; background:var(--surface-inset); font-weight:500; color:var(--muted); }
footer { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:14px; }
.feed-event-link { display:flex; align-items:center; gap:7px; padding:0; border:0; background:transparent; color:var(--muted); font-size:12px; min-width:0; max-width:75%; }
.feed-event-link:hover { color:var(--accent-text); }
.feed-event-link > span:first-child { flex-shrink:0; }
.feed-event-title { overflow:hidden; white-space:nowrap; text-overflow:ellipsis; color:var(--muted); }
.feed-unlinked { font-size:11px; color:var(--muted); }
footer :deep(.source-link) { font-size:12px; white-space:nowrap; color:var(--muted); }
@media(max-width:600px) { h2 { font-size:18px; } .feed-meta { font-size:11px; } .feed-summary { font-size:13px; } .feed-event-title { display:none; } }
</style>
