<script setup>
import {
  AlertDialogRoot,
  AlertDialogPortal,
  AlertDialogOverlay,
  AlertDialogContent,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogCancel,
} from "reka-ui";
import Button from "../ui/Button.vue";
defineProps({
  open: Boolean,
  title: String,
  description: String,
  busy: Boolean,
  error: String,
});
defineEmits(["update:open", "confirm"]);
</script>
<template>
  <AlertDialogRoot :open="open" @update:open="$emit('update:open', $event)">
    <AlertDialogPortal
      ><AlertDialogOverlay class="dialog-overlay" /><AlertDialogContent
        class="dialog-content"
      >
        <AlertDialogTitle as="h2">{{ title }}</AlertDialogTitle>
        <AlertDialogDescription>{{ description }}</AlertDialogDescription>
        <p v-if="error" role="alert" class="form-error">{{ error }}</p>
        <div class="dialog-actions">
          <AlertDialogCancel as-child
            ><Button variant="outline" :disabled="busy"
              >取消</Button
            ></AlertDialogCancel
          ><Button :disabled="busy" @click="$emit('confirm')">{{
            busy ? "删除中…" : "确认删除"
          }}</Button>
        </div>
      </AlertDialogContent></AlertDialogPortal
    >
  </AlertDialogRoot>
</template>
