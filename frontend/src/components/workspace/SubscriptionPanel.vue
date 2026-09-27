<script setup>
import { ref } from "vue";
import Button from "../ui/Button.vue";
import Panel from "../shared/Panel.vue";
import EmptyState from "../shared/EmptyState.vue";
defineProps({ items: Array, busy: Boolean });
defineEmits(["add", "toggle", "remove"]);
const query = ref("");
defineExpose({
  clear: () => {
    query.value = "";
  },
});
</script>
<template>
  <Panel title="订阅规则">
    <p class="muted">启用的关键词会参与个人摘要匹配；暂停后保留规则。</p>
    <form
      class="filter-toolbar"
      @submit.prevent="$emit('add', { query: query.trim() })"
    >
      <label class="search-field"
        >订阅关键词<input
          v-model="query"
          required
          maxlength="120"
          placeholder="例如：生成式人工智能" /></label
      ><Button type="submit" :disabled="busy || !query.trim()">添加订阅</Button>
    </form>
    <ul class="management-list">
      <li v-for="item in items" :key="item.id">
        <div>
          <strong>{{ item.query }}</strong
          ><small>{{ item.enabled ? "已启用" : "已暂停" }}</small>
        </div>
        <div class="toolbar-actions">
          <Button
            variant="outline"
            size="sm"
            :disabled="busy"
            @click="$emit('toggle', item)"
            >{{ item.enabled ? "暂停" : "恢复" }}</Button
          ><Button
            variant="ghost"
            :disabled="busy"
            @click="$emit('remove', item)"
            >删除</Button
          >
        </div>
      </li>
    </ul>
    <EmptyState
      v-if="!items.length"
      title="还没有订阅规则"
      description="添加关键词后可随时暂停或删除。"
    />
  </Panel>
</template>
