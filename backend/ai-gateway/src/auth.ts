import type { Env } from "./config";
import { num } from "./config";
import { ApiError } from "./errors";
import { base64url, hmacSha256, timingSafeEqual, tryFromBase64url } from "./http-util";

interface TokenPayload {
  sub: string;
  iat: number;
  exp: number;
  v: 1;
}

const INSTALL_ID_PATTERN = /^[A-Za-z0-9_-]{16,64}$/;

/**
 * Token instal: `installId.payloadBase64url.signatureBase64url`, ditandatangani HMAC-SHA256
 * dengan INSTALL_TOKEN_SECRET. Tidak ada kunci provider di klien; klien hanya memegang token
 * perangkat yang bisa dicabut dengan mengganti secret.
 */
export async function issueInstallToken(env: Env, installId: string): Promise<{ token: string; expiresAt: number }> {
  if (!INSTALL_ID_PATTERN.test(installId)) {
    throw new ApiError(400, "bad_request", "installId harus 16-64 karakter [A-Za-z0-9_-]");
  }
  const secret = requireSecret(env);
  const nowSec = Math.floor(Date.now() / 1000);
  const ttl = num(env.TOKEN_TTL_SEC, 30 * 24 * 3600);
  const payload: TokenPayload = { sub: installId, iat: nowSec, exp: nowSec + ttl, v: 1 };
  const encodedPayload = base64url(new TextEncoder().encode(JSON.stringify(payload)));
  const signature = await hmacSha256(secret, `${installId}.${encodedPayload}`);
  return { token: `${installId}.${encodedPayload}.${base64url(signature)}`, expiresAt: payload.exp };
}

function requireSecret(env: Env): string {
  if (!env.INSTALL_TOKEN_SECRET || env.INSTALL_TOKEN_SECRET.length < 16) {
    // Salah konfigurasi server, bukan kesalahan klien — tapi jangan sampai terbuka tanpa auth.
    throw new ApiError(503, "provider_not_configured", "INSTALL_TOKEN_SECRET belum di-set (wrangler secret put)");
  }
  return env.INSTALL_TOKEN_SECRET;
}

export interface Authenticated {
  installId: string;
  token: string;
}

export async function requireInstallToken(request: Request, env: Env): Promise<Authenticated> {
  const header = request.headers.get("authorization") ?? "";
  if (!header.toLowerCase().startsWith("bearer ")) {
    throw new ApiError(401, "unauthorized", "Header Authorization: Bearer <token> wajib ada");
  }
  const token = header.slice(7).trim();
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new ApiError(401, "unauthorized", "Format token tidak valid");
  }
  const [installId, encodedPayload, encodedSignature] = parts;
  if (!INSTALL_ID_PATTERN.test(installId)) {
    throw new ApiError(401, "unauthorized", "Token tidak valid");
  }
  const signatureBytes = tryFromBase64url(encodedSignature);
  if (!signatureBytes) {
    throw new ApiError(401, "unauthorized", "Tanda tangan token tidak valid");
  }
  const secret = requireSecret(env);
  const expected = await hmacSha256(secret, `${installId}.${encodedPayload}`);
  if (!timingSafeEqual(expected, signatureBytes)) {
    throw new ApiError(401, "unauthorized", "Tanda tangan token tidak cocok");
  }
  const payloadBytes = tryFromBase64url(encodedPayload);
  if (!payloadBytes) {
    throw new ApiError(401, "unauthorized", "Payload token tidak dapat dibaca");
  }
  let payload: TokenPayload;
  try {
    payload = JSON.parse(new TextDecoder().decode(payloadBytes)) as TokenPayload;
  } catch {
    throw new ApiError(401, "unauthorized", "Payload token tidak dapat dibaca");
  }
  if (payload.v !== 1 || payload.sub !== installId) {
    throw new ApiError(401, "unauthorized", "Token tidak terikat ke instalasi ini");
  }
  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.exp <= nowSec) {
    throw new ApiError(401, "unauthorized", "Token kedaluwarsa, minta token baru", { retryable: true });
  }
  return { installId, token };
}
