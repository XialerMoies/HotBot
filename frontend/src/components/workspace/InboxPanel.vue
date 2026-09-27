<script setup>
import { computed, ref } from "vue";
import Button from "../ui/Button.vue";
import Panel from "../shared/Panel.vue";
import EmptyState from "../shared/EmptyState.vue";
import { dateTime } from "../../lib/display.js";
const props = defineProps({ items: Array, busy: Boolean });
defineEmits(["read"]);
const unread = ref(false);
const notices = computed(() =>
  [...props.items].reverse().filter((n) => !unread.value || !n.read),
);
</script>
<template>
  <Panel title="站内提醒"
    ><template #actions
      ><label class="checkbox-label"
        ><input v-model="unread" type="checkbox" />仅看未读</label
      ></template
    >
    <ul class="management-list">
      <li v-for="item in notices" :key="item.id">
        <div>
          <strong>{{ item.title }}</strong>
          <p>{{ item.content }}</p>
          <small
            >{{ dateTime(item.createdAt) }} ·
            {{ item.read ? "已读" : "未读" }}</small
          >
        </div>
        <Button
          v-if="!item.read"
          variant="outline"
          size="sm"
          :disabled="busy"
          @click="$emit('read', item.id)"
          >标记已读</Button
        >
      </li>
    </ul>
    <EmptyState
      v-if="!notices.length"
      title="暂无提醒"
      :description="
        unread ? '未读提醒已处理完毕。' : '相关事件提醒将显示在这里。'
      "
    />
  </Panel>
</template>
