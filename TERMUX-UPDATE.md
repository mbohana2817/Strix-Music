# Update Strix Music dari Termux

1. Download ZIP update ke folder Download Android.
2. Pastikan repo sudah pernah di-clone dan punya remote `origin`.
3. Buka Termux, masuk ke folder repo:
   ```bash
   cd ~/Strix-Music
   ```
4. Jalankan script dari ZIP yang sudah diekstrak:
   ```bash
   bash /sdcard/Download/Strix-Music-main/termux-push.sh /sdcard/Download/Strix-Music-main.zip
   ```
   Sesuaikan lokasi ZIP/folder jika berbeda. Jika belum ada remote `origin`, script akan meminta URL repo GitHub.
5. Jika GitHub meminta autentikasi, gunakan Personal Access Token sebagai password Git HTTPS (jangan kirim token ke chat).

Script menyalin source ZIP ke working tree, menjaga folder `.git`, mengecek whitespace, membuat commit, lalu push ke branch aktif. Script tidak memakai `git push --force`.

## Catatan
- Build APK belum dapat diverifikasi di sini; jalankan workflow GitHub Actions setelah push.
- Foto profil developer asli tidak ada di ZIP yang dikirim. Foto stock lama sudah diganti sementara dengan artwork Strix supaya tidak menampilkan wajah orang yang bukan kamu. Kirim foto asli jika ingin dipasang.
- Android 12+ tetap menampilkan splash sistem singkat (batasan Android). Setelah Activity terbuka, GIF Strix ditampilkan utuh tanpa crop selama sekitar 3 detik.
