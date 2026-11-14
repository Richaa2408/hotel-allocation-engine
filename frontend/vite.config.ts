import path from "node:path";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// The build goes straight into Spring Boot's static folder, so one `mvnw package` gives one runnable jar.
// In development (`npm run dev`), API calls are proxied to the Java server: port 8080 unless
// STAYFLOW_API says otherwise (e.g. STAYFLOW_API=http://localhost:8085).
const apiTarget = process.env.STAYFLOW_API ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "src") },
  },
  build: {
    outDir: path.resolve(import.meta.dirname, "../target/classes/static"),
    emptyOutDir: true,
    chunkSizeWarningLimit: 1200,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": apiTarget,
      "/v3": apiTarget,
      "/swagger-ui": apiTarget,
    },
  },
  test: {
    environment: "jsdom",
  },
});
