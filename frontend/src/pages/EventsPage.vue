<script setup>
import { computed, inject, ref, watch, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ListFilter, RefreshCw, Search, SlidersHorizontal } from "lucide-vue-next";
import Button from "../components/ui/Button.vue";
import EmptyState from "../components/shared/EmptyState.vue";
import RequestState from "../components/shared/RequestState.vue";
import Pager from "../components/shared/Pager.vue";
import EventWorkbenchDetail from "../components/events/EventWorkbenchDetail.vue";
import { useResource } from "../composables/useResource.js";
import { api } from "../services/api.js";
import { dateTime, statusLabel, subjectTypeLabel } from "../lib/display.js";

const { events, workspace, openAuth } = inject("app");
const evidenceRequested = ref(false);
const route = useRoute();
const router = useRouter();
const query = ref("");
const status = ref("");
const page = ref(1);
const detail = useResource(api.eventDetail, null);

function subjectsOf(event) {
  return event.subjects?.length ? event.subjects : (event.entities || []).slice(0, 1).map((name) => ({ name, type: "UNKNOWN", role: "PRIMARY" }));
}
function primarySubject(event) { return subjectsOf(event)[0] || { name: "主体待确认", type: "UNKNOWN" }; }
const filtered = computed(() => events.data.value.filter((event) => {
  const haystack = [event.name, ...subjectsOf(event).map((subject) => subject.name), ...(event.entities || [])].join(" ").toLowerCase();
  return (!status.value || event.status === status.value) && haystack.includes(query.value.trim().toLowerCase());
}));
const rows = computed(() => filtered.value.slice((page.value - 1) * 14, page.value * 14));
const selectedSummary = computed(() => rows.value.find(event => event.id === route.query.event) || rows.value[0] || null);
const selected = computed(() => selectedSummary.value);
const evidence = computed(() => workspace.data.value && evidenceRequested.value ? detail.data.value?.articles || [] : []);
watch([query, status], () => { page.value = 1; });
// Resolve deep links before the visible page watcher selects a fallback.
watch([() => route.query.event, filtered], ([id, items]) => {
  const index = items.findIndex(event => event.id === id);
  if (index >= 0) page.value = Math.floor(index / 14) + 1;
}, { immediate: true });
watch(rows, items => {
  if (items.length && !items.some(event => event.id === route.query.event)) select(items[0]);
}, { immediate: true });
watch(() => selected.value?.id, () => { evidenceRequested.value = false; detail.reset(); });
watch(() => workspace.data.value?.id, () => { evidenceRequested.value = false; detail.reset(); });
onBeforeUnmount(() => detail.reset());
async function loadEvidence() {
  if (!workspace.data.value) return openAuth();
  if (!selected.value) return;
  evidenceRequested.value = true;
  await detail.load(selected.value.id);
}
async function refresh() {
  await events.load();
  if (workspace.data.value && evidenceRequested.value && selected.value) await loadEvidence();
}
function select(event) { router.replace({ name: "workspace", query: { tab: "events", event: event.id } }); }
function openQa() {
  if (!workspace.data.value) return openAuth();
  if (selected.value) router.push({ name: "workspace", query: { tab: "qa", event: selected.value.id } });
}
</script>
<template>
  <section class="event-workbench page-stack">
    <header class="workbench-toolbar">
      <div><h2>事件追踪</h2></div>
      <div class="workbench-toolbar-actions"><span class="workbench-count"><b>{{ filtered.length }}</b> 个事件</span><Button variant="outline" :disabled="events.loading.value" @click="refresh"><RefreshCw :size="15" />刷新</Button></div>
    </header>
    <RequestState :loading="events.loading.value" :error="events.error.value" :updated-at="events.updatedAt.value" @retry="refresh" />
    <div class="event-filter-strip">
      <label class="event-search"><Search :size="15" /><span class="sr-only">搜索事件</span><input v-model="query" type="search" placeholder="搜索主体、事件或关联实体" /></label>
      <label class="event-status"><SlidersHorizontal :size="14" /><span class="sr-only">事件状态</span><select v-model="status"><option value="">全部状态</option><option value="EMERGING">首次报道</option><option value="ONGOING">持续更新</option></select></label>
      <span class="filter-note"><ListFilter :size="13" />按最新更新时间排序</span>
    </div>
    <div class="event-workbench-grid">
      <aside class="event-rail" aria-label="事件目录">
        <div class="rail-heading"><span>事件目录</span><small>最新更新</small></div>
        <div class="rail-list">
          <button v-for="event in rows" :key="event.id" class="rail-event" :class="{ selected: route.query.event === event.id }" :aria-pressed="route.query.event === event.id" @click="select(event)">
            <span class="rail-marker"></span><span class="rail-copy"><span class="rail-subject"><b>{{ primarySubject(event).name }}</b><em>{{ subjectTypeLabel(primarySubject(event).type) }}</em></span><strong>{{ event.name }}</strong><small>{{ statusLabel(event.status) }} · {{ event.articleIds?.length || 0 }} 篇 · {{ dateTime(event.lastUpdatedAt) }}</small></span><span class="rail-arrow">›</span>
          </button>
        </div>
        <EmptyState v-if="!rows.length && !events.loading.value" title="没有匹配的事件" description="尝试清空搜索或更换状态。" />
        <Pager v-model="page" :total="filtered.length" :size="14" />
      </aside>
      <div class="event-stage">

        <EventWorkbenchDetail v-if="selected" :event="selected" :articles="evidence" :authenticated="!!workspace.data.value" :evidence-requested="evidenceRequested" :loading="detail.loading.value" :error="detail.error.value" @evidence="loadEvidence" @ask="openQa" />
        <EmptyState v-else title="选择一个事件" description="从左侧事件目录选择主体，开始查看事件脉络。" />
      </div>
    </div>
  </section>
</template>
