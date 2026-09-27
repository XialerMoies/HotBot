<script setup>
import { ref } from "vue";
import Button from "../ui/Button.vue";
import Panel from "../shared/Panel.vue";
import EmptyState from "../shared/EmptyState.vue";
const props = defineProps({ items: Array, busy: Boolean });
defineEmits(["add", "remove"]);
const value = ref("");
const type = ref("keyword");
const types = {
  keyword: "关键词",
  company: "公司",
  person: "人物",
  product: "产品",
  technology: "技术",
};
defineExpose({
  clear: () => {
    value.value = "";
  },
});
</script>
<template>
  <Panel title="关注对象">
    <form
      class="filter-toolbar"
      @submit.prevent="$emit('add', { type, value: value.trim() })"
    >
      <label
        >类型<select v-model="type">
          <option v-for="(name, key) in types" :key="key" :value="key">
            {{ name }}
          </option>
        </select></label
      ><label class="search-field"
        >关注内容<input
          v-model="value"
          required
          maxlength="120"
          placeholder="例如：OpenAI、机器人" /></label
      ><Button type="submit" :disabled="busy || !value.trim()">添加关注</Button>
    </form>
    <ul class="management-list">
      <li v-for="item in props.items" :key="item.id">
        <div>
          <strong>{{ item.value }}</strong
          ><small>{{ types[item.type] || item.type }}</small>
        </div>
        <Button variant="ghost" :disabled="busy" @click="$emit('remove', item)"
          >移除</Button
        >
      </li>
    </ul>
    <EmptyState
      v-if="!props.items.length"
      title="还没有关注对象"
      description="添加公司、人物或技术关键词，便于生成个人摘要。"
    />
  </Panel>
</template>
