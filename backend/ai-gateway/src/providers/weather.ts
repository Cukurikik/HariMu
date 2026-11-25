/**
 * Adapter Weather API (Open-Meteo / WeatherAPI.com compatible).
 *
 * Open-Meteo (gratis, tanpa API key):
 *   GET {baseUrl}/v1/forecast?latitude=..&longitude=..&current_weather=true
 *
 * WeatherAPI.com (butuh API key):
 *   GET {baseUrl}/v1/current.json?key=..&q=..&aqi=no
 *
 * API Key TIDAK pernah ada di repo; diatur lewat `wrangler secret put`.
 */

import type { Env } from "../config";
import { ApiError } from "../errors";
import type { WeatherRequest, WeatherResponse } from "./types";

function envOrThrow(env: Env, key: string, providerId: string): string {
  const value = (env as unknown as Record<string, string | undefined>)[key];
  if (!value) {
    throw new ApiError(
      501,
      "provider_not_configured",
      `Provider '${providerId}' belum dikonfigurasi: isi env '${key}' di wrangler.toml dan secret API key lewat 'wrangler secret put'.`
    );
  }
  return value;
}

export function weatherProvider(
  env: Env,
  providerId: string,
  baseUrlKey: string,
  apiKeyKey?: string
): Provider {
  const baseUrl = envOrThrow(env, baseUrlKey, providerId).replace(/\/$/, "");
  const apiKey = apiKeyKey ? envOrThrow(env, apiKeyKey, providerId) : undefined;
  const isOpenMeteo = baseUrl.includes("open-meteo.com");

  async function weather(request: WeatherRequest): Promise<WeatherResponse> {
    let url: string;
    if (isOpenMeteo) {
      url = `${baseUrl}/v1/forecast?latitude=${request.latitude}&longitude=${request.longitude}&current_weather=true`;
    } else {
      // WeatherAPI.com style
      const q = `${request.latitude},${request.longitude}`;
      url = `${baseUrl}/v1/current.json?key=${apiKey ?? ""}&q=${encodeURIComponent(q)}&aqi=no`;
    }

    let response: Response;
    try {
      const headers: Record<string, string> = {};
      if (!isOpenMeteo && apiKey) {
        // WeatherAPI puts key in query, but some providers need header
        headers["authorization"] = `Bearer ${apiKey}`;
      }
      response = await fetch(url, { method: "GET", headers });
    } catch (cause) {
      throw new ApiError(
        502,
        "provider_error",
        `Weather provider '${providerId}' tidak dapat dihubungi`,
        { retryable: true, details: { cause: cause instanceof Error ? cause.message : String(cause) } }
      );
    }

    if (!response.ok) {
      let detail = "";
      try {
        detail = (await response.text()).slice(0, 300);
      } catch {}
      throw new ApiError(
        502,
        "provider_error",
        `Weather provider '${providerId}' menolak permintaan (${response.status})`,
        { retryable: response.status >= 500 || response.status === 429, details: { status: response.status, cause: detail } }
      );
    }

    let data: any;
    try {
      data = await response.json();
    } catch (cause) {
      throw new ApiError(
        502,
        "provider_error",
        `Weather provider '${providerId}' mengembalikan respons tidak valid`,
        { retryable: true, details: { cause: cause instanceof Error ? cause.message : String(cause) } }
      );
    }

    if (isOpenMeteo) {
      const current = data.current_weather ?? data.current ?? {};
      return {
        temperature: current.temperature ?? 0,
        condition: current.weathercode ?? 0,
        location: `${request.latitude},${request.longitude}`,
      };
    }

    // WeatherAPI.com style
    const current = data.current ?? {};
    return {
      temperature: current.temp_c ?? 0,
      condition: current.condition?.text ?? "",
      location: data.location?.name ?? `${request.latitude},${request.longitude}`,
    };
  }

  return {
    id: providerId,
    mock: false,
    weather,
  };
}

import type { Provider } from "./types";
