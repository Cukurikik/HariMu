# HariMu AI Gateway

Cloudflare Worker (TypeScript) untuk semua fitur AI cloud HariMu: TTS, image generate, image
edit, image analyze, speech-to-text, dan job video asynchronous. Spesifikasi endpoint mengikuti
`01-ARSITEKTUR.md` §7.

**Fitur AI berjalan di Cloudflare Workers AI lewat binding `AI` — tidak ada kunci provider
pihak ketiga di repo maupun di APK. APK tidak pernah memegang kunci provider.**

## Endpoint

| Method | Path | Body | Hasil |
|---|---|---|---|
| GET | `/v1/health` | — | `{status, version, mode, corsOrigin}` |
| POST | `/v1/install` | `{installId}` | `{token, expiresAt, mode, limits}` |
| GET | `/v1/quota` | — (Bearer) | `{used, limit, resetsInSec}` |
| POST | `/v1/text-to-speech` | `{text, voice 1..5, language?}` | `audio/wav` |
| POST | `/v1/image-generate` | `{prompt, size}` | `image/png` |
| POST | `/v1/image-edit/:op` | multipart `image`, `prompt` | `image/png` |
| POST | `/v1/image-analyze` | multipart `image`, `question` | `{text, mock, quotaRemaining}` |
| POST | `/v1/speech-to-text` | multipart `audio`, `language?` | `{text, mock, quotaRemaining}` |
| GET | `/v1/geocode?q&lang` | — (Bearer) | `{name, label, latitude, longitude, country, timezone, mock, attribution}` |
| GET | `/v1/rates?base&symbols` | — (Bearer) | `{base, date, rates, source, mock, attribution}` |
| GET | `/v1/dictionary?word&lang` | — (Bearer) | `{word, phonetic, meanings[], source, mock, attribution}` |
| POST | `/v1/video-generate` | `{prompt, durationSec, aspect}` | `202 {jobId, statusUrl, mock, pollAfterSec}` |
| GET | `/v1/jobs/:id` | — (Bearer) | `{status, progress, downloadUrl?, error?}` |
| GET | `/v1/download/:key?exp&sig` | — | byte artefak |

`op` untuk `/v1/image-edit/:op`: `restore`, `colorize`, `upscale`, `style`, `sky`, `cartoon`,
`bokeh`. Klien mengirim prompt tambahan lewat field `prompt`.

## Provider per endpoint (mode `live`)

Semua model di bawah dipanggil lewat `env.AI.run(...)` (binding Workers AI). Nama model bisa
di-override di `wrangler.toml` (`CF_*_MODEL`); nilai default ada di `src/providers/cloudflare.ts`.

| Endpoint | Provider live | Model default | Capability yang TIDAK didukung |
|---|---|---|---|
| `/v1/text-to-speech` | **Cloudflare Workers AI** | `@cf/deepgram/aura-1` (WAV `linear16`, 24 kHz) | Aura-1 hanya bahasa Inggris; parameter `language` klien belum dipetakan ke model |
| `/v1/image-generate` | **Cloudflare Workers AI** | `@cf/black-forest-labs/flux-1-schnell` | `size` dari klien tidak dipakai (model tidak menerima lebar/tinggi); prompt maks. 2048 karakter |
| `/v1/image-edit/:op` | **Cloudflare Workers AI** | `@cf/runwayml/stable-diffusion-v1-5-img2img` | **Bukan inpainting**: tidak ada mask; hasil = img2img dengan prompt op |
| `/v1/image-analyze` | **Cloudflare Workers AI** | `@cf/meta/llama-3.2-11b-vision-instruct` | Butuh persetujuan lisensi Meta sekali (`{"prompt":"agree"}`); tanpa itu → 501 dengan pesan jelas |
| `/v1/speech-to-text` | **Cloudflare Workers AI** | `@cf/openai/whisper` | Audio dikirim sebagai array byte; berkas besar memakai memori Worker (batas unggah 10 MiB) |
| `/v1/video-generate` | **tidak ada** (K6) | — | 501 `provider_not_configured`; adapter Replicate hanya aktif bila `REPLICATE_API_TOKEN` + `REPLICATE_VIDEO_VERSION` diisi |
| `/v1/jobs/:id`, `/v1/download/:key` | KV (`job:<id>`) + R2 (`ARTIFACTS`) | — | Status tidak pernah disimpan di memori Worker, jadi aman saat isolate didaur ulang |
| `/v1/geocode?q=` | **Open-Meteo Geocoding** (tanpa kunci) | — | Non-komersial, < 10.000 panggilan/hari; wajib atribusi CC BY 4.0. Cache KV 30 hari |
| `/v1/rates` | **Frankfurter** (tanpa kunci) | — | Kurs referensi bank sentral (bukan live); boleh komersial; cache 12 jam |
| `/v1/dictionary` | **dictionaryapi.dev / Wiktionary** (tanpa kunci) | — | 12 bahasa, tanpa SLA, atribusi CC BY-SA, cache 7 hari |
| `/v1/weather?q=` | geocoding + provider cuaca | — | Satu permintaan = satu kredit; `location` memakai label hasil geocoding |
| `/v1/health`, `/v1/install`, `/v1/quota` | lokal (HMAC token, rate limit & kuota KV) | — | — |

Mode `mock` (default) mengembalikan keluaran palsu yang **selalu ditandai** (`x-mock: 1`,
`"mock": true`): WAV 440 Hz, PNG 1×1, video byte placeholder. Mode `live` tanpa binding/kunci
menjawab `501 provider_not_configured`, bukan hasil palsu.

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

## Go-live (urutan perintah)

Dijalankan **oleh pemilik** di mesin yang punya Wrangler dan akses akun Cloudflare. Jangan
pernah menempelkan nilai secret ke repo, `.dev.vars` yang di-commit, atau log.

```bash
cd backend/ai-gateway
npm install

# 1. Autentikasi (pilih salah satu; token tidak disimpan di repo)
npx wrangler login
#   atau: export CLOUDFLARE_API_TOKEN=...   # hanya di shell sesi ini
#         export CLOUDFLARE_ACCOUNT_ID=...

# 2. Resource produksi (sekali saja)
npx wrangler kv namespace create JOBS
npx wrangler r2 bucket create harimu-ai-artifacts

# 3. Resource dev (terpisah, supaya uji coba tidak mencampuri produksi)
npx wrangler kv namespace create JOBS --env dev
npx wrangler r2 bucket create harimu-ai-artifacts-dev

# 4. Tempel id KV hasil langkah 2–3 ke wrangler.toml:
#    REPLACE_WITH_PRODUCTION_JOBS_KV_ID  (produksi)
#    REPLACE_WITH_DEV_JOBS_KV_ID         (dev)

# 5. Secret produksi (WAJIB)
openssl rand -hex 32 | npx wrangler secret put INSTALL_TOKEN_SECRET
openssl rand -hex 32 | npx wrangler secret put DOWNLOAD_SIGNING_SECRET
# opsional: gerbang /v1/install (klien harus mengirim header x-install-key)
#   npx wrangler secret put INSTALL_REGISTRATION_KEY
# opsional, HANYA bila video live lewat Replicate:
#   npx wrangler secret put REPLICATE_API_TOKEN
#   npx wrangler secret put REPLICATE_VIDEO_VERSION

#    Secret dev (terpisah):
#   npx wrangler secret put INSTALL_TOKEN_SECRET --env dev
#   npx wrangler secret put DOWNLOAD_SIGNING_SECRET --env dev

# 6. Aktifkan mode live di wrangler.toml SETELAH langkah 5 selesai:
#    PROVIDER_MODE = "live"
#    (dev boleh tetap "mock")

# 7. Batasi origin bila ada pemanggil browser (aplikasi Android tidak mengirim Origin):
#    ALLOWED_ORIGIN = "https://domain-anda"

# 8. Deploy + verifikasi
npx wrangler deploy
curl https://harimu-ai-gateway.<subdomain>.workers.dev/v1/health
#    harapan: {"status":"ok","mode":"live","corsOrigin":"..."}
```

Catatan operasional:

- Bucket R2 tidak menghapus artefak otomatis; pasang lifecycle rule (mis. 7 hari) sebelum produksi.
- Record job di KV kedaluwarsa sesuai `VIDEO_JOB_TTL_SEC` (default 24 jam).
- `PROVIDER_MODE` default `"mock"` supaya deploy yang lupa secret tidak diam-diam memakai model.
- Video tetap 501 di mode live selama `REPLICATE_*` kosong — ini disengaja, bukan bug.

## Batas & proteksi

| Proteksi | Implementasi | Catatan jujur |
|---|---|---|
| Auth | token instal HMAC-SHA256 (`INSTALL_TOKEN_SECRET`), TTL `TOKEN_TTL_SEC` | stateless, bukan JWT; pencabutan = ganti secret |
| Rate limit | KV `rl:<install>:<menit>`, `RATE_LIMIT_PER_MIN` | KV tidak atomik, bisa meleset sedikit saat konkuren; ganti Durable Object untuk batas ketat |
| Kuota harian | KV berbobot: TTS 1, analisa 1, gambar 2, STT 2, video 5 | `DAILY_QUOTA` kredit/hari/instalasi |
| Ukuran unggahan | `content-length` + hitung ulang setelah dibaca, `MAX_UPLOAD_BYTES` | default 10 MiB |
| Batas teks TTS | server 4000 karakter (`requireString`), klien menolak lebih awal (`CORE-03`) | klien menampilkan pesan sebelum memakai kuota |
| Unduhan hasil | URL bertanda tangan HMAC, `exp` 10 menit | **bukan** S3 presign (binding R2 tidak bisa); sifat keamanannya setara |
| Isolasi job | `job:` record menyimpan `installId`, akses job instalasi lain → 404 | status & hasil di KV/R2, bukan memori Worker |
| CORS | `ALLOWED_ORIGIN` (default `*`) | wajib diisi untuk produksi bila ada pemanggil browser |

## Tes

`npm test` menjalankan tes integrasi di workerd/miniflare dengan provider mock, ditambah tes unit
adapter Workers AI (binding `AI` dipalsukan, tanpa panggilan jaringan):

- health & install (token terbit, id pendek ditolak, `corsOrigin` dilaporkan)
- auth: tanpa token, signature dipalsukan, `installId` ditukar → 401
- rate limit: permintaan ke-4 per menit → 429 `rate_limited`
- TTS: container RIFF/WAVE valid, header `x-mock`, kuota berkurang, batas 4000 karakter
- gambar: PNG magic bytes, `size` tidak valid → 400
- vision & STT: jawaban mock ditandai `mock: true`
- unggahan melebihi batas → 413
- video: buat job → polling `done` → unduh artefak; signature palsu → 403; job milik instalasi
  lain → 404; kuota habis → 429 `quota_exceeded`; status job dibaca dari KV dan artefak dari R2
- routing: 404 terstruktur, preflight CORS
- adapter Workers AI: nama model & input yang dikirim (`aura-1`, `flux-1-schnell`, img2img,
  llama vision, whisper), serta pemetaan keluaran ke byte/teks

**Belum dijalankan di lingkungan ini** (tanpa npm/wrangler). Yang belum diuji: panggilan model
Workers AI sungguhan (butuh akun + biaya) dan deploy.

## Status deploy

Worker belum dideploy dari workspace ini; fase K6 hanya menulis kode/konfigurasi. Langkah go-live
di atas tinggal dijalankan pemilik dengan kredensial Cloudflare yang disimpan di project settings.
