import type { Env } from "./config";
import { num } from "./config";
import { ApiError } from "./errors";
import { resolveProvider } from "./providers";
import type { VideoRequest } from "./providers";

export type JobStatus = "queued" | "running" | "done" | "failed";

export interface JobRecord {
  id: string;
  installId: string;
  kind: "video";
  prompt: string;
  status: JobStatus;
  progress: number;
  providerJobId: string;
  mock: boolean;
  createdAt: number;
  updatedAt: number;
  objectKey?: string;
  mime?: string;
  bytes?: number;
  error?: string;
}

const jobKey = (id: string) => `job:${id}`;

export async function createVideoJob(env: Env, installId: string, request: VideoRequest): Promise<JobRecord> {
  const provider = resolveProvider(env, "video");
  if (!provider.startVideo) {
    throw new ApiError(501, "provider_not_configured", "Provider aktif tidak mendukung video generation");
  }
  const started = await provider.startVideo(request);
  const now = Date.now();
  const record: JobRecord = {
    id: crypto.randomUUID(),
    installId,
    kind: "video",
    prompt: request.prompt.slice(0, 500),
    status: "queued",
    progress: 0,
    providerJobId: started.providerJobId,
    mock: provider.mock,
    createdAt: now,
    updatedAt: now,
  };
  await saveJob(env, record);
  return record;
}

async function saveJob(env: Env, record: JobRecord): Promise<void> {
  const ttl = num(env.VIDEO_JOB_TTL_SEC, 24 * 3600);
  await env.JOBS.put(jobKey(record.id), JSON.stringify(record), { expirationTtl: ttl });
}

export async function loadJob(env: Env, id: string): Promise<JobRecord> {
  const raw = await env.JOBS.get(jobKey(id));
  if (!raw) {
    throw new ApiError(404, "not_found", "Job tidak ditemukan atau sudah kedaluwarsa", { retryable: false });
  }
  return JSON.parse(raw) as JobRecord;
}

/**
 * Mengambil job dan, bila masih berjalan, menanyakan provider sekali. Hasil akhir disimpan di R2
 * dan hanya bisa diunduh lewat URL bertanda tangan berumur pendek (lihat downloads.ts).
 */
export async function refreshJob(env: Env, id: string, installId: string): Promise<JobRecord> {
  const record = await loadJob(env, id);
  if (record.installId !== installId) {
    // Jangan bebaskan informasi apakah job ada: 404 yang sama seperti tidak ditemukan.
    throw new ApiError(404, "not_found", "Job tidak ditemukan");
  }
  if (record.status === "done" || record.status === "failed") return record;

  const provider = resolveProvider(env, "video");
  if (!provider.pollVideo) return record;

  const poll = await provider.pollVideo(record.providerJobId);
  record.updatedAt = Date.now();
  if (poll.status === "failed") {
    record.status = "failed";
    record.error = poll.error ?? "Generasi video gagal";
    await saveJob(env, record);
    return record;
  }
  if (poll.status === "running") {
    record.status = "running";
    record.progress = poll.progress ?? record.progress;
    await saveJob(env, record);
    return record;
  }
  const result = poll.result;
  if (!result) {
    record.status = "failed";
    record.error = "Provider selesai tanpa berkas hasil";
    await saveJob(env, record);
    return record;
  }
  const objectKey = `jobs/${record.id}/result`;
  await env.ARTIFACTS.put(objectKey, result.bytes, {
    httpMetadata: { contentType: result.mime },
    customMetadata: { installId: record.installId, jobId: record.id, mock: String(record.mock) },
  });
  record.status = "done";
  record.progress = 100;
  record.objectKey = objectKey;
  record.mime = result.mime;
  record.bytes = result.bytes.byteLength;
  await saveJob(env, record);
  return record;
}
