package ca.schippers.hfm.books

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.medical.Window
import ca.schippers.hfm.calc.invest.SlipKind
import ca.schippers.hfm.calc.invest.TaxSlips
import ca.schippers.hfm.domain.TaxFlag
import ca.schippers.hfm.money.Currency
import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate
import java.math.RoundingMode

/** TAX-02: the parts of a year-end package, in the order a return is filled in. */
enum class PackageSection { EMPLOYMENT, OTHER_INCOME, INVESTMENTS, SELF_EMPLOYMENT, DEDUCTIONS, CREDITS, PAYMENTS }

/**
 * TAX-02: what a package line is, with the federal return line it goes on (or the schedule or form)
 * when there is one fixed place for it. Quebec's return has its own lines, left to the preparer.
 */
enum class PackageItem(val section: PackageSection, val line: String?) {
    EMPLOYMENT_INCOME(PackageSection.EMPLOYMENT, "10100"),
    CPP_QPP_CONTRIBUTIONS(PackageSection.EMPLOYMENT, "30800"),
    EI_PREMIUMS(PackageSection.EMPLOYMENT, "31200"),
    EI_QPIP_PREMIUMS(PackageSection.EMPLOYMENT, null),
    OAS_PENSION(PackageSection.OTHER_INCOME, "11300"),
    CPP_QPP_BENEFITS(PackageSection.OTHER_INCOME, "11400"),
    OTHER_PENSIONS(PackageSection.OTHER_INCOME, "11500"),
    RRIF_INCOME(PackageSection.OTHER_INCOME, null),
    EI_BENEFITS(PackageSection.OTHER_INCOME, "11900"),
    TAXABLE_DIVIDENDS(PackageSection.INVESTMENTS, "12000"),
    INTEREST(PackageSection.INVESTMENTS, "12100"),
    TAXABLE_CAPITAL_GAINS(PackageSection.INVESTMENTS, "12700"),
    FOREIGN_TAX_PAID(PackageSection.INVESTMENTS, "T2209"),
    BUSINESS_INCOME(PackageSection.SELF_EMPLOYMENT, "T2125"),
    BUSINESS_EXPENSES(PackageSection.SELF_EMPLOYMENT, "T2125"),
    SALES_TAX_PAID(PackageSection.SELF_EMPLOYMENT, null),
    PENSION_PLAN_CONTRIBUTIONS(PackageSection.DEDUCTIONS, "20700"),
    RRSP_CONTRIBUTIONS(PackageSection.DEDUCTIONS, "20800"),
    FHSA_CONTRIBUTIONS(PackageSection.DEDUCTIONS, "20805"),
    UNION_DUES(PackageSection.DEDUCTIONS, "21200"),
    CHILD_CARE(PackageSection.DEDUCTIONS, "21400"),
    MOVING(PackageSection.DEDUCTIONS, "21900"),
    EMPLOYMENT_EXPENSES(PackageSection.DEDUCTIONS, "22900"),
    MEDICAL(PackageSection.CREDITS, "33099"),
    MEDICAL_DEPENDANT(PackageSection.CREDITS, "33199"),
    TUITION(PackageSection.CREDITS, "Schedule 11"),
    DONATIONS(PackageSection.CREDITS, "Schedule 9"),
    POLITICAL(PackageSection.CREDITS, "40900"),
    INCOME_TAX_DEDUCTED(PackageSection.PAYMENTS, "43700"),
    INSTALMENTS(PackageSection.PAYMENTS, "47600"),
}

/** One line of a package: an amount in Canadian dollars, with where it comes from ([detail]: a payer, a period...). */
data class PackageLine(val item: PackageItem, val detail: String?, val amount: Money)

/** TAX-02: one person's package for a tax year; [memberId] null holds what belongs to no one in particular. */
data class PersonPackage(
    val memberId: String?,
    val province: Province,
    val lines: List<PackageLine>,
    /** Slips still expected (TAX-01), to chase before filing. */
    val missingSlips: List<ChecklistSlip>,
    /** The slips, receipts and other papers in the vault behind the lines, to hand over with it. */
    val documents: List<PackageDocument>,
) {
    fun total(item: PackageItem): Money = lines.filter { it.item == item }.fold(Money.zero(Currency.CAD)) { a, l -> a + l.amount }
}

/** A document for the package, with a name that says what it is. */
data class PackageDocument(val documentId: String, val name: String)

data class TaxPackage(val year: Int, val people: List<PersonPackage>)

/**
 * TAX-02: the year-end tax package, gathered from the books: pay stubs, flagged categories, plan
 * contributions, investment slips and gains, medical expenses, donations and instalments. Amounts in
 * other currencies are converted at each transaction's date; those without a rate are left out.
 */
class TaxPackageService internal constructor(private val books: Books) {

    private val cad = Currency.CAD

    fun build(year: Int): TaxPackage {
        val lines = HashMap<String?, MutableList<PackageLine>>()
        fun add(member: String?, item: PackageItem, detail: String?, amount: Money) {
            if (!amount.isZero) lines.getOrPut(member) { mutableListOf() } += PackageLine(item, detail, amount)
        }
        fromSplits(year, ::add)
        fromPlans(year, ::add)
        fromInvestments(year, ::add)
        fromMedical(year, ::add)
        books.donations.totals(year).forEach { t ->
            add(t.memberId, PackageItem.DONATIONS, null, t.charitable)
            add(t.memberId, PackageItem.POLITICAL, null, t.political)
        }
        books.instalments.schedule(year).groupBy { it.memberId to it.authority }.forEach { (key, rows) ->
            add(key.first, PackageItem.INSTALMENTS, key.second.name, rows.fold(Money.zero(cad)) { a, i -> a + toCad(i.covered, i.dueDate) })
        }

        val slips = books.slipChecklist.checklist(year)
        val members = books.members.list(includeArchived = true).associateBy { it.id }
        val people = (lines.keys + slips.map { it.memberId }).distinct()
        return TaxPackage(
            year,
            people.map { m ->
                val mine = lines[m].orEmpty()
                    // Lines from the same source add up: one line per item and payer.
                    .groupBy { it.item to it.detail }.map { (k, l) -> PackageLine(k.first, k.second, l.fold(Money.zero(cad)) { a, x -> a + x.amount }) }
                    .filter { !it.amount.isZero }
                    .sortedWith(compareBy({ it.item.ordinal }, { it.detail.orEmpty() }))
                val province = m?.let { books.provinceOf(it) } ?: books.province
                PersonPackage(m, province, relabel(mine, province), slips.filter { it.memberId == m && it.status == SlipStatus.EXPECTED }, documents(year, m, slips))
            }.filter { it.lines.isNotEmpty() || it.missingSlips.isNotEmpty() }
                .sortedWith(compareBy({ it.memberId == null }, { members[it.memberId]?.displayName.orEmpty() })),
        )
    }

    /** In Quebec, EI and QPIP share one category, so the combined premiums have no single federal line. */
    private fun relabel(lines: List<PackageLine>, province: Province) =
        if (province != Province.QC) lines else lines.map { if (it.item == PackageItem.EI_PREMIUMS) it.copy(item = PackageItem.EI_QPIP_PREMIUMS) else it }

    private fun toCad(m: Money, date: LocalDate): Money = if (m.currency == cad) m else books.rates.convert(m, cad, date) ?: Money.zero(cad)

    /** Income, payroll deductions and flagged spending, by person and payer. */
    private fun fromSplits(year: Int, add: (String?, PackageItem, String?, Money) -> Unit) {
        val categories = books.categories.list(includeArchived = true)
        val byKey = categories.mapNotNull { c -> c.systemKey?.let { it to c.id } }.toMap()
        val keyOf = byKey.entries.associate { (k, v) -> v to k }
        val flagOf = categories.associate { it.id to it.taxFlag }
        val accounts = books.accounts.list(includeClosed = true).associate { it.account.id to it.account }
        for (g in books.groups()) {
            val rows = books.ledger(g).taxYearQueries.packageSplits(LocalDate(year, 1, 1).toString(), LocalDate(year, 12, 31).toString()).executeAsList()
            for (r in rows) {
                val account = accounts[r.account_id] ?: continue
                if (account.type.isRegistered) continue
                val member = r.member_id ?: account.ownerMemberIds.singleOrNull()
                val amount = toCad(Money.ofMinor(r.amount_minor, account.currency), LocalDate.parse(r.date))
                val payee = r.payee_text
                val payroll = r.txn_amount_minor > 0
                val key = r.category_id?.let(keyOf::get)
                val flag = r.tax_flag?.let(TaxFlag::valueOf) ?: r.category_id?.let(flagOf::get)
                val item = when (key) {
                    "income.employment", "income.employment.salary", "income.employment.bonus" -> PackageItem.EMPLOYMENT_INCOME
                    "income.pension.oas" -> PackageItem.OAS_PENSION
                    "income.pension.qpp_cpp" -> PackageItem.CPP_QPP_BENEFITS
                    "income.pension.employer" -> PackageItem.OTHER_PENSIONS
                    "income.pension.rrif" -> PackageItem.RRIF_INCOME
                    "income.benefits.ei", "income.benefits.ei_qpip" -> PackageItem.EI_BENEFITS
                    "income.investment.interest" -> PackageItem.INTEREST
                    // Income tax taken off pay; tax paid when filing is not a deduction at source.
                    "taxes.income_tax" -> if (payroll) PackageItem.INCOME_TAX_DEDUCTED else null
                    "payroll.cpp_qpp" -> PackageItem.CPP_QPP_CONTRIBUTIONS
                    "payroll.ei_qpip" -> PackageItem.EI_PREMIUMS
                    "payroll.pension" -> PackageItem.PENSION_PLAN_CONTRIBUTIONS
                    "payroll.rrsp" -> PackageItem.RRSP_CONTRIBUTIONS
                    "work.union_dues" -> PackageItem.UNION_DUES
                    else -> when (flag) {
                        TaxFlag.CHILD_CARE -> PackageItem.CHILD_CARE
                        TaxFlag.MOVING -> PackageItem.MOVING
                        TaxFlag.EMPLOYMENT -> PackageItem.EMPLOYMENT_EXPENSES
                        TaxFlag.TUITION -> PackageItem.TUITION
                        TaxFlag.BUSINESS -> if (amount.isPositive) PackageItem.BUSINESS_INCOME else PackageItem.BUSINESS_EXPENSES
                        else -> null
                    }
                } ?: continue
                // Income is positive in the books, money paid out negative; the package shows both as positive amounts.
                val shown = if (item.section == PackageSection.EMPLOYMENT && item != PackageItem.EMPLOYMENT_INCOME ||
                    item.section == PackageSection.DEDUCTIONS || item.section == PackageSection.CREDITS || item.section == PackageSection.PAYMENTS ||
                    item == PackageItem.BUSINESS_EXPENSES
                ) -amount else amount
                add(member, item, payee, shown)
            }
            // TX-04: sales taxes paid on self-employment expenses, for input tax credits when registered.
            val business = rows.filter { r -> (r.tax_flag?.let(TaxFlag::valueOf) ?: r.category_id?.let(flagOf::get)) == TaxFlag.BUSINESS && r.amount_minor < 0 }
                .associate { it.txn_id to it.member_id }
            for (t in books.ledger(g).salesTaxQueries.salesTaxesBetween(LocalDate(year, 1, 1).toString(), LocalDate(year, 12, 31).toString()).executeAsList()) {
                if (t.id !in business) continue
                val account = accounts[t.account_id] ?: continue
                val member = business[t.id] ?: account.ownerMemberIds.singleOrNull()
                add(member, PackageItem.SALES_TAX_PAID, t.tax, toCad(Money.ofMinor(t.amount_minor, account.currency), LocalDate.parse(t.date)))
            }
        }
    }

    /** RRSP and FHSA contributions made in the year, by the person whose room they use. */
    private fun fromPlans(year: Int, add: (String?, PackageItem, String?, Money) -> Unit) {
        val today = books.today()
        for ((plan, item) in listOf(RoomPlan.RRSP to PackageItem.RRSP_CONTRIBUTIONS, RoomPlan.FHSA to PackageItem.FHSA_CONTRIBUTIONS)) {
            for (m in books.plans.peopleWith(plan)) {
                val room = books.plans.room(m.id, plan, year, today)
                room.flows.filter { !it.adjustment && it.amount.isPositive }.groupBy { it.accountName }.forEach { (name, flows) ->
                    add(m.id, item, name, flows.fold(Money.zero(cad)) { a, f -> a + f.amount })
                }
            }
        }
    }

    /** From the investment income report (INV-08): dividends as taxable amounts, interest, foreign tax, and the taxable half of capital gains. */
    private fun fromInvestments(year: Int, add: (String?, PackageItem, String?, Money) -> Unit) {
        fun box(line: SlipLine, code: String) = line.boxes[code] ?: Money.zero(cad)
        for (p in books.taxSlips.report(year).people) {
            val m = p.member?.id
            var slipGains = Money.zero(cad)
            for (line in p.slips) {
                val name = line.security?.name ?: line.account.name
                when (line.kind) {
                    SlipKind.T5 -> {
                        add(m, PackageItem.TAXABLE_DIVIDENDS, name, box(line, "11") + box(line, "25"))
                        add(m, PackageItem.INTEREST, name, box(line, "13") + box(line, "14"))
                        add(m, PackageItem.FOREIGN_TAX_PAID, name, box(line, "16"))
                        slipGains += box(line, "18")
                    }
                    SlipKind.T3 -> {
                        add(m, PackageItem.TAXABLE_DIVIDENDS, name, box(line, "32") + box(line, "50"))
                        add(m, PackageItem.INTEREST, name, box(line, "26"))
                        add(m, PackageItem.FOREIGN_TAX_PAID, name, box(line, "34"))
                        slipGains += box(line, "21")
                    }
                    // The Quebec slips repeat the same income.
                    SlipKind.RL3, SlipKind.RL16 -> Unit
                }
            }
            val gains = (p.netGain ?: Money.zero(cad)) + slipGains
            if (gains.isPositive) {
                add(m, PackageItem.TAXABLE_CAPITAL_GAINS, null, Money.of(gains.toBigDecimal().multiply(TaxSlips.INCLUSION_RATE), cad, RoundingMode.HALF_UP))
            }
        }
    }

    /**
     * MED-12, MED-14: the medical expenses as the medical expenses report claims them. The
     * household's own (spouses and children) are claimed together, by one spouse, over the best
     * 12-month period for all of them, so they are one line in the household's package; each adult
     * dependant's best period is a line of its own (federal 33199) there too.
     */
    private fun fromMedical(year: Int, add: (String?, PackageItem, String?, Money) -> Unit) {
        fun money(w: Window) = Money.of(w.total.setScale(2, RoundingMode.HALF_UP), cad)
        val report = books.medical.taxReport(year)
        report.family?.let { w -> if (w.total.signum() > 0) add(null, PackageItem.MEDICAL, "${w.start} – ${w.end}", money(w)) }
        for (p in report.people.filter { it.otherDependant }) {
            val w = p.best ?: continue
            if (w.total.signum() > 0) add(null, PackageItem.MEDICAL_DEPENDANT, "${p.member.displayName}: ${w.start} – ${w.end}", money(w))
        }
    }

    /** The slips from the checklist, donation receipts and medical receipts behind one person's package. */
    private fun documents(year: Int, memberId: String?, slips: List<ChecklistSlip>): List<PackageDocument> {
        val docs = LinkedHashMap<String, String>()
        fun put(id: String, name: String) { docs.putIfAbsent(id, name) }
        for (s in slips.filter { it.memberId == memberId && it.documents > 0 }) {
            for (d in books.documents.documentsFor(SlipChecklistService.ENTITY, s.id)) put(d.id, "${s.type.name} - ${s.issuer}")
        }
        for (d in books.donations.list(year).filter { it.memberId == memberId && it.documents > 0 }) {
            for (doc in books.documents.documentsFor(DocumentEntity.TRANSACTION, d.transactionId)) put(doc.id, "Donation - ${d.receipt?.charity ?: d.payee.orEmpty()} - ${d.date}")
        }
        // The medical receipts go with the household's package, for the periods its lines claim.
        if (memberId == null) {
            val report = books.medical.taxReport(year)
            val claimed = listOfNotNull(report.family?.let { it to report.people.filter { p -> !p.otherDependant }.map { p -> p.member.id }.toSet() }) +
                report.people.filter { it.otherDependant && it.best != null }.map { it.best!! to setOf(it.member.id) }
            for ((window, people) in claimed) {
                for (e in books.medical.expensesIn(window, people)) {
                    for (doc in books.documents.documentsFor(MedicalService.EXPENSE, e.id)) put(doc.id, "Medical - ${e.description ?: e.service.name} - ${e.taxDate}")
                }
            }
        }
        return docs.map { (id, name) -> PackageDocument(id, name) }
    }
}
