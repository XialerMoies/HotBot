<script setup>
import { computed } from "vue";
import { dateTime, statusLabel, subjectTypeLabel } from "../../lib/display.js";
import SourceLink from "../shared/SourceLink.vue";
import Button from "../ui/Button.vue";
const props = defineProps({ event: Object, articles: Array });
defineEmits(["ask", "close"]);
const subjects = computed(() => props.event?.subjects?.length
  ? props.event.subjects
  : (props.event?.entities || []).slice(0, 1).map((name) => ({ name, type: "UNKNOWN", role: "PRIMARY", confidence: 0 })));
const primarySubject = computed(() => subjects.value[0] || { name: "主体待确认", type: "UNKNOWN", role: "PRIMARY", confidence: 0 });
const sourceCount = computed(() => new Set((props.articles || []).map(a => a.source).filter(Boolean)).size);
const sourceFor = (id) => (props.articles || []).find(a => a.id === id)?.source || '来源待确认';
</script>
<template>
  <section class="panel event-detail" aria-label="事件详情" tabindex="-1">
    <div class="detail-top">
      <span class="pill">{{ statusLabel(event.status) }}</span
      ><Button variant="ghost" size="sm" @click="$emit('close')"
        >关闭详情</Button
      >
    </div>
    <div class="event-subject-hero">
      <span class="eyebrow">EVENT SUBJECT / 主体</span>
      <strong>{{ primarySubject.name }}</strong>
      <span>{{ subjectTypeLabel(primarySubject.type) }} · {{ primarySubject.role === "PRIMARY" ? "主要主体" : "关联主体" }}</span>
    </div>
    <h2>{{ event.name }}</h2>
    <dl class="event-metadata">
      <div>
        <dt>首次报道</dt>
        <dd>{{ dateTime(event.firstSeenAt) }}</dd>
      </div>
      <div>
        <dt>最新更新</dt>
        <dd>{{ dateTime(event.lastUpdatedAt) }}</dd>
      </div>
      <div>
        <dt>关联文章</dt>
        <dd>{{ event.articleIds?.length || 0 }} 篇 · {{ sourceCount }} 个来源</dd>
      </div>
      <div>
        <dt>主体识别</dt>
        <dd>{{ primarySubject.type === 'UNKNOWN' ? '待确认' : '原文实体匹配' }}</dd>
      </div>
    </dl>
    <Button @click="$emit('ask')">围绕此事件提问</Button>
    <div class="detail-block">
      <h3>事件主体</h3>
      <div class="subject-list">
        <span v-for="subject in subjects" :key="subject.name" class="subject-chip">
          <b>{{ subject.name }}</b><small>{{ subjectTypeLabel(subject.type) }} · {{ subject.role === "PRIMARY" ? "主要主体" : "关联主体" }}</small>
        </span>
        <span v-if="!subjects.length" class="muted">主体待确认，需要更多结构化报道。</span>
      </div>
    </div>
    <div class="detail-block">
      <h3>关联实体</h3>
      <div class="token-list">
        <span v-for="entity in event.entities" :key="entity" class="token">{{
          entity
        }}</span
        ><span v-if="!event.entities?.length" class="muted">暂未提取实体</span>
      </div>
    </div>
    <div class="detail-block">
      <h3>事件时间线</h3>
      <ol class="event-timeline">
        <li v-for="node in event.timeline" :key="node.articleId">
          <small
            >{{ dateTime(node.occurredAt) }} ·
            {{ statusLabel(node.stage) }} · {{ sourceFor(node.articleId) }}</small
          >
          <p>{{ node.summary }}</p>
        </li>
      </ol>
    </div>
    <div class="detail-block">
      <h3>原文报道</h3>
      <article
        v-for="article in articles"
        :key="article.id"
        class="source-item"
      >
        <strong>{{ article.title }}</strong
        ><small
          >{{ article.source || "来源未标注" }} ·
          {{ dateTime(article.publishTime) }}</small
        ><SourceLink :url="article.url" />
      </article>
      <p v-if="!articles?.length" class="muted">
        该事件未保存可读取的原文，问答可能缺少证据。
      </p>
    </div>
  </section>
</template>
