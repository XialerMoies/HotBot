import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: { "/api": `http://localhost:${process.env.BOT_SERVER_PORT || "8180"}` },
  },
});
