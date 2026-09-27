import { describe, it, expect, vi, afterEach } from "vitest";
import { ref } from "vue";
import { mount, flushPromises } from "@vue/test-utils";
import { createRouter, createMemoryHistory } from "vue-router";
import { TabsRoot } from "reka-ui";
import { api } from "../src/services/api.js";
import { useResource } from "../src/composables/useResource.js";
import Button from "../src/components/ui/Button.vue";
import ArticlesPage from "../src/pages/ArticlesPage.vue";
import QaPage from "../src/pages/QaPage.vue";
import WorkspacePage from "../src/pages/WorkspacePage.vue";
import SourceLink from "../src/components/shared/SourceLink.vue";
import AppSidebar from "../src/components/layout/AppSidebar.vue";
import EventWorkbenchDetail from "../src/components/events/EventWorkbenchDetail.vue";
import { BookOpen, Factory, Settings2 } from "lucide-vue-next";
vi.mock("../src/services/api.js", () => ({
  api: {
    message: () => "服务暂不可用",
    listArticles: vi.fn(),
    answer: vi.fn(),
    updateSubscription: vi.fn(),
    follow: vi.fn(),
  },
}));
function deferred() {
  let resolve, reject;
  const promise = new Promise((a, b) => {
    resolve = a;
    reject = b;
  });
  return { promise, resolve, reject };
}
function resource(data) {
  return {
    data: ref(data),
    loading: ref(false),
    error: ref(""),
    load: vi.fn(),
  };
}
const wrappers = [];
function render(component, app, router) {
  const wrapper = mount(component, {
    global: { provide: { app }, plugins: router ? [router] : [] },
  });
  wrappers.push(wrapper);
  return wrapper;
}
async function routerAt(path) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: "/:pathMatch(.*)*",
        name: "qa",
        component: { template: "<div />" },
      },
    ],
  });
  await router.push(path);
  await router.isReady();
  return router;
}
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.clearAllMocks();
});
describe("交互回归", () => {
  it("Button 单次点击只触发一次操作，disabled 阻止重复点击", async () => {
    const click = vi.fn();
    const wrapper = mount(Button, {
      attrs: { onClick: click },
      slots: { default: "保存" },
    });
    wrappers.push(wrapper);
    await wrapper.trigger("click");
    expect(click).toHaveBeenCalledTimes(1);
    await wrapper.setProps({ disabled: true });
    await wrapper.trigger("click");
    expect(click).toHaveBeenCalledTimes(1);
  });
  it("Dock 将账户、首页、工作台和设置收束为固定入口", async () => {
    const wrapper = mount(AppSidebar, {
      props: {
        items: [
          { id: "home", label: "首页", icon: BookOpen },
          { id: "workspace", label: "工作台", icon: Factory },
          { id: "settings", label: "设置", icon: Settings2 },
        ],
        active: "home",
        authenticated: false,
        unread: 2,
      },
    });
    wrappers.push(wrapper);
    expect(wrapper.findAll(".dock-nav button")).toHaveLength(3);
    await wrapper.get(".account-dock-button").trigger("click");
    expect(wrapper.emitted("account")).toHaveLength(1);
    await wrapper.get(".dock-nav button:nth-child(2)").trigger("click");
    expect(wrapper.emitted("navigate")[0]).toEqual(["workspace"]);
  });
  it("登录态的头像按钮点击后退出，不再导航到独立账户页", async () => {
    const wrapper = mount(AppSidebar, {
      props: {
        items: [],
        workspaceName: "张三",
        authenticated: true,
      },
    });
    wrappers.push(wrapper);
    expect(wrapper.get(".avatar").text()).toBe("张");
    await wrapper.get(".account-dock-button").trigger("click");
    expect(wrapper.emitted("logout")).toHaveLength(1);
    expect(wrapper.emitted("account")).toBeUndefined();
  });
  it("事件工作台把主体、时间线和原文证据放在同一工作上下文", () => {
    const wrapper = mount(EventWorkbenchDetail, {
      props: {
        authenticated: true,
        evidenceRequested: true,
        event: {
          id: "event-1",
          name: "某公司发布新芯片",
          status: "ONGOING",
          firstSeenAt: "2026-09-26T08:00:00Z",
          lastUpdatedAt: "2026-09-26T10:00:00Z",
          confidence: 0.86,
          subjects: [{ name: "某公司", type: "COMPANY", role: "PRIMARY", confidence: 0.9 }],
          entities: ["新芯片"],
          articleIds: ["a-1"],
          timeline: [{ articleId: "a-1", occurredAt: "2026-09-26T08:00:00Z", stage: "FIRST_REPORT", summary: "公司发布芯片" }],
        },
        articles: [{ id: "a-1", title: "芯片发布原文", source: "科技来源", publishTime: "2026-09-26T08:00:00Z", url: "https://example.com/a-1" }],
      },
    });
    wrappers.push(wrapper);
    expect(wrapper.text()).toContain("某公司");
    expect(wrapper.text()).toContain("发展时间线");
    expect(wrapper.text()).toContain("芯片发布原文");
    expect(wrapper.find(".event-focus").exists()).toBe(true);
    expect(wrapper.find(".event-evidence").exists()).toBe(true);
  });
  it("慢请求不能覆盖后选事件，失败保留已有数据", async () => {
    const first = deferred(),
      second = deferred();
    const state = useResource(
      vi
        .fn()
        .mockReturnValueOnce(first.promise)
        .mockReturnValueOnce(second.promise)
        .mockRejectedValueOnce(new Error("offline")),
      [],
    );
    const a = state.load("old"),
      b = state.load("new");
    second.resolve(["new"]);
    await b;
    first.resolve(["old"]);
    await a;
    expect(state.data.value).toEqual(["new"]);
    await state.load();
    expect(state.error.value).toBeTruthy();
    expect(state.data.value).toEqual(["new"]);
  });
  it("文章库读取全部文章并按来源与搜索词筛选", async () => {
    api.listArticles.mockResolvedValue([
      { id: "a", title: "芯片发布", source: "A", url: "https://example.com/a" },
      { id: "b", title: "模型发布", source: "B" },
    ]);
    const wrapper = render(
      ArticlesPage,
      { events: resource([]) },
      await routerAt("/articles"),
    );
    await flushPromises();
    expect(wrapper.findAll("article.feed-item")).toHaveLength(2);
    await wrapper.find("select").setValue("B");
    expect(wrapper.findAll("article.feed-item")).toHaveLength(1);
    expect(wrapper.text()).toContain("模型发布");
    expect(wrapper.findAll("a")).toHaveLength(0);
    await wrapper.find("input").setValue("芯片");
    expect(wrapper.findAll("article.feed-item")).toHaveLength(0);
  });
  it("无原文或危险协议不会渲染假链接", () => {
    const wrapper = mount(SourceLink, {
      props: { url: "javascript:alert(1)" },
    });
    wrappers.push(wrapper);
    expect(wrapper.find("a").exists()).toBe(false);
    expect(wrapper.text()).toContain("未提供");
  });
  it("切换事件后丢弃旧问答，即使旧请求晚返回", async () => {
    const pending = deferred();
    api.answer.mockReturnValue(pending.promise);
    const router = await routerAt("/qa?event=a");
    const wrapper = render(
      QaPage,
      {
        workspace: resource({id: "u1"}), openAuth: vi.fn(),
        events: resource([
          { id: "a", name: "事件A" },
          { id: "b", name: "事件B" },
        ]),
      },
      router,
    );
    await wrapper.find("textarea").setValue("发生了什么");
    await wrapper.find("form").trigger("submit");
    expect(api.answer).toHaveBeenCalledTimes(1);
    await router.push("/qa?event=b");
    await flushPromises();
    pending.resolve({ answer: "旧事件的回答", evidence: [] });
    await flushPromises();
    expect(wrapper.text()).not.toContain("旧事件的回答");
    expect(wrapper.text()).not.toContain("正在核对");
  });
  it("订阅暂停失败显示错误且不篡改状态，重试后正确更新", async () => {
    const workspace = resource({
      id: "w",
      name: "验收工作区",
      follows: [],
      subscriptions: [{ id: "s", query: "AI", enabled: true }],
      notifications: [],
      digests: [],
    });
    const wrapper = render(WorkspacePage, { workspace });
    wrapper
      .findComponent(TabsRoot)
      .vm.$emit("update:modelValue", "subscriptions");
    await flushPromises();
    api.updateSubscription
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({
        ...workspace.data.value,
        subscriptions: [{ id: "s", query: "AI", enabled: false }],
      });
    const pause = () =>
      wrapper.findAll("button").find((b) => b.text() === "暂停");
    await pause().trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("服务暂不可用");
    expect(workspace.data.value.subscriptions[0].enabled).toBe(true);
    await pause().trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("恢复");
    expect(workspace.data.value.subscriptions[0].enabled).toBe(false);
  });
});
