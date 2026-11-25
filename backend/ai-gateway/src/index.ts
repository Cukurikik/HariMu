import type { Env } from "./config";
import { isMockMode, num } from "./config";
import { signDownload, verifyDownload } from "./downloads";
import { ApiError, asApiError, binaryResponse, errorResponse, jsonResponse } from "./errors";
import { issueInstallToken, requireInstallToken } from "./auth";
import { enforceInstallRateLimit, enforceQuota, enforceRateLimit, quotaStatus, QUOTA_COST } from "./limits";
import { createVideoJob, refreshJob } from "./jobs";
import { resolveProvider, resolveTranslateProvider, resolveWeatherProvider } from "./providers";
import {
  optionalFile,
  optionalString,
  readJson,
  requireString,
} from "./http-util";

const VERSION = "1.0.0";

/** Perbandingan string waktu-konstan sederhana untuk kunci registrasi instalasi. */
function constantTimeStringEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function corsHeaders(env: Env): Record<string, string> {
  // Origin dapat dibatasi lewat ALLOWED_ORIGIN; default "*" aman karena semua endpoint data
  // memakai Bearer token (tanpa cookie), sehingga tidak ada credentialed CORS.
  const origin = (env.ALLOWED_ORIGIN ?? "*").trim() || "*";
  return {
    "access-control-allow-origin": origin,
    "access-control-allow-headers": "authorization, content-type, x-install-key",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    vary: "origin",
  };
}

function withCors(response: Response, env: Env): Response {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(corsHeaders(env))) headers.set(key, value);
  return new Response(response.body, { status: response.status, headers });
}

function mockHeaders(providerMock: boolean): Record<string, string> {
  return providerMock ? { "x-mock": "1", "x-provider": "mock" } : { "x-mock": "0" };
}

async function readUploadForm(request: Request, maxBytes: number): Promise<FormData> {
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(declared) && declared > maxBytes) {
    throw new ApiError(413, "payload_too_large", `Ukuran unggahan melebihi ${maxBytes} byte`, {
      details: { maxBytes },
    });
  }
  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    throw new ApiError(400, "bad_request", "Body harus multipart/form-data");
  }
  for (const value of form.values()) {
    if (value instanceof File && value.size > maxBytes) {
      throw new ApiError(413, "payload_too_large", `Berkas '${value.name}' melebihi ${maxBytes} byte`, {
        details: { maxBytes, name: value.name, size: value.size },
      });
    }
  }
  return form;
}

async function fileBytes(file: File): Promise<Uint8Array> {
  return new Uint8Array(await file.arrayBuffer());
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const requestId = crypto.randomUUID().slice(0, 8);
    try {
      return withCors(await route(request, env, requestId), env);
    } catch (cause) {
      return withCors(errorResponse(asApiError(cause), requestId), env);
    }
  },
};

async function route(request: Request, env: Env, requestId: string): Promise<Response> {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method.toUpperCase();
  const maxUploadBytes = num(env.MAX_UPLOAD_BYTES, 10 * 1024 * 1024);

  if (method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders(env) });
  }

  if (path === "/v1/health" && method === "GET") {
    return jsonResponse(
      {
        status: "ok",
        version: VERSION,
        mode: isMockMode(env) ? "mock" : "live",
        corsOrigin: (env.ALLOWED_ORIGIN ?? "*").trim() || "*",
        time: new Date().toISOString(),
      },
      200,
      requestId,
    );
  }

  if (path === "/v1/install" && method === "POST") {
    await enforceInstallRateLimit(env, request);
    const registrationKey = env.INSTALL_REGISTRATION_KEY ?? "";
    if (registrationKey) {
      const provided = request.headers.get("x-install-key") ?? "";
      if (!constantTimeStringEquals(provided, registrationKey)) {
        throw new ApiError(401, "unauthorized", "Header x-install-key tidak valid");
      }
    }
    const body = await readJson<{ installId?: string }>(request, 4096);
    const installId = requireString(body.installId, "installId", 64);
    const issued = await issueInstallToken(env, installId);
    return jsonResponse(
      {
        token: issued.token,
        expiresAt: issued.expiresAt,
        mode: isMockMode(env) ? "mock" : "live",
        limits: {
          ratePerMinute: num(env.RATE_LIMIT_PER_MIN, 30),
          dailyQuota: num(env.DAILY_QUOTA, 20),
          maxUploadBytes,
        },
      },
      200,
      requestId,
    );
  }

  // Unduhan artefak: tanda tangan HMAC berumur pendek adalah autentikasinya, bukan token instal.
  if (path.startsWith("/v1/download/") && method === "GET") {
    const objectKey = decodeURIComponent(path.slice("/v1/download/".length));
    await verifyDownload(env, objectKey, url.searchParams.get("exp"), url.searchParams.get("sig"));
    const object = await env.ARTIFACTS.get(objectKey);
    if (!object) throw new ApiError(404, "not_found", "Artefak tidak ditemukan atau sudah dihapus");
    const headers = new Headers();
    headers.set("content-type", object.httpMetadata?.contentType ?? "application/octet-stream");
    headers.set("cache-control", "no-store");
    headers.set("x-request-id", requestId);
    return new Response(object.body, { status: 200, headers });
  }

  const auth = await requireInstallToken(request, env);
  await enforceRateLimit(env, auth.installId);

  if (path === "/v1/quota" && method === "GET") {
    return jsonResponse(await quotaStatus(env, auth.installId), 200, requestId);
  }

  if (path === "/v1/text-to-speech" && method === "POST") {
    const body = await readJson<{ text?: string; voice?: number; language?: string }>(request, 65536);
    const text = requireString(body.text, "text", 4000);
    const voice = Math.min(5, Math.max(1, Math.round(Number(body.voice ?? 1)) || 1));
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.tts);
    const provider = resolveProvider(env, "tts");
    if (!provider.tts) throw new ApiError(501, "provider_not_configured", "Provider TTS tidak tersedia");
    const result = await provider.tts({ text, voice, language: body.language });
    const response = binaryResponse(result.bytes, result.mime, requestId);
    Object.entries(mockHeaders(provider.mock)).forEach(([k, v]) => response.headers.set(k, v));
    response.headers.set("x-quota-remaining", String(Math.max(0, quota.limit - quota.used)));
    return response;
  }

  if (path === "/v1/image-generate" && method === "POST") {
    const body = await readJson<{ prompt?: string; size?: string }>(request, 65536);
    const prompt = requireString(body.prompt, "prompt", 2000);
    const size = (body.size ?? "1024x1024").replace("×", "x");
    if (!/^\d{2,4}x\d{2,4}$/.test(size)) {
      throw new ApiError(400, "bad_request", "size harus dalam bentuk LEBARxTINGGI, mis. 1024x1024");
    }
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.imageGenerate);
    const provider = resolveProvider(env, "imageGenerate");
    if (!provider.imageGenerate) throw new ApiError(501, "provider_not_configured", "Provider gambar tidak tersedia");
    const result = await provider.imageGenerate({ prompt, size });
    const response = binaryResponse(result.bytes, result.mime, requestId);
    Object.entries(mockHeaders(provider.mock)).forEach(([k, v]) => response.headers.set(k, v));
    response.headers.set("x-quota-remaining", String(Math.max(0, quota.limit - quota.used)));
    return response;
  }

  if (path.startsWith("/v1/image-edit/") && method === "POST") {
    const op = path.slice("/v1/image-edit/".length);
    if (!/^[a-z0-9-]{2,32}$/.test(op)) {
      throw new ApiError(400, "bad_request", "Operasi edit tidak valid");
    }
    const form = await readUploadForm(request, maxUploadBytes);
    const image = optionalFile(form, "image");
    const params: Record<string, string> = { prompt: optionalString(form, "prompt") };
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.imageEdit);
    const provider = resolveProvider(env, "imageEdit");
    if (!provider.imageEdit) throw new ApiError(501, "provider_not_configured", "Provider edit gambar tidak tersedia");
    const result = await provider.imageEdit({
      op,
      bytes: await fileBytes(image),
      mime: image.type || "image/png",
      params,
    });
    const response = binaryResponse(result.bytes, result.mime, requestId);
    Object.entries(mockHeaders(provider.mock)).forEach(([k, v]) => response.headers.set(k, v));
    response.headers.set("x-quota-remaining", String(Math.max(0, quota.limit - quota.used)));
    return response;
  }

  if (path === "/v1/image-analyze" && method === "POST") {
    const form = await readUploadForm(request, maxUploadBytes);
    const image = optionalFile(form, "image");
    const question = requireString(optionalString(form, "question"), "question", 1000);
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.imageAnalyze);
    const provider = resolveProvider(env, "imageAnalyze");
    if (!provider.imageAnalyze) throw new ApiError(501, "provider_not_configured", "Provider vision tidak tersedia");
    const result = await provider.imageAnalyze({
      bytes: await fileBytes(image),
      mime: image.type || "image/png",
      question,
    });
    return jsonResponse(
      {
        text: result.text,
        mock: provider.mock || result.mock === true,
        quotaRemaining: Math.max(0, quota.limit - quota.used),
      },
      200,
      requestId,
    );
  }

  if (path === "/v1/speech-to-text" && method === "POST") {
    const form = await readUploadForm(request, maxUploadBytes);
    const audio = optionalFile(form, "audio");
    const language = optionalString(form, "language");
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.speechToText);
    const provider = resolveProvider(env, "speechToText");
    if (!provider.speechToText) throw new ApiError(501, "provider_not_configured", "Provider STT tidak tersedia");
    const result = await provider.speechToText({
      bytes: await fileBytes(audio),
      mime: audio.type || "audio/wav",
      language: language || undefined,
    });
    return jsonResponse(
      {
        text: result.text,
        mock: provider.mock || result.mock === true,
        quotaRemaining: Math.max(0, quota.limit - quota.used),
      },
      200,
      requestId,
    );
  }

  if (path === "/v1/chat" && method === "POST") {
    const body = await readJson<{ messages?: unknown; maxTokens?: number; temperature?: number }>(request, 65536);
    const messages = Array.isArray(body.messages) ? body.messages : [];
    if (messages.length === 0) {
      throw new ApiError(400, "bad_request", "Field 'messages' wajib berupa array non-kosong");
    }
    const normalized = messages
      .filter((m: any) => m && typeof m.role === "string" && typeof m.content === "string")
      .map((m: any) => ({ role: m.role, content: m.content }));
    if (normalized.length === 0) {
      throw new ApiError(400, "bad_request", "Setidaknya satu pesan dengan role dan content wajib ada");
    }
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.chat);
    const provider = resolveProvider(env, "chat");
    if (!provider.chat) throw new ApiError(501, "provider_not_configured", "Provider chat tidak tersedia");
    const result = await provider.chat({
      messages: normalized,
      maxTokens: body.maxTokens,
      temperature: body.temperature,
    });
    return jsonResponse(
      {
        text: result.text,
        mock: provider.mock,
        quotaRemaining: Math.max(0, quota.limit - quota.used),
      },
      200,
      requestId,
    );
  }

  if (path === "/v1/translate" && method === "POST") {
    const body = await readJson<{ text?: string; targetLang?: string; sourceLang?: string }>(request, 65536);
    const text = requireString(body.text, "text", 4000);
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.translate);
    const provider = resolveTranslateProvider(env);
    if (!provider.translate) throw new ApiError(501, "provider_not_configured", "Provider translate tidak tersedia");
    const result = await provider.translate({
      text,
      targetLang: body.targetLang ?? "id",
      sourceLang: body.sourceLang,
    });
    return jsonResponse(
      {
        text: result.text,
        sourceLang: result.sourceLang,
        mock: provider.mock,
        quotaRemaining: Math.max(0, quota.limit - quota.used),
      },
      200,
      requestId,
    );
  }

  if (path === "/v1/weather" && method === "GET") {
    const latStr = url.searchParams.get("lat");
    const lonStr = url.searchParams.get("lon");
    if (!latStr || !lonStr) {
      throw new ApiError(400, "bad_request", "Parameter 'lat' dan 'lon' wajib diisi");
    }
    const latitude = Number(latStr);
    const longitude = Number(lonStr);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      throw new ApiError(400, "bad_request", "Parameter 'lat' dan 'lon' harus berupa angka");
    }
    const quota = await enforceQuota(env, auth.installId, QUOTA_COST.weather);
    const provider = resolveWeatherProvider(env);
    if (!provider.weather) throw new ApiError(501, "provider_not_configured", "Provider weather tidak tersedia");
    const result = await provider.weather({ latitude, longitude });
    return jsonResponse(
      {
        temperature: result.temperature,
        condition: result.condition,
        location: result.location,
        mock: provider.mock,
        quotaRemaining: Math.max(0, quota.limit - quota.used),
      },
      200,
      requestId,
    );
  }

  if (path === "/v1/video-generate" && method === "POST") {
    const body = await readJson<{ prompt?: string; durationSec?: number; aspect?: string }>(request, 8192);
    const prompt = requireString(body.prompt, "prompt", 1000);
    const durationSec = Math.min(10, Math.max(1, Math.round(Number(body.durationSec ?? 4)) || 4));
    const aspect = ["16:9", "9:16", "1:1"].includes(String(body.aspect)) ? String(body.aspect) : "16:9";
    await enforceQuota(env, auth.installId, QUOTA_COST.video);
    const job = await createVideoJob(env, auth.installId, { prompt, durationSec, aspect });
    return jsonResponse(
      {
        jobId: job.id,
        status: job.status,
        mock: job.mock,
        statusUrl: `/v1/jobs/${job.id}`,
        pollAfterSec: 5,
      },
      202,
      requestId,
    );
  }

  if (path.startsWith("/v1/jobs/") && method === "GET") {
    const id = path.slice("/v1/jobs/".length);
    if (!/^[0-9a-fA-F-]{8,64}$/.test(id)) {
      throw new ApiError(400, "bad_request", "Format job id tidak valid");
    }
    const job = await refreshJob(env, id, auth.installId);
    let downloadUrl: string | undefined;
    let expiresAt: number | undefined;
    if (job.status === "done" && job.objectKey) {
      const signed = await signDownload(env, job.objectKey, 600);
      downloadUrl = signed.path;
      expiresAt = signed.expiresAt;
    }
    return jsonResponse(
      {
        jobId: job.id,
        status: job.status,
        progress: job.progress,
        mock: job.mock,
        mime: job.mime,
        bytes: job.bytes,
        downloadUrl,
        downloadExpiresAt: expiresAt,
        error: job.error,
      },
      200,
      requestId,
    );
  }

  throw new ApiError(404, "not_found", `Endpoint tidak dikenal: ${method} ${path}`);
}
