package ca.schippers.hfm.books

import ca.schippers.hfm.money.Money
import kotlinx.datetime.LocalDate

/** SAL-02: what a pay stub takes off gross pay, each kind with its own default category. */
enum class DeductionKind(val categoryKey: String) {
    INCOME_TAX("taxes.income_tax"),
    CPP_QPP("payroll.cpp_qpp"),
    EI_QPIP("payroll.ei_qpip"),
    UNION_DUES("work.union_dues"),
    PENSION("payroll.pension"),
    GROUP_INSURANCE("payroll.group_insurance"),
    RRSP("payroll.rrsp"),
    CHARITY("gifts.charity"),
    OTHER("payroll.other"),
}

/** An earnings line (salary, overtime, vacation pay, bonus...) as printed. */
data class PayEarning(val description: String, val amount: Money)

/** A deduction, a positive amount taken off gross pay. */
data class PayDeduction(val kind: DeductionKind, val description: String?, val amount: Money)

/**
 * SAL-02: a pay stub, entered by hand or read by AI. [net] is what reaches the account: gross pay
 * less the deductions; [printedNet] is the net pay the stub shows, when known, to check against.
 */
data class PayStub(
    val employer: String,
    val payDate: LocalDate,
    val earnings: List<PayEarning>,
    val deductions: List<PayDeduction>,
    val printedNet: Money? = null,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
) {
    val gross: Money get() = earnings.map { it.amount }.reduce(Money::plus)
    val net: Money get() = deductions.fold(gross) { a, d -> a - d.amount }

    /** True when the stub's own net pay disagrees with gross less deductions (a misread or a missed line). */
    val netDisagrees: Boolean get() = printedNet != null && printedNet != net
}

/** SAL-02: a pay stub becomes one deposit, split into gross pay and each deduction. */
class PayStubService internal constructor(private val books: Books) {

    /**
     * The deposit for [stub] into [accountId]: each earnings line on salary (or bonuses, for a bonus,
     * commission or premium), each deduction as a negative split on its category, so the
     * transaction's amount is the net pay. [memberId] is the person paid.
     */
    fun draft(accountId: String, stub: PayStub, memberId: String? = null): TransactionDraft {
        validate(stub.employer.isNotBlank(), "error.payStubEmployer")
        validate(stub.earnings.isNotEmpty() && !stub.gross.isNegative && !stub.gross.isZero, "error.payStubGross")
        validate(stub.deductions.all { !it.amount.isNegative }, "error.payStubDeduction")
        validate(!stub.net.isNegative && !stub.net.isZero, "error.payStubNet")
        val keys = books.categories.list().mapNotNull { c -> c.systemKey?.let { it to c.id } }.toMap()
        val earnings = stub.earnings.filter { !it.amount.isZero }.map { e ->
            val key = if (BONUS.containsMatchIn(e.description)) "income.employment.bonus" else "income.employment.salary"
            SplitDraft(keys[key] ?: keys["income.employment"], e.amount, e.description, memberId)
        }
        val deductions = stub.deductions.filter { !it.amount.isZero }.map { d ->
            SplitDraft(keys[d.kind.categoryKey] ?: keys["payroll.other"], -d.amount, d.description, memberId)
        }
        val period = listOfNotNull(stub.periodStart, stub.periodEnd).takeIf { it.size == 2 }?.joinToString(" – ")
        return TransactionDraft(accountId, stub.payDate, stub.net, stub.employer.trim(), earnings + deductions, period, memberId)
    }

    /** Records [stub] as a deposit, filed with the pay stub [documentId] when there is one. */
    fun record(accountId: String, stub: PayStub, memberId: String? = null, documentId: String? = null): Transaction {
        val draft = draft(accountId, stub, memberId)
        return if (documentId != null) books.documents.fileAsTransaction(documentId, draft) else books.transactions.create(draft)
    }

    companion object {
        private val BONUS = Regex("bonus|commission|prime|incentive|gratification", RegexOption.IGNORE_CASE)

        /** Payroll charity deductions (United Way, Centraide...) count as donations. */
        val CHARITY = Regex("united way|centraide|charit|donation|\\bdons?\\b|bienfaisance", RegexOption.IGNORE_CASE)
    }
}
