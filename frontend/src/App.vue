<script setup>
import { computed, onMounted, onBeforeUnmount, provide, ref } from "vue";
import { useRoute, useRouter, RouterView } from "vue-router";
import { BookOpen, Factory, Settings2, X } from "lucide-vue-next";
import {
  DialogRoot,
  DialogClose,
  DialogPortal,
  DialogOverlay,
  DialogContent,
  DialogTitle,
  DialogDescription,
} from "reka-ui";
import AppSidebar from "./components/layout/AppSidebar.vue";
import AuthForm from "./components/workspace/AuthForm.vue";
import { api } from "./services/api.js";
import { useResource } from "./composables/useResource.js";
import { sortEvents } from "./lib/display.js";

const route = useRoute();
const router = useRouter();
const authOpen = ref(false);
const sessionNotice = ref("");
function expireSession() { workspace.reset(null); sessionNotice.value = "登录已失效，请重新登录。"; authOpen.value = true; }
function syncSession(event) { if (event.key === "hotbot-token" || event.key === null) { workspace.reset(null); workspace.load(); } }
const nav = [
  { id: "home", label: "首页", icon: BookOpen },
  { id: "workspace", label: "工作台", icon: Factory },
  { id: "settings", label: "设置", icon: Settings2 },
];
const events = useResource(async () => sortEvents(await api.listEvents()), []);
const sources = useResource(api.sources, []);
const workspace = useResource(api.workspace, null);
provide("app", { events, sources, workspace, openAuth: () => (authOpen.value = true) });
const unread = computed(
  () =>
    workspace.data.value?.notifications?.filter((item) => !item.read).length ||
    0,
);
function navigate(id) {
  router.push({ name: id });
}
function logout() {
  sessionNotice.value = "已退出登录。";
  api.logout();
  workspace.reset(null);
  navigate("home");
}
function authenticated(value) {
  workspace.reset(value);
  sessionNotice.value = "登录成功。";
  workspace.error.value = "";
  authOpen.value = false;
}
onMounted(() => {
  window.addEventListener('hotbot:session-expired', expireSession);
  window.addEventListener('storage', syncSession);
  Promise.all([events.load(), sources.load(), workspace.load()]);
});
onBeforeUnmount(() => { window.removeEventListener('hotbot:session-expired', expireSession); window.removeEventListener('storage', syncSession); });
</script>

<template>
  <div class="app-frame">
    <div class="app-brand" aria-label="HotBot 科技情报">HB</div>
      <AppSidebar
        :items="nav"
        :active="route.name"
        :workspace-name="workspace.data.value?.name"
        :unread="unread"
        :authenticated="!!workspace.data.value"
        @navigate="navigate"
        @account="authOpen = true"
        @logout="logout"
      />
    <main class="main-area">
      <div class="content"><div v-if="sessionNotice" class="session-notice" role="status"><span>{{ sessionNotice }}</span><button aria-label="关闭提示" @click="sessionNotice = ''"><X :size="16" /></button></div><RouterView /></div>
    </main>
    <DialogRoot v-model:open="authOpen">
      <DialogPortal>
        <DialogOverlay class="dialog-overlay" />
        <DialogContent class="dialog-content auth-dialog">
          <DialogClose class="dialog-close" aria-label="关闭登录窗口"><X :size="20" /></DialogClose>
          <DialogTitle class="dialog-title">登录 / 注册</DialogTitle>
          <DialogDescription class="dialog-description">登录后查看证据、提问并管理个人关注。</DialogDescription>
          <AuthForm v-if="authOpen" @authenticated="authenticated" />
        </DialogContent>
      </DialogPortal>
    </DialogRoot>
  </div>
</template>
