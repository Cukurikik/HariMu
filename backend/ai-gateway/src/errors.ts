export type ErrorCode =
  | "bad_request"
  | "unauthorized"
  | "forbidden"
  | "not_found"
  | "method_not_allowed"
  | "payload_too_large"
  | "unsupported_media_type"
  | "rate_limited"
  | "quota_exceeded"
  | "provider_not_configured"
  | "provider_error"
  | "job_expired"
  | "internal";

/** Semua error klien memakai bentuk {code, message, retryable} sesuai 01-ARSITEKTUR.md §7. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode;
  readonly retryable: boolean;
  readonly retryAfterSec?: number;
  readonly details?: Record<string, unknown>;

  constructor(
    status: number,
    code: ErrorCode,
    message: string,
    options: { retryable?: boolean; retryAfterSec?: number; details?: Record<string, unknown> } = {},
  ) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.retryable = options.retryable ?? false;
    this.retryAfterSec = options.retryAfterSec;
    this.details = options.details;
  }
}

export function errorResponse(error: ApiError, requestId: string): Response {
  const body: Record<string, unknown> = {
    code: error.code,
    message: error.message,
    retryable: error.retryable,
    requestId,
  };
  if (error.retryAfterSec !== undefined) body.retryAfterSec = error.retryAfterSec;
  if (error.details) body.details = error.details;
  const headers: Record<string, string> = {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-request-id": requestId,
  };
  if (error.retryAfterSec !== undefined) headers["retry-after"] = String(error.retryAfterSec);
  return new Response(JSON.stringify(body), { status: error.status, headers });
}

export function jsonResponse(data: unknown, status: number, requestId: string): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-request-id": requestId,
    },
  });
}

export function binaryResponse(bytes: Uint8Array, mime: string, requestId: string): Response {
  return new Response(bytes, {
    status: 200,
    headers: {
      "content-type": mime,
      "content-length": String(bytes.byteLength),
      "cache-control": "no-store",
      "x-request-id": requestId,
    },
  });
}

export function asApiError(cause: unknown): ApiError {
  if (cause instanceof ApiError) return cause;
  const message = cause instanceof Error ? cause.message : "Kesalahan tak terduga";
  return new ApiError(500, "internal", message, { retryable: true });
}
