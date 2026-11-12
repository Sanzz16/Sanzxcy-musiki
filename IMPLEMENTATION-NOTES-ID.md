# Catatan Implementasi — Sanzxcy Music

## Perubahan yang dibuat

- Moderasi Bot Android kini memiliki Ringkasan dan audit log, lookup IP, Histori Maintenance (edit/terapkan/hapus), Room Control, serta pengelolaan status/arsip laporan komunitas. Daftar pengguna mendapat aksi blokir IP. Penghapusan akun meminta username yang tepat; password sementara hanya ditampilkan pada dialog sementara.
- Grid pilihan playlist komunitas diperluas menjadi hingga 12 kartu. Tiap kartu memuat hingga 20 lagu dari server dan menampilkan sampai 10 lagu dalam daftar vertikal yang dapat digulir.
- Owner web mendapat tab Upload APK. Upload hanya dapat dimulai oleh akun Owner yang login; file dibatasi 120 MiB, ditransfer langsung ke Vercel Blob, lalu diterbitkan sebagai asset GitHub Release. Bila dipilih, metadata update aplikasi otomatis diarahkan ke APK yang baru dirilis. Token GitHub hanya dibaca backend.
- Hash password dan salt tetap disimpan di server. Proyek menggunakan `scrypt` dengan salt acak; keduanya tidak diserialisasi ke klien, dan endpoint profil hanya mengubah field yang diizinkan. Tes regresi memastikan body HTTP tidak dapat mengganti `salt`, `passHash`, atau versi sesi.
- Catatan teks terkait password keystore yang ikut terarsip dikeluarkan dari salinan ini. `.gitignore` kini mengecualikan keystore, berkas sertifikat, dan nama file yang memuat `keystore`. Jika arsip/repo tersebut pernah dibagikan secara publik, rotasi password signing dianjurkan tanpa membuat ulang kunci signing aplikasi.

## Environment Vercel sebelum upload APK aktif

Buat Vercel Blob store dan set environment berikut pada proyek server: `BLOB_READ_WRITE_TOKEN`, `GITHUB_TOKEN`, dan `GITHUB_REPO` (default kode: `Sanzz16/Sanzxcy`). `GITHUB_RELEASE_TARGET` opsional dan default-nya `main`. Buat GitHub fine-grained token yang dibatasi ke repository tujuan dan izin **Contents: read/write**. Jangan masukkan token ke `owner.js`, APK, atau file source. Setelah perubahan environment disimpan, deploy ulang proyek server.

Endpoint APK disiapkan, tetapi perubahan ini **belum dideploy** dan secret Vercel belum dikonfigurasi dari sesi ini. Tab Upload APK memerlukan akun Owner dengan username/password; owner-key saja tidak cukup untuk memperoleh token upload.

## URL dan validasi

Aplikasi saat ini memakai `https://sanzxcy-music-server.vercel.app`, yang merespons endpoint status publik. Domain yang tertulis di permintaan (`sanzxcysmusic-server.vercel.app`) tidak merespons saat diperiksa, sehingga URL aktif tidak diganti ke domain tersebut.

Tes server: `node --test test/*.test.js` — 51 tes lulus. `node --check` untuk endpoint dan JavaScript owner juga lulus. Kompilasi Android belum tervalidasi karena Android SDK tidak terpasang pada lingkungan build ini; Gradle gagal sebelum tahap kompilasi karena `ANDROID_HOME`/`sdk.dir` tidak tersedia.
