# Strix Music Splash + Pengaturan Update

Perubahan:
- Splash GIF `app/src/main/res/drawable-nodpi/strix_splash.gif` tampil setiap Activity dibuat.
- Splash menampilkan progress bar 4,2 detik.
- Teks: `Please Wait` / `Connect To Server`.
- GIF support memakai Coil GIF decoder.
- Tab `Profile` di navigasi utama diubah menjadi `Pengaturan` dan membuka SettingsScreen.
- Ikon tab Profile diganti ke ikon settings.

Catatan build:
- Compile belum dapat diverifikasi di environment ini karena Gradle Wrapper perlu mengunduh Gradle dari services.gradle.org dan koneksi jaringan environment tidak tersedia.

Push ke GitHub:
```bash
git add app/build.gradle.kts app/src/main/kotlin/com/blazify/music/App.kt app/src/main/kotlin/com/blazify/music/MainActivity.kt app/src/main/kotlin/com/blazify/music/ui/screens/Screens.kt app/src/main/kotlin/com/blazify/music/ui/screens/NavigationBuilder.kt app/src/main/res/values-id/strings.xml app/src/main/res/drawable-nodpi/strix_splash.gif
git diff --cached --check
git commit -m "feat: add animated Strix splash and settings navigation"
git push origin main
```
