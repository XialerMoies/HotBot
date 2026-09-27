<script setup>
import Button from "../ui/Button.vue";
import Panel from "../shared/Panel.vue";
import EmptyState from "../shared/EmptyState.vue";
defineProps({ items: Array, busy: Boolean });
defineEmits(["generate"]);
</script>
<template>
  <Panel title="每日摘要"
    ><template #actions
      ><Button :disabled="busy" @click="$emit('generate')">{{
        busy ? "处理中…" : "生成今日摘要"
      }}</Button></template
    >
    <p class="muted">
      按关注对象与启用的订阅规则匹配事件；同一天重新生成会更新该日摘要。
    </p>
    <article
      v-for="item in [...items].reverse()"
      :key="item.id"
      class="digest-entry"
    >
      <small>{{ item.date }}</small>
      <h3>{{ item.title }}</h3>
      <p class="answer-text">{{ item.content }}</p>
    </article>
    <EmptyState
      v-if="!items.length"
      title="暂无每日摘要"
      description="配置关注或订阅，然后生成今日摘要。"
    />
  </Panel>
</template>
