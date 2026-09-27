import { createRouter, createWebHashHistory } from "vue-router";
import EventHomePage from "./pages/EventHomePage.vue";
import WorkspaceHubPage from "./pages/WorkspaceHubPage.vue";
import SettingsPage from "./pages/SettingsPage.vue";
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", redirect: "/home" },
    { path: "/home", name: "home", component: EventHomePage },
    { path: "/workspace", name: "workspace", component: WorkspaceHubPage },
    { path: "/settings", name: "settings", component: SettingsPage },
    { path: "/dashboard", redirect: "/workspace" },
    { path: "/articles", redirect: "/workspace?tab=articles" },
    { path: "/events", redirect: (to) => ({ path: "/workspace", query: { ...to.query, tab: "events" } }) },
    { path: "/qa", redirect: (to) => ({ path: "/workspace", query: { ...to.query, tab: "qa" } }) },
    { path: "/operations", redirect: "/settings?tab=operations" },
    { path: "/:pathMatch(.*)*", redirect: "/home" },
  ],
  scrollBehavior: () => ({ top: 0 }),
});
