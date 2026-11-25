import type { Env } from "../config";
import { isMockMode } from "../config";
import { ApiError } from "../errors";
import { cloudflareProvider } from "./cloudflare";
import { mockProvider } from "./mock";
import { openaiProvider } from "./openai";
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

  // Provider live utama: Cloudflare Workers AI (binding AI, tanpa kunci pihak ketiga).
  if (env.AI) return cloudflareProvider(env);

  // Fallback lama, hanya bila OPENAI_API_KEY di-set dan binding AI tidak ada.
  const provider = openaiProvider(env);
  const supported: Record<Exclude<Capability, "video">, boolean> = {
    tts: typeof provider.tts === "function",
    imageGenerate: typeof provider.imageGenerate === "function",
    imageEdit: typeof provider.imageEdit === "function",
    imageAnalyze: typeof provider.imageAnalyze === "function",
    speechToText: typeof provider.speechToText === "function",
  };
  if (!supported[capability]) {
    throw new ApiError(501, "provider_not_configured", `Provider aktif tidak mendukung '${capability}'`);
  }
  return provider;
}

export * from "./types";
export { cloudflareProvider } from "./cloudflare";
export { mockProvider } from "./mock";
