package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.Beneficiary
import ca.schippers.hfm.books.BeneficiaryKind
import ca.schippers.hfm.books.GrantKind
import ca.schippers.hfm.books.Member
import ca.schippers.hfm.books.Pension
import ca.schippers.hfm.books.PensionKind
import ca.schippers.hfm.books.PensionStatement
import ca.schippers.hfm.books.PlanDetails
import ca.schippers.hfm.books.RoomAdjustment
import ca.schippers.hfm.books.RoomEntry
import ca.schippers.hfm.books.RoomPlan
import ca.schippers.hfm.books.RoomSource
import ca.schippers.hfm.books.RoomStatus
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.AccountType
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.LocalDate

private sealed interface PlanAction {
    data class Room(val plan: RoomPlan, val memberId: String?, val year: Int) : PlanAction
    data class Adjust(val plan: RoomPlan, val memberId: String?) : PlanAction
    data class Details(val account: Account, val year: Int) : PlanAction
    data class Grant(val account: Account?) : PlanAction
    data class EditPension(val pension: Pension?) : PlanAction
    data class Statements(val pension: Pension) : PlanAction
    data class EditBeneficiary(val account: Account, val beneficiary: Beneficiary?) : PlanAction
}

/** INV-09 to INV-11 and pensions: room, withdrawals, RESP grants, pensions and beneficiaries. */
@Composable
fun PlansScreen(model: BooksModel) {
    var tab by remember { mutableStateOf(0) }
    var year by remember { mutableStateOf(today().year) }
    var action by remember { mutableStateOf<PlanAction?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.t("nav.plans"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Picker(model.t("loans.year"), (today().year + 1 downTo today().year - 6).toList(), year, { it.toString() }, Modifier.width(140.dp)) { year = it }
        }
        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("plans.room", "plans.withdrawals", "plans.resp", "plans.pensions", "plans.beneficiaries").forEachIndexed { i, key ->
                Tab(tab == i, { tab = i }, text = { Text(model.t(key)) })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> RoomTab(model, year) { action = it }
                1 -> WithdrawalsTab(model, year) { action = it }
                2 -> RespTab(model, year) { action = it }
                3 -> PensionsTab(model, year) { action = it }
                else -> BeneficiariesTab(model) { action = it }
            }
        }
    }
    when (val a = action) {
        is PlanAction.Room -> RoomDialog(model, a) { action = null }
        is PlanAction.Adjust -> AdjustmentDialog(model, a) { action = null }
        is PlanAction.Details -> DetailsDialog(model, a.account, a.year) { action = null }
        is PlanAction.Grant -> GrantDialog(model, a.account) { action = null }
        is PlanAction.EditPension -> PensionDialog(model, a.pension) { action = null }
        is PlanAction.Statements -> StatementsDialog(model, a.pension) { action = null }
        is PlanAction.EditBeneficiary -> BeneficiaryDialog(model, a.account, a.beneficiary) { action = null }
        null -> Unit
    }
}

// --- Contribution room (INV-09) ------------------------------------------------------------------------

@Composable
private fun RoomTab(model: BooksModel, year: Int, onAction: (PlanAction) -> Unit) {
    val books = model.books
    val rows = remember(model.revision, year) {
        RoomPlan.entries.flatMap { plan -> books.plans.peopleWith(plan).map { m -> runCatching { books.plans.room(m.id, plan, year, today()) }.getOrNull() } }.filterNotNull()
    }
    Text(model.t("plans.roomHint"), style = MaterialTheme.typography.bodySmall)
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onAction(PlanAction.Room(RoomPlan.RRSP, null, year)) }) { Text(model.t("plans.enterRoom")) }
        OutlinedButton(onClick = { onAction(PlanAction.Adjust(RoomPlan.RRSP, null)) }) { Text(model.t("plans.addOutside")) }
    }
    if (rows.isEmpty()) Text(model.t("plans.noRoom"), Modifier.padding(8.dp))
    for (r in rows) RoomCard(model, r, onAction)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomCard(model: BooksModel, r: RoomStatus, onAction: (PlanAction) -> Unit) {
    var open by remember(r.member.id, r.plan, r.year) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.t("roomPlan.${r.plan}") + " · " + r.member.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(model.t("roomSource.${r.source}.${r.plan}", r.year.toString()), style = MaterialTheme.typography.bodySmall,
                        color = if (r.source == RoomSource.UNKNOWN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                }
                TextButton(onClick = { onAction(PlanAction.Room(r.plan, r.member.id, r.year)) }) { Text(model.t("plans.enterRoom")) }
            }
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Stat(model.t("plans.available"), r.available?.let(model::money) ?: "–")
                Stat(model.t("plans.contributed"), model.money(r.contributions))
                if (r.plan == RoomPlan.TFSA) Stat(model.t("plans.withdrawn"), model.money(r.withdrawals))
                Stat(model.t(if (r.over) "plans.overBy" else "plans.left"), r.left?.let { model.money(it.abs()) } ?: "–")
                r.lifetimeLeft?.let { Stat(model.t("plans.lifetimeLeft"), model.money(it)) }
            }
            if (r.over) {
                Text(
                    model.t("plans.overWarning.${r.plan}", r.penaltyPerMonth?.let(model::money) ?: model.money(Money.zero(r.contributions.currency))),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (r.plan == RoomPlan.TFSA && r.withdrawals.isPositive) Text(model.t("plans.tfsaWithdrawals"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open = !open }) { Text(model.t(if (open) "plans.hideLines" else "plans.showLines", r.flows.size)) }
            if (open) {
                for (f in r.flows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(model.date(f.date), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                        Text(if (f.adjustment) model.t("plans.outsideLine", f.accountName) else f.accountName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        MoneyText(model, f.amount, modifier = Modifier.width(130.dp), textAlign = TextAlign.End)
                    }
                }
                TextButton(onClick = { onAction(PlanAction.Adjust(r.plan, r.member.id)) }) { Text(model.t("plans.addOutside")) }
            }
        }
    }
}

// --- RRIF and LIF (INV-10) ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WithdrawalsTab(model: BooksModel, year: Int, onAction: (PlanAction) -> Unit) {
    val rows = remember(model.revision, year) { model.books.plans.withdrawals(year) }
    Text(model.t("plans.withdrawalsHint"), style = MaterialTheme.typography.bodySmall)
    if (rows.isEmpty()) Text(model.t("plans.noRrif"), Modifier.padding(8.dp))
    for (w in rows) {
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.account.name + " · " + model.t("accountType.${w.account.type}"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAction(PlanAction.Details(w.account, year)) }) { Text(model.t("plans.details")) }
                }
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Stat(model.t(if (w.valueEntered) "plans.valueStatement" else "plans.valueBooks", year.toString()), w.valueJanuary1?.let(model::money) ?: "–")
                    Stat(model.t("plans.age"), w.age?.toString() ?: "–")
                    Stat(model.t("plans.minimum"), w.minimum?.let(model::money) ?: "–")
                    w.maximum?.let { Stat(model.t("plans.maximum"), model.money(it)) }
                    Stat(model.t("plans.withdrawnYear"), model.money(w.withdrawn))
                    Stat(model.t("plans.stillToWithdraw"), w.leftToWithdraw?.let(model::money) ?: "–")
                }
                when {
                    w.firstYear -> Text(model.t("plans.firstYear"), style = MaterialTheme.typography.bodySmall)
                    w.age == null -> Text(model.t("plans.needBirthDate"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    w.overMaximum -> Text(model.t("plans.overMaximum"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// --- RESP -------------------------------------------------------------------------------------------------

@Composable
private fun RespTab(model: BooksModel, year: Int, onAction: (PlanAction) -> Unit) {
    val rows = remember(model.revision, year) { model.books.plans.respBeneficiaries(year) }
    Text(model.t("plans.respHint"), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { onAction(PlanAction.Grant(null)) }, modifier = Modifier.padding(vertical = 8.dp)) { Text(model.t("plans.recordGrant")) }
    if (rows.isEmpty()) Text(model.t("plans.noResp"), Modifier.padding(8.dp))
    TableView(
        model,
        ReportTable(
            model.t("plans.resp"), year.toString(),
            listOf(
                model.t("plans.beneficiary"), model.t("plans.contributedYear", year.toString()), model.t("plans.contributedTotal"), model.t("plans.cesgExpected"),
                model.t("plans.cesgReceived"), model.t("plans.qesiExpected"), model.t("plans.qesiReceived"), model.t("plans.respLifetimeLeft"),
            ),
            rows.map { r -> listOf(r.member.displayName, r.contributionsThisYear, r.contributionsTotal, r.cesgExpected, r.cesgReceived, r.qesiExpected, r.qesiReceived, r.lifetimeLeft) },
        ),
        startOpen = true,
    )
    for (r in rows) {
        val pending = listOfNotNull(
            (r.cesgExpected - r.cesgReceived).takeIf { it.isPositive }?.let { model.t("plans.cesgShort") + " " + model.money(it) },
            (r.qesiExpected - r.qesiReceived).takeIf { it.isPositive }?.let { model.t("plans.qesiShort") + " " + model.money(it) },
        )
        if (pending.isNotEmpty()) Text(model.t("plans.grantsPending", r.member.displayName, pending.joinToString(", ")), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        if (r.member.birthDate == null) Text(model.t("plans.respBirthDate", r.member.displayName), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

// --- Pensions --------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PensionsTab(model: BooksModel, year: Int, onAction: (PlanAction) -> Unit) {
    val rows = remember(model.revision, year) { model.books.plans.pensionSummaries(year) }
    val members = remember { model.books.members.list(includeArchived = true).associate { it.id to it.displayName } }
    Text(model.t("plans.pensionsHint"), style = MaterialTheme.typography.bodySmall)
    Button(onClick = { onAction(PlanAction.EditPension(null)) }, modifier = Modifier.padding(vertical = 8.dp)) { Text(model.t("plans.addPension")) }
    if (rows.isEmpty()) Text(model.t("plans.noPensions"), Modifier.padding(8.dp))
    for (s in rows) {
        val p = s.pension
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name + " · " + members[p.memberId].orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(model.t("pensionKind.${p.kind}"), p.administrator, p.normalRetirementAge?.let { model.t("plans.normalAge", it) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onAction(PlanAction.Statements(p)) }) { Text(model.t("plans.statements")) }
                    TextButton(onClick = { onAction(PlanAction.EditPension(p)) }) { Text(model.t("common.edit")) }
                }
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Stat(model.t("plans.paymentsYear", year.toString()), model.money(s.paymentsThisYear))
                    s.latest?.let { st ->
                        st.projectedAnnual?.let { Stat(model.t("plans.projected", st.year.toString()), model.money(it)) }
                        st.accruedAnnual?.let { Stat(model.t("plans.accrued"), model.money(it)) }
                        st.pensionAdjustment?.let { Stat(model.t("plans.pensionAdjustment", st.year.toString()), model.money(it)) }
                        st.value?.let { Stat(model.t("plans.pensionValue"), model.money(it)) }
                    }
                }
            }
        }
    }
}

// --- Beneficiaries (INV-11) --------------------------------------------------------------------------

@Composable
private fun BeneficiariesTab(model: BooksModel, onAction: (PlanAction) -> Unit) {
    val accounts = remember(model.revision) { model.books.plans.registeredAccounts() }
    Text(model.t("plans.beneficiariesHint"), style = MaterialTheme.typography.bodySmall)
    if (accounts.isEmpty()) Text(model.t("plans.noPlans"), Modifier.padding(8.dp))
    for (a in accounts) {
        val list = remember(model.revision, a.id) { model.books.plans.beneficiaries(a.id) }
        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.name + " · " + model.t("accountType.${a.type}"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (a.type in WITH_DETAILS) TextButton(onClick = { onAction(PlanAction.Details(a, today().year)) }) { Text(model.t("plans.details")) }
                    TextButton(onClick = { onAction(PlanAction.EditBeneficiary(a, null)) }) { Text(model.t("plans.addBeneficiary")) }
                }
                if (list.isEmpty()) Text(model.t("plans.noBeneficiary"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                for (b in list) {
                    Row(Modifier.fillMaxWidth().clickable { onAction(PlanAction.EditBeneficiary(a, b)) }.padding(vertical = 4.dp)) {
                        Text(model.t("beneficiaryKind.${b.kind}"), Modifier.width(200.dp), style = MaterialTheme.typography.bodySmall)
                        Text(b.name + (b.relationship?.let { " ($it)" } ?: ""), Modifier.weight(1f))
                        Text(b.sharePercent?.let { "${it.stripTrailingZeros().toPlainString()} %" }.orEmpty(), Modifier.width(80.dp), textAlign = TextAlign.End)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// --- Dialogs -------------------------------------------------------------------------------------------------

/** Plans with details to set: a contributor, the age for the minimum, the January 1 value, the LIF rate. */
private val WITH_DETAILS = setOf(AccountType.SPOUSAL_RRSP, AccountType.SPOUSAL_RRIF, AccountType.RESP, AccountType.RRIF, AccountType.LIF)

private fun parseDate(text: String): LocalDate = runCatching { LocalDate.parse(text.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }

@Composable
private fun GroupChoice(model: BooksModel, groupId: String?, onPick: (String) -> Unit) {
    val groups = remember { model.editableGroups() }
    if (groups.size > 1) Picker(model.t("calendar.storeIn"), groups, groups.firstOrNull { it.id == groupId }, { it.name }) { onPick(it.id) }
}

/** INV-09: the CRA's figure for a person, plan and year. */
@Composable
private fun RoomDialog(model: BooksModel, start: PlanAction.Room, onClose: () -> Unit) {
    val locale = model.language.locale
    val c = model.books.rates.baseCurrency
    val members = remember { model.books.members.list().filter { it.kind != ca.schippers.hfm.domain.MemberKind.CHILD } }
    var plan by remember { mutableStateOf(start.plan) }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == start.memberId } ?: members.firstOrNull()) }
    var year by remember { mutableStateOf(start.year.toString()) }
    val existing = remember(plan, member, year) { model.books.plans.roomEntries().firstOrNull { it.memberId == member?.id && it.plan == plan && it.year.toString() == year.trim() } }
    var limit by remember(existing) { mutableStateOf(existing?.let { MoneyFormat.formatAmount(it.limit, locale) }.orEmpty()) }
    var unused by remember(existing) { mutableStateOf(existing?.unused?.takeIf { it.isPositive }?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var groupId by remember { mutableStateOf(model.defaultGroupForPersonalRecords()?.id) }
    FormDialog(model.t("plans.enterRoom"), model.t("common.save"), model.t("common.cancel"), canSave = member != null && groupId != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            val y = year.trim().toIntOrNull() ?: throw ValidationException("error.invalidNumber")
            model.books.plans.saveRoom(
                RoomEntry("", member!!.id, plan, y, parseAmount(limit, c, locale) ?: throw ValidationException("error.amountPositive"), parseAmount(unused, c, locale) ?: Money.zero(c)),
                groupId!!,
            )
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("plans.roomEntryHint.$plan"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("plans.plan"), RoomPlan.entries, plan, { model.t("roomPlan.$it") }, Modifier.weight(1f)) { plan = it }
            Picker(model.t("plans.person"), members, member, { it.displayName }, Modifier.weight(1f)) { member = it }
            TextInput(model.t("loans.year"), year, Modifier.weight(0.6f)) { year = it }
        }
        AmountInput(model.t("plans.limitField.$plan"), limit, c, locale, Modifier.fillMaxWidth(), model::money) { limit = it }
        if (plan == RoomPlan.RRSP) AmountInput(model.t("plans.unusedField"), unused, c, locale, Modifier.fillMaxWidth(), model::money) { unused = it }
        GroupChoice(model, groupId) { groupId = it }
        if (existing != null) TextButton(onClick = { if (model.act { model.books.plans.deleteRoom(existing.id) } != null) onClose() }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
    }
}

/** A contribution made outside the books, or a negative amount to leave a line out. */
@Composable
private fun AdjustmentDialog(model: BooksModel, start: PlanAction.Adjust, onClose: () -> Unit) {
    val locale = model.language.locale
    val c = model.books.rates.baseCurrency
    val members = remember { model.books.members.list() }
    var plan by remember { mutableStateOf(start.plan) }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == start.memberId } ?: members.firstOrNull()) }
    var date by remember { mutableStateOf(today().toString()) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf(model.defaultGroupForPersonalRecords()?.id) }
    val existing = remember(model.revision, plan, member) { model.books.plans.adjustments().filter { it.plan == plan && it.memberId == member?.id } }
    FormDialog(model.t("plans.addOutside"), model.t("common.save"), model.t("common.cancel"), canSave = member != null && groupId != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.plans.addAdjustment(RoomAdjustment("", member!!.id, plan, parseDate(date), parseAmount(amount, c, locale) ?: throw ValidationException("error.invalidNumber"), notes), groupId!!)
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("plans.outsideHint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("plans.plan"), RoomPlan.entries, plan, { model.t("roomPlan.$it") }, Modifier.weight(1f)) { plan = it }
            Picker(model.t("plans.person"), members, member, { it.displayName }, Modifier.weight(1f)) { member = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            AmountInput(model.t("register.amount"), amount, c, locale, Modifier.weight(1f), model::money) { amount = it }
        }
        TextInput(model.t("register.memo"), notes) { notes = it }
        GroupChoice(model, groupId) { groupId = it }
        for (a in existing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${model.date(a.date)}  ${model.money(a.amount)}  ${a.notes.orEmpty()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { model.act { model.books.plans.deleteAdjustment(a.id) } }) { Text(model.t("common.delete")) }
            }
        }
    }
}

/** Plan details: contributor, the age used for the minimum, the LIF rate, and the January 1 value. */
@Composable
private fun DetailsDialog(model: BooksModel, account: Account, year: Int, onClose: () -> Unit) {
    val locale = model.language.locale
    val members = remember { model.books.members.list() }
    val d = remember { model.books.plans.details(account.id) }
    var contributor by remember { mutableStateOf(members.firstOrNull { it.id == d.contributorMemberId }) }
    var ageMember by remember { mutableStateOf(members.firstOrNull { it.id == d.minimumAgeMemberId }) }
    var rate by remember { mutableStateOf(d.lifReferenceRate?.movePointRight(2)?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    val status = remember { model.books.plans.withdrawalStatus(account, year) }
    var value by remember { mutableStateOf(if (status.valueEntered) status.valueJanuary1?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty() else "") }
    var earnings by remember { mutableStateOf("") }
    val withdrawals = account.type in setOf(AccountType.RRIF, AccountType.SPOUSAL_RRIF, AccountType.LIF)
    FormDialog(model.t("plans.detailsOf", account.name), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.plans.saveDetails(
                PlanDetails(account.id, contributor?.id, ageMember?.id, rate.trim().ifEmpty { null }?.let { MoneyFormat.parseDecimal(it, locale).movePointLeft(2) }, d.notes),
            )
            if (withdrawals) model.books.plans.setValueJanuary1(account.id, year, parseAmount(value, account.currency, locale), parseAmount(earnings, account.currency, locale))
        }
        if (ok != null) onClose()
    }) {
        if (account.type in setOf(AccountType.SPOUSAL_RRSP, AccountType.SPOUSAL_RRIF, AccountType.RESP)) {
            Text(model.t(if (account.type == AccountType.RESP) "plans.subscriberHint" else "plans.contributorHint"), style = MaterialTheme.typography.bodySmall)
            Picker(model.t(if (account.type == AccountType.RESP) "plans.subscriber" else "plans.contributor"), listOf<Member?>(null) + members, contributor, { it?.displayName ?: model.t("common.none") }) { contributor = it }
        }
        if (withdrawals) {
            Text(model.t("plans.valueHint", year.toString()), style = MaterialTheme.typography.bodySmall)
            AmountInput(model.t("plans.valueStatement", year.toString()), value, account.currency, locale, Modifier.fillMaxWidth(), model::money) { value = it }
            Picker(model.t("plans.ageOf"), listOf<Member?>(null) + members, ageMember, { it?.displayName ?: model.t("plans.ageOwner") }) { ageMember = it }
            if (account.type == AccountType.LIF) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextInput(model.t("plans.referenceRate"), rate, Modifier.weight(1f), supporting = model.t("plans.referenceRateHint")) { rate = it }
                    AmountInput(model.t("plans.lastYearEarnings"), earnings, account.currency, locale, Modifier.weight(1f), model::money) { earnings = it }
                }
            }
        }
    }
}

@Composable
private fun GrantDialog(model: BooksModel, start: Account?, onClose: () -> Unit) {
    val locale = model.language.locale
    val resps = remember { model.books.plans.registeredAccounts().filter { it.type == AccountType.RESP } }
    var account by remember { mutableStateOf(start ?: resps.firstOrNull()) }
    val beneficiaries = remember(account) { account?.let { a -> model.books.plans.beneficiaries(a.id).filter { it.kind == BeneficiaryKind.RESP_BENEFICIARY } }.orEmpty() }
    var who by remember(account) { mutableStateOf(beneficiaries.firstOrNull()) }
    var kind by remember { mutableStateOf(GrantKind.CESG) }
    var date by remember { mutableStateOf(today().toString()) }
    var amount by remember { mutableStateOf("") }
    FormDialog(model.t("plans.recordGrant"), model.t("common.save"), model.t("common.cancel"), canSave = account != null && who != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            val a = account!!
            model.books.plans.recordGrant(a.id, who!!.memberId!!, parseDate(date), kind, parseAmount(amount, a.currency, locale) ?: throw ValidationException("error.amountPositive"))
        }
        if (ok != null) onClose()
    }) {
        if (resps.isEmpty()) Text(model.t("plans.noResp"))
        Picker(model.t("plans.respAccount"), resps, account, { it.name }) { account = it }
        if (account != null && beneficiaries.isEmpty()) Text(model.t("plans.respNeedsBeneficiary"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker(model.t("plans.beneficiary"), beneficiaries, who, { it.name }, Modifier.weight(1f)) { who = it }
            Picker(model.t("plans.grantKind"), GrantKind.entries, kind, { model.t("grantKind.$it") }, Modifier.weight(1f)) { kind = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateInput(model.t("report.date"), date, Modifier.weight(1f)) { date = it }
            account?.let { AmountInput(model.t("register.amount"), amount, it.currency, locale, Modifier.weight(1f), model::money) { v -> amount = v } }
        }
    }
}

@Composable
private fun PensionDialog(model: BooksModel, existing: Pension?, onClose: () -> Unit) {
    val members = remember { model.books.members.list().filter { it.kind == ca.schippers.hfm.domain.MemberKind.ADULT } }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == existing?.memberId } ?: members.firstOrNull()) }
    var kind by remember { mutableStateOf(existing?.kind ?: PensionKind.DEFINED_BENEFIT) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var administrator by remember { mutableStateOf(existing?.administrator.orEmpty()) }
    var number by remember { mutableStateOf(existing?.memberNumber.orEmpty()) }
    var age by remember { mutableStateOf(existing?.normalRetirementAge?.toString() ?: "65") }
    var indexed by remember { mutableStateOf(existing?.indexed ?: false) }
    var survivor by remember { mutableStateOf(existing?.survivorPercent?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var payer by remember { mutableStateOf(existing?.payer.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var groupId by remember { mutableStateOf(existing?.groupId ?: model.defaultGroupForPersonalRecords()?.id) }
    FormDialog(model.t(if (existing == null) "plans.addPension" else "plans.editPension"), model.t("common.save"), model.t("common.cancel"), canSave = member != null && name.isNotBlank() && groupId != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.plans.savePension(
                Pension(
                    existing?.id.orEmpty(), member!!.id, kind, name, administrator, number, existing?.startDate, age.trim().ifEmpty { null }?.toIntOrNull(), indexed,
                    survivor.trim().ifEmpty { null }?.let { MoneyFormat.parseDecimal(it, model.language.locale) }, existing?.accountId, payer, notes, existing?.groupId,
                ),
                groupId!!,
            )
        }
        if (ok != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Picker(model.t("plans.person"), members, member, { it.displayName }, Modifier.weight(1f)) { member = it }
                Picker(model.t("plans.pensionKind"), PensionKind.entries, kind, { model.t("pensionKind.$it") }, Modifier.weight(1f)) { k ->
                    kind = k
                    if (name.isBlank()) name = model.t("pensionKind.$k")
                    if (payer.isBlank()) payer = when (k) { PensionKind.QPP -> "Retraite Québec"; PensionKind.CPP, PensionKind.OAS -> "Service Canada"; else -> "" }
                }
            }
            TextInput(model.t("investments.name"), name) { name = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("plans.administrator"), administrator, Modifier.weight(1f)) { administrator = it }
                TextInput(model.t("plans.memberNumber"), number, Modifier.weight(1f)) { number = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("plans.normalAgeField"), age, Modifier.weight(1f)) { age = it }
                TextInput(model.t("plans.survivor"), survivor, Modifier.weight(1f)) { survivor = it }
            }
            LabeledCheckbox(model.t("plans.indexed"), indexed) { indexed = it }
            TextInput(model.t("plans.payer"), payer, supporting = model.t("plans.payerHint")) { payer = it }
            TextInput(model.t("account.notes"), notes, singleLine = false) { notes = it }
            GroupChoice(model, groupId) { groupId = it }
            if (existing != null) TextButton(onClick = { if (model.act { model.books.plans.deletePension(existing) } != null) onClose() }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Yearly pension statements: pension adjustment, accrued and projected pension, value, contributions. */
@Composable
private fun StatementsDialog(model: BooksModel, pension: Pension, onClose: () -> Unit) {
    val locale = model.language.locale
    val c = model.books.rates.baseCurrency
    val statements = remember(model.revision) { model.books.plans.statements(pension) }
    var year by remember { mutableStateOf((today().year - 1).toString()) }
    var pa by remember { mutableStateOf("") }
    var accrued by remember { mutableStateOf("") }
    var projected by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var contributions by remember { mutableStateOf("") }
    WideDialog(model.t("plans.statementsOf", pension.name), model.t("common.close"), onClose) {
        Text(model.t("plans.statementHint"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextInput(model.t("loans.year"), year, Modifier.width(90.dp)) { year = it }
            AmountInput(model.t("plans.paShort"), pa, c, locale, Modifier.weight(1f), model::money) { pa = it }
            AmountInput(model.t("plans.accrued"), accrued, c, locale, Modifier.weight(1f), model::money) { accrued = it }
            AmountInput(model.t("plans.projectedShort"), projected, c, locale, Modifier.weight(1f), model::money) { projected = it }
            AmountInput(model.t("plans.pensionValue"), value, c, locale, Modifier.weight(1f), model::money) { value = it }
            AmountInput(model.t("plans.yourContributions"), contributions, c, locale, Modifier.weight(1f), model::money) { contributions = it }
            Button(onClick = {
                model.act {
                    model.books.plans.saveStatement(
                        pension,
                        PensionStatement(
                            "", pension.id, year.trim().toIntOrNull() ?: throw ValidationException("error.invalidNumber"), parseAmount(pa, c, locale), parseAmount(accrued, c, locale),
                            parseAmount(projected, c, locale), parseAmount(value, c, locale), parseAmount(contributions, c, locale),
                        ),
                    )
                }
            }) { Text(model.t("common.add")) }
        }
        TableView(
            model,
            ReportTable(
                model.t("plans.statementsOf", pension.name), "",
                listOf(model.t("loans.year"), model.t("plans.paShort"), model.t("plans.accrued"), model.t("plans.projectedShort"), model.t("plans.pensionValue"), model.t("plans.yourContributions")),
                statements.map { listOf(it.year.toString(), it.pensionAdjustment, it.accruedAnnual, it.projectedAnnual, it.value, it.contributions) },
            ),
            startOpen = true,
        )
        for (s in statements) {
            TextButton(onClick = { model.act { model.books.plans.deleteStatement(pension, s.id) } }) { Text(model.t("plans.deleteYear", s.year.toString())) }
        }
    }
}

@Composable
private fun BeneficiaryDialog(model: BooksModel, account: Account, existing: Beneficiary?, onClose: () -> Unit) {
    val members = remember { model.books.members.list() }
    val kinds = if (account.type == AccountType.RESP) listOf(BeneficiaryKind.RESP_BENEFICIARY) else listOf(BeneficiaryKind.BENEFICIARY, BeneficiaryKind.SUCCESSOR_HOLDER)
    var kind by remember { mutableStateOf(existing?.kind ?: kinds.first()) }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == existing?.memberId }) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var relationship by remember { mutableStateOf(existing?.relationship.orEmpty()) }
    var share by remember { mutableStateOf(existing?.sharePercent?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    FormDialog(model.t("plans.beneficiaryOf", account.name), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank() || member != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.plans.saveBeneficiary(
                Beneficiary(existing?.id.orEmpty(), account.id, kind, name.ifBlank { member?.displayName.orEmpty() }, member?.id, relationship,
                    share.trim().ifEmpty { null }?.let { MoneyFormat.parseDecimal(it, model.language.locale) }),
            )
        }
        if (ok != null) onClose()
    }) {
        Text(model.t("beneficiaryKind.$kind.hint"), style = MaterialTheme.typography.bodySmall)
        if (kinds.size > 1) Picker(model.t("plans.role"), kinds, kind, { model.t("beneficiaryKind.$it") }) { kind = it }
        Picker(model.t("plans.householdMember"), listOf<Member?>(null) + members, member, { it?.displayName ?: model.t("plans.someoneElse") }) {
            member = it
            if (it != null) name = it.displayName
        }
        TextInput(model.t("investments.name"), name) { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextInput(model.t("plans.relationship"), relationship, Modifier.weight(1f)) { relationship = it }
            if (kind == BeneficiaryKind.BENEFICIARY) TextInput(model.t("plans.share"), share, Modifier.weight(1f)) { share = it }
        }
        if (existing != null) TextButton(onClick = { if (model.act { model.books.plans.deleteBeneficiary(account.id, existing.id) } != null) onClose() }) {
            Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error)
        }
    }
}
