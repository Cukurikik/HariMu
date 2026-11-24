import { ApiError } from "./errors";

export function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fromBase64url(text: string): Uint8Array {
  const padded = text.replace(/-/g, "+").replace(/_/g, "/");
  const binary = atob(padded + "=".repeat((4 - (padded.length % 4)) % 4));
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) out[i] = binary.charCodeAt(i);
  return out;
}

export async function hmacSha256(secret: string, data: string): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  return new Uint8Array(signature);
}

/** Perbandingan waktu-konstan supaya signature tidak bisa ditebak lewat timing. */
export function timingSafeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.byteLength !== b.byteLength) return false;
  let diff = 0;
  for (let i = 0; i < a.byteLength; i += 1) diff |= a[i] ^ b[i];
  return diff === 0;
}

export async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Membaca body dengan batas ukuran. Content-Length diperiksa lebih dulu supaya upload besar
 * ditolak sebelum di-buffer, lalu sisa stream dihitung ulang (header bisa bohong).
 */
export async function readBodyLimited(request: Request, maxBytes: number): Promise<Uint8Array> {
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(declared) && declared > maxBytes) {
    throw new ApiError(413, "payload_too_large", `Ukuran unggahan melebihi ${maxBytes} byte`, {
      details: { maxBytes },
    });
  }
  const buffer = await request.arrayBuffer();
  if (buffer.byteLength > maxBytes) {
    throw new ApiError(413, "payload_too_large", `Ukuran unggahan melebihi ${maxBytes} byte`, {
      details: { maxBytes },
    });
  }
  return new Uint8Array(buffer);
}

export async function readJson<T>(request: Request, maxBytes: number): Promise<T> {
  const raw = await readBodyLimited(request, maxBytes);
  try {
    return JSON.parse(new TextDecoder().decode(raw)) as T;
  } catch {
    throw new ApiError(400, "bad_request", "Body bukan JSON yang valid");
  }
}

export function requireString(value: unknown, field: string, maxLength = 4000): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new ApiError(400, "bad_request", `Field '${field}' wajib berupa teks`);
  }
  const trimmed = value.trim();
  if (trimmed.length > maxLength) {
    throw new ApiError(400, "bad_request", `Field '${field}' melebihi ${maxLength} karakter`);
  }
  return trimmed;
}

export function optionalFile(form: FormData, field: string): File {
  const value = form.get(field);
  if (!(value instanceof File)) {
    throw new ApiError(400, "bad_request", `Lampiran '${field}' tidak ditemukan`);
  }
  return value;
}

export function optionalString(form: FormData, field: string, fallback = ""): string {
  const value = form.get(field);
  return typeof value === "string" ? value : fallback;
}
