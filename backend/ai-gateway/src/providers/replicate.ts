import type { Env } from "../config";
import { ApiError } from "../errors";
import type { Provider, VideoPoll } from "./types";

interface PredictionResponse {
  id?: string;
  status?: string;
  output?: string | string[] | null;
  error?: string | null;
  logs?: string | null;
}

/**
 * Adapter video async (gaya Replicate: POST /predictions → GET /predictions/:id).
 * Hanya dipakai bila REPLICATE_API_TOKEN dan REPLICATE_VIDEO_VERSION tersedia; kalau tidak,
 * endpoint video mengembalikan 501 provider_not_configured (bukan hasil palsu).
 */
export function replicateVideoProvider(env: Env): Provider {
  const token = env.REPLICATE_API_TOKEN;
  const version = env.REPLICATE_VIDEO_VERSION;
  if (!token || !version) {
    throw new ApiError(
      501,
      "provider_not_configured",
      "Video generation butuh REPLICATE_API_TOKEN dan REPLICATE_VIDEO_VERSION di Worker",
    );
  }
  const baseUrl = (env.REPLICATE_BASE_URL ?? "https://api.replicate.com/v1").replace(/\/$/, "");

  async function call(path: string, init: RequestInit = {}): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set("authorization", `Bearer ${token}`);
    if (!headers.has("content-type")) headers.set("content-type", "application/json");
    let response: Response;
    try {
      response = await fetch(`${baseUrl}${path}`, { ...init, headers });
    } catch (cause) {
      throw new ApiError(502, "provider_error", "Provider video tidak dapat dihubungi", {
        retryable: true,
        details: { cause: cause instanceof Error ? cause.message : String(cause) },
      });
    }
    if (!response.ok) {
      throw new ApiError(502, "provider_error", `Provider video menolak permintaan (${response.status})`, {
        retryable: response.status >= 500 || response.status === 429,
        details: { status: response.status },
      });
    }
    return response;
  }

  return {
    id: "replicate",
    mock: false,

    async startVideo(request) {
      const response = await call("/predictions", {
        method: "POST",
        body: JSON.stringify({
          version,
          input: {
            prompt: request.prompt,
            duration: request.durationSec,
            aspect_ratio: request.aspect,
          },
        }),
      });
      const data = (await response.json()) as PredictionResponse;
      if (!data.id) throw new ApiError(502, "provider_error", "Provider video tidak mengembalikan job id", { retryable: true });
      return { providerJobId: data.id };
    },

    async pollVideo(providerJobId): Promise<VideoPoll> {
      const data = (await (await call(`/predictions/${providerJobId}`)).json()) as PredictionResponse;
      const status = (data.status ?? "").toLowerCase();
      if (status === "failed" || status === "canceled") {
        return { status: "failed", error: data.error ?? "Generasi video gagal di provider" };
      }
      if (status !== "succeeded") {
        return { status: "running", progress: status === "processing" ? 50 : 10 };
      }
      const output = Array.isArray(data.output) ? data.output[0] : data.output;
      if (!output) return { status: "failed", error: "Provider tidak mengembalikan berkas video" };
      const file = await fetch(output);
      if (!file.ok) return { status: "failed", error: `Unduhan hasil gagal (${file.status})` };
      return {
        status: "done",
        progress: 100,
        result: {
          bytes: new Uint8Array(await file.arrayBuffer()),
          mime: file.headers.get("content-type") ?? "video/mp4",
        },
      };
    },
  };
}
