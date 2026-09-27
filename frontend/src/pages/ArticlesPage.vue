<script setup>
import { computed, inject, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { RefreshCw, Search, ArrowUpRight, Layers } from "lucide-vue-next";
import Button from "../components/ui/Button.vue";
import EmptyState from "../components/shared/EmptyState.vue";
import RequestState from "../components/shared/RequestState.vue";
import Pager from "../components/shared/Pager.vue";
import ArticleFeedItem from "../components/articles/ArticleFeedItem.vue";
import { useResource } from "../composables/useResource.js";
import { api } from "../services/api.js";
import { dateTime, statusLabel } from "../lib/display.js";
const { events } = inject("app");
const router = useRouter();
const articles = useResource(api.listArticles, []);
const query = ref("");
const source = ref("");
const page = ref(1);
const sources = computed(() => [...new Set(articles.data.value.map(a => a.source).filter(Boolean))].sort());
const eventIndex = computed(() => {
  const index = new Map();
  for (const event of events.data.value) for (const id of event.articleIds || []) if (!index.has(id)) index.set(id, event);
  return index;
});
const filtered = computed(() => articles.data.value.filter(a =>
  (!source.value || a.source === source.value) && [a.title, a.summary, a.source].filter(Boolean).join(" ").toLowerCase().includes(query.value.trim().toLowerCase())
).sort((a, b) => (Date.parse(b.publishTime) || 0) - (Date.parse(a.publishTime) || 0)));
const rows = computed(() => filtered.value.slice((page.value - 1) * 12, page.value * 12));
const recentEvents = computed(() => events.data.value.slice(0, 5));
function openEvent(event) { router.push({ name: "workspace", query: { tab: "events", event: event.id } }); }
function clearFilters() { query.value = ""; source.value = ""; }
async function refresh() { await Promise.all([articles.load(), events.load()]); }
watch([query, source], () => { page.value = 1; });
onMounted(articles.load);
</script>
<template>
  <section class="news-home">
    <header class="news-masthead">
      <h1>来源文章</h1>
      <Button variant="outline" :disabled="articles.loading.value || events.loading.value" @click="refresh"><RefreshCw :size="15" />刷新情报</Button>
    </header>
    <div class="news-layout">
      <div class="news-main">
        <form class="news-filters" role="search" @submit.prevent>
          <label class="news-search"><Search :size="18" /><span class="sr-only">搜索文章</span><input v-model="query" type="search" placeholder="搜索标题、关键词或来源" /></label>
          <label class="news-source"><span class="sr-only">来源</span><select v-model="source"><option value="">全部来源</option><option v-for="name in sources" :key="name">{{ name }}</option></select></label>
        </form>
        <div class="news-section-heading"><h2>{{ query || source ? '筛选结果' : '最新报道' }}</h2><span>{{ filtered.length }} 篇 · 按发布时间排序</span><button v-if="query || source" type="button" @click="clearFilters">清空筛选</button></div>
        <RequestState :loading="articles.loading.value" :error="articles.error.value" @retry="articles.load" />
        <div class="news-feed" :aria-busy="articles.loading.value">
          <ArticleFeedItem v-for="(article, index) in rows" :key="article.id" :article="article" :event="eventIndex.get(article.id)" :featured="page === 1 && index === 0 && !query && !source" @open-event="openEvent" />
          <EmptyState v-if="!rows.length && !articles.loading.value && !articles.error.value" :title="query || source ? '没有找到相关报道' : '暂时没有报道'" :description="query || source ? '试试其他关键词，或清空来源筛选。' : '采集到的公开报道会出现在这里。'" />
        </div>
        <Pager v-if="filtered.length" v-model="page" :total="filtered.length" />
      </div>
      <aside class="news-context" aria-label="最近更新的事件">
        <div class="news-context-heading"><Layers :size="17" /><h2>事件观察</h2><span>{{ events.data.value.length }}</span></div>

        <RequestState :loading="events.loading.value" :error="events.error.value" @retry="events.load" />
        <button v-for="(event, index) in recentEvents" :key="event.id" class="news-event" type="button" @click="openEvent(event)">
          <span class="news-event-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="news-event-copy"><span v-if="event.subjects?.length" class="news-event-subject">{{ event.subjects[0].name }}</span><strong>{{ event.name }}</strong><small>{{ event.articleIds?.length || 0 }} 篇报道 · {{ statusLabel(event.status) }}</small><time>{{ dateTime(event.lastUpdatedAt) }}</time></span><ArrowUpRight :size="15" />
        </button>
        <p v-if="!recentEvents.length && !events.loading.value && !events.error.value" class="news-context-intro">暂无事件</p>
        <Button variant="outline" class="news-workbench-link" @click="router.push({name: 'workspace', query: {tab: 'events'}})">进入事件工作台<ArrowUpRight :size="15" /></Button>

      </aside>
    </div>
  </section>
</template>
<style scoped>
.news-home { --news-line: var(--surface-inset); color: var(--ink); }
.news-masthead { display:flex; align-items:center; justify-content:space-between; gap:24px; padding:12px 0 32px; border-bottom:1px solid var(--border); margin-bottom:28px; }
.news-masthead h1 { font-size:clamp(28px,3.2vw,44px); line-height:1.35; font-weight:650; letter-spacing:-.04em; margin:0; }
.news-masthead h1 span { color:var(--accent-text); }
.news-masthead > .ui-button { flex-shrink:0; }
.news-layout { display:grid; grid-template-columns:minmax(0,1fr) 280px; gap:clamp(24px,3vw,48px); align-items:start; }
.news-main { min-width:0; }
.news-filters { display:flex; gap:10px; padding-bottom:24px; }
.news-search { display:flex; align-items:center; gap:10px; flex:1; min-width:0; padding:0 14px; background:var(--surface); border:1px solid var(--border); border-radius:8px; }
.news-search:focus-within { outline:2px solid var(--muted); outline-offset:2px; }
.news-search input { border:0; box-shadow:none; background:transparent; padding:12px 0; font-size:14px; outline:none; }
.news-source { width:145px; flex-shrink:0; }
.news-source select { height:46px; border-radius:8px; }
.news-section-heading { display:flex; align-items:baseline; flex-wrap:wrap; gap:12px; padding:0 0 15px; border-bottom:1px solid var(--news-line); }
.news-section-heading h2 { font-size:17px; margin:0; font-weight:650; }
.news-section-heading > span { font-size:12px; color:var(--muted); }
.news-section-heading button { margin-left:auto; border:0; background:transparent; color:var(--muted); font-size:12px; text-decoration:underline; }
.news-feed { min-width:0; }
.news-main :deep(.pagination) { padding:22px 0; }
.news-context { border-left:1px solid var(--news-line); padding-left:24px; min-width:0; }
.news-context-heading { margin-bottom:18px; display:flex; align-items:center; gap:8px; }
.news-context-heading h2 { font-size:16px; margin:0; }
.news-context-heading > span { margin-left:auto; font:12px ui-monospace,monospace; color:var(--muted); }
.news-context-intro { font-size:12px; line-height:1.9; color:var(--muted); margin:14px 0 20px; }
.news-event { display:flex; width:100%; gap:10px; text-align:left; padding:19px 0; border:0; border-top:1px solid var(--news-line); background:transparent; color:inherit; }
.news-event:hover strong { color:var(--accent-text); }
.news-event-index { font:11px ui-monospace,monospace; color:var(--muted); padding-top:3px; }
.news-event-copy { display:grid; gap:8px; min-width:0; flex:1; }
.news-event-copy strong { font-size:14px; line-height:1.75; font-weight:550; overflow-wrap:anywhere; }
.news-event-subject { font-size:11px; color:var(--accent-text); }
.news-event-copy small,.news-event-copy time { font-size:11px; color:var(--muted); }
.news-event > svg { margin-top:4px; color:var(--muted); }
.news-workbench-link { margin-top:14px; width:100%; justify-content:space-between; }
@media(max-width:1100px) { .news-layout { grid-template-columns:minmax(0,1fr); } .news-context { border-left:0; border-top:1px solid var(--news-line); padding:24px 0 0; } }
@media(max-width:600px) { .news-masthead { align-items:flex-start; flex-direction:column; padding-top:0; gap:18px; } .news-filters { flex-wrap:wrap; } .news-search { flex-basis:100%; } .news-source { width:100%; } .news-section-heading > span { font-size:11px; } }
</style>
