/**
 * Chain provider: mencoba beberapa provider secara berurutan untuk setiap capability.
 * Jika provider pertama gagal (error, rate limit, quota habis), otomatis coba provider berikutnya.
 * Digunakan sebagai fallback ketika Cloudflare Workers AI free tier telah tercapai batasnya.
 */

import type { Provider } from "./types";
import type {
  TtsRequest, ImageRequest, ImageEditRequest, AnalyzeRequest, AsrRequest,
  VideoRequest, ChatRequest, ChatResponse, ProviderResult,
  TranslateRequest, TranslateResponse, WeatherRequest, WeatherResponse,
  VideoStart, VideoPoll,
} from "./types";

function firstAvailable<T>(providers: Provider[], fn: (p: Provider) => Promise<T>): Promise<T> {
  return (async () => {
    let lastError: unknown;
    for (const p of providers) {
      try {
        return await fn(p);
      } catch (e) {
        lastError = e;
        // Lanjutkan ke provider berikutnya
      }
    }
    throw lastError ?? new Error("Tidak ada provider yang tersedia");
  })();
}

export function chainProvider(providers: Provider[]): Provider {
  return {
    id: "chain",
    mock: providers.some((p) => p.mock),

    async tts(request: TtsRequest): Promise<ProviderResult> {
      return firstAvailable(providers, (p) => p.tts?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung TTS`)));
    },

    async imageGenerate(request: ImageRequest): Promise<ProviderResult> {
      return firstAvailable(providers, (p) => p.imageGenerate?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung image generate`)));
    },

    async imageEdit(request: ImageEditRequest): Promise<ProviderResult> {
      return firstAvailable(providers, (p) => p.imageEdit?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung image edit`)));
    },

    async imageAnalyze(request: AnalyzeRequest): Promise<{ text: string; mock?: boolean }> {
      return firstAvailable(providers, (p) => p.imageAnalyze?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung image analyze`)));
    },

    async speechToText(request: AsrRequest): Promise<{ text: string; mock?: boolean }> {
      return firstAvailable(providers, (p) => p.speechToText?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung speech to text`)));
    },

    async startVideo(request: VideoRequest): Promise<VideoStart> {
      return firstAvailable(providers, (p) => p.startVideo?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung video`)));
    },

    async pollVideo(providerJobId: string): Promise<VideoPoll> {
      return firstAvailable(providers, (p) => p.pollVideo?.(providerJobId) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung video poll`)));
    },

    async chat(request: ChatRequest): Promise<ChatResponse> {
      return firstAvailable(providers, (p) => p.chat?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung chat`)));
    },

    async translate(request: TranslateRequest): Promise<TranslateResponse> {
      return firstAvailable(providers, (p) => p.translate?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung translate`)));
    },

    async weather(request: WeatherRequest): Promise<WeatherResponse> {
      return firstAvailable(providers, (p) => p.weather?.(request) ?? Promise.reject(new Error(`Provider ${p.id} tidak mendukung weather`)));
    },
  };
}
