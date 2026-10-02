# ADR 0007: Market data and watch-only wallets

Status: Accepted (Phase 3d, 2026-10-02)

## Context

- INV-04, CR-05, PM-02: prices for securities, crypto-assets and precious metals, downloaded daily, with manual prices for anything not quoted.
- CR-02: watch-only tracking of a wallet from its public address or extended public key, never a private key or seed phrase.
- ARC-01 and the privacy requirements (PRV-01 to PRV-03): the household's data stays on its own computer.

Until Phase 3 the only outside services were the Bank of Canada and, if turned on, a second exchange-rate source (FX-08). Nothing about what the household owns left the computer.

## Decision

**Every download is optional and off by default.**
- The owner decided this on 2026-10-02, as for FX-08.
- Each feed is turned on separately under Rates and prices: securities, crypto-assets, metals.
- The screen says what is sent: the symbols of what is held (tickers, coin names, the four metals), never amounts, accounts or anything about the people.
- Prices entered by hand are never replaced by downloads.

**Sources.** All free and keyless, all requested over HTTPS:

| Feed | Service | Used for |
| --- | --- | --- |
| Securities | Yahoo Finance chart service (unofficial) | Daily closes of stocks and ETFs. Canadian mutual funds, bonds and GICs are not quoted there and keep manual prices. |
| Crypto-assets | CoinGecko public API | Daily prices in CAD, credited on the screen as CoinGecko asks. |
| Metals | Yahoo Finance near-month futures (GC=F, SI=F, PL=F, PA=F) | USD per troy ounce, converted at the Bank of Canada rate, as an estimate of spot. |

- Yahoo's service is unofficial and may change without notice.
- The price service is a small interface, so another source can be added without touching the rest.

**Where prices are kept.**
- Security prices are kept in each account group's ledger, as the securities are (HH-11).
- A coin's price is kept as its CAD exchange rate in `core.db`, so wallets convert like a foreign currency in every report and in net worth.
- Metal spot prices are kept in `core.db`, in their own table.

**Watch-only wallets (CR-02).**
- **What is followed:** Bitcoin only, from an address or an xpub/ypub/zpub.
- **Address derivation:** done on the computer (BIP32 public derivation, BIP44/49/84 address types). It is checked against the published BIP84 and BIP44 test vectors.
- **Keys:** a private key (xprv...) is refused outright, and no private key or seed phrase is ever stored.
- **Lookup:** a sync runs only when the user asks. It queries mempool.space, a public block explorer, which therefore learns the wallet's addresses.
- **What is recorded:** only confirmed transactions. Each one is recorded once, under its transaction id, with the network fee on its own line.

## Consequences

- Privacy is a choice the user makes per feed. The default app makes no request about holdings.
- Any of these sources may stop working. The app stays fully usable with manual prices, and a failed download is reported, not fatal (NFR-10).
- Watch-only for other coins (Ethereum and others) would need other explorers and is not covered.
- Tested without network access: `PriceServiceTest` and `CryptoServiceTest` use recorded responses, and `BitcoinTest` uses the published test vectors.
