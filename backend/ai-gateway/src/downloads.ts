import type { Env } from "./config";
import { ApiError } from "./errors";
import { base64url, hmacSha256, timingSafeEqual, tryFromBase64url } from "./http-util";

/**
 * URL unduhan berumur pendek untuk artefak job.
 *
 * Catatan jujur: ini BUKAN S3 presigned URL (Worker binding R2 tidak bisa membuatnya), tapi URL
 * bertanda tangan HMAC dengan masa berlaku pendek — sifat keamanannya sama: tanpa secret tidak
 * bisa dipalsukan, kedaluwarsa otomatis, dan tidak pernah membocorkan kredensial R2 ke klien.
 */
export interface SignedDownload {
  path: string;
  expiresAt: number;
}

function signingSecret(env: Env): string {
  if (!env.DOWNLOAD_SIGNING_SECRET || env.DOWNLOAD_SIGNING_SECRET.length < 16) {
    throw new ApiError(503, "provider_not_configured", "DOWNLOAD_SIGNING_SECRET belum di-set");
  }
  return env.DOWNLOAD_SIGNING_SECRET;
}

export async function signDownload(
  env: Env,
  objectKey: string,
  ttlSec = 600,
): Promise<SignedDownload> {
  const secret = signingSecret(env);
  const expiresAt = Math.floor(Date.now() / 1000) + ttlSec;
  const signature = await hmacSha256(secret, `${objectKey}.${expiresAt}`);
  const path =
    `/v1/download/${encodeURIComponent(objectKey)}` +
    `?exp=${expiresAt}&sig=${base64url(signature)}`;
  return { path, expiresAt };
}

export async function verifyDownload(
  env: Env,
  objectKey: string,
  exp: string | null,
  sig: string | null,
): Promise<void> {
  const secret = signingSecret(env);
  const expiresAt = Number(exp);
  if (!Number.isFinite(expiresAt) || !sig) {
    throw new ApiError(403, "forbidden", "URL unduhan tidak lengkap");
  }
  const signatureBytes = tryFromBase64url(sig);
  if (!signatureBytes) {
    throw new ApiError(403, "forbidden", "Tanda tangan URL unduhan tidak valid");
  }
  const expected = await hmacSha256(secret, `${objectKey}.${expiresAt}`);
  if (!timingSafeEqual(expected, signatureBytes)) {
    throw new ApiError(403, "forbidden", "Tanda tangan URL unduhan tidak cocok");
  }
  if (expiresAt <= Math.floor(Date.now() / 1000)) {
    throw new ApiError(403, "job_expired", "URL unduhan sudah kedaluwarsa", { retryable: true });
  }
}
