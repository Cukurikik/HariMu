import type { Env } from "../config";
import { isMockMode } from "../config";
import { ApiError } from "../errors";
import { cloudflareProvider } from "./cloudflare";
import { mockProvider } from "./mock";
import { replicateVideoProvider } from "./replicate";
import { openaiCompatibleProvider } from "./openai-compatible";
import { chainProvider } from "./chain";
import type { Capability, Provider } from "./types";

/** Daftar provider OpenAI-compatible dari atas ke bawah (prioritas fallback).
 * Setiap entri: id, baseUrlKey, apiKeyKey, modelKey, defaultModel.
 * API Key TIDAK pernah di-repo; diatur lewat `wrangler secret put <API_KEY_KEY>`.
 */
const EXTERNAL_PROVIDERS: Array<{
  id: string;
  baseUrlKey: string;
  apiKeyKey: string;
  modelKey: string;
  defaultModel?: string;
}> = [
  // 1. AION LABS
  { id: "aion", baseUrlKey: "AION_BASE_URL", apiKeyKey: "AION_API_KEY", modelKey: "AION_MODEL", defaultModel: "aion-labs/aion-2.0" },
  // 2. Gonka Broker
  { id: "gonka", baseUrlKey: "GONKA_BASE_URL", apiKeyKey: "GONKA_API_KEY", modelKey: "GONKA_MODEL", defaultModel: "deepseek-ai/DeepSeek-V4-Flash-0731" },
  // 3. Groq
  { id: "groq", baseUrlKey: "GROQ_BASE_URL", apiKeyKey: "GROQ_API_KEY", modelKey: "GROQ_MODEL", defaultModel: "openai/gpt-oss-120b" },
  // 4. NVIDIA Models AI
  { id: "nvidia", baseUrlKey: "NVIDIA_BASE_URL", apiKeyKey: "NVIDIA_API_KEY", modelKey: "NVIDIA_MODEL", defaultModel: "deepseek-ai/deepseek-v4.1-flash" },
  // 5. RunCreate
  { id: "runcrate", baseUrlKey: "RUNCRATE_BASE_URL", apiKeyKey: "RUNCRATE_API_KEY", modelKey: "RUNCRATE_MODEL", defaultModel: "deepseek-ai/DeepSeek-V4.1-Flash" },
  // 6. Opencode
  { id: "opencode", baseUrlKey: "OPENCODE_BASE_URL", apiKeyKey: "OPENCODE_API_KEY", modelKey: "OPENCODE_MODEL", defaultModel: "deepseek-ai/DeepSeek-V4.1-Flash Free" },
  // 7. Agnes AI
  { id: "agnes", baseUrlKey: "AGNES_BASE_URL", apiKeyKey: "AGNES_API_KEY", modelKey: "AGNES_MODEL", defaultModel: "agnes-2.5-flash" },
  // 8. Requesty AI
  { id: "requesty", baseUrlKey: "REQUESTY_BASE_URL", apiKeyKey: "REQUESTY_API_KEY", modelKey: "REQUESTY_MODEL", defaultModel: "nemotron-3.5-lightning-30b-a3b" },
  // 9. AMD AI Models
  { id: "amd", baseUrlKey: "AMD_BASE_URL", apiKeyKey: "AMD_API_KEY", modelKey: "AMD_MODEL", defaultModel: "deepseek-ai/deepseek-v4-flash-0731" },
  // 10. Coarena
  { id: "coarena", baseUrlKey: "COARENA_BASE_URL", apiKeyKey: "COARENA_API_KEY", modelKey: "COARENA_MODEL", defaultModel: "deepseek-ai/deepseek-v4-flash-0731" },
  // 11. B.AI
  { id: "bai", baseUrlKey: "BAI_BASE_URL", apiKeyKey: "BAI_API_KEY", modelKey: "BAI_MODEL", defaultModel: "deepseek-v4-flash" },
  // 12. Inferx AI
  { id: "inferx", baseUrlKey: "INFERX_BASE_URL", apiKeyKey: "INFERX_API_KEY", modelKey: "INFERX_MODEL", defaultModel: "deepseek-v4-flash-0731" },
  // 13. Xkiro AI
  { id: "xkiro", baseUrlKey: "XKIRO_BASE_URL", apiKeyKey: "XKIRO_API_KEY", modelKey: "XKIRO_MODEL", defaultModel: "deepseek-v4-flash-0731" },
  // 14. Blueminds AI
  { id: "blueminds", baseUrlKey: "BLUEMINDS_BASE_URL", apiKeyKey: "BLUEMINDS_API_KEY", modelKey: "BLUEMINDS_MODEL", defaultModel: "deepseek-v4-flash-0731" },
  // 15. Servernusa
  { id: "servernusa", baseUrlKey: "SERVERSUSA_BASE_URL", apiKeyKey: "SERVERSUSA_API_KEY", modelKey: "SERVERSUSA_MODEL", defaultModel: "nusa/deepseek-v4.1-flash-free" },
];

function buildExternalProviders(env: Env): Provider[] {
  const providers: Provider[] = [];
  for (const p of EXTERNAL_PROVIDERS) {
    // Hanya tambahkan provider jika base URL dan API key tersedia
    const envRecord = env as unknown as Record<string, string | undefined>;
    if (!envRecord[p.baseUrlKey] || !envRecord[p.apiKeyKey]) continue;
    try {
      providers.push(openaiCompatibleProvider(env, p.id, p.baseUrlKey, p.apiKeyKey, p.modelKey, p.defaultModel));
    } catch {
      // Provider tidak dikonfigurasi dengan lengkap, lewati
    }
  }
  return providers;
}

export function resolveProvider(env: Env, capability: Capability): Provider {
  if (isMockMode(env)) return mockProvider;

  if (capability === "video") {
    // Video hanya pakai Replicate bila dikonfigurasi; kalau tidak, 501 (bukan hasil palsu).
    if (env.REPLICATE_API_TOKEN && env.REPLICATE_VIDEO_VERSION) return replicateVideoProvider(env);
    throw new ApiError(
      501,
      "provider_not_configured",
      "Video generation belum punya provider live. Isi REPLICATE_API_TOKEN + REPLICATE_VIDEO_VERSION atau pakai PROVIDER_MODE=mock."
    );
  }

  // Bangun daftar provider eksternal yang terkonfigurasi (dari atas ke bawah).
  const external = buildExternalProviders(env);

  // Jika binding Workers AI tersedia, taruh di urutan pertama (gratis, tanpa kunci pihak ketiga).
  const providers: Provider[] = [];
  if (env.AI) {
    providers.push(cloudflareProvider(env));
  }
  providers.push(...external);

  if (providers.length === 0) {
    throw new ApiError(
      501,
      "provider_not_configured",
      "Tidak ada provider AI yang tersedia. Aktifkan binding Workers AI di wrangler.toml atau konfigurasikan setidaknya satu provider eksternal."
    );
  }

  // Chain provider: coba satu per satu sampai sukses.
  return chainProvider(providers);
}

export * from "./types";
export { cloudflareProvider } from "./cloudflare";
export { mockProvider } from "./mock";
export { openaiCompatibleProvider } from "./openai-compatible";
export { chainProvider } from "./chain";
