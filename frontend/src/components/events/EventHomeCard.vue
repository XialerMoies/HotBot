<script setup>
import { computed, inject, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { CollapsibleRoot, CollapsibleTrigger, CollapsibleContent } from 'reka-ui';
import { ChevronDown, ChevronUp, Plus, Check, MessageSquare, ArrowUpRight } from 'lucide-vue-next';
import Button from '../ui/Button.vue';
import SourceLink from '../shared/SourceLink.vue';
import RequestState from '../shared/RequestState.vue';
import { api } from '../../services/api.js';
import { dateTime, statusLabel } from '../../lib/display.js';
const props = defineProps({event:{type:Object,required:true}});
const {workspace,openAuth} = inject('app');
const router = useRouter();
const expanded = ref(true), evidenceOpen = ref(false), loading = ref(false), busy = ref(false), error = ref(''), articles = ref([]), selectedArticle = ref(null);
let generation = 0;
const subjects = computed(() => props.event.subjects?.length ? props.event.subjects : (props.event.entities || []).map(name=>({name,type:'keyword'})));
const primary = computed(() => subjects.value.find(s=>s.role==='PRIMARY') || subjects.value[0]);
const types = {COMPANY:'公司',PERSON:'人物',PRODUCT:'产品',TECHNOLOGY:'技术',ORGANIZATION:'机构',UNKNOWN:'主体',keyword:'主体'};
const timeline = computed(() => [...(props.event.timeline || [])].sort((a,b)=>(Date.parse(a.occurredAt)||0)-(Date.parse(b.occurredAt)||0)));
const visibleArticles = computed(() => selectedArticle.value ? articles.value.filter(a=>a.id===selectedArticle.value) : articles.value);
const followed = computed(() => workspace.data.value?.follows?.some(f=>f.value.toLowerCase()===primary.value?.name.toLowerCase()));
function requireLogin(){if(workspace.data.value)return true;openAuth();return false;}
async function showEvidence(articleId = null){
  if(!requireLogin())return;
  selectedArticle.value=articleId; evidenceOpen.value=true; error.value='';loading.value=true;
  const request=++generation, owner=workspace.data.value.id;
  try{const result=await api.eventDetail(props.event.id);if(request===generation && workspace.data.value?.id===owner)articles.value=result.articles || [];}
  catch(e){if(request===generation)error.value=api.message(e);}
  finally{if(request===generation)loading.value=false;}
}
async function follow(){
  if(!requireLogin() || !primary.value || followed.value || busy.value)return;
  busy.value=true;error.value='';const owner=workspace.data.value.id;
  const type=primary.value.type?.toLowerCase();
  try{const result=await api.follow({value:primary.value.name,type:['company','person','product','technology'].includes(type)?type:'keyword'});if(workspace.data.value?.id===owner)workspace.data.value=result;}
  catch(e){if(workspace.data.value?.id===owner)error.value=api.message(e);}
  finally{busy.value=false;}
}
function ask(){if(requireLogin())router.push({name:'workspace',query:{tab:'qa',event:props.event.id}});}
watch(()=>workspace.data.value?.id,()=>{generation++;articles.value=[];evidenceOpen.value=false;loading.value=false;error.value='';});
watch(()=>props.event.lastUpdatedAt,()=>{generation++;articles.value=[];evidenceOpen.value=false;loading.value=false;});
</script>
<template>
<CollapsibleRoot v-model:open="expanded" class="home-event">
  <CollapsibleTrigger class="he-head"><span class="he-subject"><span class="he-monogram">{{ primary?.name?.slice(0,1) || '—' }}</span><span><strong>{{ primary?.name || '主体待确认' }}</strong><small>{{ types[primary?.type] || '主体' }}<template v-if="subjects.length>1"> · {{ subjects.slice(1,3).map(s=>s.name).join(' / ') }}</template></small></span></span><span class="he-state" :class="{ongoing:event.status==='ONGOING'}">{{ statusLabel(event.status) }}</span><span class="he-toggle">{{ expanded?'收起':'展开' }}<ChevronUp v-if="expanded" :size="14"/><ChevronDown v-else :size="14"/></span><span class="he-title">{{ event.name }}</span></CollapsibleTrigger>
  <CollapsibleContent><div class="he-meta"><span>{{ dateTime(event.firstSeenAt) }} 首次出现</span><span>{{ dateTime(event.lastUpdatedAt) }} 更新</span><span>{{ event.articleIds?.length || 0 }} 篇报道</span></div>
    <div class="he-update"><b>最新进展</b><p>{{ timeline.at(-1)?.summary || '暂无已整理的进展。' }}</p></div>
    <div class="he-body"><h3>事件进展 <small>{{ timeline.length }} 个节点</small></h3><div class="he-timeline"><button v-for="(node,index) in timeline.slice(-3)" :key="node.articleId" class="he-step" :class="{latest:index===timeline.slice(-3).length-1}" @click="showEvidence(node.articleId)"><time>{{ dateTime(node.occurredAt) }}</time><strong>{{ node.summary }}</strong><span>{{ statusLabel(node.stage) }} <ArrowUpRight :size="13"/></span></button><p v-if="!timeline.length" class="he-empty">时间线尚未建立</p></div>
      <Button v-if="timeline.length>3" variant="ghost" @click="router.push({name:'workspace',query:{tab:'events',event:event.id}})">查看完整时间线（{{ timeline.length }}）<ArrowUpRight :size="14"/></Button>
      <RequestState :error="error" @retry="evidenceOpen?showEvidence(selectedArticle):follow()"/>
      <section v-if="evidenceOpen && workspace.data.value" class="he-evidence" aria-label="来源证据"><div class="he-evidence-title"><h3>来源证据</h3><Button variant="ghost" size="sm" @click="evidenceOpen=false">收起证据</Button></div><RequestState :loading="loading"/><template v-if="!loading && !error"><article v-for="article in visibleArticles" :key="article.id"><small>{{ article.source || '来源未标注' }} · {{ dateTime(article.publishTime) }}</small><h4>{{ article.title }}</h4><blockquote v-if="article.content">{{ article.content.slice(0,600) }}{{ article.content.length>600?'…':'' }}</blockquote><p v-else>暂无原文片段，请打开来源核对。</p><SourceLink :url="article.url" title="打开原文" /></article><p v-if="!visibleArticles.length">暂无可读取的来源证据。</p></template></section>
    </div>
    <div class="he-actions"><Button @click="showEvidence()">查看依据</Button><Button variant="outline" :disabled="busy || !primary || !!followed" @click="follow"><Check v-if="followed" :size="14"/><Plus v-else :size="14"/>{{ followed?'已关注':'关注主体' }}</Button><Button variant="outline" @click="ask"><MessageSquare :size="14"/>基于证据提问</Button></div>
  </CollapsibleContent>
</CollapsibleRoot>
</template>
<style scoped>
.home-event{border:1px solid #a9afb7;background:#f5f6f7;border-radius:3px;margin-bottom:24px;overflow:hidden;box-shadow:0 3px 0 #ccd0d6}.he-head{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:18px;width:100%;text-align:left;border:0;border-top:4px solid #727a85;background:#191c20;color:#f5f6f7;padding:22px 24px}.he-head:hover{background:#252a30}.he-subject{display:flex;align-items:center;gap:12px;min-width:0}.he-subject strong{font-size:17px}.he-subject small{display:block;color:#b8bec7;font-size:12px}.he-monogram{display:grid;place-items:center;width:43px;height:43px;flex-shrink:0;background:#3d434c;border:1px solid #727a85;font-size:20px}.he-state{align-self:center;color:#d2d7de;border:1px solid #626a75;padding:3px 8px;font-size:12px;white-space:nowrap}.he-state.ongoing{background:#ff5a1f;color:#191c20;border-color:#ff5a1f;font-weight:700}.he-toggle{display:flex;align-items:center;gap:5px;font-size:12px;color:#c9cfd7;border-left:1px solid #626a75;padding-left:12px}.he-title{grid-column:1/-1;font-size:clamp(21px,2.1vw,29px);font-weight:650;line-height:1.55;overflow-wrap:anywhere}.he-meta{display:flex;gap:10px 22px;flex-wrap:wrap;padding:12px 24px;background:#e8ebef;border-bottom:1px solid #bdc3cb;color:#626a75;font:12px/1.7 Consolas,'Microsoft YaHei',sans-serif}.he-update{display:grid;grid-template-columns:80px minmax(0,1fr);gap:16px;padding:22px 24px;border-bottom:1px solid #ccd1d7}.he-update b{font-size:12px;color:#b5360b;padding-top:3px}.he-update p{margin:0;font-size:15px;line-height:1.85;overflow-wrap:anywhere}.he-body{padding:20px 24px}.he-body h3{font-size:13px;display:flex;align-items:center;gap:10px}.he-body h3 small{margin-left:auto;font-weight:400}.he-timeline{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,180px),1fr));border:1px solid #bdc3cb;background:#eef0f3}.he-step{position:relative;display:flex;flex-direction:column;align-items:flex-start;gap:12px;border:0;border-right:1px solid #bdc3cb;background:transparent;padding:16px;text-align:left;min-width:0;color:#20242a}.he-step:last-child{border-right:0}.he-step:hover{background:#e1e5eb}.he-step time{font:12px/1.6 Consolas,monospace;color:#626a75}.he-step strong{font-size:14px;font-weight:500;line-height:1.7;overflow-wrap:anywhere}.he-step span{font-size:12px;color:#626a75;margin-top:auto;display:flex;align-items:center;gap:5px}.he-step.latest::before{content:'';position:absolute;top:0;left:16px;width:32px;height:3px;background:#ff5a1f}.he-step.latest time{color:#b5360b}.he-actions{display:flex;gap:8px;flex-wrap:wrap;padding:15px 24px;background:#e3e7ec;border-top:1px solid #bdc3cb}.he-actions .ui-button{border-radius:3px;font-size:13px}.he-actions .ui-button-default{background:#ff5a1f;border-color:#ff5a1f;color:#191c20}.he-evidence{border-left:3px solid #ff5a1f;padding:16px;background:#fff;margin-top:20px}.he-evidence-title{display:flex;align-items:center;justify-content:space-between}.he-evidence article+article{border-top:1px solid #bdc3cb;margin-top:16px;padding-top:16px}.he-evidence h4{margin:12px 0}.he-evidence blockquote{margin:12px 0;white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px}.he-empty{padding:16px;color:#626a75}
@media(max-width:680px){.he-head{padding:16px;grid-template-columns:1fr auto;gap:12px}.he-subject{grid-column:1/-1}.he-state{justify-self:start}.he-toggle{justify-self:end}.he-title{font-size:21px}.he-meta{padding:12px 16px}.he-update{padding:18px 16px;grid-template-columns:1fr;gap:6px}.he-body{padding:16px}.he-timeline{grid-template-columns:1fr}.he-step{border-right:0;border-bottom:1px solid #bdc3cb}.he-step:last-child{border-bottom:0}.he-actions{padding:14px 16px}.he-actions .ui-button{padding:8px 10px;font-size:12px}}
</style>
