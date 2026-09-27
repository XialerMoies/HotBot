<script setup>
import { computed, inject } from "vue";
import { useRouter } from "vue-router";
import { ArrowRight, RefreshCw } from "lucide-vue-next";
import Button from "../components/ui/Button.vue";
import PageHeader from "../components/shared/PageHeader.vue";
import Panel from "../components/shared/Panel.vue";
import MetricCard from "../components/shared/MetricCard.vue";
import EmptyState from "../components/shared/EmptyState.vue";
import RequestState from "../components/shared/RequestState.vue";
import { dateTime, statusLabel } from "../lib/display.js";
const { events, sources, workspace } = inject("app");
const router = useRouter();
const count = computed(
  () => new Set(events.data.value.flatMap((e) => e.articleIds || [])).size,
);
const unread = computed(
  () => workspace.data.value?.notifications?.filter((n) => !n.read).length || 0,
);
async function refresh() {
  await Promise.all([events.load(), sources.load(), workspace.load()]);
}
</script>
<template>
  <section class="page-stack">
    <PageHeader
      title="态势概览"
      ><template #actions
        ><Button
          variant="outline"
          :disabled="events.loading.value || sources.loading.value"
          @click="refresh"
          ><RefreshCw :size="16" />刷新概览</Button
        ></template
      ></PageHeader
    >
    <RequestState
      :error="events.error.value || sources.error.value"
      :loading="events.loading.value || sources.loading.value"
      @retry="refresh"
    />
    <div class="metric-grid">
      <MetricCard
        label="追踪事件"
        :value="events.data.value.length"
        hint="已保存的事件集合"
      />
      <MetricCard label="关联文章" :value="count" hint="按文章编号去重" />
      <MetricCard
        label="未读提醒"
        :value="unread"
        :hint="workspace.data.value ? '查看工作区提醒' : '登录后查看个人提醒'"
      />
      <MetricCard
        label="采集成功"
        :value="
          sources.data.value.filter((s) => s.state === 'SUCCESS').length +
          ' / ' +
          sources.data.value.length
        "
        hint="最近一次运行状态"
      />
    </div>
    <div class="two-col">
      <Panel title="最近更新的事件" flush>
        <template #actions
          ><Button variant="ghost" @click="router.push('/workspace?tab=events')"
            >全部事件<ArrowRight :size="14" /></Button
        ></template>
        <div class="compact-list">
          <button
            v-for="event in events.data.value.slice(0, 6)"
            :key="event.id"
            @click="router.push({ name: 'workspace', query: { tab: 'events', event: event.id } })"
          >
            <span class="event-marker"></span
            ><span
              ><strong>{{ event.name }}</strong
              ><small
                >{{ statusLabel(event.status) }} ·
                {{ event.articleIds?.length || 0 }} 篇报道 ·
                {{ dateTime(event.lastUpdatedAt) }}</small
              ></span
            ><ArrowRight :size="16" />
          </button>
        </div>
        <EmptyState
          v-if="!events.data.value.length && !events.loading.value"
          title="暂无事件"
          description="采集并聚类后，最新事件会出现在这里。"
        />
      </Panel>
      <Panel title="采集源状态" flush>
        <template #actions
          ><Button variant="ghost" @click="router.push('/settings?tab=operations')"
            >任务监控<ArrowRight :size="14" /></Button
        ></template>
        <div class="health-list">
          <div v-for="source in sources.data.value" :key="source.name">
            <span class="health-dot" :class="source.state?.toLowerCase()"></span
            ><span>{{ source.name }}</span
            ><em>{{ statusLabel(source.state) }}</em>
          </div>
        </div>
        <EmptyState
          v-if="!sources.data.value.length && !sources.loading.value"
          title="暂无运行记录"
          description="采集任务运行后显示状态。"
        />
      </Panel>
    </div>
    <Panel title="我的关注"
      ><div class="token-list" v-if="workspace.data.value?.follows.length">
        <span
          class="token"
          v-for="item in workspace.data.value.follows"
          :key="item.id"
          >{{ item.value }}</span
        >
      </div>
      <p v-else class="muted">
        {{
          workspace.data.value
            ? "还没有关注对象。"
            : "登录后保存公司、人物、产品和技术关注。"
        }}
      </p>
      <Button variant="outline" @click="router.push('/settings')"
        >管理关注</Button
      ></Panel
    >
  </section>
</template>
