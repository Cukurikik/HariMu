# Berkontribusi ke HariMu

## Alur kerja
1. Buat branch dari `main`: `git checkout -b fix/nama-singkat`.
2. Jalankan verifikasi lokal sebelum push:
   ```bash
   ./gradlew assembleDebug testDebugUnitTest lintDebug
   cd backend/ai-gateway && npm ci && npm run typecheck && npm test
   ```
3. Format Kotlin mengikuti ktlint 1.5.0 (CI memeriksa `app/src/**/*.kt`).
4. Ajukan Pull Request dengan deskripsi: masalah, perubahan, dan batas yang belum diuji.

## Aturan kode
- Jangan menambahkan library baru tanpa mencatatnya di `docs/LICENSES.md`.
- Jangan menulis stub lalu mengklaim fitur selesai; tandai batas secara eksplisit di UI, katalog, dan `docs/DECISIONS.md`.
- Jangan pernah meng-commit `keystore.properties`, `*.jks`, `*.keystore`, `.env`, atau token apa pun.
- Rahasia klien (kunci provider AI) tidak boleh masuk APK; gunakan gateway.
- Setiap kenaikan `version` Room **wajib** disertai `Migration` eksplisit (tidak ada destructive upgrade).

## Keamanan
- Temuan kerentanan: buka issue privat/email maintainer, jangan PR publik berisi detail eksploitasi.
- Perubahan pada `VaultCryptoEngine`, `BackupEngine`, `SecretBox`, atau `backend/ai-gateway/src/auth.ts` harus disertai test.
