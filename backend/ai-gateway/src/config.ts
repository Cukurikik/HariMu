/** Binding dan secret yang dipakai Worker. Secret TIDAK pernah ada di repo. */
export interface Env {
  JOBS: KVNamespace;
  ARTIFACTS: R2Bucket;

  PROVIDER_MODE?: string;
  INSTALL_TOKEN_SECRET?: string;
  DOWNLOAD_SIGNING_SECRET?: string;

  OPENAI_API_KEY?: string;
  OPENAI_BASE_URL?: string;
  OPENAI_IMAGE_MODEL?: string;
  OPENAI_TTS_MODEL?: string;
  OPENAI_ASR_MODEL?: string;
  OPENAI_VISION_MODEL?: string;

  REPLICATE_API_TOKEN?: string;
  REPLICATE_VIDEO_VERSION?: string;
  REPLICATE_BASE_URL?: string;

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
