<script setup>
import { LogIn, LogOut } from "lucide-vue-next";
defineProps({
  items: Array,
  active: String,
  workspaceName: String,
  unread: Number,
  authenticated: Boolean,
});
defineEmits(["navigate", "account", "logout"]);
</script>
<template>
  <aside class="sidebar dock">
    <div class="dock-actions">
      <button
        v-if="!authenticated"
        class="dock-button account-dock-button"
        type="button"
        aria-label="登录或注册"
        data-tooltip="登录 / 注册"
        @click="$emit('account')"
      ><LogIn :size="19" /><span>登录 / 注册</span></button>
      <button
        v-else
        class="dock-button account-dock-button signed-in"
        type="button"
        :aria-label="workspaceName + '，点击退出登录'"
        :data-tooltip="workspaceName + ' · 点击退出'"
        @click="$emit('logout')"
      ><b class="avatar">{{ workspaceName?.slice(0, 1).toUpperCase() || 'U' }}</b><LogOut class="account-exit" :size="18" /><span>退出登录</span></button>
    </div>
    <nav aria-label="主导航" class="dock-nav">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        :class="{ active: active === item.id }"
        :aria-current="active === item.id ? 'page' : undefined"
        :data-tooltip="item.label"
        @click="$emit('navigate', item.id)"
      >
        <component :is="item.icon" :size="19" /><span>{{ item.label }}</span
        ><b v-if="item.id === 'settings' && unread" class="dock-badge">{{ unread }}</b>
      </button>
    </nav>
  </aside>
</template>
