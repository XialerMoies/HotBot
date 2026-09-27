<script setup>
defineProps({ title: String });
</script>
<template>
  <header class="topbar">
    <p class="breadcrumb"><span class="topbar-index">01</span> HOTBOT / <strong>{{ title }}</strong></p>
    <span class="topbar-status"><i></i>公开信息持续汇集</span>
  </header>
</template>
