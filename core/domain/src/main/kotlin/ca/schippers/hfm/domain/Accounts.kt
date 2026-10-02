package ca.schippers.hfm.domain

/** Account types (ACC-02). Investment and registered plan types are detailed in Phase 3. */
enum class AccountType(val kind: AccountKind) {
    CHEQUING(AccountKind.BANK),
    SAVINGS(AccountKind.BANK),
    HIGH_INTEREST_SAVINGS(AccountKind.BANK),
    GIC_TERM_DEPOSIT(AccountKind.BANK),
    CASH(AccountKind.BANK),
    PREPAID_GIFT_CARD(AccountKind.BANK),
    CREDIT_CARD(AccountKind.CREDIT),
    LINE_OF_CREDIT(AccountKind.CREDIT),
    HELOC(AccountKind.CREDIT),
    LOAN(AccountKind.LOAN),
    MORTGAGE(AccountKind.LOAN),
    CRYPTO_WALLET(AccountKind.INVESTMENT),
    BROKERAGE(AccountKind.INVESTMENT),
    RRSP(AccountKind.INVESTMENT),
    SPOUSAL_RRSP(AccountKind.INVESTMENT),
    RRIF(AccountKind.INVESTMENT),
    SPOUSAL_RRIF(AccountKind.INVESTMENT),
    LIRA(AccountKind.INVESTMENT),
    LIF(AccountKind.INVESTMENT),
    TFSA(AccountKind.INVESTMENT),
    FHSA(AccountKind.INVESTMENT),
    RESP(AccountKind.INVESTMENT),
    PENSION(AccountKind.INVESTMENT),
    PRECIOUS_METALS(AccountKind.INVESTMENT),
    PROPERTY(AccountKind.ASSET),
    VEHICLE(AccountKind.ASSET),
    OTHER_ASSET(AccountKind.ASSET),
    ;

    /** Registered plans and pensions: no capital gains while the money stays inside (INV-03). */
    val isRegistered: Boolean
        get() = this in setOf(RRSP, SPOUSAL_RRSP, RRIF, SPOUSAL_RRIF, LIRA, LIF, TFSA, FHSA, RESP, PENSION)
}

enum class AccountKind(val isLiability: Boolean) {
    BANK(false),
    CREDIT(true),
    LOAN(true),
    INVESTMENT(false),
    ASSET(false),
}

/** ACC-04, ACC-05: closed accounts keep their history but are hidden day to day. */
enum class AccountStatus { OPEN, DORMANT, CLOSED }

/** TX-01 cleared / reconciled status. */
enum class ClearedStatus { UNCLEARED, CLEARED, RECONCILED }

enum class MemberKind { ADULT, CHILD, DEPENDANT }

enum class CategoryKind { EXPENSE, INCOME }

/** Tax treatment flags on categories and splits, feeding the tax summary (CAT-05). */
enum class TaxFlag { MEDICAL, CHARITABLE, POLITICAL, CHILD_CARE, TUITION, BUSINESS, EMPLOYMENT, MOVING }
