<script setup>
import { computed, inject, ref } from "vue";
import { RefreshCw } from "lucide-vue-next";
import Button from "../components/ui/Button.vue";
import PageHeader from "../components/shared/PageHeader.vue";
import RequestState from "../components/shared/RequestState.vue";
import EmptyState from "../components/shared/EmptyState.vue";
import MetricCard from "../components/shared/MetricCard.vue";
import { dateTime, statusLabel } from "../lib/display.js";
const { sources } = inject("app");
const state = ref("");
const rows = computed(() =>
  sources.data.value.filter((s) => !state.value || s.state === state.value),
);
</script>
<template>
  <section class="page-stack">
    <PageHeader
      title="任务监控"
      ><template #actions
        ><Button
          variant="outline"
          :disabled="sources.loading.value"
          @click="sources.load"
          ><RefreshCw :size="16" />{{
            sources.loading.value ? "刷新中…" : "刷新状态"
          }}</Button
        ></template
      ></PageHeader
    >
    <div class="metric-grid three">
      <MetricCard
        v-for="item in [
          { value: 'SUCCESS', label: '采集成功' },
          { value: 'RUNNING', label: '执行中' },
          { value: 'FAILED', label: '执行失败' },
        ]"
        :key="item.value"
        :label="item.label"
        :value="sources.data.value.filter((s) => s.state === item.value).length"
        hint="按数据源统计"
      />
    </div>
    <div class="filter-toolbar">
      <label
        >运行状态<select v-model="state">
          <option value="">全部状态</option>
          <option value="SUCCESS">成功</option>
          <option value="RUNNING">运行中</option>
          <option value="FAILED">失败</option>
        </select></label
      >
      <p class="muted">刷新仅获取最新记录；采集由后台定时任务执行。</p>
    </div>
    <RequestState
      :loading="sources.loading.value"
      :error="sources.error.value"
      :updated-at="sources.updatedAt.value"
      @retry="sources.load"
    />
    <section class="panel">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>数据源</th>
              <th>状态</th>
              <th>尝试次数</th>
              <th>最近运行</th>
              <th>错误记录</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="source in rows" :key="source.name">
              <td>
                <strong>{{ source.name }}</strong>
              </td>
              <td>
                <span class="pill" :class="source.state?.toLowerCase()">{{
                  statusLabel(source.state)
                }}</span>
              </td>
              <td>{{ source.attempts }}</td>
              <td>{{ dateTime(source.lastRunAt) }}</td>
              <td class="error-cell">{{ source.lastError || "无错误" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-if="!rows.length && !sources.loading.value && !sources.error.value"
        title="没有匹配的运行记录"
        description="可更换状态筛选或稍后刷新。"
      />
    </section>
  </section>
</template>
