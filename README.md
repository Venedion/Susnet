# Susnet

Aplikasi Android untuk mendeteksi tanda-tanda serangan pada WiFi publik (evil twin, intersepsi TLS, dan anomali sinyal atau latency). Hasil deteksi digabung oleh Fuzzy Inference System tipe Mamdani menjadi satu skor risiko 0-100 dengan tiga tingkat: Aman, Waspada, Bahaya.

Proyek mata kuliah Kecerdasan Komputasional. Laporan UTS: progres hingga minggu ke-7.

## Status saat ini

Hanya skenario evil twin yang sudah diuji di perangkat nyata (hotspot ponsel lain dengan nama WiFi yang sama). Komponen lain sudah terintegrasi atau diimplementasikan tetapi belum diuji secara terstruktur.

| Komponen | Status |
|---|---|
| Deteksi evil twin (perubahan BSSID) | Telah diuji (1 skenario) |
| Baseline jaringan (Room) | Terintegrasi |
| Mesin fuzzy (4 input, 14 aturan) | Terintegrasi |
| Pemantau sertifikat TLS (pinning) | Terintegrasi, belum diuji dengan sertifikat tidak valid |
| Pemantau kestabilan sinyal (RSSI) | Terintegrasi, ada bug terbuka |
| Pemantau latency (TCP handshake) | Terintegrasi, ada bug terbuka |
| Dashboard | Terintegrasi |
| Detail Alert | Terintegrasi, belum diuji tap-through |
| Pencatatan riwayat (basis data) | Diimplementasikan, belum ada layar tampilan |
| Monitor ARP | Belum dimulai |
| Pemantauan berkelanjutan | Belum dimulai |

Bug terbuka: monitor sinyal dan latency dibuat ulang setiap aplikasi dibuka dan hanya dievaluasi sekali, sehingga jendela sampel tidak terisi dan skor keduanya selalu 0. Perbaikan direncanakan.

## Cara kerja singkat

1. Empat sensor menghasilkan skor 0-100: perubahan BSSID, kegagalan pinning TLS, kestabilan RSSI, dan kestabilan latency.
2. `FuzzyEngine.kt` memfuzzifikasi tiap skor (rendah, sedang, tinggi, fungsi keanggotaan trapesium), mengevaluasi 14 aturan (AND memakai minimum, agregasi memakai maksimum), lalu defuzzifikasi dengan centroid.
3. Dashboard menampilkan skor dan status, Detail Alert menjelaskan tiap sinyal dengan bahasa awam, dan setiap hasil dicatat ke basis data lokal Room.

## Persyaratan

- Android Studio versi stabil terbaru dengan Android SDK 35
- Ponsel Android fisik (minSdk 26) dengan USB debugging aktif. Emulator kurang cocok karena tidak punya WiFi nyata.
- Koneksi internet saat sinkronisasi Gradle pertama

Dependensi utama (lihat `gradle/libs.versions.toml`):

| Pustaka | Versi |
|---|---|
| Kotlin | 2.0.21 |
| Android Gradle Plugin | 8.7.3 |
| Room (runtime, ktx, compiler via KSP) | 2.6.1 |
| TrustKit-Android | 1.1.5 |
| LocalBroadcastManager | 1.1.0 |
| Material Components | 1.12.0 |

## Cara menjalankan

1. Salin atau unduh repositori ini, lalu buka foldernya di Android Studio dan tunggu sinkronisasi Gradle selesai.
2. Sambungkan ponsel Android dan pilih ponsel itu sebagai perangkat target.
3. Jalankan aplikasi (Run).
4. Izinkan akses lokasi saat diminta. Android mensyaratkan izin ini agar nama (SSID) dan alamat (BSSID) WiFi dapat dibaca.
5. Hubungkan ponsel ke sebuah WiFi. Dashboard menampilkan nama WiFi, skor, dan status.

Kunjungan pertama ke sebuah WiFi tidak dicurigai karena belum ada baseline. Tingkat kepercayaan sebuah alamat access point naik seiring kunjungan.

## Skenario uji evil twin

1. Hubungkan ponsel yang menjalankan Susnet ke sebuah WiFi beberapa kali agar baseline tersimpan.
2. Di ponsel kedua, nyalakan hotspot dengan nama yang sama persis.
3. Putuskan dari WiFi asli lalu sambungkan ponsel Susnet ke hotspot tersebut.
4. Buka Susnet. Alamat access point yang berbeda dari baseline menaikkan skor.

## Pengaturan pinning TLS

Domain yang dipantau diatur di `app/src/main/res/xml/network_security_config.xml` (saat ini `learn.uph.edu`). Untuk memakai domain sendiri, hitung pin SHA-256 dari kunci publik sertifikatnya:

```
openssl s_client -connect contoh.com:443 -servername contoh.com </dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
```

Hasilnya dimasukkan sebagai `<pin digest="SHA-256">...</pin>`. Konfigurasi memakai `enforcePinning="false"`, jadi koneksi tidak diblokir; kegagalan pinning hanya dilaporkan sebagai skor.

## Basis data

Data disimpan di `susnet.db` (Room) pada penyimpanan privat aplikasi, dengan tabel jaringan, access point, dan riwayat deteksi. Untuk melihat isinya, jalankan aplikasi dari Android Studio lalu buka App Inspection, tab Database Inspector. Saat versi skema dinaikkan, basis data lama dihapus dan dibuat ulang (`fallbackToDestructiveMigration`), sehingga ini hanya cocok untuk tahap pengembangan.

## Struktur kode

```
app/src/main/java/com/example/susnet/
  MainActivity.kt        Dashboard dan alur evaluasi
  DetailActivity.kt      Penjelasan lengkap per sinyal
  SusnetApplication.kt   Inisialisasi TrustKit
  Detection/             EvilTwinDetector, TlsPinningMonitor, SignalStabilityMonitor,
                         LatencyMonitor, FuzzyEngine, ThreatEvaluator
  Data/                  Entitas, DAO, BaselineStore, SusnetDatabase
app/src/main/res/        Layout, ikon vektor, warna, konfigurasi keamanan jaringan
```

## Keterbatasan

- Titik fungsi keanggotaan, ambang simpangan baku, dan ambang kepercayaan adalah nilai awal yang belum dikalibrasi dari data lapangan.
- Rule base dirancang sendiri dan belum divalidasi terhadap acuan eksternal.
- Hanya sisi klien: aplikasi tidak melihat metrik dari sisi access point dan tidak membutuhkan akses root.
- Evaluasi masih sekali per aplikasi dibuka, belum berkelanjutan.
- Basis data belum terenkripsi.

## Referensi

- Antipov & Vasylenko (2026). Fuzzy-Logic Algorithm for Risk Assessment in Wi-Fi Networks.
- Mamdani, E. H., & Assilian, S. (1975). An experiment in linguistic synthesis with a fuzzy logic controller. International Journal of Man-Machine Studies, 7(1), 1-13.
- TrustKit-Android: https://github.com/datatheorem/TrustKit-Android
- Android WifiManager: https://developer.android.com/reference/android/net/wifi/WifiManager
- Room: https://developer.android.com/training/data-storage/room
