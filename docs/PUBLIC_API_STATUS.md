# Status API Publik Indonesia (katalog `DATA_PUBLIC_API_INDONESIA_NONE.json`)

Katalog berisi **102 entri** dengan `authentication: false`. Dokumen ini mencatat mana yang sudah
diuji langsung dan dipakai aplikasi, mana yang mati, dan mana yang belum disapu.

## Dipakai di aplikasi

| API (nama katalog) | Endpoint | Uji terakhir | Status | Tool |
|---|---|---|---|---|
| Data BMKG | `data.bmkg.go.id/DataMKG/TEWS/autogempa.json` | 2026-11-26 | 200 | `NUS-01` |
| Data BMKG | `data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json` | 2026-11-26 | 200 | `NUS-01` |
| BMKG (prakiraan publik) | `api.bmkg.go.id/publik/prakiraan-cuaca?adm4=31.71.01.1001` | 2026-11-26 | 200 | `NUS-02` |
| Nama Provinsi, Kota, Kabupaten | `www.emsifa.com/api-wilayah-indonesia/api/provinces.json` | 2026-11-26 | 200 | `NUS-02`, `NUS-03` |
| Pahlawan Nasional Indonesia | `indonesia-public-static-api.vercel.app/api/heroes` | 2026-11-26 | 200 | `NUS-04` |
| Gunung Berapi di Indonesia | `indonesia-public-static-api.vercel.app/api/volcanoes` | 2026-11-26 | 200 | `NUS-05` |
| Doa Doa API | `doa-doa-api-ahmadramadhan.fly.dev/api` | 2026-11-26 | 200 | `NUS-06` |
| API alamat Desa/Kelurahan | `alamat.thecloudalert.com/api/cari/index/?keyword=…` | 2026-11-26 | 200 | `CORE-16` |
| Quran API ID | `api.myquran.com/v2/sholat/kota/cari/…` | 2026-11-26 | 200 | `CORE-15` |

## Terverifikasi mati / tidak layak

| API | Endpoint | Status | Catatan |
|---|---|---|---|
| API Berita Indonesia | `api-berita-indonesia.vercel.app/cnn/terbaru/` | 402 | Deployment Vercel dimatikan (`DEPLOYMENT_DISABLED`) |
| Kode Pos (sooluh) | `kodepos.vercel.app/search/?q=…` | 402 | Mirror `kodepos.onrender.com` masih 200 tetapi free tier tidur (cold start puluhan detik) |
| Daftar Stasiun Kereta Api | `booking.kai.id/api/stations2` | 403 | Menolak akses publik |
| Puasa Sunnah API | `api.puasa-sunnah.granitebps.com/api/v1/puasa?year=…` | 404 | Swagger hidup; path perlu dibaca dari `swagger/doc.json` sebelum dipakai |
| Dayoff API | `dayoffapi.vercel.app` | 402 | Deployment dimatikan |
| BMKG JSON API (ibnux) | `ibnux.github.io/BMKG-importer/…` | 404 | Endpoint contoh di dokumentasi sudah tidak ada |

## Belum disapu

Sisa entri katalog (kategori Hiburan 20, Pendidikan 17, Agama Islam 12, Lokasi 12, Berita 7,
Utilitas 7, Sosial Media 5, Finansial 4, Pemerintahan 4, Kesehatan 4, Buku 3, Serba Guna 3,
Cuaca 3, Kripto 2, Musik 1, Umum 1) belum diuji satu per satu. Rencana Tahap 2: sapuan otomatis
`curl -o /dev/null -w "%{http_code}"` per `documentationUrl`, hasilnya dimasukkan ke tabel ini
sebelum ada tool baru yang dibangun di atasnya.

Catatan metode: pengujian dilakukan dengan GET read-only (browser/`read_url`), tanpa kunci API.
Status 402 di atas berasal dari host tujuan, bukan dari jaringan penguji — dipastikan dengan
kontrol positif (`api.myquran.com` menjawab 200 pada waktu yang sama).
