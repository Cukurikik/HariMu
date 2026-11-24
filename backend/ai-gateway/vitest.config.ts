import { defineWorkersConfig } from "@cloudflare/vitest-pool-workers/config";

export default defineWorkersConfig({
  test: {
    poolOptions: {
      workers: {
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          compatibilityFlags: ["nodejs_compat"],
          bindings: {
            INSTALL_TOKEN_SECRET: "test-install-secret-0123456789",
            DOWNLOAD_SIGNING_SECRET: "test-download-secret-0123456789",
            PROVIDER_MODE: "mock",
            RATE_LIMIT_PER_MIN: "3",
            DAILY_QUOTA: "5",
            MAX_UPLOAD_BYTES: "1024",
            TOKEN_TTL_SEC: "3600",
            INSTALL_RATE_PER_MIN: "4",
            INSTALL_DAILY_PER_IP: "20",
          },
          r2Buckets: ["ARTIFACTS"],
          kvNamespaces: ["JOBS"],
        },
      },
    },
    testTimeout: 20000,
  },
});
