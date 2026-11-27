# P12 — Nusantara & Everyday Utilities Redesign

Dokumen ini merangkum desain ulang dan integrasi ekosistem **HariMu Nusantara** (`NUS-01` s.d. `NUS-12`) serta utilitas harian inti (`CORE-15` Jadwal Sholat, `CORE-16` Kode Pos & Wilayah, `CORE-10` Key Vault, dan `CORE-11` Cadangan Workspace `.omni`) sesuai panduan Studio Precision (`/docs/DESIGN-SYSTEM.md`).

---

## 1. Tujuan & Prinsip Kejujuran Data

- **Ekosistem Utilitas Indonesia Terpadu**: Menyatukan 12 layanan Nusantara dan utilitas harian dalam satu bahasa visual, navigasi, pencarian, filter cepat, status muat, error, kosong, dan atribusi sumber.
- **Kejujuran Kesegaran & Ketersediaan Offline**:
  - Tidak ada cache/penyimpanan offline Room pada repositori API Nusantara, Jadwal Sholat, atau Kode Pos; seluruh hasil diambil langsung dari API publik saat sesi berjalan.
  - Setiap layar menampilkan `UtilityAttributionFooter` dengan sumber data spesifik, catatan bahwa data **memerlukan internet dan dimuat untuk sesi ini saja (tidak disimpan offline)**, serta waktu pengambilan lokal sesi (`fetchedAtMillis`) atau keterangan bahwa data belum dimuat pada sesi ini.
  - Data geografis/referensi (`Regions`, `Weather`, `Heroes`, `Volcanoes`, `Schools`, `Pesantren`) menampilkan persis data yang dikembalikan oleh API tanpa fabrikasi koordinat, wilayah, atau status kesegaran real-time (khususnya `VolcanoesScreen` menegaskan bahwa dataset bersifat referensi statis, bukan pemantauan aktivitas vulkanik live).

---

## 2. Sistem Komponen Utilitas Bersama (`UtilityComponents.kt`)

Berkas `app/src/main/java/com/harimu/app/core/design/utilities/UtilityComponents.kt` menggantikan helper lama `NusantaraUi.kt` (yang telah dihapus setelah migrasi seluruh pemanggil) dengan komponen Studio Precision yang seragam:

| Komponen | Fungsi Utama | Detail Desain & Aksesibilitas |
|---|---|---|
| `UtilityScreenScaffold` | Kerangka `Scaffold` + `TopAppBar` | Lebar konten dibatasi `catalogMaxWidth` (`760.dp`) atau `dashboardMaxWidth` (`1040.dp`), tombol kembali & refresh dengan `testTag` yang membungkus area sentuh minimal `48.dp × 48.dp`. |
| `UtilityHeroBanner` | Panel pengenalan layanan | Memakai permukaan `surfaceContainerHigh`, garis tepi `1.dp`, serta lencana `contrastSafeAccent` (tanpa gradien). |
| `UtilityBadgePill` | Lencana kode/status monospace | Dipakai untuk kode `adm4`, kode pos, magnitudo, `NPSN`, `NSPP`, dan jumlah huruf TTS. |
| `UtilitySearchBar` | Pencarian / input tervalidasi | `OutlinedTextField` satu baris dengan IME action (`Search`/`Done`) dan tombol hapus `48.dp`. |
| `UtilityActionButton` | Tombol eksekusi utama | Tinggi minimal `controls.buttonMinHeight`, indikator progres `CircularProgressIndicator`, dan pelindung klik ganda saat memuat. |
| `UtilityQuickChips` | Bar chip filter / preset horizontal | `LazyRow` berisi `FilterChip` dengan kontras aksen aman dan tinggi sentuh minimal `48.dp`. |
| `UtilityBreadcrumbBar` | Jejak hierarki wilayah dapat diketuk | `FlowRow` interaktif pada layar bertingkat (`RegionsScreen`, `WeatherScreen`, `PesantrenScreen`). |
| `UtilityLoadingCard`, `UtilityErrorCard`, `UtilityEmptyState` | Status muat, galat + coba lagi, dan kosong/panduan | Pesan galat ramah pengguna + tombol retry terstandar; empty state dengan ikon & aksi reset jika relevan. |
| `UtilitySectionCard` & `UtilityCopyIconButton` | Kartu konten tanpa kartu bersarang + salin cepat | Detail internal memakai `HariMuInsetPanel` & `HariMuDivider`; salin clipboard menampilkan umpan balik toast. |
| `UtilityAttributionFooter` | Atribusi & transparansi sesi | Sumber resmi API publik, batasan penggunaan (mis. verifikasi mushaf/peringatan BMKG), dan stempel waktu sesi `HH:mm`. |

---

## 3. Peningkatan Layar & ViewModel

### A. Hub & Utilitas Inti
- **`NusantaraHubScreen` + `NusantaraHubViewModel`**:
  - Menyajikan pencarian real-time, chip kategori (`Semua`, `Info Langsung & Cuaca`, `Ibadah & Bahasa`, `Wilayah & Direktori`, `Referensi & Harian`), bar akses cepat utilitas harian (`CORE-15`, `CORE-16`, `CORE-12`, `CORE-13`, `NUS-01`, `NUS-02`, `NUS-10`, `NUS-06`), banner gempa BMKG terkini, tata letak responsif 1 kolom (`<600.dp`) / 2 kolom (`≥600.dp`), empty state dengan reset filter, serta `UtilityAttributionFooter`.
  - `TopLevelDestination.NUSANTARA` kini menyetel `providesOwnTopBar = true` (dengan `showInNavigationBar = false` tetap terjaga) agar tidak ada top bar ganda saat dibuka dari shell.
- **`PrayerTimesScreen` (`CORE-15`) + `PrayerTimesViewModel`**:
  - Preset kota populer Indonesia, jadwal 8 waktu sholat, penyorotan waktu sholat terdekat berdasarkan jam lokal perangkat (`findNextPrayerIndex`), tombol salin jadwal lengkap, penanganan error + retry, dan catatan kesegaran berbasis tanggal perangkat.
- **`AreaLookupScreen` (`CORE-16`) + `AreaLookupViewModel`**:
  - Contoh pencarian cepat (`Soreang`, `40915`, `Gambir`, `Surabaya`, `Yogyakarta`), status awal sebelum mencari, status kosong dengan tombol bersihkan filter, hasil terstruktur (kelurahan, kecamatan, kab/kota, provinsi), dan tombol salin kode pos.
- **`BackupScreen` (`CORE-11`) & `KeyVaultScreen` (`CORE-10`)**:
  - `BackupScreen` dibungkus `HariMuResponsiveContainer`, memindahkan penyalinan stream SAF ke `Dispatchers.IO`, memakai string terlokalisasi penuh (`backup_saved_toast`), `testTag` aksi, dan tinggi tombol minimal `48.dp`.
  - `KeyVaultScreen` memakai pembatas lebar `catalogMaxWidth`, `contentDescription` terlokalisasi (`cd_back`, `key_vault_delete_cd`), dan target sentuh `minimumInteractiveComponentSize()`.

### B. Layar Nusantara (`NUS-01` s.d. `NUS-12`)
- **`NUS-01` `EarthquakeScreen`**: Kartu gempa terbaru dengan lencana magnitudo, panel waktu & kedalaman, banner status tsunami (`StatusTone.WARNING` vs `INFO`), wilayah dirasakan, salin koordinat, peta guncangan BMKG (`AsyncImage`), dan daftar gempa terkini/kosong.
- **`NUS-02` `WeatherScreen` + `WeatherViewModel`**: Mendukung navigasi langsung via `initialAdm4` (`BmkgWeatherRoute(initialAdm4: String? = null)`), filter teks di tiap level wilayah, `provincesJob` terpisah agar pemuatan daftar provinsi tidak membatalkan `loadForecastForAdm4`, kondisi terlokalisasi (`conditionEn` vs `conditionDescId`), serta kelembapan & kecepatan angin.
- **`NUS-03` `RegionsScreen` + `RegionsViewModel`**: Breadcrumb bertingkat yang bisa diketuk untuk naik kembali ke level sebelumnya, filter daftar wilayah, `Paging 3` daftar desa dengan penanganan error refresh/append + retry, salin kode wilayah, dan CTA langsung **"Lihat Cuaca BMKG"** yang meneruskan kode `adm4` ke `WeatherScreen`.
- **`NUS-04` `HeroesScreen` & `NUS-05` `VolcanoesScreen`**: Kartu referensi terstruktur, filter pencarian dengan aksi reset saat kosong, salin koordinat gunung berapi, dan catatan statis jujur.
- **`NUS-06` `DoasScreen`**: Header jumlah doa, kartu dapat diperluas/dilipat dengan ikon & deskripsi aksesibilitas terlokalisasi, panel Arab + transliterasi + arti, tombol salin doa lengkap, dan reset filter saat kosong.
- **`NUS-07` `SchoolScreen`**: Validasi input minimal 3 karakter dengan petunjuk eksplisit, pencarian IME, badge `NPSN`/jenjang/status, salin `NPSN` & koordinat, serta `Paging 3` tombol/indikator muat berikutnya.
- **`NUS-08` `SundaneseScreen`**: Chip contoh kata, tombol lookup, dan kartu terstruktur *Undak Usuk Basa* (`Sorangan`, `Batur`, `Loma`) beserta arti Indonesia/Inggris dan salin entri.
- **`NUS-09` `TtsScreen`**: Chip contoh petunjuk TTS, kartu kandidat jawaban dengan badge jumlah huruf, peringkat bintang, dan tombol salin jawaban.
- **`NUS-10` `QuranScreen`**: `BackHandler` untuk kembali dari detail surah ke daftar surah, panel pengingat verifikasi mushaf cetak, salin ayat, dan pemutaran audio ayat via `MediaPlayer` yang aman dengan pembersihan `DisposableEffect`.
- **`NUS-11` `FootballScreen`**: `BackHandler` untuk kembali ke daftar liga, switcher musim berbasis `UtilityQuickChips`, ikon panah Material, serta empty state untuk liga maupun klasemen musim.
- **`NUS-12` `PesantrenScreen`**: `BackHandler` bertahap (`pesantren` → `kab/kota` → `provinsi`), breadcrumb dapat diketuk, filter pencarian provinsi & kabupaten/kota, serta salin `NSPP`.

---

## 4. Berkas Utama yang Diubah / Ditambahkan

- **Komponen & Tema**: `app/src/main/java/com/harimu/app/core/design/utilities/UtilityComponents.kt`
- **Navigasi**: `app/src/main/java/com/harimu/app/navigation/TopLevelDestination.kt`, `app/src/main/java/com/harimu/app/MainActivity.kt`
- **Nusantara UI & ViewModel**:
  - `feature/nusantara/NusantaraHubScreen.kt`, `NusantaraHubViewModel.kt`
  - `feature/nusantara/EarthquakeScreen.kt`, `EarthquakeViewModel.kt`
  - `feature/nusantara/WeatherScreen.kt`, `WeatherViewModel.kt`
  - `feature/nusantara/RegionsScreen.kt`, `RegionsViewModel.kt`
  - `feature/nusantara/ReferenceScreens.kt`, `ReferenceViewModel.kt`
  - `feature/nusantara/DoasScreen.kt`, `DoasViewModel.kt`
  - `feature/nusantara/SchoolScreen.kt`, `SchoolViewModel.kt`
  - `feature/nusantara/SundaneseScreen.kt`, `SundaneseViewModel.kt`
  - `feature/nusantara/TtsScreen.kt`, `TtsViewModel.kt`
  - `feature/nusantara/QuranScreen.kt`, `QuranViewModel.kt`
  - `feature/nusantara/FootballScreen.kt`, `FootballViewModel.kt`
  - `feature/nusantara/PesantrenScreen.kt`, `PesantrenViewModel.kt`
  - Dihapus: `feature/nusantara/NusantaraUi.kt`
- **Utilitas Inti**:
  - `feature/prayer/PrayerTimesScreen.kt`, `PrayerTimesViewModel.kt`
  - `feature/area/AreaLookupScreen.kt`, `AreaLookupViewModel.kt`
  - `feature/studios/BackupScreen.kt`, `KeyVaultScreen.kt`
- **Sumber Daya String**: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`
- **Pengujian**: `app/src/test/java/com/harimu/app/NusantaraAndUtilitiesUiTest.kt`

---

## 5. Verifikasi Pengujian

Suite unit & Robolectric UI dijalankan terhadap seluruh layar P12 dan suite regresi terkait:

```bash
gradle :app:testDebugUnitTest \
  --tests "com.harimu.app.NusantaraAndUtilitiesUiTest" \
  --tests "com.harimu.app.NusantaraTest" \
  --tests "com.harimu.app.NusantaraPhase2Test" \
  --tests "com.harimu.app.PrayerTimesTest" \
  --tests "com.harimu.app.AreaLookupTest" \
  --tests "com.harimu.app.NavigationDestinationTest" \
  --tests "com.harimu.app.HariMuUiAndNavigationTest" \
  --tests "com.harimu.app.MainActivitySmokeTest" \
  --console=plain
```

- **Cakupan `NusantaraAndUtilitiesUiTest`** (9 test):
  1. `findNextPrayerIndexResolvesUpcomingOrWrapsToImsak` — perhitungan slot sholat terdekat & wrap-around ke Imsak.
  2. `nusantaraDestinationProvidesOwnTopBarWithoutEnteringBottomNav` — integritas `TopLevelDestination.NUSANTARA`.
  3. `nusantaraHubFiltersSearchesShowsQuickAccessAndResetsEmptyState` — akses cepat `CORE-15`/`CORE-16`, banner gempa, target sentuh `≥48.dp`, pencarian, dan reset empty state.
  4. `nusantaraHubExpandedRendersTwoColumnsWithSourceAttribution` — tata letak 2 kolom pada layar lebar (`840dp`) dan footer atribusi.
  5. `prayerTimesShowsPresetsScheduleHighlightAndErrorRetry` — empty state, preset, jadwal + sorotan sholat berikutnya, error, dan retry recovery.
  6. `areaLookupShowsPromptEmptyClearAndCopyPostalCode` — prompt awal, pencarian `Soreang`, hasil `40915`, empty state, dan tombol bersihkan.
  7. `weatherScreenRendersLocalizedConditionsAndRegionsOpensWeatherWithAdm4` — drilldown `RegionsScreen` sampai desa dan handoff `adm4` ke `WeatherScreen` dalam bahasa Inggris & Indonesia.
  8. `earthquakeQuranFootballAndPesantrenSupportDetailStatesAndBackHandler` — kartu gempa + shakemap, serta `BackHandler` pada `QuranScreen`, `FootballScreen`, dan `PesantrenScreen`.
  9. `doasSchoolsSundaneseTtsAndBackupRenderStatesAndScrollableCompact` — ekspansi doa, validasi 3 huruf & pencarian sekolah, preset Undak Usuk Basa Sunda, panjang huruf TTS, dan keterjangkauan scroll `BackupScreen` pada layar kompak (`360dp × 640dp`).
