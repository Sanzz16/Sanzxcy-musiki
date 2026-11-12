# Sanzxcy Server (Komunitas, Owner Panel, Status Aplikasi)

Server ini dijalankan di **Vercel** (serverless) dengan **Upstash Redis** untuk data dan **Vercel Blob** untuk foto/video/stiker.

## Deploy (bisa dari HP)
1. Di Vercel: **Add New → Project**, pilih repo `Sanzz16/Sanzxcy-Music`, atur **Root Directory = `server`**, lalu Deploy.
2. Di tab **Storage** project itu: tambahkan **Upstash Redis** (Marketplace) dan **Blob**. Env `KV_REST_API_URL`, `KV_REST_API_TOKEN`, `BLOB_READ_WRITE_TOKEN` terisi otomatis.
3. Di **Settings → Environment Variables** tambahkan:
   - `OWNER_KEY` — kunci rahasia panel `owner.html` (wajib, buat yang panjang dan acak).
   - `OWNER_USERNAME` — (opsional) username yang otomatis berole `owner` saat mendaftar di aplikasi.
   - `GITHUB_TOKEN` — fine-grained token yang dibatasi ke repository tujuan dengan **Contents: read/write** (termasuk GitHub Releases). Simpan hanya di Vercel Environment Variables; jangan pernah menaruh token di browser, aplikasi, atau source.
   - `GITHUB_REPO` opsional (default `Sanzz16/Sanzxcy-Music`) dan `GITHUB_RELEASE_TARGET` opsional (default `main`). `BLOB_READ_WRITE_TOKEN` disediakan saat Vercel Blob dihubungkan dan dipakai untuk menampung APK sementara sebelum dipindahkan ke GitHub Release; berkas sementara dihapus setelah sukses.
   - `RESEND_API_KEY` + `EMAIL_FROM` — (opsional) aktifkan kode verifikasi email dan reset password. `EMAIL_FROM` harus memakai sender/domain yang sudah diverifikasi di Resend; penerima dapat memakai Gmail.
4. Redeploy. Buka `https://<domain-kamu>/owner.html` untuk panel owner.
5. Untuk domain baru, gunakan `https://sanzxcy-music-server.vercel.app`. Di GitHub repo aplikasi, set **Variable** `SANZXCY_SERVER_URL` ke URL tersebut agar APK memakai server ini.
6. Untuk impor playlist Spotify publik, tambahkan `SPOTIFY_CLIENT_ID` dan `SPOTIFY_CLIENT_SECRET` sebagai Environment Variables di Vercel (hanya server-side), lalu redeploy. Endpoint `/api/spotify-playlist?url=<playlist-url>` mengembalikan nama playlist, cover, pemilik, dan metadata lagu; URL playlist privat/invalid ditolak.

## Keamanan
- Password disimpan sebagai hash scrypt + salt, **tidak pernah** bisa dilihat siapa pun, termasuk owner. User dengan email terverifikasi dapat reset melalui kode sekali pakai. Akun tanpa email: staf/admin/owner dapat membuat password sementara di panel; pengguna wajib menggantinya setelah login.
- Login tetap mendukung username + password. Email hanya bisa dipakai setelah diverifikasi dari editor profil. Tanpa `RESEND_API_KEY` dan `EMAIL_FROM`, fitur verifikasi/reset via email belum aktif.
- Hanya owner yang dapat mengubah role, mengarsipkan/memulihkan, atau menghapus akun. Arsip dapat dipulihkan. Hapus akun mencabut akses dan menghapus email/profil, tetapi menyimpan tombstone username untuk mencegah sesi lama hidup kembali; pesan chat mengikuti retensi 24 jam.
- IP pengguna dicatat untuk keperluan moderasi (ban/unban IP) dan hanya terlihat oleh admin/owner.
- Jangan pernah membagikan `OWNER_KEY`.

## Endpoint
`/api/auth`, `/api/profile`, `/api/upload`, `/api/apk-upload` (owner-only), `/api/chat`, `/api/stickers`, `/api/listen`, `/api/spotify-playlist` (playlist publik), `/api/app` (publik), `/api/owner`.

## Moderasi chat
Pesan chat dipertahankan maksimal 24 jam. Sanzxcy Bot memeriksa teks saat dikirim, menghapus pesan yang melanggar, dan menerbitkan pemberitahuan moderasi; pengiriman terlalu cepat memicu jeda 40 detik dan mute 80 detik. Staff, admin, dan owner dapat menghapus pesan melalui endpoint chat. Mereka juga dapat menyematkan satu pesan Global Chat; pin berakhir bersama masa retensi pesannya atau dapat dilepas lebih awal. Pemeriksaan izin tetap dilakukan di server.

## Replica media profil (opsional)
Avatar/banner tetap diunggah ke Vercel Blob. Untuk replica kedua di Supabase Storage, atur `SUPABASE_URL` dan `SUPABASE_SECRET_KEY` di Vercel **Environment Variables**; key lama `SUPABASE_SERVICE_ROLE_KEY` tetap didukung. Gunakan secret key baru berawalan `sb_secret_` jika tersedia—jangan gunakan `sb_publishable_`—atau legacy `service_role` JWT bila memang itu yang tersedia. Jangan pernah menaruh key di aplikasi klien, source code, atau membagikannya. `SUPABASE_STORAGE_BUCKET` opsional (default `community-media`). Untuk key baru, server mengirimnya melalui header `apikey` saja; legacy JWT juga dikirim sebagai Bearer. Jika bucket belum ada, server mencoba membuatnya sebagai bucket publik dengan batas 3 MB dan MIME `image/jpeg`, `image/png`, `image/webp`. Jika setup belum dikonfigurasi atau upload salah satu storage gagal, respons upload menyatakan replica berhasil secara aktual (tidak menganggap replica kedua sukses); Blob tetap dipakai saat Supabase belum diset. Respons mencakup `urls`, `replicaCount`, `replicaTarget`, dan status per provider; `url` tetap kompatibel. Profil menerima/menyimpan `avatarUrls`/`bannerUrls` dan mempertahankan `avatarUrl`/`bannerUrl` sebagai URL utama untuk kompatibilitas versi lama. URL profil dibatasi ke host Blob publik Vercel dan host/bucket publik Supabase yang persis dikonfigurasi.

## Lampiran masukan komunitas
Foto/video feedback diunggah server-side ke Vercel Blob memakai `BLOB_READ_WRITE_TOKEN`; jika Blob gagal, server dapat memakai `SUPABASE_URL` dan `SUPABASE_SECRET_KEY` (atau `SUPABASE_SERVICE_ROLE_KEY`) sebagai fallback. Fallback memakai bucket publik khusus `feedback-media` secara default; nama dapat diganti dengan `FEEDBACK_STORAGE_BUCKET`. Jika bucket belum ada, server mencoba membuat bucket publik, tetapi **tidak akan mengubah bucket yang sudah ada dari privat menjadi publik**. Pastikan secret Supabase hanya berada di Vercel. URL lampiran bersifat publik tak-tercantum (siapa pun yang mendapat URL dapat membukanya), jadi pengguna sebaiknya tidak mengirim informasi sensitif.
