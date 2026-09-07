# Security model v0.1

1. Paper-only; tidak ada blockchain signer.
2. Tidak ada secret/API key.
3. Tidak ada self-update.
4. Tidak ada remote command.
5. Hard cap input maxCapital 0.25 SOL ekuivalen untuk simulasi.
6. Kill-switch drawdown 10% dari saldo awal paper.
7. Emergency stop menghentikan service dan mereset akun paper.

Versi live nanti harus memakai wallet terpisah, encrypted keystore, transaction simulation, allowlist program/token, daily loss limit, max position, dan explicit live-mode unlock.
