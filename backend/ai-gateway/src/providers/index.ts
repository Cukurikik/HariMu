import type { Env } from "../config";
import { isMockMode } from "../config";
import { ApiError } from "../errors";
import { cloudflareProvider } from "./cloudflare";
import { mockProvider } from "./mock";
import { replicateVideoProvider } from "./replicate";
import type { Capability, Provider } from "./types";

export function resolveProvider(env: Env, capability: Capability): Provider {
  if (isMockMode(env)) return mockProvider;

  if (capability === "video") {
    // K6: video belum punya provider live Cloudflare. Adapter Replicate hanya dipakai bila
    // pemilik mengisinya; kalau tidak, 501 (bukan hasil mock yang menyamar).
    if (env.REPLICATE_API_TOKEN && env.REPLICATE_VIDEO_VERSION) return replicateVideoProvider(env);
    throw new ApiError(
      501,
      "provider_not_configured",
      "Video generation belum punya provider live. Isi REPLICATE_API_TOKEN + REPLICATE_VIDEO_VERSION atau pakai PROVIDER_MODE=mock.",
    );
  }

  // Provider live utama (satu-satunya): Cloudflare Workers AI (binding AI, tanpa kunci pihak ketiga).
  // Tidak ada fallback OpenAI: bila binding AI tidak ada, fitur non-video (selain video) tidak tersedia.
  if (!env.AI) {
    throw new ApiError(
      501,
      "provider_not_configured",
      "Binding Workers AI belum tersedia. Tambahkan [ai] binding = \"AI\" di wrangler.toml lalu deploy.",
    );
  }
  return cloudflareProvider(env);
}

export * from "./types";
export { cloudflareProvider } from "./cloudflare";
export { mockProvider } from "./mock";
