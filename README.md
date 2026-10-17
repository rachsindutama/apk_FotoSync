# FotoSync

**FotoSync** adalah aplikasi Android profesional berbasis **Kotlin & Jetpack Compose** yang dirancang khusus untuk fotografer, kreator konten, dan penyelenggara acara untuk mengelola sesi pemotretan, berbagi foto secara instan ke klien melalui **Local HTTP Server** dengan **QR Code**, serta mengelola penyimpanan lokal dengan fleksibel menggunakan **Storage Access Framework (SAF)**.

---

## Fitur Utama

1. **Manajemen Sesi Foto (Photo Session Management)**
   - Buat dan atur sesi pemotretan berdasarkan nama klien, tanggal, kategori (Pernikahan, Wisuda, Portofolio, Komersial, dll.), dan catatan penting.
   - Status sesi aktif untuk mempermudah identifikasi sesi yang sedang berjalan.

2. **Local HTTP Server & Portal Klien**
   - Jalankan server web lokal langsung di perangkat Android Anda.
   - Klien dapat mengakses galeri foto secara nirkabel melalui browser di jaringan Wi-Fi lokal yang sama.
   - **QR Code Generator**: Hasilkan kode QR secara instan yang dapat dipindai oleh perangkat klien untuk langsung membuka portal galeri foto.
   - Interaksi Klien: Klien dapat melihat foto resolusi tinggi, memberikan "Suka" (Like), dan mengunduh foto pilihan langsung dari browser mereka.

3. **Penyimpanan Fleksibel dengan SAF (Storage Access Framework)**
   - Pilih direktori penyimpanan khusus pada perangkat atau kartu SD eksternal menggunakan sistem folder bawaan Android.
   - Organisasi file otomatis per sesi pemotretan.

4. **Unduh & Ekspor Foto (ZIP)**
   - Ekspor seluruh foto dalam sesi menjadi arsip ZIP untuk kemudahan transfer dan pencadangan.

5. **Antarmuka Pengguna Modern (Material Design 3)**
   - Desain estetis dengan tema gelap modern (`Cobalt Blue` & `Studio Gold`).
   - Navigasi mulus, *Speed Dial FAB* yang intuitif, dan animasi transisi yang halus.

---

## Arsitektur & Teknologi

- **Bahasa**: 100% Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3, Dynamic Color, Edge-to-Edge)
- **Arsitektur**: MVVM (Model-View-ViewModel) dengan StateFlow & Coroutines
- **Database Lokal**: Room Database untuk penyimpanan data sesi dan foto secara offline-first
- **Networking / Web Server**: Ktor Embedded Server (CIO engine) untuk Local Web Portal klien
- **Pemrosesan Gambar**: Coil untuk pemuatan gambar berkinerja tinggi

---

## Panduan Penggunaan Singkat

1. **Membuat Sesi Baru**:
   - Ketuk tombol aksi cepat (+) di sudut kanan bawah.
   - Pilih "Buat Sesi Baru", masukkan nama klien, tanggal, dan detail sesi.
2. **Menjalankan Server Lokal**:
   - Buka menu "Server Web Lokal" dari tombol aksi cepat.
   - Nyalakan server untuk mendapatkan alamat URL lokal (misal: `http://192.168.x.x:8080`).
3. **Berbagi via QR Code**:
   - Tampilkan QR Code kepada klien agar mereka dapat memindai dan mengakses portal galeri di perangkat mereka.
4. **Kelola Penyimpanan & Ekspor**:
   - Atur jalur folder penyimpanan melalui opsi "Jalur Penyimpanan SAF".
   - Unduh atau ekspor sesi ke arsip ZIP kapan saja.

---

## Dukungan & Traktir Kopi

Aplikasi **FotoSync** dikembangkan dengan penuh penasaran dan dedikasi oleh **Saya Sendiri**. Jika aplikasi ini bermanfaat untuk karya, bisnis, atau hobi Anda, Anda dapat mendukung pengembangan lebih lanjut dengan cara **Traktir Kopi** ☕ melalui ShopeePay atau QRIS nasional:

| Platform | Keterangan |
| :--- | :--- |
| **Nama Pemilik** | Rachmad Sindu Pratama |
| **Nomor ShopeePay** | `(+62) 82192222772` |
| **Metode Pembayaran** | QRIS (Mendukung semua Bank & E-Wallet) |

> **Terima kasih banyak atas dukungan, apresiasi, dan kebaikan Anda!**

---

## Lisensi
Hak Cipta © 2026 FotoSync.
