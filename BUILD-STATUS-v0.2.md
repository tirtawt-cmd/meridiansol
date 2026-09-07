# Meridian Safe v0.2 — Build Status

Source v0.2 sudah disiapkan dengan Solana live-market scanner + paper trading.

Perubahan utama:
- GeckoTerminal `solana/new_pools` untuk discovery pool baru.
- Filter liquidity, volume 1h, aktivitas buy/sell, umur pool, dan availability harga.
- Dashboard menampilkan pipeline scanner dan alasan reject.
- DEX Screener pair endpoint untuk mark harga posisi paper yang terbuka.
- Paper TP/SL/time exit berdasarkan data harga pasar aktual, dengan allowance biaya simulasi 0.60% round-trip.
- Tetap tidak memiliki wallet/private key/signing/swap nyata.

Status build di lingkungan ChatGPT saat ini:
- Gradle 8.10.2 tersedia.
- Android command-line tools tersedia.
- Android Platform 35 + Build Tools 35.0.0 belum tersedia lokal dan SDK Manager tidak dapat mengambilnya dari dl.google.com pada lingkungan build ini.
- Karena itu APK v0.2 belum dapat dikompilasi di sini sampai paket SDK platform/build-tools tersebut tersedia.
