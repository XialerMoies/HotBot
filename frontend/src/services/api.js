import axios from "axios";
const client = axios.create({ baseURL: "/api", timeout: 20000 });
const token = () => localStorage.getItem("hotbot-token");
const auth = () => ({ headers: { "X-Workspace-Token": token() } });
// Invalidate only the session that made the rejected authenticated request.
client.interceptors.response.use(response => response, error => {
  const sentToken = error.config?.headers?.['X-Workspace-Token'];
  const reason = error.response?.data?.error || error.response?.data?.message;
  if (sentToken && sentToken === token() && (error.response?.status === 401 || reason === 'invalid token')) {
    localStorage.removeItem('hotbot-token');
    window.dispatchEvent(new Event('hotbot:session-expired'));
  }
  return Promise.reject(error);
});
const data = (response) => response.data;
const id = (value) => encodeURIComponent(value);
const messages = {
  "invalid credentials": "用户名或密码不正确。",
  "username already exists": "该用户名已被使用。",
  "invalid token": "登录已失效，请退出后重新登录。",
  "username requires 3 chars and password requires 6 chars":
    "用户名至少 3 位，密码至少 6 位。",
};
export const api = {
  listEvents: () => client.get("/events").then(data),
  evidenceStatus: () => client.get('/evidence/status', {timeout:12000}).then(data),
  listArticles: () => client.get("/events/articles").then(data),
  eventDetail: (value) => client.get("/events/" + id(value)).then(data),
  evidenceSources: (value) => client.get('/evidence/events/' + id(value) + '/sources', {timeout:40000}).then(data),
  sources: () => client.get("/operations/sources").then(data),
  workspace: () =>
    token()
      ? client.get("/workspace", auth()).then(data)
      : Promise.resolve(null),
  register: (body) => client.post("/workspace/register", body).then(data),
  login: (body) => client.post("/workspace/login", body).then(data),
  follow: (body) => client.post("/workspace/follows", body, auth()).then(data),
  removeFollow: (value) =>
    client.delete("/workspace/follows/" + id(value), auth()).then(data),
  subscribe: (body) =>
    client.post("/workspace/subscriptions", body, auth()).then(data),
  updateSubscription: (value, enabled) =>
    client
      .patch("/workspace/subscriptions/" + id(value), { enabled }, auth())
      .then(data),
  removeSubscription: (value) =>
    client.delete("/workspace/subscriptions/" + id(value), auth()).then(data),
  digest: () =>
    client.post("/workspace/digests/generate", {}, auth()).then(data),
  readNotice: (value) =>
    client
      .post("/workspace/notifications/" + id(value) + "/read", {}, auth())
      .then(data),
  answer: (eventId, question, signal) =>
    client
      .post(
        "/evidence/events/" + id(eventId) + "/answer",
        { question },
        { timeout: 60000, signal },
      )
      .then(data),
  message: (err, fallback = "请求失败，请重试。") => {
    const message = err?.response?.data?.error || err?.response?.data?.message;
    if (messages[message]) return messages[message];
    if (err?.code === "ECONNABORTED") return "请求超时，请稍后重试。";
    if (err?.code === "ERR_NETWORK")
      return "无法连接服务，请检查后端是否启动。";
    return typeof message === "string" ? message : fallback;
  },
  setToken: (value) => localStorage.setItem("hotbot-token", value),
  logout: () => localStorage.removeItem("hotbot-token"),
};
