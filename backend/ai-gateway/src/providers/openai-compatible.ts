/**
 * Adapter OpenAI-compatible untuk provider eksternal (AION, Gonka, Groq, NVIDIA,
 * RunCreate, Opencode, Agnes, Requesty, AMD, Coarena, B.AI, Inferx, Xkiro, Blueminds,
 * Servernusa, dan lainnya).
 *
 * Semua provider ini mengekspos endpoint OpenAI-compatible:
 *   POST {baseUrl}/chat/completions  dengan header Authorization: Bearer {apiKey}
 *   Body: { model, messages, max_tokens, temperature }
 *
 * API Key TIDAK pernah ada di repo; diatur lewat `wrangler secret put`.
 */

import type { Env } from "../config";
import { ApiError } from "../errors";
import type { ChatRequest, ChatResponse, Provider } from "./types";

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

export function openaiCompatibleProvider(
  env: Env,
  providerId: string,
  baseUrlKey: string,
  apiKeyKey: string,
  modelKey?: string,
  defaultModel?: string
): Provider {
  const baseUrl = envOrThrow(env, baseUrlKey, providerId).replace(/\/$/, "");
  const apiKey = envOrThrow(env, apiKeyKey, providerId);
  const model = (modelKey ? (env as unknown as Record<string, string | undefined>)[modelKey] : undefined) ?? defaultModel ?? "";

  if (!model) {
    throw new ApiError(
      501,
      "provider_not_configured",
      `Provider '${providerId}' belum punya model default. Isi '${modelKey ?? "model"}' di wrangler.toml.`
    );
  }

  async function chat(request: ChatRequest): Promise<ChatResponse> {
    const body = JSON.stringify({
      model,
      messages: request.messages,
      ...(request.maxTokens ? { max_tokens: request.maxTokens } : {}),
      ...(request.temperature !== undefined ? { temperature: request.temperature } : {}),
    });

    let response: Response;
    try {
      response = await fetch(`${baseUrl}/chat/completions`, {
        method: "POST",
        headers: {
          authorization: `Bearer ${apiKey}`,
          "content-type": "application/json",
        },
        body,
      });
    } catch (cause) {
      throw new ApiError(
        502,
        "provider_error",
        `Provider '${providerId}' tidak dapat dihubungi`,
        { retryable: true, details: { cause: cause instanceof Error ? cause.message : String(cause) } }
      );
    }

    if (!response.ok) {
      let detail = "";
      try {
        const err = (await response.json()) as Record<string, unknown>;
        detail = (err.error as { message?: string })?.message ?? JSON.stringify(err).slice(0, 300);
      } catch {
        detail = (await response.text().catch(() => "")).slice(0, 300);
      }
      throw new ApiError(
        502,
        "provider_error",
        `Provider '${providerId}' menolak permintaan (${response.status})`,
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
        `Provider '${providerId}' mengembalikan respons yang tidak valid`,
        { retryable: true, details: { cause: cause instanceof Error ? cause.message : String(cause) } }
      );
    }

    const text = data.choices?.[0]?.message?.content ?? "";
    if (!text) {
      throw new ApiError(
        502,
        "provider_error",
        `Provider '${providerId}' tidak mengembalikan teks`,
        { retryable: true }
      );
    }
    return { text };
  }

  return {
    id: providerId,
    mock: false,
    chat,
  };
}
