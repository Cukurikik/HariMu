import type { Env } from "../config";
import { ApiError } from "../errors";
import type { Provider, ProviderResult } from "./types";

const VOICES = ["alloy", "echo", "fable", "onyx", "nova"];

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function toBlob(bytes: Uint8Array, mime: string): Blob {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return new Blob([copy], { type: mime });
}

/**
 * Adapter OpenAI-compatible (juga berlaku untuk endpoint yang meniru API OpenAI, mis. gateway
 * internal). Base URL bisa di-override lewat OPENAI_BASE_URL supaya tidak terkunci ke satu vendor.
 */
export function openaiProvider(env: Env): Provider {
  const apiKey = env.OPENAI_API_KEY;
  if (!apiKey) {
    throw new ApiError(
      501,
      "provider_not_configured",
      "OPENAI_API_KEY belum di-set pada Worker (wrangler secret put OPENAI_API_KEY)",
    );
  }
  const baseUrl = (env.OPENAI_BASE_URL ?? "https://api.openai.com/v1").replace(/\/$/, "");
  const imageModel = env.OPENAI_IMAGE_MODEL ?? "gpt-image-1";
  const ttsModel = env.OPENAI_TTS_MODEL ?? "gpt-4o-mini-tts";
  const asrModel = env.OPENAI_ASR_MODEL ?? "whisper-1";
  const visionModel = env.OPENAI_VISION_MODEL ?? "gpt-4o-mini";

  async function call(path: string, init: RequestInit): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set("authorization", `Bearer ${apiKey}`);
    let response: Response;
    try {
      response = await fetch(`${baseUrl}${path}`, { ...init, headers });
    } catch (cause) {
      throw new ApiError(502, "provider_error", "Provider tidak dapat dihubungi", {
        retryable: true,
        details: { cause: cause instanceof Error ? cause.message : String(cause) },
      });
    }
    if (!response.ok) {
      const detail = (await response.text()).slice(0, 400);
      if (response.status === 429) {
        throw new ApiError(429, "rate_limited", "Provider membatasi permintaan", {
          retryable: true,
          retryAfterSec: 20,
          details: { provider: "openai", detail },
        });
      }
      throw new ApiError(502, "provider_error", `Provider menolak permintaan (${response.status})`, {
        retryable: response.status >= 500,
        details: { provider: "openai", status: response.status, detail },
      });
    }
    return response;
  }

  return {
    id: "openai",
    mock: false,

    async tts(request): Promise<ProviderResult> {
      const voice = VOICES[Math.min(VOICES.length - 1, Math.max(0, request.voice - 1))];
      const response = await call("/audio/speech", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          model: ttsModel,
          voice,
          input: request.text,
          response_format: "wav",
        }),
      });
      return { bytes: new Uint8Array(await response.arrayBuffer()), mime: "audio/wav" };
    },

    async imageGenerate(request): Promise<ProviderResult> {
      const response = await call("/images/generations", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ model: imageModel, prompt: request.prompt, size: request.size, n: 1 }),
      });
      const data = (await response.json()) as { data?: Array<{ b64_json?: string; url?: string }> };
      const first = data.data?.[0];
      if (first?.b64_json) {
        const binary = atob(first.b64_json);
        const bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
        return { bytes, mime: "image/png" };
      }
      if (first?.url) {
        const image = await fetch(first.url);
        return { bytes: new Uint8Array(await image.arrayBuffer()), mime: image.headers.get("content-type") ?? "image/png" };
      }
      throw new ApiError(502, "provider_error", "Provider tidak mengembalikan gambar", { retryable: true });
    },

    async imageEdit(request): Promise<ProviderResult> {
      const form = new FormData();
      form.set("model", imageModel);
      form.set("prompt", `${request.op}: ${request.params["prompt"] ?? "perbaiki dan tingkatkan kualitas gambar ini"}`);
      form.set("image", toBlob(request.bytes, request.mime), "input.png");
      if (request.params["mask"]) form.set("mask", request.params["mask"]);
      const response = await call("/images/edits", { method: "POST", body: form });
      const data = (await response.json()) as { data?: Array<{ b64_json?: string }> };
      const b64 = data.data?.[0]?.b64_json;
      if (!b64) throw new ApiError(502, "provider_error", "Provider tidak mengembalikan gambar hasil edit", { retryable: true });
      const binary = atob(b64);
      const bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
      return { bytes, mime: "image/png" };
    },

    async imageAnalyze(request) {
      const dataUrl = `data:${request.mime};base64,${toBase64(request.bytes)}`;
      const response = await call("/chat/completions", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          model: visionModel,
          messages: [
            {
              role: "user",
              content: [
                { type: "text", text: request.question },
                { type: "image_url", image_url: { url: dataUrl } },
              ],
            },
          ],
          max_tokens: 700,
        }),
      });
      const data = (await response.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
      };
      const text = data.choices?.[0]?.message?.content?.trim();
      if (!text) throw new ApiError(502, "provider_error", "Provider tidak mengembalikan teks", { retryable: true });
      return { text, mock: false };
    },

    async speechToText(request) {
      const form = new FormData();
      form.set("model", asrModel);
      form.set("file", toBlob(request.bytes, request.mime), "audio.wav");
      if (request.language) form.set("language", request.language);
      const response = await call("/audio/transcriptions", { method: "POST", body: form });
      const data = (await response.json()) as { text?: string };
      return { text: data.text?.trim() ?? "", mock: false };
    },
  };
}
