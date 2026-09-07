# Meridian Safe Mobile v0.3 — Solana Safety Scanner + Paper Trading

Aplikasi Android untuk belajar dan menguji alur scanner token Solana dengan data pasar nyata, tetapi seluruh transaksi tetap **PAPER**.

## Perubahan v0.3
- Pipeline lengkap: `DISCOVERY → LIQUIDITY → VOLUME → ACTIVITY → AGE → SAFETY → SCORE → SIGNAL`.
- Discovery pool baru dari GeckoTerminal.
- Enrichment kandidat yang lolos filter dasar menggunakan DEX Screener.
- Menampilkan harga, liquidity, volume 1h/24h, buy/sell, umur pool, DEX, perubahan 5m/1h, market cap, FDV, mint, dan pool address.
- Safety filter heuristik: trusted quote (SOL/WSOL/USDC/USDT), momentum ekstrem, buy ratio, kelengkapan data, dan anomali FDV/liquidity.
- Top Candidate Detail di dashboard.
- Tombol buka kandidat di Solscan dan DEX Screener.
- Cooldown token 6 jam setelah paper position ditutup agar bot tidak berulang kali masuk token yang sama.
- Momentum exit tambahan untuk paper position.
- Cost allowance paper berbeda per preset risiko.
- Semua alasan REJECT ditampilkan berdasarkan stage yang gagal.

## Tetap PAPER ONLY
Tidak ada private key, seed phrase, wallet signing, swap, Jupiter execution, atau transaksi blockchain nyata.

## Catatan Safety Filter
Filter safety v0.3 adalah filter pasar/heuristik, **bukan audit smart contract penuh**. Mint authority, freeze authority, holder concentration, honeypot/sellability, dan analisis transaksi on-chain mendalam belum diverifikasi langsung. Karena itu jangan menganggap label `SAFE` sebagai jaminan token aman.

## Polling
Siklus default sekitar 60 detik. Ini near-real-time polling, bukan WebSocket tick-by-tick.

## Build
Project Android Java: minSdk 26, target/compileSdk 35. GitHub Actions workflow tersedia untuk menghasilkan APK debug.
