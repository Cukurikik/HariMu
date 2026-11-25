import type { Env } from "../config";
import { ApiError } from "../errors";
import type { Provider, ProviderResult } from "./types";

/**
 * Adapter Cloudflare Workers AI (binding `AI`, tanpa kunci pihak ketiga).
 *
 * Model yang dipakai (default, bisa di-override lewat env):
 * - TTS        `@cf/deepgram/aura-1`                        (speaker, WAV linear16)
 * - Gambar     `@cf/black-forest-labs/flux-1-schnell`       (text-to-image, base64)
 * - Edit gambar`@cf/runwayml/stable-diffusion-v1-5-img2img`  (img2img; TIDAK mendukung mask)
 * - Vision     `@cf/meta/llama-3.2-11b-vision-instruct`     (butuh persetujuan lisensi Meta sekali)
 * - STT        `@cf/openai/whisper`                         (audio → teks, multibahasa)
 *
 * Semua panggilan memakai `env.AI.run(...)`; tidak ada HTTP ke provider luar.
 */
type AiRunner = {
  run(model: string, inputs: unknown, options?: unknown): Promise<unknown>;
};

/** 5 suara aura-1 yang dipetakan dari slider klien (voice 1..5). */
const SPEAKERS = ["angus", "asteria", "arcas", "orion", "orpheus"] as const;

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value: string): Uint8Array {
  const raw = value.includes(",") ? value.slice(value.indexOf(",") + 1) : value;
  const binary = atob(raw);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function clampVoice(voice: number): number {
  return Math.min(SPEAKERS.length - 1, Math.max(0, Math.round(voice) - 1));
}

function requireBinding(env: Env): AiRunner {
  if (!env.AI) {
    throw new ApiError(
      501,
      "provider_not_configured",
      "Binding Workers AI belum tersedia. Tambahkan [ai] binding = \"AI\" di wrangler.toml lalu deploy.",
    );
  }
  return env.AI as unknown as AiRunner;
}

function providerFailure(capability: string, cause: unknown): ApiError {
  const message = cause instanceof Error ? cause.message : String(cause);
  // Pesan lisensi Meta muncul sebagai error model, bukan error jaringan; jangan ditandai retryable.
  const license = /license|agree/i.test(message);
  return new ApiError(
    license ? 501 : 502,
    license ? "provider_not_configured" : "provider_error",
    license
      ? "Model vision butuh persetujuan lisensi Meta: kirim sekali {\"prompt\":\"agree\"} ke @cf/meta/llama-3.2-11b-vision-instruct"
      : `Workers AI gagal pada '${capability}'`,
    { retryable: !license, details: { cause: message.slice(0, 300) } },
  );
}

export function cloudflareProvider(env: Env): Provider {
  const ai = requireBinding(env);
  const ttsModel = env.CF_TTS_MODEL ?? "@cf/deepgram/aura-1";
  const imageModel = env.CF_IMAGE_MODEL ?? "@cf/black-forest-labs/flux-1-schnell";
  const editModel = env.CF_IMAGE_EDIT_MODEL ?? "@cf/runwayml/stable-diffusion-v1-5-img2img";
  const visionModel = env.CF_VISION_MODEL ?? "@cf/meta/llama-3.2-11b-vision-instruct";
  const asrModel = env.CF_ASR_MODEL ?? "@cf/openai/whisper";

  async function imageResult(result: unknown, capability: string): Promise<ProviderResult> {
    const payload = result as { image?: string; result?: { image?: string } };
    const base64 = payload.image ?? payload.result?.image;
    if (!base64) throw new ApiError(502, "provider_error", `Workers AI tidak mengembalikan gambar (${capability})`, {
      retryable: true,
    });
    return { bytes: fromBase64(base64), mime: "image/png" };
  }

  return {
    id: "cloudflare-workers-ai",
    mock: false,

    async tts(request): Promise<ProviderResult> {
      try {
        const result = await ai.run(
          ttsModel,
          {
            text: request.text,
            speaker: SPEAKERS[clampVoice(request.voice)],
            encoding: "linear16",
            container: "wav",
            sample_rate: 24000,
          },
          { returnRawResponse: true },
        );
        if (result instanceof Response) {
          const bytes = new Uint8Array(await result.arrayBuffer());
          if (bytes.byteLength > 44) return { bytes, mime: "audio/wav" };
          throw new ApiError(502, "provider_error", "Workers AI mengembalikan audio kosong", { retryable: true });
        }
        const payload = result as { audio?: string };
        if (!payload?.audio) {
          throw new ApiError(502, "provider_error", "Workers AI tidak mengembalikan audio", { retryable: true });
        }
        return { bytes: fromBase64(payload.audio), mime: "audio/wav" };
      } catch (cause) {
        throw cause instanceof ApiError ? cause : providerFailure("tts", cause);
      }
    },

    async imageGenerate(request): Promise<ProviderResult> {
      try {
        const result = await ai.run(imageModel, { prompt: request.prompt });
        return await imageResult(result, "imageGenerate");
      } catch (cause) {
        throw cause instanceof ApiError ? cause : providerFailure("imageGenerate", cause);
      }
    },

    async imageEdit(request): Promise<ProviderResult> {
      try {
        const instruction = String(request.params["prompt"] ?? "").trim();
        const prompt = instruction ? `${request.op}: ${instruction}` : `perbaiki dan tingkatkan kualitas gambar (${request.op})`;
        const result = await ai.run(editModel, {
          prompt,
          image_b64: toBase64(request.bytes),
          strength: 0.6,
          num_steps: 8,
        });
        return await imageResult(result, "imageEdit");
      } catch (cause) {
        throw cause instanceof ApiError ? cause : providerFailure("imageEdit", cause);
      }
    },

    async imageAnalyze(request) {
      try {
        const result = (await ai.run(visionModel, {
          prompt: request.question,
          image: toBase64(request.bytes),
          max_tokens: 700,
        })) as { response?: string; description?: string };
        const text = (result.response ?? result.description ?? "").trim();
        if (!text) throw new ApiError(502, "provider_error", "Workers AI tidak mengembalikan teks", { retryable: true });
        return { text, mock: false };
      } catch (cause) {
        throw cause instanceof ApiError ? cause : providerFailure("imageAnalyze", cause);
      }
    },

    async speechToText(request) {
      try {
        const result = (await ai.run(asrModel, {
          audio: Array.from(request.bytes),
          ...(request.language ? { language: request.language } : {}),
        })) as { text?: string };
        return { text: (result.text ?? "").trim(), mock: false };
      } catch (cause) {
        throw cause instanceof ApiError ? cause : providerFailure("speechToText", cause);
      }
    },
  };
}
