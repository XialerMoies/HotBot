<script setup>
import { computed, inject, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import Button from "../components/ui/Button.vue";
import PageHeader from "../components/shared/PageHeader.vue";
import Panel from "../components/shared/Panel.vue";
import RequestState from "../components/shared/RequestState.vue";
import EmptyState from "../components/shared/EmptyState.vue";
import SourceLink from "../components/shared/SourceLink.vue";
import { api } from "../services/api.js";
import { dateTime } from "../lib/display.js";
const { events, workspace, openAuth } = inject("app");
const route = useRoute();
const router = useRouter();
const eventId = computed({
  get: () => String(route.query.event || ""),
  set: (id) => router.replace({ name: "workspace", query: id ? { tab: "qa", event: id } : { tab: "qa" } }),
});
const selected = computed(() =>
  events.data.value.find((e) => e.id === eventId.value),
);
const question = ref("");
const answer = ref(null);
const error = ref("");
const asking = ref(false);
let generation = 0;
let controller;
function reset() {
  generation++;
  controller?.abort();
  asking.value = false;
  answer.value = null;
  error.value = "";
}
watch(eventId, reset);
watch(() => workspace.data.value?.id, () => { reset(); question.value = ""; });
onBeforeUnmount(reset);
async function ask() {
  if (!workspace.data.value) return openAuth();
  if (!selected.value || !question.value.trim() || asking.value) return;
  const current = ++generation;
  controller = new AbortController();
  asking.value = true;
  answer.value = null;
  error.value = "";
  try {
    const result = await api.answer(
      eventId.value,
      question.value.trim(),
      controller.signal,
    );
    if (generation === current) answer.value = result;
  } catch (err) {
    if (generation === current)
      error.value = api.message(err, "问答失败，请重试。");
  } finally {
    if (generation === current) asking.value = false;
  }
}
</script>
<template>
  <section class="page-stack">
    <PageHeader
      title="证据问答"

    />
    <RequestState
      :loading="events.loading.value"
      :error="events.error.value"
      @retry="events.load"
    />
    <section v-if="!workspace.data.value" class="signin-state panel"><h2>登录后基于证据提问</h2><p class="muted">选择事件、核对来源并查看回答。</p><Button @click="openAuth()">登录 / 注册</Button></section>
    <template v-else>
    <Panel title="提问范围">
      <label class="field"
        >选择事件<select v-model="eventId">
          <option value="">请选择一个事件</option>
          <option
            v-for="event in events.data.value"
            :key="event.id"
            :value="event.id"
          >
            {{ event.name }}
          </option>
        </select></label
      >
      <p v-if="selected" class="muted">
        {{ selected.articleIds?.length || 0 }} 篇关联报道 · 最近更新
        {{ dateTime(selected.lastUpdatedAt) }}
      </p>
      <form class="question-form" @submit.prevent="ask">
        <label class="field"
          >你的问题<textarea
            v-model="question"
            required
            rows="3"
            :disabled="!selected"
            placeholder="例如：发生了什么？后续有什么进展？"
            @keydown.ctrl.enter.prevent="ask"
          ></textarea>
        </label>
        <div class="question-actions">
          <div class="question-presets">
            <Button
              v-for="prompt in [
                '发生了什么？',
                '为什么重要？',
                '后续有什么进展？',
              ]"
              :key="prompt"
              variant="outline"
              size="sm"
              :disabled="!selected || asking"
              @click="question = prompt"
              >{{ prompt }}</Button
            >
          </div>
          <Button
            type="submit"
            :disabled="!selected || !question.trim() || asking"
            >{{ asking ? "正在检索和生成…" : "提交问题" }}</Button
          >
        </div>
      </form>
    </Panel>
    <RequestState :error="error" @retry="ask" />
    <p v-if="asking" role="status" class="feedback">
      正在核对事件原文，请稍候。切换事件会取消当前请求。
    </p>
    <div v-if="answer" class="answer-layout">
      <Panel title="回答"
        ><span v-if="answer.fallback" class="pill">检索降级回答</span>
        <p class="answer-text">{{ answer.answer }}</p>
        <p v-if="answer.fallback" class="muted">
          当前结果来自检索原文；证据不足时应继续核对来源。
        </p></Panel
      >
      <Panel :title="'原文证据 · ' + (answer.evidence?.length || 0)">
        <article
          v-for="(item, index) in answer.evidence"
          :key="item.id"
          class="evidence-item"
        >
          <small>证据 {{ index + 1 }} · {{ item.id }}</small>
          <h3>{{ item.title || "来源报道" }}</h3>
          <blockquote>{{ item.quote || item.text }}</blockquote>
          <SourceLink :url="item.url" /><small
            v-if="item.published_at || item.publishedAt"
            >{{ dateTime(item.published_at || item.publishedAt) }}</small
          >
        </article>
        <EmptyState
          v-if="!answer.evidence?.length"
          title="暂无支持证据"
          description="当前事件没有可检索原文，不能据此作出事实结论。"
        />
      </Panel>
    </div>
    <EmptyState
      v-else-if="!asking && !error"
      title="从一个具体问题开始"
      description="提交后在这里查看回答、证据片段与原文链接。"
    />
    </template>
  </section>
</template>
