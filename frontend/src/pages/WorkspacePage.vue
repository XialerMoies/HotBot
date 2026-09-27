<script setup>
import { inject, ref, watch } from "vue";
import { TabsRoot, TabsList, TabsTrigger, TabsContent } from "reka-ui";
import PageHeader from "../components/shared/PageHeader.vue";
import RequestState from "../components/shared/RequestState.vue";
import ConfirmDialog from "../components/shared/ConfirmDialog.vue";
import Button from "../components/ui/Button.vue";
import FollowPanel from "../components/workspace/FollowPanel.vue";
import SubscriptionPanel from "../components/workspace/SubscriptionPanel.vue";
import InboxPanel from "../components/workspace/InboxPanel.vue";
import DigestPanel from "../components/workspace/DigestPanel.vue";
import { api } from "../services/api.js";
const { workspace, openAuth } = inject("app");
const busy = ref(false);
const error = ref("");
const notice = ref("");
const active = ref("follows");
const pendingDelete = ref(null);
const followForm = ref();
const subscriptionForm = ref();
let generation = 0;
watch(() => workspace.data.value?.id, () => { generation++; busy.value = false; error.value = ""; notice.value = ""; pendingDelete.value = null; active.value = "follows"; });
async function run(action, message) {
  if (!workspace.data.value) { openAuth(); return false; }
  if (busy.value) return false;
  const current = ++generation;
  busy.value = true;
  error.value = "";
  notice.value = "";
  const previous = workspace.data.value?.id;
  try {
    const result = await action();
    if (current !== generation || workspace.data.value?.id !== previous) return false;
    workspace.data.value = result;
    notice.value = message;
    return true;
  } catch (err) {
    if (current === generation) error.value = api.message(err);
    return false;
  } finally {
    if (current === generation) busy.value = false;
  }
}
async function addFollow(body) {
  if (
    workspace.data.value.follows.some(
      (v) =>
        v.type === body.type &&
        v.value.toLowerCase() === body.value.toLowerCase(),
    )
  ) {
    error.value = "已关注该对象。";
    return;
  }
  if (await run(() => api.follow(body), "已添加关注。"))
    followForm.value?.clear();
}
async function addSubscription(body) {
  if (
    workspace.data.value.subscriptions.some(
      (v) => v.query.toLowerCase() === body.query.toLowerCase(),
    )
  ) {
    error.value = "该订阅已存在。";
    return;
  }
  if (await run(() => api.subscribe(body), "已添加订阅。"))
    subscriptionForm.value?.clear();
}
async function confirmDelete() {
  const item = pendingDelete.value;
  if (!item) return;
  if (
    await run(
      () =>
        item.kind === "follow"
          ? api.removeFollow(item.id)
          : api.removeSubscription(item.id),
      "已删除。",
    )
  )
    pendingDelete.value = null;
}
</script>
<template>
  <section class="page-stack">
    <PageHeader
      title="个人配置"
      :description="
        workspace.data.value?.name ||
        '登录后管理个人关注、订阅、提醒和每日摘要。'
      "
    />
    <RequestState
      :loading="workspace.loading.value"
      :error="workspace.error.value"
      @retry="workspace.load"
    />
    <section v-if="!workspace.data.value && !workspace.loading.value" class="signin-state panel">
      <h2>登录后管理你的情报范围</h2>
      <p class="muted">关注对象、订阅规则、提醒和每日摘要仅保存在个人工作区内。</p>
      <Button @click="openAuth()">登录 / 注册</Button>
    </section>
    <template v-if="workspace.data.value">
      <p v-if="error && !pendingDelete" role="alert" class="feedback error">
        {{ error }}
      </p>
      <p v-if="notice" role="status" class="feedback success">{{ notice }}</p>
      <TabsRoot
        v-model="active"
        class="workspace-tabs"
        @update:model-value="
          error = '';
          notice = '';
        "
      >
        <TabsList aria-label="工作区管理" class="tab-list">
          <TabsTrigger value="follows"
            >关注对象 ({{ workspace.data.value.follows.length }})</TabsTrigger
          >
          <TabsTrigger value="subscriptions"
            >订阅规则 ({{
              workspace.data.value.subscriptions.length
            }})</TabsTrigger
          >
          <TabsTrigger value="notifications"
            >提醒 ({{
              workspace.data.value.notifications.filter((n) => !n.read).length
            }})</TabsTrigger
          >
          <TabsTrigger value="digests">每日摘要</TabsTrigger>
        </TabsList>
        <TabsContent value="follows"
          ><FollowPanel
            ref="followForm"
            :items="workspace.data.value.follows"
            :busy="busy"
            @add="addFollow"
            @remove="
              pendingDelete = { ...$event, kind: 'follow' };
              error = '';
            "
        /></TabsContent>
        <TabsContent value="subscriptions"
          ><SubscriptionPanel
            ref="subscriptionForm"
            :items="workspace.data.value.subscriptions"
            :busy="busy"
            @add="addSubscription"
            @toggle="
              run(
                () => api.updateSubscription($event.id, !$event.enabled),
                $event.enabled ? '订阅已暂停。' : '订阅已恢复。',
              )
            "
            @remove="
              pendingDelete = { ...$event, kind: 'subscription' };
              error = '';
            "
        /></TabsContent>
        <TabsContent value="notifications"
          ><InboxPanel
            :items="workspace.data.value.notifications"
            :busy="busy"
            @read="run(() => api.readNotice($event), '已标记为已读。')"
        /></TabsContent>
        <TabsContent value="digests"
          ><DigestPanel
            :items="workspace.data.value.digests"
            :busy="busy"
            @generate="run(api.digest, '今日摘要已更新。')"
        /></TabsContent>
      </TabsRoot>
      <ConfirmDialog
        :open="!!pendingDelete"
        title="确认删除"
        :description="
          '将删除「' +
          (pendingDelete?.value || pendingDelete?.query || '') +
          '」。此操作无法撤销。'
        "
        :busy="busy"
        :error="error"
        @confirm="confirmDelete"
        @update:open="!$event && !busy && (pendingDelete = null)"
      />
    </template>
  </section>
</template>
