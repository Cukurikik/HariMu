/**
 * Adapter Translation API (LibreTranslate / Google Translate compatible).
 *
 * Endpoint: POST {baseUrl}/translate  dengan body { q, source, target, format }
 * API Key opsional: header Authorization: Bearer {apiKey} (tergantung provider).
 *
 * API Key TIDAK pernah ada di repo; diatur lewat `wrangler secret put`.
 */

import type { Env } from "../config";
import { ApiError } from "../errors";
import type { TranslateRequest, TranslateResponse } from "./types";

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

export function translationProvider(
  env: Env,
  providerId: string,
  baseUrlKey: string,
  apiKeyKey?: string,
  defaultTargetLang: string = "id"
): Provider {
  const baseUrl = envOrThrow(env, baseUrlKey, providerId).replace(/\/$/, "");
  const apiKey = apiKeyKey ? envOrThrow(env, apiKeyKey, providerId) : undefined;

  async function translate(request: TranslateRequest): Promise<TranslateResponse> {
    const body = JSON.stringify({
      q: request.text,
      source: request.sourceLang ?? "auto",
      target: request.targetLang ?? defaultTargetLang,
      format: "text",
    });

    const headers: Record<string, string> = { "content-type": "application/json" };
    if (apiKey) headers["authorization"] = `Bearer ${apiKey}`;

    let response: Response;
    try {
      response = await fetch(`${baseUrl}/translate`, { method: "POST", headers, body });
    } catch (cause) {
      throw new ApiError(
        502,
        "provider_error",
        `Translation provider '${providerId}' tidak dapat dihubungi`,
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
        `Translation provider '${providerId}' menolak permintaan (${response.status})`,
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
        `Translation provider '${providerId}' mengembalikan respons tidak valid`,
        { retryable: true, details: { cause: cause instanceof Error ? cause.message : String(cause) } }
      );
    }

    const translatedText = data.translatedText ?? data.translation ?? "";
    if (!translatedText) {
      throw new ApiError(
        502,
        "provider_error",
        `Translation provider '${providerId}' tidak mengembalikan teks terjemahan`,
        { retryable: true }
      );
    }
    return {
      text: translatedText,
      sourceLang: data.detectedLanguage ?? undefined,
    };
  }

  return {
    id: providerId,
    mock: false,
    translate,
  };
}

import type { Provider } from "./types";
