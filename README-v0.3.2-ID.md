# Meridian Safe v0.3.2 — Capital Protection

PAPER ONLY. Tidak ada wallet, private key, signing, atau real order.

Perubahan utama dari v0.3.1:
- Anti-pump: kandidat dengan momentum 5m terlalu tinggi tidak boleh entry.
- Konfirmasi 3–4 scan, lebih ketat terhadap penurunan harga/liquidity.
- Position sizing dinamis: input Max Capital hanya batas atas; ukuran nyata dibatasi saldo dan liquidity pool.
- Agresif maksimum sekitar 5% saldo per posisi sebelum liquidity cap (bukan 12%).
- Stop loss Agresif -4%, Seimbang -3%, Aman -2%.
- Position monitor ±10 detik; discovery scanner tetap ±60 detik.
- Momentum exit dipercepat pada 5m <= -15%; liquidity emergency exit < $8K.
- Kill switch akun -10% tetap aktif.

Catatan: polling 10 detik tetap tidak menjamin exit tepat pada SL ketika token gap/dump ekstrem. Ini simulasi paper dan bukan jaminan performa real.
