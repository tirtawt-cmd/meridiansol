# Meridian Safe Mobile v0.3.1 — Calibration WATCH/CONFIRM

Versi kalibrasi dari v0.3. Scanner tetap memakai data pasar Solana nyata dan seluruh transaksi tetap **PAPER ONLY**.

## Tujuan v0.3.1
v0.3 terlalu ketat untuk banyak new pool. v0.3.1 menurunkan ambang awal agar kandidat potensial tidak langsung dibuang, tetapi kandidat **tidak langsung BUY**. Kandidat harus melewati sistem:

`DISCOVERY → LIQUIDITY → VOLUME → ACTIVITY → AGE → SAFETY → WATCH → CONFIRM → PAPER BUY`

## Ambang kalibrasi default
Preset **Aman** kira-kira: liquidity >= $18K, volume 1h >= $5K, transaksi >= 18, umur >= 3 menit, buy ratio >= 50%, WATCH score >= 76. Preset Seimbang/Agresif lebih longgar.

## WATCH/CONFIRM
- Kandidat yang lolos filter + safety masuk `WATCH`.
- Aman perlu 3 scan berturut-turut; Seimbang/Agresif perlu 2 scan.
- Streak reset bila kandidat hilang terlalu lama atau liquidity/volume/harga turun tajam.
- Konfirmasi akhir masih memeriksa score dan momentum sebelum PAPER ENTRY.

## Yang tetap sama
- Hanya satu posisi paper pada satu waktu.
- Cooldown token 6 jam setelah exit.
- TP/SL/time exit dan cost allowance tetap mengikuti preset risiko.
- Kill switch paper balance -10% tetap aktif.
- Tidak ada wallet, private key, seed phrase, signing, swap, atau transaksi blockchain nyata.

## Catatan
Safety di versi ini tetap market heuristic, bukan audit smart contract lengkap. Label WATCH/SAFE bukan jaminan token aman.
