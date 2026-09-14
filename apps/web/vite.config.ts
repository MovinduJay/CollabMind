import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts",
    coverage: {
      provider: "v8",
      reporter: ["text", "html", "lcov"],
      exclude: ["src/test/**", "src/main.tsx"],
      thresholds: {
        statements: 40,
        branches: 55,
        functions: 25,
        lines: 40,
      },
    },
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
  },
});
