<script setup>
import { ref, onBeforeUnmount } from "vue";
import Button from "../ui/Button.vue";
import { api } from "../../services/api.js";
const emit = defineEmits(["authenticated"]);
const mode = ref("login");
const username = ref("");
const password = ref("");
const busy = ref(false);
const error = ref("");
let mounted = true;
onBeforeUnmount(() => { mounted = false; });
async function submit() {
  if (busy.value) return;
  if (username.value.trim().length < 3 || password.value.length < 6) {
    error.value = "用户名至少 3 位，密码至少 6 位。";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const result = await api[mode.value]({
      username: username.value.trim(),
      password: password.value,
    });
    if (!mounted) return;
    api.setToken(result.token);
    password.value = "";
    emit("authenticated", result.workspace);
  } catch (err) {
    if (mounted) error.value = api.message(err);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="auth-form">
    <div class="auth-mode" role="group" aria-label="账户操作">
      <button :disabled="busy" :class="{ active: mode === 'login' }" type="button" @click="mode = 'login'; error = ''">登录</button>
      <button :disabled="busy" :class="{ active: mode === 'register' }" type="button" @click="mode = 'register'; error = ''">注册</button>
    </div>
    <form class="form-stack" @submit.prevent="submit">
      <label class="field"
        >用户名<input
          :disabled="busy"
          v-model="username"
          autocomplete="username"
          required
          minlength="3"
          placeholder="至少 3 位"
      /></label>
      <label class="field"
        >密码<input
          :disabled="busy"
          v-model="password"
          type="password"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          required
          minlength="6"
          placeholder="至少 6 位"
      /></label>
      <p v-if="error" role="alert" class="form-error">{{ error }}</p>
      <Button type="submit" :disabled="busy">{{
        busy ? "提交中…" : mode === "login" ? "登录" : "注册并创建工作区"
      }}</Button>
    </form>
  </section>
</template>
