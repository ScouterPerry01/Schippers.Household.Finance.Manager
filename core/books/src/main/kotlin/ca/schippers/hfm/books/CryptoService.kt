package ca.schippers.hfm.books

import ca.schippers.hfm.calc.invest.CostBase
import ca.schippers.hfm.calc.invest.CostBaseException
import ca.schippers.hfm.calc.invest.CostEvent
import ca.schippers.hfm.calc.invest.CostEventKind
import ca.schippers.hfm.calc.invest.CostPool
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.domain.PermissionLevel
import ca.schippers.hfm.importers.CryptoEvent
import ca.schippers.hfm.importers.CryptoEventKind
import ca.schippers.hfm.importers.CryptoExchangeFile
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.security.Bitcoin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.math.BigDecimal
import java.security.MessageDigest

enum class WatchKind { ADDRESS, XPUB }
enum class CryptoIncomeKind { STAKING, MINING, INTEREST, REWARD, AIRDROP }

/** CR-01, CR-02: what a wallet needs beyond its account. [openingCost] is in the base currency. */
data class WalletDetails(
    val accountId: String,
    val openingCost: Money? = null,
    val watch: String? = null,
    val watchKind: WatchKind? = null,
    val lastSync: Long? = null,
    val notes: String? = null,
)

/** How a coin was valued on a date: the price in the base currency, when known. */
data class WalletValue(val account: Account, val balance: Money, val value: Money?, val priceDate: LocalDate?)

data class CryptoAcb(val pools: List<AcbPool>, val gains: List<CapitalGain>, val missingRates: Set<Currency>, val problems: List<String>)

data class CryptoImportPlan(val exchange: String, val fiat: List<String>, val coins: List<String>, val events: Int, val warnings: List<String>)

data class CryptoImportResult(val added: Int, val alreadyThere: Int, val walletsCreated: Int, val linked: Int, val warnings: List<String>)

data class SyncResult(val added: Int, val addresses: Int, val linked: Int)

/**
 * CR-01 to CR-06: crypto-asset wallets. A wallet is an account held in its coin, to 8 decimals.
 * Buying and selling are transfers with a fiat account, conversions are transfers between two
 * wallets, network fees and rewards are lines in the coin. The adjusted cost base follows each coin
 * per owner, as for securities: purchases at their cost, rewards at their value when received;
 * sales, conversions, fees and coins sent to someone else are disposals at their value. Coins moved
 * between the household's own wallets are not disposals.
 */
class CryptoService internal constructor(private val books: Books) {

    private val base get() = books.rates.baseCurrency

    fun wallets(includeClosed: Boolean = false): List<Account> =
        books.accounts.list(includeClosed).map { it.account }.filter { it.type == AccountType.CRYPTO_WALLET }

    fun details(accountId: String): WalletDetails {
        val (group, _) = books.accounts.locate(accountId)
        return books.ledger(group).cryptoQueries.wallet(accountId).executeAsOneOrNull()?.let {
            WalletDetails(it.account_id, it.opening_cost_minor?.let { m -> Money.ofMinor(m, base) }, it.watch, it.watch_kind?.let(WatchKind::valueOf), it.last_sync, it.notes)
        } ?: WalletDetails(accountId)
    }

    /** Saves a wallet's opening cost and what it watches. A private key is refused outright. */
    fun saveDetails(d: WalletDetails) {
        val (group, account) = books.accounts.locate(d.accountId)
        books.require(group, PermissionLevel.EDIT)
        validate(account.type == AccountType.CRYPTO_WALLET, "error.notWallet")
        val watch = d.watch?.trim()?.ifEmpty { null }
        val kind = watch?.let {
            validate(account.currency == Currency.BTC, "error.watchBitcoinOnly")
            when {
                Bitcoin.isValidAddress(it) -> WatchKind.ADDRESS
                else -> try {
                    Bitcoin.parseExtendedKey(it); WatchKind.XPUB
                } catch (e: Bitcoin.InvalidKeyException) {
                    throw ValidationException(if ("private" in (e.message ?: "")) "error.privateKey" else "error.watchInvalid")
                }
            }
        }
        validate(d.openingCost == null || !d.openingCost.isNegative, "error.amountPositive")
        books.ledger(group).cryptoQueries.upsertWallet(d.accountId, d.openingCost?.minorUnits, watch, kind?.name, d.lastSync, d.notes.blankToNull())
        books.session.audit("UPDATE", "wallet", d.accountId)
    }

    fun value(account: Account, date: LocalDate): WalletValue {
        val balance = books.accounts.list(includeClosed = true).first { it.account.id == account.id }.balance
        val rate = books.rates.list(account.currency, LocalDate.fromEpochDays(date.toEpochDays() - 30), date).lastOrNull()
        return WalletValue(account, balance, books.rates.convert(balance, base, date), rate?.date)
    }

    // --- Recording (CR-04) ------------------------------------------------------------------------

    /** Coins bought with money from [fromAccountId]: [paid] includes the exchange's fees. */
    fun buy(walletId: String, fromAccountId: String, date: LocalDate, coins: Money, paid: Money, memo: String? = null) {
        requireWallet(walletId, coins)
        validate(!paid.currency.isCrypto && paid.isPositive && coins.isPositive, "error.amountPositive")
        books.transactions.transfer(TransferDraft(fromAccountId, walletId, date, paid, coins, memo ?: books.text("generated.buy", coins.currency.code)))
    }

    /** Coins sold into [toAccountId]: [received] is after the exchange's fees. */
    fun sell(walletId: String, toAccountId: String, date: LocalDate, coins: Money, received: Money, memo: String? = null) {
        requireWallet(walletId, coins)
        validate(!received.currency.isCrypto && received.isPositive && coins.isPositive, "error.amountPositive")
        books.transactions.transfer(TransferDraft(walletId, toAccountId, date, coins, received, memo ?: books.text("generated.sell", coins.currency.code)))
    }

    /** One coin exchanged for another: a disposal of the first at the value of the second. */
    fun convert(fromWalletId: String, toWalletId: String, date: LocalDate, sent: Money, received: Money, memo: String? = null) {
        requireWallet(fromWalletId, sent)
        requireWallet(toWalletId, received)
        validate(sent.currency != received.currency && sent.isPositive && received.isPositive, "error.amountPositive")
        books.transactions.transfer(TransferDraft(fromWalletId, toWalletId, date, sent, received, memo ?: books.text("generated.convert", sent.currency.code, received.currency.code)))
    }

    /** Coins moved between the household's own wallets; the network fee is recorded on its own. */
    fun move(fromWalletId: String, toWalletId: String, date: LocalDate, arrived: Money, networkFee: Money? = null, memo: String? = null) {
        requireWallet(fromWalletId, arrived)
        requireWallet(toWalletId, arrived)
        validate(fromWalletId != toWalletId && arrived.isPositive, "error.transferSameAccount")
        books.transactions.transfer(TransferDraft(fromWalletId, toWalletId, date, arrived, memo = memo ?: books.text("generated.move")))
        networkFee?.takeIf { it.isPositive }?.let { fee(fromWalletId, date, it, books.text("generated.networkFee")) }
    }

    fun fee(walletId: String, date: LocalDate, amount: Money, memo: String? = null): Transaction {
        requireWallet(walletId, amount)
        validate(amount.isPositive, "error.amountPositive")
        return books.transactions.create(TransactionDraft(walletId, date, -amount, memo ?: books.text("generated.networkFee"), listOf(SplitDraft(category(FEES), -amount))))
    }

    fun income(walletId: String, date: LocalDate, amount: Money, kind: CryptoIncomeKind, memo: String? = null): Transaction {
        requireWallet(walletId, amount)
        validate(amount.isPositive, "error.amountPositive")
        return books.transactions.create(TransactionDraft(walletId, date, amount, memo ?: books.text("cryptoIncome.${kind.name}"), listOf(SplitDraft(category(INCOME), amount))))
    }

    private fun requireWallet(walletId: String, amount: Money) {
        val account = books.accounts.get(walletId)
        validate(account.type == AccountType.CRYPTO_WALLET, "error.notWallet")
        validate(amount.currency == account.currency, "error.currencyMismatch", account.currency.code)
    }

    private fun category(key: String): String? = books.categories.list().firstOrNull { it.systemKey == key }?.id

    // --- Linking sends and receives ------------------------------------------------------------------

    /**
     * Finds coins sent from one wallet that arrived in another of the household's wallets (the same
     * coin, within three days, the difference being a network fee of at most 1%) and records them as
     * one move, so they are not counted as a disposal. Returns how many were linked.
     */
    fun linkTransfers(): Int {
        val wallets = wallets(includeClosed = true)
        data class Line(val account: Account, val t: Transaction)
        fun loose(a: Account) = books.transactions.register(a.id).map { Line(a, it.transaction) }
            .filter { it.t.transfer == null && it.t.investmentId == null && it.t.splits.all { s -> s.categoryId == null } }
        val lines = wallets.flatMap(::loose)
        val used = HashSet<String>()
        var linked = 0
        for (send in lines.filter { it.t.amount.isNegative }.sortedBy { it.t.date }) {
            if (send.t.id in used) continue
            val sent = -send.t.amount
            val match = lines.filter { it.t.id !in used && it.account.id != send.account.id && it.account.currency == send.account.currency && it.t.amount.isPositive }
                .filter { send.t.date.daysUntil(it.t.date) in 0..3 && it.t.amount <= sent && (sent - it.t.amount) <= sent.times(BigDecimal("0.01")) }
                .minByOrNull { (sent - it.t.amount).minorUnits } ?: continue
            val sendId = externalId(send.account.id, send.t.id)
            val receiveId = externalId(match.account.id, match.t.id)
            books.transactions.delete(send.t.id)
            books.transactions.delete(match.t.id)
            val (from, to) = books.transactions.transfer(TransferDraft(send.account.id, match.account.id, match.t.date, match.t.amount, memo = send.t.memo ?: match.t.memo))
            sendId?.let { setExternalId(send.account.id, from.id, it) }
            receiveId?.let { setExternalId(match.account.id, to.id, it) }
            val fee = sent - match.t.amount
            if (fee.isPositive) fee(send.account.id, send.t.date, fee, books.text("generated.networkFee")).also { f -> sendId?.let { setExternalId(send.account.id, f.id, "$it:fee") } }
            used += send.t.id
            used += match.t.id
            linked++
        }
        return linked
    }

    private fun externalId(accountId: String, txnId: String): String? {
        val (group, _) = books.accounts.locate(accountId)
        return books.ledger(group).ledgerQueries.txnById(txnId).executeAsOneOrNull()?.external_id
    }

    private fun setExternalId(accountId: String, txnId: String, externalId: String) {
        val (group, _) = books.accounts.locate(accountId)
        books.ledger(group).ledgerQueries.setExternalId(externalId, books.now(), txnId)
    }

    private fun hasExternal(accountId: String, externalId: String): Boolean {
        val (group, _) = books.accounts.locate(accountId)
        return books.ledger(group).ledgerQueries.txnByExternalId(accountId, externalId).executeAsOneOrNull() != null
    }

    // --- Adjusted cost base (CR-06) ---------------------------------------------------------------

    /** The ACB of each coin per owner, in the base currency, with capital gains and losses. */
    fun acb(through: LocalDate): CryptoAcb {
        val zero = Money.zero(base)
        val missing = HashSet<Currency>()
        val problems = ArrayList<String>()
        val all = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        val wallets = wallets(includeClosed = true)
        fun fmv(m: Money, date: LocalDate): Money =
            if (m.currency == base) m.abs() else books.rates.convert(m.abs(), base, date) ?: run { missing += m.currency; zero }

        data class Key(val coin: Currency, val owners: Set<String>)
        data class Ev(val key: Key, val event: CostEvent, val account: Account)
        val events = ArrayList<Ev>()
        for (w in wallets) {
            val key = Key(w.currency, w.ownerMemberIds)
            if (w.openingBalance.isPositive) {
                val cost = details(w.id).openingCost
                if (cost == null) problems += "${w.name}: opening cost not entered"
                events += Ev(key, CostEvent(w.openingDate, CostEventKind.ACQUIRE, w.openingBalance.toBigDecimal(), cost ?: zero, order = -1), w)
            }
            for (t in books.transactions.register(w.id).map { it.transaction }.filter { it.date <= through }) {
                val qty = t.amount.abs().toBigDecimal()
                val other = t.transfer?.let { all[it.otherAccountId] }
                val value = when {
                    other != null && other.currency == w.currency -> null // between own wallets: not a disposal
                    t.transfer != null && t.originalAmount != null -> fmv(t.originalAmount, t.date)
                    else -> fmv(t.amount, t.date)
                } ?: continue
                val kind = if (t.amount.isPositive) CostEventKind.ACQUIRE else CostEventKind.DISPOSE
                events += Ev(key, CostEvent(t.date, kind, qty, value, ref = t.id), w)
            }
        }
        val pools = LinkedHashMap<Key, CostPool>()
        // Every wallet holding the coin for the same owners shares the pool, even one that only received coins moved in.
        val accountsByKey = HashMap<Key, MutableSet<String>>()
        wallets.forEach { w -> accountsByKey.getOrPut(Key(w.currency, w.ownerMemberIds)) { HashSet() } += w.id }
        for (e in events.sortedWith(compareBy({ it.event.date }, { it.event.order }, { if (it.event.kind == CostEventKind.ACQUIRE) 0 else 1 }))) {
            accountsByKey.getOrPut(e.key) { HashSet() } += e.account.id
            try {
                pools.getOrPut(e.key) { CostPool(zero) }.apply(e.event)
            } catch (x: CostBaseException) {
                problems += "${e.account.name}, ${e.event.date}: ${x.message}"
            }
        }
        val gains = pools.flatMap { (key, p) ->
            val acquisitions = events.filter { it.key == key && it.event.kind == CostEventKind.ACQUIRE }.map { it.event }
            CostBase.flagSuperficial(p.dispositions, acquisitions).map { CapitalGain(coinSecurity(key.coin), key.owners, it) }
        }.sortedBy { it.disposition.date }
        val list = pools.filter { it.value.state.quantity.signum() != 0 }.map { (key, p) ->
            AcbPool(coinSecurity(key.coin), key.owners, accountsByKey[key].orEmpty(), p.state.quantity, p.state.cost)
        }
        return CryptoAcb(list, gains, missing, problems)
    }

    /** A coin shown alongside securities in the ACB and gains views. */
    fun coinSecurity(c: Currency) = Security("coin:${c.code}", c.code, null, COIN_NAMES[c.code] ?: c.code, SecurityKind.OTHER, base, AssetClass.OTHER, Region.GLOBAL)

    // --- Exchange imports (CR-03) ----------------------------------------------------------------

    fun plan(file: CryptoExchangeFile): CryptoImportPlan {
        val codes = file.events.flatMap { listOfNotNull(it.sentCurrency, it.receivedCurrency, it.feeCurrency) }.distinct()
        val (fiat, coins) = codes.partition { isFiat(it) }
        return CryptoImportPlan(file.exchange, fiat.sorted(), coins.sorted(), file.events.size, file.warnings)
    }

    private fun isFiat(code: String) = runCatching { !Currency.of(code).isCrypto }.getOrDefault(false)

    /**
     * Imports an exchange history. [fiatAccounts] gives the account for each fiat currency the
     * exchange held; [walletAccounts] the wallet for each coin, or null to create "<exchange> <coin>"
     * in [groupId]. Events already imported are skipped, and sends that arrived in another of the
     * household's wallets are linked afterwards.
     */
    fun importExchange(file: CryptoExchangeFile, groupId: String, fiatAccounts: Map<String, String>, walletAccounts: Map<String, String?>, owners: Set<String> = emptySet()): CryptoImportResult {
        val group = books.group(groupId)
        books.require(group, PermissionLevel.EDIT)
        val warnings = ArrayList(file.warnings)
        var created = 0
        val accounts = HashMap<String, String>()
        accounts += fiatAccounts
        for (coin in plan(file).coins) {
            accounts[coin] = walletAccounts[coin] ?: run {
                val currency = Currency.registerCrypto(coin)
                created++
                books.accounts.create(AccountDraft(groupId, "${file.exchange} $coin", AccountType.CRYPTO_WALLET, currency, Money.zero(currency), file.events.minOf { it.date }, ownerMemberIds = owners)).id
            }
        }
        val seen = HashMap<String, Int>()
        var added = 0
        var already = 0
        for (e in file.events) {
            val ext = "${file.exchange.lowercase()}:" + (e.externalId ?: fingerprint(e, seen))
            val ok = runCatching { post(e, ext, accounts) }
                .onFailure { warnings += "${e.date} ${e.kind}: ${(it as? ValidationException)?.message ?: it.message}" }.getOrNull() ?: continue
            if (ok) added++ else already++
        }
        val linked = linkTransfers()
        books.session.audit("IMPORT", "crypto", null, "${file.exchange}: $added")
        return CryptoImportResult(added, already, created, linked, warnings.distinct())
    }

    /** Posts one event; false when it was already there. */
    private fun post(e: CryptoEvent, ext: String, accounts: Map<String, String>): Boolean {
        fun acct(code: String?) = code?.let(accounts::get) ?: throw ValidationException("error.importNoAccount", code.orEmpty())
        fun money(v: BigDecimal?, code: String?) = Money.of(v ?: BigDecimal.ZERO, currencyOf(code!!))
        val fee = e.fee?.takeIf { it.signum() > 0 }
        when (e.kind) {
            CryptoEventKind.TRADE -> {
                val to = acct(e.receivedCurrency)
                if (hasExternal(to, ext)) return false
                val from = acct(e.sentCurrency)
                val sent = money(e.sent, e.sentCurrency) + if (fee != null && e.feeCurrency == e.sentCurrency) money(fee, e.sentCurrency) else Money.zero(currencyOf(e.sentCurrency!!))
                val received = money(e.received, e.receivedCurrency) - if (fee != null && e.feeCurrency == e.receivedCurrency) money(fee, e.receivedCurrency) else Money.zero(currencyOf(e.receivedCurrency!!))
                val (a, b) = books.transactions.transfer(TransferDraft(from, to, e.date, sent, received.takeIf { it.currency != sent.currency }, e.memo))
                setExternalId(from, a.id, ext)
                setExternalId(to, b.id, ext)
            }
            CryptoEventKind.DEPOSIT, CryptoEventKind.INCOME -> {
                val to = acct(e.receivedCurrency)
                if (hasExternal(to, ext)) return false
                val amount = money(e.received, e.receivedCurrency)
                val coin = amount.currency.isCrypto
                val splits = if (e.kind == CryptoEventKind.INCOME) listOf(SplitDraft(category(if (coin) INCOME else "income.investment.interest"), amount)) else emptyList()
                val t = books.transactions.create(TransactionDraft(to, e.date, amount, e.memo, splits, e.memo))
                setExternalId(to, t.id, ext)
                if (fee != null && e.feeCurrency == e.receivedCurrency) feeLine(to, e.date, money(fee, e.feeCurrency), "$ext:fee")
            }
            CryptoEventKind.WITHDRAWAL -> {
                val from = acct(e.sentCurrency)
                if (hasExternal(from, ext)) return false
                val t = books.transactions.create(TransactionDraft(from, e.date, -money(e.sent, e.sentCurrency), e.memo, memo = e.memo))
                setExternalId(from, t.id, ext)
                if (fee != null && e.feeCurrency == e.sentCurrency) feeLine(from, e.date, money(fee, e.feeCurrency), "$ext:fee")
            }
            CryptoEventKind.FEE -> {
                val from = acct(e.feeCurrency ?: e.sentCurrency)
                if (hasExternal(from, ext)) return false
                feeLine(from, e.date, money(fee ?: e.sent, e.feeCurrency ?: e.sentCurrency), ext)
            }
        }
        return true
    }

    private fun feeLine(accountId: String, date: LocalDate, amount: Money, ext: String) {
        val key = if (amount.currency.isCrypto) FEES else "financial.bank_fees"
        val t = books.transactions.create(TransactionDraft(accountId, date, -amount, books.text("generated.fee"), listOf(SplitDraft(category(key), -amount))))
        setExternalId(accountId, t.id, ext)
    }

    private fun currencyOf(code: String): Currency = runCatching { Currency.of(code) }.getOrElse { Currency.registerCrypto(code) }

    private fun fingerprint(e: CryptoEvent, seen: MutableMap<String, Int>): String {
        val text = listOf(e.date, e.kind, e.sent, e.sentCurrency, e.received, e.receivedCurrency).joinToString("|")
        val n = seen.merge(text, 1, Int::plus)!!
        return MessageDigest.getInstance("SHA-256").digest("$text#$n".toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

    // --- Watch-only (CR-02) ----------------------------------------------------------------------

    /**
     * Brings a watch-only Bitcoin wallet up to date from a public block explorer (mempool.space):
     * the addresses it follows (for an extended public key, every used address until 20 unused in a
     * row), and each confirmed transaction's effect on them, with the network fee on its own line.
     */
    fun sync(walletId: String, fetch: (String) -> String): SyncResult {
        val d = details(walletId)
        val watch = d.watch ?: throw ValidationException("error.watchNone")
        val account = books.accounts.get(walletId)
        val addresses = when (d.watchKind) {
            WatchKind.XPUB -> usedAddresses(Bitcoin.parseExtendedKey(watch), fetch)
            else -> listOf(watch)
        }.toSet()
        val txs = LinkedHashMap<String, JsonObject>()
        for (a in addresses) {
            var last: String? = null
            // The explorer's answers are not trusted: pages are capped, a page that repeats ends the
            // walk, and only a well-formed transaction id goes into the next request.
            var pages = 0
            while (pages++ < MAX_PAGES) {
                val page = Json.parseToJsonElement(fetch("$ESPLORA/address/$a/txs/chain" + (last?.let { "/$it" } ?: ""))).jsonArray.map { it.jsonObject }
                page.forEach { txs[it["txid"]!!.jsonPrimitive.content] = it }
                val next = page.lastOrNull()?.get("txid")?.jsonPrimitive?.content
                if (page.size < 25 || next == null || next == last || !TXID.matches(next)) break
                last = next
            }
        }
        var added = 0
        for ((txid, tx) in txs) {
            val status = tx["status"]?.jsonObject ?: continue
            if (status["confirmed"]?.jsonPrimitive?.content != "true") continue
            val time = status["block_time"]?.jsonPrimitive?.long ?: continue
            val day = java.time.Instant.ofEpochSecond(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val date = LocalDate(day.year, day.monthValue, day.dayOfMonth)
            val ext = "btc:$txid"
            if (hasExternal(walletId, ext)) continue
            val ins = tx["vin"]!!.jsonArray.map { it.jsonObject["prevout"]?.jsonObject }
            val received = tx["vout"]!!.jsonArray.map { it.jsonObject }.filter { it["scriptpubkey_address"]?.jsonPrimitive?.content in addresses }.sumOf { it["value"]!!.jsonPrimitive.long }
            val spent = ins.filter { it?.get("scriptpubkey_address")?.jsonPrimitive?.content in addresses }.sumOf { it!!["value"]!!.jsonPrimitive.long }
            val net = received - spent
            if (net == 0L) continue
            if (net > 0) {
                val t = books.transactions.create(TransactionDraft(walletId, date, Money.ofMinor(net, account.currency), books.text("generated.received"), memo = books.text("generated.receivedWatch")))
                setExternalId(walletId, t.id, ext)
            } else {
                val allOurs = ins.all { it?.get("scriptpubkey_address")?.jsonPrimitive?.content in addresses }
                val fee = if (allOurs) tx["fee"]?.jsonPrimitive?.long ?: 0L else 0L
                val t = books.transactions.create(TransactionDraft(walletId, date, Money.ofMinor(net + fee, account.currency), books.text("generated.sent"), memo = books.text("generated.sentWatch")))
                setExternalId(walletId, t.id, ext)
                if (fee > 0) feeLine(walletId, date, Money.ofMinor(fee, account.currency), "$ext:fee")
            }
            added++
        }
        val (group, _) = books.accounts.locate(walletId)
        books.ledger(group).cryptoQueries.setWalletSynced(books.now(), walletId)
        return SyncResult(added, addresses.size, if (added > 0) linkTransfers() else 0)
    }

    private fun usedAddresses(key: Bitcoin.ExtendedKey, fetch: (String) -> String): List<String> {
        val used = ArrayList<String>()
        for (chain in 0..1) {
            var gap = 0
            var i = 0
            while (gap < GAP_LIMIT && i < MAX_ADDRESSES) {
                val address = key.address(chain, i++)
                val stats = Json.parseToJsonElement(fetch("$ESPLORA/address/$address")).jsonObject
                val count = stats["chain_stats"]?.jsonObject?.get("tx_count")?.jsonPrimitive?.long ?: 0L
                if (count > 0) { used += address; gap = 0 } else gap++
            }
        }
        return used
    }

    companion object {
        const val ESPLORA = "https://mempool.space/api"
        private const val MAX_PAGES = 400
        private const val MAX_ADDRESSES = 10_000
        private val TXID = Regex("[0-9a-f]{64}")
        const val GAP_LIMIT = 20
        private const val FEES = "financial.crypto_fees"
        private const val INCOME = "income.investment.crypto"

        val COIN_NAMES = mapOf(
            "BTC" to "Bitcoin", "ETH" to "Ether", "LTC" to "Litecoin", "BCH" to "Bitcoin Cash", "XRP" to "XRP", "ADA" to "Cardano", "SOL" to "Solana",
            "DOT" to "Polkadot", "DOGE" to "Dogecoin", "XLM" to "Stellar", "USDC" to "USD Coin", "USDT" to "Tether", "AVAX" to "Avalanche",
            "LINK" to "Chainlink", "MATIC" to "Polygon", "POL" to "Polygon", "ATOM" to "Cosmos",
        )
    }
}
