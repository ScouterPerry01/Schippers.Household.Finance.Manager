package ca.schippers.hfm.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Chore
import ca.schippers.hfm.books.ChoreTick
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.MemberKind
import ca.schippers.hfm.money.MoneyFormat
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

/** "1,00 $ · 2 points": what a chore is worth. */
private fun BooksModel.worth(amount: ca.schippers.hfm.money.Money?, points: Int?): String =
    listOfNotNull(amount?.takeIf { it.isPositive }?.let(::money), points?.takeIf { it > 0 }?.let { t("chores.points", it) }).joinToString(" · ").ifEmpty { "—" }

/**
 * CHO-01: each child's chores, ticked here or on the phone; the money earned is paid with the
 * allowance (or marked paid by hand for a child without one), and points add up.
 */
@Composable
internal fun ChoresTab(model: BooksModel) {
    val books = model.books
    val chores = remember(model.revision) { books.chores.list() }
    val members = remember(model.revision) { books.members.list() }
    val allowances = remember(model.revision) { books.allowances.list() }
    var editing by remember { mutableStateOf<Chore?>(null) }
    var history by remember { mutableStateOf<Chore?>(null) }
    val child = members.firstOrNull { it.kind == MemberKind.CHILD } ?: members.firstOrNull()
    val access = rememberAccess(model)
    if (child != null && access.canCreate) {
        Button(onClick = { editing = Chore("", model.trackerGroup(), child.id, "", books.reports.base) }) { Text(model.t("chores.add")) }
    }
    Text(model.t("chores.hint"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    if (chores.isEmpty()) Text(model.t("chores.none"))
    val week = today().minus(DatePeriod(days = 6))
    Column(Modifier.verticalScroll(rememberScrollState())) {
        for ((memberId, mine) in chores.groupBy { it.memberId }) {
            val name = members.firstOrNull { it.id == memberId }?.displayName.orEmpty()
            val currency = mine.first().currency
            val e = books.chores.earnings(memberId, currency, today())
            val allowance = allowances.firstOrNull { it.memberId == memberId && it.amount.currency == currency }
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(
                                    model.t("chores.unpaid", model.money(e.unpaid), e.unpaidTicks),
                                    model.t("chores.pointsTotal", e.points, e.pointsThisMonth).takeIf { e.points > 0 },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (e.unpaid.isPositive) {
                            // Paying marks every chore of the child paid: it needs the right to change each one's group.
                            val mayPay = mine.all { access.mayEdit(it.groupId) }
                            if (allowance != null) {
                                OutlinedButton(onClick = { model.act { books.chores.pay(allowance, today()) } }, enabled = mayPay) { Text(model.t("chores.payWithAllowance")) }
                            } else {
                                OutlinedButton(onClick = { model.act { books.chores.markPaid(memberId, currency, today()) } }, enabled = mayPay) { Text(model.t("chores.markPaid")) }
                            }
                        }
                    }
                    for (c in mine) {
                        val recent = c.ticks.count { it.date >= week && it.date <= today() }
                        val doneToday = c.ticks.any { it.date == today() }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name, Modifier.weight(1f).clickable(enabled = access.mayEdit(c.groupId)) { editing = c })
                            Text(model.worth(c.amount, c.points), Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall)
                            Text(model.t("chores.thisWeek", recent), Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { history = c }) { Text(model.t("chores.history")) }
                            // Once a day unless the chore may be done several times a day: a second tick would earn twice.
                            OutlinedButton(onClick = { model.act { books.chores.tick(c.id, today()) } }, enabled = access.mayAdd(c.groupId) && (!doneToday || c.severalADay)) {
                                Text(
                                    when {
                                        doneToday && c.severalADay -> model.t("chores.doneAgain")
                                        doneToday -> model.t("chores.doneTodayAlready")
                                        else -> model.t("chores.doneToday")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { c -> ChoreDialog(model, c) { editing = null } }
    history?.let { c -> ChoreHistoryDialog(model, books.chores.list(true).firstOrNull { it.id == c.id } ?: c, access) { history = null } }
}

@Composable
private fun ChoreDialog(model: BooksModel, c: Chore, onClose: () -> Unit) {
    val locale = model.language.locale
    val members = remember { model.books.members.list().sortedBy { it.kind != MemberKind.CHILD } }
    var member by remember { mutableStateOf(members.firstOrNull { it.id == c.memberId }) }
    var name by remember { mutableStateOf(c.name) }
    var amount by remember { mutableStateOf(c.amount?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var points by remember { mutableStateOf(c.points?.toString().orEmpty()) }
    var archived by remember { mutableStateOf(c.archived) }
    var several by remember { mutableStateOf(c.severalADay) }
    var groupId by remember { mutableStateOf(c.groupId) }
    var asking by remember { mutableStateOf(false) }
    FormDialog(model.t(if (c.id.isBlank()) "chores.add" else "chores.edit"), model.t("common.save"), model.t("common.cancel"), canSave = name.isNotBlank() && member != null, onDismiss = onClose, onSave = {
        val ok = model.act {
            model.books.chores.save(
                c.copy(
                    groupId = groupId, memberId = member!!.id, name = name, amount = parseAmount(amount, c.currency, locale)?.abs(),
                    points = points.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.chorePoints") }, archived = archived,
                    severalADay = several,
                ),
            )
        }
        if (ok != null) onClose()
    }) {
        Picker(model.t("chores.child"), members, member, { it.displayName }) { member = it }
        TextInput(model.t("chores.name"), name) { name = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountInput(model.t("chores.amount"), amount, c.currency, locale, Modifier.weight(1f), model::money) { amount = it }
            TextInput(model.t("chores.pointsLabel"), points, Modifier.weight(1f)) { points = it }
        }
        Text(model.t("chores.worthHint"), style = MaterialTheme.typography.bodySmall)
        LabeledCheckbox(model.t("chores.severalADay"), several) { several = it }
        Text(model.t("chores.severalADayHint"), style = MaterialTheme.typography.bodySmall)
        if (c.id.isBlank()) StoreInPicker(model, groupId) { groupId = it }
        if (c.id.isNotBlank()) {
            LabeledCheckbox(model.t("utilities.archived"), archived) { archived = it }
            TextButton(onClick = { asking = true }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (asking) {
        AskBeforeDeleting(model, model.t("chores.delete.body", c.name), onDismiss = { asking = false }) {
            (model.act { model.books.chores.delete(c) } != null).also { if (it) onClose() }
        }
    }
}

/** A chore's ticks, newest first: when, what it earned, paid or not; an unpaid one can be removed. */
@Composable
private fun ChoreHistoryDialog(model: BooksModel, c: Chore, access: GroupAccess, onClose: () -> Unit) {
    var day by remember { mutableStateOf(today().toString()) }
    var deleting by remember { mutableStateOf<ChoreTick?>(null) }
    FormDialog(c.name, model.t("chores.tickOn"), model.t("common.close"), canSave = access.mayAdd(c.groupId), onDismiss = onClose, onSave = {
        if (model.act { model.books.chores.tick(c.id, trackerDate(day)) } != null) onClose()
    }) {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            if (c.ticks.isEmpty()) Text(model.t("chores.noTicks"), style = MaterialTheme.typography.bodySmall)
            for (t in c.ticks.reversed().take(TICKS_SHOWN)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.date(t.date), Modifier.width(100.dp))
                    Text(
                        listOfNotNull(
                            model.worth(t.amount, t.points), t.paidDate?.let { model.t("chores.paidOn", model.date(it)) }, model.t("tracker.fromPhone").takeIf { t.fromPhone },
                        ).joinToString(" · "),
                        Modifier.weight(1f),
                    )
                    RemoveButton(model.t("common.delete"), enabled = !t.paid && access.mayEdit(c.groupId)) { deleting = t }
                }
            }
        }
        DateInput(model.t("report.date"), day) { day = it }
    }
    deleting?.let { t ->
        AskBeforeDeleting(model, model.t("chores.untick.body", c.name, model.date(t.date)), onDismiss = { deleting = null }) {
            (model.act { model.books.chores.untick(c, t.id) } != null).also { if (it) onClose() }
        }
    }
}

private const val TICKS_SHOWN = 60
