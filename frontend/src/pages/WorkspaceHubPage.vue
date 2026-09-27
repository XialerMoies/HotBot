<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { TabsContent, TabsList, TabsRoot, TabsTrigger } from "reka-ui";
import DashboardPage from "./DashboardPage.vue";
import EventsPage from "./EventsPage.vue";
import ArticlesPage from "./ArticlesPage.vue";
import QaPage from "./QaPage.vue";

const route = useRoute();
const router = useRouter();
const allowed = new Set(["overview", "events", "qa", "articles"]);
const active = computed(() => allowed.has(route.query.tab) ? route.query.tab : "events");
function select(tab) {
  const query = { ...route.query, tab };
  if (tab !== "events" && tab !== "qa") delete query.event;
  router.push({ name: "workspace", query });
}
</script>
<template>
  <section class="hub-page page-stack">
    <div class="hub-heading">
      <h1>工作台</h1>
    </div>
    <TabsRoot :model-value="active" class="hub-tabs" @update:model-value="select">
      <TabsList class="tab-list industrial-tabs" aria-label="工作台内容">
        <TabsTrigger value="overview">态势概览</TabsTrigger>
        <TabsTrigger value="events">事件追踪</TabsTrigger>
        <TabsTrigger value="articles">来源文章</TabsTrigger>
        <TabsTrigger value="qa">证据问答</TabsTrigger>
      </TabsList>
      <TabsContent value="overview"><DashboardPage /></TabsContent>
      <TabsContent value="events"><EventsPage /></TabsContent>
      <TabsContent value="articles"><ArticlesPage /></TabsContent>
      <TabsContent value="qa"><QaPage /></TabsContent>
    </TabsRoot>
  </section>
</template>
