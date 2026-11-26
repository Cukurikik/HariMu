# Status API Publik Indonesia (katalog `DATA_PUBLIC_API_INDONESIA_NONE.json`)

Katalog berisi **102 entri** dengan `authentication: false`. Sapuan penuh (Tahap 2) dilakukan pada
**2026-11-26** dengan GET read-only tanpa kunci API: setiap `documentationUrl` diuji, lalu untuk
entri GitHub README mentahnya dibaca untuk mengambil URL API yang benar-benar dipanggil aplikasi.
Status di bawah adalah hasil uji itu, bukan asumsi dari dokumentasi.

## Ringkasan

| Hasil sapuan | Jumlah | Arti |
|---|---|---|
| API hidup dan sudah dipakai aplikasi | 12 entri | Sudah jadi tool `NUS-01..12` dan `CORE-16` |
| API hidup dan layak jadi tool baru | 7 entri | Kandidat Tahap 3 (lihat bawah) |
| API hidup tetapi tidak dipakai | 11 entri | Duplikat, di luar tema, atau datanya kedaluwarsa |
| Dokumentasi hidup, endpoint API tidak terbukti | 48 entri | README tidak menyebut host API atau API harus di-self-host |
| API/host mati | 19 entri | 4xx/5xx permanen, DNS hilang, atau deployment Vercel dimatikan |
| Bukan API ter-hosting (library/data/auth) | 5 entri | Tidak bisa dipanggil sebagai HTTP API publik |

Angka di tabel ini menjumlah tepat 102 entri.

## Dipakai di aplikasi

| API (nama katalog) | Endpoint | Uji terakhir | Status | Tool |
|---|---|---|---|---|
| Data BMKG | `data.bmkg.go.id/DataMKG/TEWS/autogempa.json` | 2026-11-26 | 200 | `NUS-01` |
| Data BMKG | `data.bmkg.go.id/DataMKG/TEWS/gempaterkini.json` | 2026-11-26 | 200 | `NUS-01` |
| Data BMKG | `data.bmkg.go.id/DataMKG/TEWS/gempadirasakan.json` | 2026-11-26 | 200 | `NUS-01` |
| Data BMKG (prakiraan publik) | `api.bmkg.go.id/publik/prakiraan-cuaca?adm4=31.71.01.1001` | 2026-11-26 | 200 | `NUS-02` |
| Nama Provinsi, Kota, Kabupaten | `www.emsifa.com/api-wilayah-indonesia/api/provinces.json` | 2026-11-26 | 200 | `NUS-02`, `NUS-03` |
| Pahlawan Nasional Indonesia | `indonesia-public-static-api.vercel.app/api/heroes` | 2026-11-26 | 200 | `NUS-04` |
| Gunung Berapi di Indonesia | `indonesia-public-static-api.vercel.app/api/volcanoes` | 2026-11-26 | 200 | `NUS-05` |
| Doa Doa API | `doa-doa-api-ahmadramadhan.fly.dev/api` | 2026-11-26 | 200 | `NUS-06` |
| API alamat Desa/Kelurahan | `alamat.thecloudalert.com/api/cari/index/?keyword=…` | 2026-11-26 | 200 | `CORE-16` |
| API Data Sekolah Indonesia | `api-sekolah-indonesia.vercel.app/sekolah?nama=sman%201&page=1` | 2026-11-26 | 200 | `NUS-07` |
| Hibersunda Undak Usuk Basa Sunda | `hibersunda.vercel.app/undakusukbasa/lebet` | 2026-11-26 | 200 | `NUS-08` |
| Kunci Jawaban TTS API | `kunci-tts-api.vercel.app/api/answers?question=tidak` | 2026-11-26 | 200 | `NUS-09` |
| Quran API ID | `quran-api-id.vercel.app/surah/18` dan `/surah/18/1` | 2026-11-26 | 200 | `NUS-10` |
| Klasemen Sepak Bola | `football-standings-api-azharimm.vercel.app/leagues` dan `/leagues/eng.1/standings?season=2024` | 2026-11-26 | 200 | `NUS-11` |
| Pesantren se Indonesia | `api-pesantren-indonesia.vercel.app/provinsi.json` | 2026-11-26 | 200 | `NUS-12` |

Tambahan di luar katalog: `api.myquran.com/v2/sholat/...` (200, 2026-11-26) dipakai `CORE-15`.
Entri katalog **Quran API ID** sekarang dipakai untuk tool `NUS-10` (Al-Qur'an); host
`quran-api-id.vercel.app` tidak sama dengan `api.myquran.com` yang dipakai `CORE-15`.

## API hidup dan layak jadi tool baru

Semua baris sudah diuji dengan contoh permintaan nyata dan mengembalikan JSON berisi data.

| API | Endpoint uji | Status | Isi respons | Catatan |
|---|---|---|---|---|
| API Data Sekolah Seluruh Indonesia | `sekolah.devapi.id/sekolah?nama=madani` | 200 | `data[]` sekolah negeri/swasta | Duplikat fungsi dengan API Data Sekolah Indonesia; pilih satu |
| Lirik lagu | `song-lyrics-api-azharimm.vercel.app/search?q=Kala%20cinta%20menggoda` | 200 | `songId`, artist, songTitle, URL lirik | Detail di `/lyrics/{songId}` |
| Alquran ID (bachors) | `al-quran-8d642.firebaseio.com/data.json` | 200 | 114 surah: arti, asma, audio, jumlah ayat | Duplikat Quran API ID, data lebih tua |
| Jadwal Sholat (lakuapik) | `cdn.statically.io/gh/lakuapik/jadwalsholatorg/master/adzan/semarang/2026/11.json` | 200 | jadwal harian per kota per tahun | Duplikat `CORE-15`; berguna sebagai cadangan luring |
| Kode Pos (nbc.vanmason) | `nbc.vanmason.web.id/service/kodepos/42173` | 200 | kelurahan + kecamatan per kode pos | Duplikat `CORE-16` |
| Translasi | `api-translate-azharimm.vercel.app/` | 200 | terjemahan id/en | Sudah tercakup teks dua bahasa aplikasi |
| Wikipedia API | `www.mediawiki.org/wiki/API:Tutorial` | 200 | pencarian & ringkasan artikel | Umum, bukan khas Indonesia |

## API hidup tetapi tidak dipakai

| API | Endpoint | Status | Alasan |
|---|---|---|---|
| Kotonogi API | `kotonogi-api.vercel.app` | 200 | Materi huruf Jepang, di luar tema aplikasi |
| LK21 & NontonDrama / LK21 API | `tv.nontondrama.lol` | 200 | Konten streaming tidak berlisensi |
| Student Blue Archive | `github.com/arufars/api-blue-archive` | 200 | Data wiki game, di luar tema |
| Data RS rujukan COVID-19 | `dekontaminasi.com/api/id/covid19/hospitals` | 200 | Data berhenti sekitar 2020 |
| Data GeoJSON WhatGeo | `z4nr.github.io/WhatGeo/` | 200 | Berkas statis GitHub Pages, bukan API |
| Kode Pos (bachors) | `kodepos-2d475.firebaseio.com/list_propinsi.json` | 200 | Root database 401; hanya sebagian path terbuka |
| Lambang Daerah | `symbolsofindonesia.vercel.app/provinces/33/200` | 200 | URL gambar mengembalikan halaman galat Wikimedia |
| Twitter Trends | `twitter-trends-api-azharimm.vercel.app/` | 200 | Bergantung pada endpoint Twitter yang tidak stabil |
| Google Playstore | `google-play-api-azharimm.vercel.app/` | 200 | Pencarian toko aplikasi, di luar cakupan |
| Screenshot API | `statically.io` | 200 | Layanan umum, bukan data Indonesia |

## Dokumentasi hidup, endpoint API tidak terbukti

Dokumentasi (umumnya README GitHub) merespons 200, tetapi tidak ada host API yang bisa dipanggil
tanpa menebak path, API-nya harus dijalankan sendiri, atau contoh di README sudah tidak berlaku.
Entri ini **tidak** dijadikan dasar tool baru sampai endpoint-nya terbukti.

Buku Indonesia · Bukuacak · Indodax · API KBBI Daring · API KBBI Luring · Data Sekolah API
(AhmadRamadhan) · KBBI Complete REST API · Maganghub API · New KBBI API · 1Cak · AnimeAPI · Anitop ·
Epic Free Games · FILMAPIK · JagoKata · Katanime · MyInstants · Otakudesu · Quotes Generator ·
Strygwyr · Tanggal Lahiran Pasaran Zodiak · Unofficial Liga Indonesia · YAAAS · API Harga Emas
(cacing69) · Harga Emas (codeberg) · OJK Investasi · Bahasa Daerah · Batik Indonesia · rs-bed
availability · rs-bed-covid-indo · Al-Qur'an Indonesia · Dua & Dhikr · Hadith API · Holy Quran API ·
Quran API (rzkytmgr) · Quran JSON · Waktu Sholat API · Daftar nama Bank (thecloudalert) · Distrowatch
(diwa) · Indiwtf · Akuari · Ryzumi · CNN Indonesia · Detik News · Jakarta Post · The Lazy Media ·
Facebook Video Downloader · Unofficial Youtube · API & Graphql Cuaca BMKG (pace11)

Catatan: `jakpost.vercel.app/api` dan `the-lazy-media-api.vercel.app/api` menjawab 200 dengan JSON
sapaan, tetapi path berita sesungguhnya (`/api/latest`, `/api/terbaru`) 404 — belum dipakai.

## API/host mati

| API | Endpoint | Status | Catatan |
|---|---|---|---|
| Masak Apa | `masak-apa-tomorisakura.vercel.app/api/recipes` | 500 | `FUNCTION_INVOCATION_FAILED` |
| Rekeningku | `api.rekeningku.com` | DNS | Hostname tidak lagi punya alamat |
| KBBI API (azharimm) | `kbbi-api-azharimm.vercel.app` | 404 | `DEPLOYMENT_NOT_FOUND` |
| Spesifikasi HP (azharimm) | `phone-specs-api-azharimm.vercel.app` | 404 | `DEPLOYMENT_NOT_FOUND` |
| Currency Exchange (azharimm) | `currency-exchange-api-azharimm.vercel.app` | 404 | `DEPLOYMENT_NOT_FOUND` |
| Google Trends (azharimm) | `google-trends-api-azharimm.vercel.app` | 404 | `DEPLOYMENT_NOT_FOUND` |
| Info Gempa & Cuaca | `cuaca-gempa-rest-api.vercel.app` | 404 | `DEPLOYMENT_NOT_FOUND` |
| Data COVID-19 Indonesia | `apicovid19indonesia-v2.vercel.app/api` | 500 | `FUNCTION_INVOCATION_FAILED` |
| API Berita Indonesia | `api-berita-indonesia.vercel.app/cnn/terbaru/` | 402 | `DEPLOYMENT_DISABLED` |
| Berita Indo API | `berita-indo-api.vercel.app` | 402 | `DEPLOYMENT_DISABLED` |
| Indo News API | `indo-news-api.vercel.app` | 402 | `DEPLOYMENT_DISABLED` |
| Dayoff API | `dayoffapi.vercel.app` | 402 | `DEPLOYMENT_DISABLED` |
| Kode Pos (sooluh) | `kodepos.vercel.app/search/?q=…` | 402 | Mirror `kodepos.onrender.com` 200 tetapi free tier tidur |
| Daftar Stasiun Kereta Api | `booking.kai.id/api/stations2` | 403 | Menolak akses publik |
| Nusantara API | `nusantara.clowdlab.com/docs` | 403 | Diblokir Cloudflare challenge |
| KyioAPI | `api.kyio.web.id/docs` | 403 | Diblokir Cloudflare challenge |
| Manga/Komik API | `mangamint.kaedenoki.net/api/...` | — | Hanya mengembalikan halaman pengalihan JavaScript |
| BMKG JSON API (ibnux) | `ibnux.github.io/BMKG-importer/…` | 404 | Endpoint contoh di dokumentasi sudah tidak ada |
| Puasa Sunnah API | `api.puasa-sunnah.granitebps.com/api/v1/puasa?year=…` | 404 | Swagger hidup; path perlu dibaca dari `swagger/doc.json` |

## Bukan API ter-hosting

`API KBBI Luring` dan `PDDIKTI wrapper` adalah basis data/pustaka Python yang harus dijalankan
sendiri; `idn-area` adalah pustaka data wilayah; `WhatsApp Cloud API Wrapper` dan `WhatsApp Official
API` memerlukan token Meta sehingga tidak termasuk katalog tanpa-autentikasi yang bisa dipakai
langsung.

## Catatan metode

Pengujian memakai GET read-only tanpa kunci API, `User-Agent` browser, batas waktu 25 detik, dan
paralel 10 permintaan. Status 402/403/404/500 di atas berasal dari host tujuan, bukan dari jaringan
penguji — dipastikan dengan kontrol positif (`data.bmkg.go.id` dan `api-sekolah-indonesia.vercel.app`
menjawab 200 pada waktu yang sama). Angka status adalah kondisi 2026-11-26 dan bisa berubah.
