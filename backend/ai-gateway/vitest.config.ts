import { defineWorkersConfig } from "@cloudflare/vitest-pool-workers/config";

export default defineWorkersConfig({
  test: {
    poolOptions: {
      workers: {
        // Audit K6: hindari remote proxy Workers AI saat tes (tidak ada kredensial Cloudflare di CI).
        // Binding AI tetap dideklarasikan di wrangler.toml untuk produksi; di tes, env.AI tidak
        // tersedia dan kode memperlakukannya sebagai provider_not_configured (mode live tidak dipakai).
        remoteBindings: false,
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          compatibilityFlags: ["nodejs_compat"],
          // Tanggal kompatibilitas uji sengaja di bawah native `node:vm` workerd
          // (Oktober 2025): vitest-pool-workers 0.9.x masih memakai polyfill yang
          // bertabrakan dengan modul native tersebut (workers-sdk#11047) sehingga
          // workerd gagal start. Produksi tetap memakai compatibility_date wrangler.toml.
          compatibilityDate: "2025-09-01",
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
