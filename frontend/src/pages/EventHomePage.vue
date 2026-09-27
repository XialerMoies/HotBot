<script setup>
import { computed, inject, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { RefreshCw, Search, ArrowUpRight } from 'lucide-vue-next';
import Button from '../components/ui/Button.vue';
import RequestState from '../components/shared/RequestState.vue';
import EmptyState from '../components/shared/EmptyState.vue';
import Pager from '../components/shared/Pager.vue';
import EventHomeCard from '../components/events/EventHomeCard.vue';
const { events, workspace, openAuth } = inject('app');
const router = useRouter();
const query = ref(''), mode = ref('all'), page = ref(1);
const follows = computed(() => workspace.data.value?.follows || []);
const filtered = computed(() => events.data.value.filter(event => {
  const text = [event.name, ...(event.entities || []), ...(event.subjects || []).map(s => s.name)].join(' ').toLowerCase();
  return text.includes(query.value.trim().toLowerCase()) && (mode.value !== 'following' || follows.value.some(f => text.includes(f.value.toLowerCase()))) && (mode.value !== 'ongoing' || event.status === 'ONGOING');
}));
const rows = computed(() => filtered.value.slice((page.value - 1) * 8, page.value * 8));
const unread = computed(() => (workspace.data.value?.notifications || []).filter(n => !n.read).length);
function select(value) { if (value === 'following' && !workspace.data.value) return openAuth(); mode.value = value; }
watch([query, mode], () => page.value = 1);
watch(() => workspace.data.value, value => { if (!value && mode.value === 'following') mode.value = 'all'; });
</script>
<template>
<section class="event-home">
  <header class="eh-masthead"><h1>事件动态</h1><Button variant="outline" :disabled="events.loading.value" @click="events.load()"><RefreshCw :size="16" />刷新情报</Button></header>
  <div class="eh-filters"><div class="eh-tabs" aria-label="事件筛选"><Button v-for="item in [{id:'all',label:'全部事件'},{id:'ongoing',label:'持续更新'},{id:'following',label:'我的关注'}]" :key="item.id" variant="ghost" :class="{selected:mode===item.id}" :aria-pressed="mode===item.id" @click="select(item.id)">{{ item.label }}</Button></div><label class="eh-search"><Search :size="16"/><span class="sr-only">搜索主体或事件</span><input v-model="query" type="search" placeholder="搜索主体或事件" /></label><span class="eh-total">{{ filtered.length }} 个事件</span></div>
  <div class="eh-layout"><div class="eh-feed"><RequestState :loading="events.loading.value" :error="events.error.value" @retry="events.load()" /><EventHomeCard v-for="event in rows" :key="event.id" :event="event" /><EmptyState v-if="!rows.length && !events.loading.value && !events.error.value" title="暂无匹配事件" :description="mode==='following'?'关注主体后，相关事件会出现在这里。':'尝试其他关键词，或等待采集与聚类完成。'"/><Pager v-if="filtered.length" v-model="page" :total="filtered.length" :size="8" /></div>
  <aside class="eh-aside"><section><h2>我的观察范围</h2><template v-if="workspace.data.value"><div class="eh-count">{{ follows.length }}<small>个关注对象</small></div><p>{{ unread }} 条未读提醒</p><Button variant="outline" @click="router.push({name:'settings'})">管理关注与提醒<ArrowUpRight :size="15" /></Button></template><template v-else><p>登录后保存关注与订阅。</p><Button variant="outline" @click="openAuth">登录 / 注册</Button></template></section><section><h2>事件概况</h2><dl><div><dt>已聚合事件</dt><dd>{{ events.data.value.length }}</dd></div><div><dt>持续更新</dt><dd>{{ events.data.value.filter(e=>e.status==='ONGOING').length }}</dd></div></dl><Button variant="outline" @click="router.push({name:'workspace',query:{tab:'events'}})">进入工作台<ArrowUpRight :size="15" /></Button></section></aside></div>
</section>
</template>
<style scoped>
.event-home{color:#20242a}.eh-masthead{display:flex;align-items:center;justify-content:space-between;gap:20px;margin:8px 0 30px}.eh-masthead h1{font-size:clamp(32px,4vw,54px);letter-spacing:-2px;margin:0;font-weight:800}.eh-filters{display:flex;align-items:center;gap:16px;border-top:2px solid #191c20;border-bottom:1px solid #a9afb7;margin-bottom:28px;flex-wrap:wrap}.eh-tabs{display:flex}.eh-tabs .ui-button{background:transparent;border-radius:0;border:0;border-right:1px solid #a9afb7;padding:17px 20px;height:auto;color:#20242a}.eh-tabs .selected{background:#191c20;color:#fff;box-shadow:inset 0 -3px #ff5a1f}.eh-search{display:flex;gap:8px;align-items:center;flex:1;min-width:180px;margin:0}.eh-search input{border:0;background:transparent;padding:10px 0;box-shadow:none;min-width:0;width:100%;color:#20242a}.eh-total{font-size:12px;color:#626a75;padding-right:10px}.eh-layout{display:grid;grid-template-columns:minmax(0,1fr) 245px;gap:28px;align-items:start}.eh-feed{min-width:0}.eh-aside{background:#dce0e5;border:1px solid #a9afb7;border-top:4px solid #727a85;padding:20px}.eh-aside h2{font-size:16px}.eh-aside p{font-size:13px;color:#626a75}.eh-aside section+section{border-top:1px solid #a9afb7;margin-top:24px;padding-top:20px}.eh-aside .ui-button{width:100%;justify-content:space-between}.eh-count{font:48px Consolas,monospace}.eh-count small{font:12px 'Microsoft YaHei',sans-serif;margin-left:10px}.eh-aside dl div{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #bfc5ce;padding:12px 0}.eh-aside dd{font:24px Consolas,monospace}.eh-aside dt{font-size:13px}
@media(max-width:1100px){.eh-layout{grid-template-columns:1fr}.eh-aside{display:grid;grid-template-columns:1fr 1fr;gap:28px}.eh-aside section+section{border-top:0;border-left:1px solid #a9afb7;margin:0;padding:0 0 0 28px}}
@media(max-width:600px){.eh-masthead{align-items:flex-start}.eh-masthead h1{font-size:32px;letter-spacing:-1px}.eh-masthead>.ui-button{padding:8px}.eh-filters{gap:6px}.eh-tabs{width:100%}.eh-tabs .ui-button{flex:1;padding:14px 10px}.eh-search{padding-left:10px}.eh-aside{display:block}.eh-aside section+section{border:0;border-top:1px solid #a9afb7;margin-top:20px;padding:20px 0 0}}
</style>
