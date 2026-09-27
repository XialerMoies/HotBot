<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { TabsContent, TabsList, TabsRoot, TabsTrigger } from "reka-ui";
import WorkspacePage from "./WorkspacePage.vue";
import OperationsPage from "./OperationsPage.vue";

const route = useRoute();
const router = useRouter();
const allowed = new Set(["personal", "operations"]);
const active = computed(() => allowed.has(route.query.tab) ? route.query.tab : "personal");
function select(tab) {
  router.push({ name: "settings", query: tab === "personal" ? {} : { tab } });
}
</script>
<template>
  <section class="hub-page page-stack">
    <div class="hub-heading">
      <h1>设置</h1>
    </div>
    <TabsRoot :model-value="active" class="hub-tabs" @update:model-value="select">
      <TabsList class="tab-list industrial-tabs" aria-label="设置内容">
        <TabsTrigger value="personal">个人配置</TabsTrigger>
        <TabsTrigger value="operations">任务监控</TabsTrigger>
      </TabsList>
      <TabsContent value="personal"><WorkspacePage /></TabsContent>
      <TabsContent value="operations"><OperationsPage /></TabsContent>
    </TabsRoot>
  </section>
</template>
