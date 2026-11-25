/**
 * Adapter kurs mata uang (Frankfurter).
 *
 * Endpoint: GET {baseUrl}/v1/latest?base=IDR&symbols=USD,EUR
 * Tanpa API key. Gratis termasuk pemakaian komersial; cache dianjurkan oleh provider.
 *
 * Data adalah **kurs referensi** bank sentral (ECB dan sumber resmi lain), dipublikasikan
 * sekali atau beberapa kali sehari — BUKAN kuotasi pasar langsung. Tanggal `date` wajib
 * ditampilkan ke pengguna supaya tidak dikira kurs real-time.
 */

import type { Env } from "../config";
import { isMockMode } from "../config";
import { ApiError } from "../errors";

export interface RatesResult {
  base: string;
  date: string;
  rates: Record<string, number>;
  source: string;
  mock?: boolean;
}

const CACHE_TTL_SEC = 12 * 3600;
const USER_AGENT = "HariMu-Android/1.0 (+https://github.com/Cukurikik/HariMu)";

function baseUrl(env: Env): string {
  return (env.RATES_BASE_URL ?? "https://api.frankfurter.dev").replace(/\/$/, "");
}

function normalizeCurrency(value: string): string {
  const code = value.trim().toUpperCase();
  if (!/^[A-Z]{3}$/.test(code)) {
    throw new ApiError(400, "bad_request", `Kode mata uang tidak valid: '${value}'`);
  }
  return code;
}

export async function exchangeRates(
  env: Env,
  base: string,
  symbols: string[],
): Promise<RatesResult> {
  const baseCode = normalizeCurrency(base);
  const targets = [...new Set(symbols.map(normalizeCurrency))].filter((code) => code !== baseCode);
  if (targets.length === 0) {
    throw new ApiError(400, "bad_request", "Pilih minimal satu mata uang tujuan yang berbeda dari base");
  }
  if (targets.length > 8) {
    throw new ApiError(400, "bad_request", "Maksimal 8 mata uang tujuan per permintaan");
  }

  const today = new Date().toISOString().slice(0, 10);
  if (isMockMode(env)) {
    return {
      base: baseCode,
      date: today,
      rates: Object.fromEntries(targets.map((code) => [code, 1])),
      source: "MOCK — bukan kurs nyata",
      mock: true,
    };
  }

  const cacheKey = `fx:${baseCode}:${targets.join(",")}`;
  const cached = await env.JOBS.get(cacheKey);
  if (cached) {
    try {
      return JSON.parse(cached) as RatesResult;
    } catch {
      // entri rusak: ambil ulang dari provider
    }
  }

  const url = `${baseUrl(env)}/v1/latest?base=${baseCode}&symbols=${targets.join(",")}`;
  let response: Response;
  try {
    response = await fetch(url, { headers: { "user-agent": USER_AGENT } });
  } catch (cause) {
    throw new ApiError(502, "provider_error", "Layanan kurs tidak dapat dihubungi", {
      retryable: true,
      details: { cause: cause instanceof Error ? cause.message : String(cause) },
    });
  }

  if (!response.ok) {
    throw new ApiError(502, "provider_error", `Layanan kurs menolak permintaan (${response.status})`, {
      retryable: response.status >= 500 || response.status === 429,
      details: { status: response.status },
    });
  }

  let data: any;
  try {
    data = await response.json();
  } catch (cause) {
    throw new ApiError(502, "provider_error", "Layanan kurs mengembalikan respons tidak valid", {
      retryable: true,
      details: { cause: cause instanceof Error ? cause.message : String(cause) },
    });
  }

  const rawRates = data?.rates;
  if (!rawRates || typeof rawRates !== "object") {
    throw new ApiError(502, "provider_error", "Layanan kurs tidak mengembalikan tabel kurs", {
      retryable: true,
    });
  }

  const rates: Record<string, number> = {};
  for (const code of targets) {
    const value = Number(rawRates[code]);
    if (Number.isFinite(value) && value > 0) rates[code] = value;
  }
  if (Object.keys(rates).length === 0) {
    throw new ApiError(404, "not_found", `Tidak ada kurs untuk ${targets.join(", ")}`);
  }

  const result: RatesResult = {
    base: typeof data.base === "string" ? data.base : baseCode,
    date: typeof data.date === "string" ? data.date : today,
    rates,
    source: "Frankfurter (kurs referensi bank sentral)",
  };
  await env.JOBS.put(cacheKey, JSON.stringify(result), { expirationTtl: CACHE_TTL_SEC });
  return result;
}
