# Build APK dari HP tanpa Android Studio

Project ini sudah memiliki GitHub Actions untuk membangun APK otomatis.

1. Buat repository GitHub baru (private juga boleh).
2. Upload seluruh isi folder project ini ke root repository. Pastikan folder `.github/workflows/` ikut ter-upload.
3. Buka tab **Actions** pada repository.
4. Pilih **Build Meridian Safe APK**.
5. Tekan **Run workflow**.
6. Setelah job selesai, buka hasil run dan unduh artifact **MeridianSafe-v0.1-paper**.
7. Ekstrak ZIP artifact; di dalamnya ada `MeridianSafe-v0.1-paper.apk`.
8. Install APK di Android. Jika diminta, izinkan instalasi dari sumber tersebut.

Versi v0.1 adalah PAPER MODE lokal. Tidak meminta private key dan tidak mengirim transaksi blockchain.
