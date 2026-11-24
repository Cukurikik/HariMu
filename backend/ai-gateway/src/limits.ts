import type { Env } from "./config";
import { num } from "./config";
import { ApiError } from "./errors";

function secondsUntilNextMinute(): number {
  return 60 - (Math.floor(Date.now() / 1000) % 60);
}

function secondsUntilTomorrowUtc(): number {
  const now = Date.now();
  const tomorrow = Date.UTC(
    new Date(now).getUTCFullYear(),
    new Date(now).getUTCMonth(),
    new Date(now).getUTCDate() + 1,
  );
  return Math.max(1, Math.ceil((tomorrow - now) / 1000));
}

/**
 * Rate limit per instalasi per menit. Catatan jujur: KV tidak atomik, jadi dalam kondisi
 * konkurensi tinggi nilai bisa sedikit meleset. Untuk batas ketat, ganti dengan Durable Object
 * `RateLimiter` (lihat README). Ini cukup untuk mencegah penyalahgunaan satu perangkat.
 */
export async function enforceRateLimit(env: Env, installId: string): Promise<void> {
  const limit = num(env.RATE_LIMIT_PER_MIN, 30);
  const minute = Math.floor(Date.now() / 60000);
  const key = `rl:${installId}:${minute}`;
  const used = Number((await env.JOBS.get(key)) ?? "0");
  if (used >= limit) {
    throw new ApiError(429, "rate_limited", `Batas ${limit} permintaan/menit tercapai`, {
      retryable: true,
      retryAfterSec: secondsUntilNextMinute(),
      details: { limit, used },
    });
  }
  await env.JOBS.put(key, String(used + 1), { expirationTtl: 180 });
}

export interface QuotaSnapshot {
  used: number;
  limit: number;
  resetsInSec: number;
}

function quotaKey(installId: string): string {
  return `quota:${installId}:${new Date().toISOString().slice(0, 10)}`;
}

export async function quotaStatus(env: Env, installId: string): Promise<QuotaSnapshot> {
  const limit = num(env.DAILY_QUOTA, 20);
  const used = Number((await env.JOBS.get(quotaKey(installId))) ?? "0");
  return { used, limit, resetsInSec: secondsUntilTomorrowUtc() };
}

/** Kuota harian berbobot: TTS=1, analisa=1, gambar=2, STT=2, video=5. */
export async function enforceQuota(env: Env, installId: string, cost: number): Promise<QuotaSnapshot> {
  const snapshot = await quotaStatus(env, installId);
  if (snapshot.used + cost > snapshot.limit) {
    throw new ApiError(
      429,
      "quota_exceeded",
      `Kuota harian habis (${snapshot.used}/${snapshot.limit} kredit)`,
      { retryable: false, details: { ...snapshot, requested: cost } },
    );
  }
  const used = snapshot.used + cost;
  await env.JOBS.put(quotaKey(installId), String(used), { expirationTtl: 172800 });
  return { used, limit: snapshot.limit, resetsInSec: snapshot.resetsInSec };
}
