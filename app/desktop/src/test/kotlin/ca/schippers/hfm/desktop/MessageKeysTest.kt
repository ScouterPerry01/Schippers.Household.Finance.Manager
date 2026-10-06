package ca.schippers.hfm.desktop

import ca.schippers.hfm.i18n.Language
import ca.schippers.hfm.i18n.Messages
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every message key used by the desktop app and the bookkeeping rules exists in both languages
 * (NFR-06). Keys built from enum names (e.g. "accountType.$type") are checked for every value.
 */
class MessageKeysTest {

    private val sources = listOf(File("src/main/kotlin"), File("../../core/books/src/main/kotlin"))
        .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }

    @Test
    fun `every key used in code exists in English and French`() {
        val keys = sources.flatMapTo(sortedSetOf()) { file ->
            val text = file.readText()
            Regex("""\bt\("([A-Za-z0-9_.]+)"""").findAll(text).map { it.groupValues[1] } +
                // t(if (...) "a" else "b")
                Regex("""\bt\(if \([^"]*"([A-Za-z0-9_.]+)" else "([A-Za-z0-9_.]+)"""").findAll(text).flatMap { listOf(it.groupValues[1], it.groupValues[2]) } +
                Regex(""""(error\.[A-Za-z0-9_.]+)"""").findAll(text).map { it.groupValues[1] }
        }
        val enumKeys = listOf(
            "accountType" to ca.schippers.hfm.domain.AccountType.entries,
            "accountKind" to ca.schippers.hfm.domain.AccountKind.entries,
            "status" to ca.schippers.hfm.domain.AccountStatus.entries,
            "memberKind" to ca.schippers.hfm.domain.MemberKind.entries,
            "tax" to ca.schippers.hfm.domain.TaxFlag.entries,
            "category.new" to ca.schippers.hfm.domain.CategoryKind.entries,
            "nav" to Section.entries.map { it.name.lowercase() },
            "rateRules.type" to ca.schippers.hfm.calc.rules.RuleType.entries,
            "lineStatus" to ca.schippers.hfm.books.LineStatus.entries,
            "statementStatus" to ca.schippers.hfm.books.StatementStatus.entries,
            "import.amountMode" to listOf("single", "split"),
            "billKind" to ca.schippers.hfm.books.BillKind.entries,
            "report" to ReportKind.entries,
            "range" to RangePreset.entries,
            "compare" to Compare.entries,
            "report.export" to ExportFormat.entries,
            "budgetPeriod" to ca.schippers.hfm.books.BudgetPeriod.entries,
            "backupFrequency" to ca.schippers.hfm.books.BackupFrequency.entries,
            "amountKind" to ca.schippers.hfm.books.AmountKind.entries,
            "paymentMethod" to ca.schippers.hfm.books.PaymentMethod.entries,
            "monthDay" to ca.schippers.hfm.calc.schedule.MonthDay.entries,
            "adjust" to ca.schippers.hfm.calc.schedule.BusinessDayAdjust.entries,
            "bills.tab" to listOf("AGENDA", "ALL", "CALENDAR", "SUBSCRIPTIONS", "FORECAST"),
            "eventCategory" to ca.schippers.hfm.books.EventCategory.entries,
            "calendar.mark" to ca.schippers.hfm.books.OccurrenceMark.entries,
            "calendar.tab" to CalendarView.entries,
            "calendarKind" to ca.schippers.hfm.books.CalendarKind.entries,
            "scheduleKind" to ca.schippers.hfm.books.ScheduleKind.entries,
            "occurrenceStatus" to ca.schippers.hfm.books.OccurrenceStatus.entries,
            "healthDue" to listOf("Refill", "TestFollowUp", "ImmunizationDue"),
            "providerKind" to ca.schippers.hfm.books.ProviderKind.entries,
            "contactKind" to ca.schippers.hfm.books.ContactKind.entries,
            "detailType" to ca.schippers.hfm.books.DetailType.entries,
            "linkRole" to ca.schippers.hfm.books.LinkRole.entries,
            "linkRoleShort" to ca.schippers.hfm.books.LinkRole.entries,
            "linkTarget" to ca.schippers.hfm.books.LinkTarget.entries,
            "conditionStatus" to ca.schippers.hfm.books.ConditionStatus.entries,
            "severity" to ca.schippers.hfm.books.Severity.entries,
            "health.tab" to listOf("MEDICATIONS", "APPOINTMENTS", "CONDITIONS", "ALLERGIES", "TESTS", "IMMUNIZATIONS", "PROVIDERS"),
            "health.add" to listOf("MEDICATIONS", "APPOINTMENTS", "CONDITIONS", "ALLERGIES", "TESTS", "IMMUNIZATIONS", "PROVIDERS"),
            "health.new" to listOf("HealthCondition", "Allergy", "HealthTest", "Immunization"),
            "health.edit" to listOf("HealthCondition", "Allergy", "HealthTest", "Immunization"),
            "health.field" to listOf("HealthCondition", "Allergy", "HealthTest", "Immunization"),
            "rates.source" to ca.schippers.hfm.books.RateSource.entries,
            "goalStatus" to ca.schippers.hfm.books.GoalStatus.entries,
            "species" to ca.schippers.hfm.books.Species.entries,
            "sex" to ca.schippers.hfm.books.Sex.entries,
            "pets.neutered" to ca.schippers.hfm.books.Sex.entries,
            "documentKind" to ca.schippers.hfm.ocr.DocumentKind.entries,
            "taxName" to ca.schippers.hfm.ocr.TaxName.entries,
            "documents.tab" to listOf("INBOX", "ALL", "RETENTION"),
            "dateOrder" to ca.schippers.hfm.importers.DateOrder.entries,
            "action" to ca.schippers.hfm.books.UserService.ACTIONS,
            "entity" to ca.schippers.hfm.books.UserService.ENTITIES,
            "role" to ca.schippers.hfm.domain.Role.entries,
            "role" to ca.schippers.hfm.domain.Role.entries.map { "$it.explain" },
            "permission" to ca.schippers.hfm.domain.PermissionLevel.entries,
            "users.tab" to listOf("USERS", "ACCESS", "ACTIVITY"),
            "renewalKind" to ca.schippers.hfm.books.RenewalKind.entries,
            "fuelType" to ca.schippers.hfm.books.FuelType.entries,
            "vehicleStatus" to ca.schippers.hfm.books.VehicleStatus.entries,
            "warrantyKind" to ca.schippers.hfm.books.WarrantyKind.entries,
            "readingSource" to ca.schippers.hfm.books.ReadingSource.entries,
            "taskState" to ca.schippers.hfm.books.TaskState.entries,
            "maintenance" to ca.schippers.hfm.books.TaskState.entries,
            "task" to ca.schippers.hfm.books.VehicleService.TEMPLATE_KEYS,
            "vehicles.tab" to listOf("OVERVIEW", "MAINTENANCE", "SERVICE", "FUEL", "WARRANTIES", "COSTS"),
            // Every section a calendar date can open (BooksModel.renewalSection, maintenance).
            "calendar.open" to listOf("PETS", "VEHICLES", "LOANS", "ACCOUNTS", "MEDICAL", "ASSETS"),
            "assetTemplate" to ca.schippers.hfm.books.AssetMaintenanceService.TEMPLATE_KEYS,
            "meterUnit" to ca.schippers.hfm.books.MeterUnit.entries,
            "assets.tab" to listOf("ASSETS", "UPKEEP", "COVERED", "INSURANCE"),
            "upkeep.perUnit" to ca.schippers.hfm.books.MeterUnit.entries,
            "upkeep.every" to ca.schippers.hfm.books.MeterUnit.entries,
            "upkeep.lastAt" to ca.schippers.hfm.books.MeterUnit.entries,
            "upkeep.remind" to ca.schippers.hfm.books.MeterUnit.entries,
            "goalEntry" to ca.schippers.hfm.books.GoalEntryKind.entries,
            "goals.amount" to listOf("SET_ASIDE", "SPEND", "RELEASE"),
            "goals.amount" to listOf("SET_ASIDE.explain", "SPEND.explain", "RELEASE.explain", "SPEND.why", "RELEASE.why"),
            "secretStore" to ca.schippers.hfm.ai.SecretStoreException.Reason.entries,
            "ai.failure" to ca.schippers.hfm.ai.AiFailure.Reason.entries,
            "aiProblem" to listOf(
                "references", "notClosed", "noType", "noForm", "mustBe", "notOneOf", "expected", "missing", "notAllowed", "fewerFields", "fewerItems",
                "moreItems", "notDate", "shorter", "longer", "noMatch", "below", "above", "notJson", "fileName", "fileSize", "fileNotJson",
            ) + listOf("linesSubtotal", "subtotalTotal", "linesTotal", "amountDue", "newBalance", "closingBalance", "netPay", "grossPay", "totalPaid", "totalValue", "openingCash", "tradeGross", "tradeNetSell", "tradeNetBuy").map { "sum.$it" },
            "network" to listOf("http", "tooLarge", "timeout", "noConnection", "secure", "other"),
            "repeat" to listOf("ONCE", "WEEKLY", "BI_WEEKLY", "SEMI_MONTHLY", "MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "EVERY_N_DAYS", "EVERY_N_WEEKS", "EVERY_N_MONTHS"),
        ).flatMap { (prefix, values) -> values.map { "$prefix.$it" } }

        val chosenInCode = listOf(
            "bills.overdue", "bills.dueToday", "bills.upcoming", "bills.markPaid", "bills.markReceived", "bills.edit",
            "bills.payingAccount", "bills.depositAccount", "backup.now", "backup.running", "welcome.restoring", "repeat.EVERY_N_DAYS.n", "repeat.EVERY_N_WEEKS.n", "repeat.EVERY_N_MONTHS.n",
        )
        for (language in Language.entries) {
            val missing = (keys + enumKeys + chosenInCode).filterNot { it in Messages.keys(language) }
            assertEquals(emptyList(), missing, "missing in $language")
        }
    }
}
