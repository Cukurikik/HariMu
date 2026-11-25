/** Binding dan secret yang dipakai Worker. Secret TIDAK pernah ada di repo. */
export interface Env {
  JOBS: KVNamespace;
  ARTIFACTS: R2Bucket;
  /** Binding Workers AI (wrangler.toml `[ai] binding = "AI"`). */
  AI?: Ai;

  PROVIDER_MODE?: string;
  INSTALL_TOKEN_SECRET?: string;
  DOWNLOAD_SIGNING_SECRET?: string;

  /** Override model Workers AI; default ada di src/providers/cloudflare.ts. */
  CF_TTS_MODEL?: string;
  CF_IMAGE_MODEL?: string;
  CF_IMAGE_EDIT_MODEL?: string;
  CF_VISION_MODEL?: string;
  CF_ASR_MODEL?: string;

  REPLICATE_API_TOKEN?: string;
  REPLICATE_VIDEO_VERSION?: string;
  REPLICATE_BASE_URL?: string;

  // Provider eksternal OpenAI-compatible (dari atas ke bawah).
  // Base URL dan model default bisa di [vars]; API key wajib lewat `wrangler secret put`.
  AION_BASE_URL?: string;
  AION_API_KEY?: string;
  AION_MODEL?: string;

  GONKA_BASE_URL?: string;
  GONKA_API_KEY?: string;
  GONKA_MODEL?: string;

  GROQ_BASE_URL?: string;
  GROQ_API_KEY?: string;
  GROQ_MODEL?: string;

  NVIDIA_BASE_URL?: string;
  NVIDIA_API_KEY?: string;
  NVIDIA_MODEL?: string;

  RUNCRATE_BASE_URL?: string;
  RUNCRATE_API_KEY?: string;
  RUNCRATE_MODEL?: string;

  OPENCODE_BASE_URL?: string;
  OPENCODE_API_KEY?: string;
  OPENCODE_MODEL?: string;

  AGNES_BASE_URL?: string;
  AGNES_API_KEY?: string;
  AGNES_MODEL?: string;

  REQUESTY_BASE_URL?: string;
  REQUESTY_API_KEY?: string;
  REQUESTY_MODEL?: string;

  AMD_BASE_URL?: string;
  AMD_API_KEY?: string;
  AMD_MODEL?: string;

  COARENA_BASE_URL?: string;
  COARENA_API_KEY?: string;
  COARENA_MODEL?: string;

  BAI_BASE_URL?: string;
  BAI_API_KEY?: string;
  BAI_MODEL?: string;

  INFERX_BASE_URL?: string;
  INFERX_API_KEY?: string;
  INFERX_MODEL?: string;

  XKIRO_BASE_URL?: string;
  XKIRO_API_KEY?: string;
  XKIRO_MODEL?: string;

  BLUEMINDS_BASE_URL?: string;
  BLUEMINDS_API_KEY?: string;
  BLUEMINDS_MODEL?: string;

  SERVERSUSA_BASE_URL?: string;
  SERVERSUSA_API_KEY?: string;
  SERVERSUSA_MODEL?: string;

  // Translation API (LibreTranslate / Google Translate compatible)
  TRANSLATE_BASE_URL?: string;
  TRANSLATE_API_KEY?: string;
  TRANSLATE_DEFAULT_TARGET?: string;

  // Weather API (Open-Meteo / WeatherAPI.com compatible)
  WEATHER_BASE_URL?: string;
  WEATHER_API_KEY?: string;

  // Utilitas tanpa kunci (semuanya boleh diganti lewat [vars])
  GEOCODE_BASE_URL?: string;
  RATES_BASE_URL?: string;
  DICTIONARY_BASE_URL?: string;

  RATE_LIMIT_PER_MIN?: string;
  DAILY_QUOTA?: string;
  MAX_UPLOAD_BYTES?: string;
  TOKEN_TTL_SEC?: string;
  /** Batas penerbitan token baru per IP (anti token-farming). */
  INSTALL_RATE_PER_MIN?: string;
  INSTALL_DAILY_PER_IP?: string;
  /** Bila di-set, /v1/install wajib menyertakan header x-install-key yang cocok. */
  INSTALL_REGISTRATION_KEY?: string;
  /** Origin yang diizinkan CORS; kosong berarti "*" (aman untuk Bearer token). */
  ALLOWED_ORIGIN?: string;
  VIDEO_JOB_TTL_SEC?: string;
}

export function num(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export function isMockMode(env: Env): boolean {
  return (env.PROVIDER_MODE ?? "mock").toLowerCase() !== "live";
}
