/**
 * Adapter Geocoding (Open-Meteo Geocoding API).
 *
 * Endpoint: GET {baseUrl}/v1/search?name=..&count=..&language=..&format=json
 * Tanpa API key untuk pemakaian non-komersial.
 *
 * Lisensi data: CC BY 4.0 — respons WAJIB menyertakan atribusi ke Open-Meteo.
 * Ketentuan gratis: < 10.000 panggilan/hari, 5.000/jam, 600/menit, hanya non-komersial.
 * Karena itu hasil di-cache di KV selama 30 hari supaya satu Worker tidak menghabiskan kuota
 * bersama untuk semua instalasi.
 */

import type { Env } from "../config";
import { isMockMode } from "../config";
import { ApiError } from "../errors";

export interface GeocodeResult {
  name: string;
  label: string;
  latitude: number;
  longitude: number;
  country?: string;
  admin1?: string;
  timezone?: string;
  mock?: boolean;
}

const CACHE_TTL_SEC = 30 * 24 * 3600;
const USER_AGENT = "HariMu-Android/1.0 (+https://github.com/Cukurikik/HariMu)";

function baseUrl(env: Env): string {
  return (env.GEOCODE_BASE_URL ?? "https://geocoding-api.open-meteo.com").replace(/\/$/, "");
}

/** Geocode nama tempat menjadi koordinat. Melempar ApiError dengan kode terstruktur. */
export async function geocodePlace(
  env: Env,
  query: string,
  language = "id",
): Promise<GeocodeResult> {
  const q = query.trim();
  if (!q) throw new ApiError(400, "bad_request", "Nama tempat tidak boleh kosong");
  if (q.length > 120) throw new ApiError(400, "bad_request", "Nama tempat terlalu panjang (maks 120)");

  if (isMockMode(env)) {
    return {
      name: "Jakarta",
      label: "Jakarta, Indonesia",
      latitude: -6.2146,
      longitude: 106.8451,
      country: "Indonesia",
      timezone: "Asia/Jakarta",
      mock: true,
    };
  }

  const lang = /^[a-z]{2}$/.test(language) ? language : "id";
  const cacheKey = `geo:${lang}:${q.toLowerCase().slice(0, 80)}`;
  const cached = await env.JOBS.get(cacheKey);
  if (cached) {
    try {
      return JSON.parse(cached) as GeocodeResult;
    } catch {
      // entri rusak: abaikan dan ambil ulang dari provider
    }
  }

  const url = `${baseUrl(env)}/v1/search?name=${encodeURIComponent(q)}&count=1&language=${lang}&format=json`;
  let response: Response;
  try {
    response = await fetch(url, { headers: { "user-agent": USER_AGENT } });
  } catch (cause) {
    throw new ApiError(502, "provider_error", "Layanan geocoding tidak dapat dihubungi", {
      retryable: true,
      details: { cause: cause instanceof Error ? cause.message : String(cause) },
    });
  }

  if (!response.ok) {
    throw new ApiError(502, "provider_error", `Layanan geocoding menolak permintaan (${response.status})`, {
      retryable: response.status >= 500 || response.status === 429,
      details: { status: response.status },
    });
  }

  let data: any;
  try {
    data = await response.json();
  } catch (cause) {
    throw new ApiError(502, "provider_error", "Layanan geocoding mengembalikan respons tidak valid", {
      retryable: true,
      details: { cause: cause instanceof Error ? cause.message : String(cause) },
    });
  }

  const first = Array.isArray(data?.results) ? data.results[0] : undefined;
  if (!first || typeof first.latitude !== "number" || typeof first.longitude !== "number") {
    throw new ApiError(404, "not_found", `Tempat '${q}' tidak ditemukan`);
  }

  const parts = [first.name, first.admin1, first.country].filter(
    (part: unknown): part is string => typeof part === "string" && part.trim().length > 0,
  );
  const result: GeocodeResult = {
    name: String(first.name ?? q),
    label: [...new Set(parts)].join(", "),
    latitude: first.latitude,
    longitude: first.longitude,
    country: typeof first.country === "string" ? first.country : undefined,
    admin1: typeof first.admin1 === "string" ? first.admin1 : undefined,
    timezone: typeof first.timezone === "string" ? first.timezone : undefined,
  };

  await env.JOBS.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL_SEC });
  return result;
}
