<script setup>
import { computed } from "vue";
import { Primitive } from "reka-ui";
defineOptions({ inheritAttrs: false });

const props = defineProps({
  variant: { type: String, default: "default" },
  size: { type: String, default: "default" },
  disabled: Boolean,
  as: { type: String, default: "button" },
});
const classes = computed(() => [
  "ui-button",
  `ui-button-${props.variant}`,
  `ui-button-${props.size}`,
]);
</script>
<template>
  <Primitive
    :as="as"
    :type="as === 'button' ? 'button' : undefined"
    :class="classes"
    :disabled="disabled"
    v-bind="$attrs"
    ><slot
  /></Primitive>
</template>
