package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.Account
import ca.schippers.hfm.books.AlertSettings
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.AccountKind
import ca.schippers.hfm.money.MoneyFormat

/** ACC-06: an account's alerts, each off while left empty. Kept with the account in its group. */
@Composable
internal fun AccountAlertsDialog(model: BooksModel, account: Account, onClose: () -> Unit) {
    val books = model.books
    val locale = model.language.locale
    val existing = remember(account.id) { books.accountAlerts.settings(account.id) }
    val card = account.type.kind == AccountKind.CREDIT
    val hasLimit = remember(account.id) { card && books.creditCards.terms(account.id)?.creditLimit?.isPositive == true }
    var low by remember { mutableStateOf(existing.lowBalance?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    var near by remember { mutableStateOf(existing.nearLimitPercent?.toString().orEmpty()) }
    var over by remember { mutableStateOf(existing.overLimit) }
    var multiple by remember { mutableStateOf(existing.largeMultiple?.stripTrailingZeros()?.toPlainString().orEmpty()) }
    var newPayee by remember { mutableStateOf(existing.newPayeeAbove?.let { MoneyFormat.formatAmount(it, locale) }.orEmpty()) }
    FormDialog(
        model.t("alert.title", account.name), model.t("common.save"), model.t("common.cancel"), onDismiss = onClose,
        onSave = {
            val saved = model.act {
                books.accountAlerts.save(
                    AlertSettings(
                        account.id,
                        lowBalance = if (card) null else parseAmount(low, account.currency, locale),
                        nearLimitPercent = if (card) near.trim().ifEmpty { null }?.let { it.toIntOrNull() ?: throw ValidationException("error.invalidNumber") } else null,
                        overLimit = card && over,
                        largeMultiple = multiple.trim().replace(',', '.').ifEmpty { null }?.let { it.toBigDecimalOrNull() ?: throw ValidationException("error.invalidNumber") },
                        newPayeeAbove = parseAmount(newPayee, account.currency, locale),
                    ),
                )
            }
            if (saved != null) onClose()
        },
    ) {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.t("alert.explain"), style = MaterialTheme.typography.bodySmall)
            if (!card) {
                AmountInput(model.t("alert.lowBalance"), low, account.currency, locale, Modifier.width(260.dp), model::money) { low = it }
            } else {
                if (!hasLimit) Text(model.t("alert.noLimit"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                LabeledCheckbox(model.t("alert.overLimit"), over) { over = it }
                TextInput(model.t("alert.nearLimit"), near, Modifier.width(260.dp), supporting = model.t("alert.nearLimitHint")) { near = it }
            }
            Text(model.t("alert.unusual"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(model.t("alert.largeMultiple"), multiple, Modifier.weight(1f), supporting = model.t("alert.largeMultipleHint")) { multiple = it }
                AmountInput(model.t("alert.newPayee"), newPayee, account.currency, locale, Modifier.weight(1f), model::money) { newPayee = it }
            }
            Text(model.t("alert.unusualHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
