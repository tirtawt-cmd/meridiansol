# Meridian Safe Mobile v0.1 (Paper Only)

Versi belajar untuk Android. **Tidak melakukan transaksi Solana nyata. Tidak meminta private key.**

## Fitur
- Paper balance 1 SOL.
- Preset risiko Aman / Seimbang / Agresif.
- Batas modal per posisi.
- Simulasi fee dan pergerakan hasil trading.
- Kill-switch otomatis jika paper balance turun 10%.
- Foreground service dengan notifikasi permanen saat bot aktif.
- Wake-lock terbatas agar siklus lebih tahan saat layar mati.
- Opsi mencoba auto-restart setelah reboot.
- Emergency stop dan reset paper account.
- Activity log lokal.

## Catatan Android
Aplikasi memakai foreground service tipe `specialUse` karena proses simulasi dimulai pengguna dan perlu terus berjalan saat layar mati. Produsen HP dapat tetap menerapkan pembatasan baterai sendiri. HP yang benar-benar power-off tidak dapat menjalankan APK.

## Build
Buka folder ini di Android Studio, tunggu Gradle sync, lalu Build > Build APK(s).
Project: Java, minSdk 26, targetSdk 35.

## Yang sengaja BELUM ada
- Private key/wallet.
- Live trading.
- Meteora/Jupiter execution.
- LLM/OpenRouter.
- Telegram command execution.
- VPS/server.

Tahap selanjutnya setelah alur paper mode dipahami: ganti simulator dengan market-data read-only, lalu tambahkan live execution di balik risk guard terpisah.

## v0.2 — Solana Live Scanner + Paper Position
- Discovery pool Solana baru menggunakan GeckoTerminal API.
- Menampilkan pipeline DISCOVERY → LIQUIDITY → VOLUME → ACTIVITY → SIGNAL.
- Menampilkan token yang ditolak beserta alasannya.
- PAPER ENTRY hanya untuk kandidat yang lolos filter.
- Harga posisi dimonitor dari endpoint pair DEX Screener; TP/SL/time-exit hanya memengaruhi saldo simulasi.
- Tidak menyimpan private key, tidak meminta seed phrase, tidak menandatangani transaksi, dan tidak mengirim swap nyata.
- Siklus default 60 detik: ini near-real-time polling, bukan WebSocket tick-by-tick.
