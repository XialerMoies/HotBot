<script setup>
import { computed } from "vue";
import { ArrowUpRight, CircleHelp, Clock3, FileText, Link2 } from "lucide-vue-next";
import { dateTime, statusLabel, subjectTypeLabel } from "../../lib/display.js";
import SourceLink from "../shared/SourceLink.vue";
import RequestState from "../shared/RequestState.vue";
import Button from "../ui/Button.vue";

const props = defineProps({ event: Object, articles: Array, authenticated: Boolean, evidenceRequested: Boolean, loading: Boolean, error: String });
defineEmits(["ask", "evidence"]);
const subjects = computed(() => props.event?.subjects?.length
  ? props.event.subjects
  : (props.event?.entities || []).slice(0, 1).map((name) => ({ name, type: "UNKNOWN", role: "PRIMARY", confidence: 0 })));
const primary = computed(() => subjects.value[0] || { name: "主体待确认", type: "UNKNOWN", role: "PRIMARY", confidence: 0 });
const articleCount = computed(() => props.event?.articleIds?.length || props.articles?.length || 0);
</script>
<template>
  <section class="event-focus" aria-label="事件工作区" tabindex="-1">
    <div class="event-focus-header">
      <div class="event-status-line"><span class="pill">{{ statusLabel(event.status) }}</span></div>
      <div class="event-focus-actions"><Button variant="outline" size="sm" @click="$emit('ask')"><CircleHelp :size="14" />围绕事件提问</Button></div>
    </div>
    <div class="event-proposition">
      <h2>{{ event.name }}</h2>
      <p><strong>{{ primary.name }}</strong></p>
    </div>
    <div class="event-facts">
      <div><span>主体</span><strong>{{ primary.name }}</strong><small>{{ subjectTypeLabel(primary.type) }} · {{ primary.role === 'PRIMARY' ? '主要主体' : '关联主体' }}</small></div>
      <div><span>时间窗</span><strong>{{ dateTime(event.firstSeenAt).slice(0, 10) }}</strong><small>至 {{ dateTime(event.lastUpdatedAt).slice(0, 10) }}</small></div>
      <div><span>证据规模</span><strong>{{ articleCount }} <small>篇</small></strong><small>置信度 {{ event.confidence ? Math.round(event.confidence * 100) + '%' : '待评估' }}</small></div>
    </div>
    <div class="event-focus-grid">
      <section class="focus-section timeline-section">
        <div class="focus-section-title"><span><Clock3 :size="15" />发展时间线</span><small>{{ event.timeline?.length || 0 }} 个节点</small></div>
        <ol class="workbench-timeline">
          <li v-for="(node, index) in event.timeline" :key="node.articleId" :class="{ latest: index === event.timeline.length - 1 }">
            <div class="timeline-rail"><span>{{ String(index + 1).padStart(2, '0') }}</span></div>
            <div class="timeline-copy"><small>{{ dateTime(node.occurredAt) }} · {{ statusLabel(node.stage) }}</small><strong>{{ node.summary }}</strong></div>
          </li>
        </ol>
        <p v-if="!event.timeline?.length" class="workbench-empty">时间线仍在建立中，等待更多主体相关报道。</p>
      </section>
      <section class="focus-section subject-section">
        <div class="focus-section-title"><span><Link2 :size="15" />主体关系</span><small>{{ subjects.length }} 个主体</small></div>
        <div class="workbench-subjects">
          <div v-for="subject in subjects" :key="subject.name" class="workbench-subject" :class="{ primary: subject.role === 'PRIMARY' }"><span class="subject-node"></span><div><strong>{{ subject.name }}</strong><small>{{ subjectTypeLabel(subject.type) }} · {{ subject.role === 'PRIMARY' ? '主要主体' : '关联主体' }}</small></div><em>{{ subject.confidence ? Math.round(subject.confidence * 100) + '%' : '—' }}</em></div>
          <p v-if="!subjects.length" class="workbench-empty">主体待确认，需要更多结构化报道。</p>
        </div>
        <div class="related-entities"><span>关联实体</span><div><b v-for="entity in event.entities" :key="entity">{{ entity }}</b><small v-if="!event.entities?.length">暂无已提取实体</small></div></div>
      </section>
    </div>
  </section>
  <aside class="event-evidence" aria-label="事件证据包">
    <div class="evidence-header"><h3>来源证据</h3><strong>{{ articleCount }} 篇原文</strong></div>
    <div v-if="!authenticated || !evidenceRequested" class="evidence-intro"><p>{{ authenticated ? '按需加载该事件的来源报道。' : '登录后查看原文证据与引用。' }}</p><Button @click="$emit('evidence')">{{ authenticated ? '查看依据' : '登录查看证据' }}</Button></div>
    <template v-else>
    <RequestState :loading="loading" :error="error" @retry="$emit('evidence')" />
    <template v-if="!loading && !error">
    <article v-for="article in articles" :key="article.id" class="evidence-card">
      <div class="evidence-card-top"><span><FileText :size="14" />{{ article.source || '来源未标注' }}</span><small>{{ dateTime(article.publishTime) }}</small></div>
      <h3>{{ article.title || '来源报道' }}</h3>
      <p v-if="article.summary">{{ article.summary }}</p>
      <SourceLink :url="article.url" title="打开原文" />
    </article>
    <div v-if="!articles?.length" class="workbench-empty evidence-empty">当前事件没有可读取的原文证据。</div>
    </template>
    </template>
    <Button class="evidence-ask" @click="$emit('ask')"><ArrowUpRight :size="15" />进入证据问答</Button>
  </aside>
</template>
