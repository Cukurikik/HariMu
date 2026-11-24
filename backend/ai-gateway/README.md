# HariMu AI Gateway

Cloudflare Worker (TypeScript) untuk semua fitur AI cloud HariMu: TTS, image generate, image
edit, image analyze, speech-to-text, dan job video asynchronous. Spesifikasi endpoint mengikuti
`01-ARSITEKTUR.md` §7.

**Kunci provider AI hanya ada di Worker (secret). APK tidak pernah memegang kunci provider.**

## Endpoint

| Method | Path | Body | Hasil |
|---|---|---|---|
| GET | `/v1/health` | — | `{status, version, mode}` |
| POST | `/v1/install` | `{installId}` | `{token, expiresAt, mode, limits}` |
| GET | `/v1/quota` | — (Bearer) | `{used, limit, resetsInSec}` |
| POST | `/v1/text-to-speech` | `{text, voice 1..5, language?}` | `audio/wav` |
| POST | `/v1/image-generate` | `{prompt, size}` | `image/png` |
| POST | `/v1/image-edit/:op` | multipart `image`, `prompt` | `image/png` |
| POST | `/v1/image-analyze` | multipart `image`, `question` | `{text, mock, quotaRemaining}` |
| POST | `/v1/speech-to-text` | multipart `audio`, `language?` | `{text, mock, quotaRemaining}` |
| POST | `/v1/video-generate` | `{prompt, durationSec, aspect}` | `202 {jobId, statusUrl, mock, pollAfterSec}` |
| GET | `/v1/jobs/:id` | — (Bearer) | `{status, progress, downloadUrl?, error?}` |
| GET | `/v1/download/:key?exp&sig` | — | byte artefak |

`op` untuk `/v1/image-edit/:op`: `restore`, `colorize`, `upscale`, `style`, `sky`, `cartoon`,
`bokeh`. Klien mengirim prompt tambahan lewat field `prompt`.

### Error terstruktur

Semua error berbentuk:

```json
{ "code": "quota_exceeded", "message": "…", "retryable": false, "requestId": "…", "details": { } }
```

Kode: `bad_request`, `unauthorized`, `forbidden`, `not_found`, `method_not_allowed`,
`payload_too_large`, `unsupported_media_type`, `rate_limited`, `quota_exceeded`,
`provider_not_configured`, `provider_error`, `job_expired`, `internal`.

Kode 429 menyertakan header `retry-after` (detik). Klien wajib menghormati `retryable` dan
`retry-after` sebelum mencoba ulang.

## Mode provider

- `PROVIDER_MODE=mock` (default) — **semua keluaran palsu dan ditandai**:
  respons biner membawa header `x-mock: 1`, respons JSON membawa `"mock": true`.
  TTS menghasilkan WAV 440 Hz yang valid, gambar berupa PNG 1×1, video berupa byte placeholder
  yang tidak bisa diputar. Dipakai untuk tes dan pengembangan tanpa kunci.
- `PROVIDER_MODE=live` — memakai adapter nyata:
  - `OPENAI_API_KEY` untuk TTS, image generate/edit, vision, dan speech-to-text
    (`OPENAI_BASE_URL` bisa diarahkan ke endpoint kompatibel OpenAI).
  - `REPLICATE_API_TOKEN` + `REPLICATE_VIDEO_VERSION` untuk video async.
  Capability yang kuncinya tidak ada menjawab `501 provider_not_configured` — bukan hasil palsu.

## Batas & proteksi

| Proteksi | Implementasi | Catatan jujur |
|---|---|---|
| Auth | token instal HMAC-SHA256 (`INSTALL_TOKEN_SECRET`), TTL `TOKEN_TTL_SEC` | tanstatik, bukan JWT; pencabutan = ganti secret |
| Rate limit | KV `rl:<install>:<menit>`, `RATE_LIMIT_PER_MIN` | KV tidak atomik, bisa meleset sedikit saat konkuren; ganti Durable Object untuk batas ketat |
| Kuota harian | KV berbobot: TTS 1, analisa 1, gambar 2, STT 2, video 5 | `DAILY_QUOTA` kredit/hari/instalasi |
| Ukuran unggahan | `content-length` + hitung ulang setelah dibaca, `MAX_UPLOAD_BYTES` | default 10 MiB |
| Unduhan hasil | URL bertanda tangan HMAC, `exp` 10 menit | **bukan** S3 presign (binding R2 tidak bisa); sifat keamanannya setara |
| Isolasi job | `job:` record menyimpan `installId`, akses job instalasi lain → 404 | — |

Kebijakan retensi: record job di KV kedaluwarsa `VIDEO_JOB_TTL_SEC` (default 24 jam). Artefak
R2 tidak otomatis dihapus — tambahkan lifecycle rule pada bucket (mis. 7 hari) sebelum produksi.

## Setup

```bash
cd backend/ai-gateway
npm install

# 1. Buat resource (sekali saja)
npx wrangler kv namespace create JOBS
npx wrangler kv namespace create JOBS --env dev
npx wrangler r2 bucket create harimu-ai-artifacts
npx wrangler r2 bucket create harimu-ai-artifacts-dev

# 2. Tempel id KV ke wrangler.toml (REPLACE_WITH_KV_ID / REPLACE_WITH_DEV_KV_ID)

# 3. Secret (tidak pernah masuk repo / APK)
openssl rand -hex 32 | npx wrangler secret put INSTALL_TOKEN_SECRET
openssl rand -hex 32 | npx wrangler secret put DOWNLOAD_SIGNING_SECRET
# opsional, hanya untuk mode live:
#   npx wrangler secret put OPENAI_API_KEY
#   npx wrangler secret put REPLICATE_API_TOKEN
#   npx wrangler secret put REPLICATE_VIDEO_VERSION

# 4. Tes lokal & deploy
npm test            # vitest + miniflare (workerd)
npm run typecheck
npx wrangler deploy --env dev
curl https://harimu-ai-gateway-dev.<subdomain>.workers.dev/v1/health
```

Jangan pernah menuliskan nilai secret ke `wrangler.toml`, file `.dev.vars` yang di-commit, atau
ke log. Untuk dev lokal pakai `npx wrangler dev --env dev` dan isi secret lewat
`npx wrangler secret put ... --env dev`.

## Tes

`npm test` menjalankan 21 tes integrasi di workerd/miniflare dengan provider mock:

- health & install (token terbit, id pendek ditolak)
- auth: tanpa token, signature dipalsukan, `installId` ditukar → 401
- rate limit: permintaan ke-4 per menit → 429 `rate_limited`
- TTS: container RIFF/WAVE valid, header `x-mock`, kuota berkurang
- gambar: PNG magic bytes, `size` tidak valid → 400
- vision & STT: jawaban mock ditandai `mock: true`
- unggahan melebihi batas → 413
- video: buat job → polling `done` → unduh artefak; signature palsu → 403; job milik instalasi
  lain → 404; kuota habis → 429 `quota_exceeded`
- routing: 404 terstruktur, preflight CORS

Yang **tidak** diuji di sini: pemanggilan provider nyata (butuh kunci berbayar) dan penerbitan
presigned URL S3. Keduanya diverifikasi manual saat kredensial tersedia.

## Status deploy

Worker belum dideploy dari workspace ini karena `wrangler whoami` melaporkan belum login dan
tidak ada `CLOUDFLARE_API_TOKEN` di lingkungan. Kode, tes (21/21 lulus) dan typecheck siap;
`npx wrangler deploy --env dev` tinggal dijalankan setelah langkah setup di atas.
