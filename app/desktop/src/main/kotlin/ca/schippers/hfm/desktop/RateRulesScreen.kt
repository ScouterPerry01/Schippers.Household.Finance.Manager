package ca.schippers.hfm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.rules.Rule
import ca.schippers.hfm.calc.rules.RuleType
import ca.schippers.hfm.calc.rules.RuleValue
import ca.schippers.hfm.calc.rules.Rules
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Rates and rules (the owner's decision 2026-10-05): every rate, limit and threshold the app
 * applies, by area, with its value in effect today, its history by date and province, and the
 * household's own values, which an administrator adds and deletes. Built from [Rules.catalogue],
 * so a new rule shows here as soon as it is defined.
 */
@Composable
fun RateRulesScreen(model: BooksModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val catalogue = model.books.rateRules.catalogue
    val shown = catalogue.filter { RateRuleInput.matches(model.t("rateRule.${it.key}"), it.key, query) }
    val selected = catalogue.firstOrNull { it.key == selectedKey }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(340.dp).fillMaxHeight().padding(12.dp)) {
            Text(model.t("nav.rate_rules"), style = MaterialTheme.typography.titleLarge)
            TextInput(model.t("rateRules.filter"), query, Modifier.fillMaxWidth().padding(vertical = 8.dp)) { query = it }
            LazyColumn(Modifier.weight(1f)) {
                if (shown.isEmpty()) item { Text(model.t("rateRules.noMatch"), Modifier.padding(8.dp)) }
                for ((area, rules) in shown.groupBy { it.area }) {
                    item(key = "area:$area") {
                        Text(model.t("rateArea.$area"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    }
                    items(rules, key = { it.key }) { rule ->
                        val isSelected = rule.key == selectedKey
                        Column(
                            Modifier.fillMaxWidth()
                                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable { selectedKey = rule.key }.padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(model.t("rateRule.${rule.key}"), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Text(rule.key, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        VerticalDivider()
        Box(Modifier.fillMaxSize()) {
            if (selected == null) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(model.t("rateRules.explain"))
                    Text(model.t("rateRules.pick"), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                RuleDetail(model, selected)
            }
        }
    }
}

@Composable
private fun RuleDetail(model: BooksModel, rule: Rule) {
    val books = model.books
    val today = today()
    val values = remember(model.revision, rule.key) { books.rateRules.values(rule.key) }
    val origins = remember(model.revision) { books.rateRules.origins() }
    val users = remember(model.revision) { runCatching { books.users.list() }.getOrDefault(emptyList()).associate { it.id to it.displayName } }
    var deleting by remember(rule.key) { mutableStateOf<RuleValue?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(model.t("rateRule.${rule.key}"), style = MaterialTheme.typography.titleLarge)
        Text(model.t("rateRules.key", rule.key), style = MaterialTheme.typography.bodySmall)
        Text(model.t("rateRule.${rule.key}.hint"))
        Text(
            model.t("rateRules.type.${rule.type}") + " · " + model.t(if (rule.perProvince) "rateRules.perProvince" else "rateRules.sameEverywhere"),
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text(model.t("rateRules.today"), style = MaterialTheme.typography.titleMedium)
        if (rule.perProvince) {
            val home = books.province
            val provinces = listOf(home) + Province.entries.filter { it != home }.sortedBy { model.t("province.$it") }
            InEffectLine(model, rule, model.t("rateRules.everywhere"), books.rateRules.valueOn(rule.key, today, null), own = true)
            for (p in provinces) {
                val v = books.rateRules.valueOn(rule.key, today, p)
                val name = if (p == home) model.t("rateRules.yourProvince", model.t("province.$p")) else model.t("province.$p")
                InEffectLine(model, rule, name, v, own = v?.province == p)
            }
        } else {
            InEffectLine(model, rule, null, books.rateRules.valueOn(rule.key, today, null), own = true)
        }

        HorizontalDivider()
        Text(model.t("rateRules.history"), style = MaterialTheme.typography.titleMedium)
        if (values.isEmpty()) Text(model.t("rateRules.none"))
        for (v in values.reversed()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(model.date(v.from), Modifier.width(110.dp))
                if (rule.perProvince) Text(v.province?.let { model.t("province.$it") } ?: model.t("rateRules.everywhere"), Modifier.width(200.dp))
                Column(Modifier.weight(1f)) {
                    Text(model.ruleValueText(rule.type, v.value))
                    val origin = if (v.builtIn) {
                        v.source?.let { model.t("rateRules.builtIn", it) } ?: model.t("rateRules.builtInNoSource")
                    } else {
                        val o = v.id?.let { origins[it] }
                        model.t("rateRules.household", o?.createdBy?.let { users[it] } ?: "?", o?.let { dateTime(model, it.createdAt) } ?: "?")
                    }
                    Text(origin, style = MaterialTheme.typography.bodySmall)
                    if (!v.builtIn) v.source?.let { Text(model.t("rateRules.note", it), style = MaterialTheme.typography.bodySmall) }
                }
                if (!v.builtIn && books.users.isAdministrator) {
                    TextButton(onClick = { deleting = v }) { Text(model.t("common.delete"), color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        HorizontalDivider()
        Text(model.t("rateRules.add"), style = MaterialTheme.typography.titleMedium)
        Text(model.t("rateRules.addExplain"), style = MaterialTheme.typography.bodySmall)
        if (books.users.isAdministrator) AddValueForm(model, rule) else Text(model.t("rateRules.adminOnly"))
    }

    deleting?.let { v ->
        val where = if (rule.perProvince) " · " + (v.province?.let { model.t("province.$it") } ?: model.t("rateRules.everywhere")) else ""
        AskBeforeDeleting(model, model.t("rateRules.deleteAsk", model.ruleValueText(rule.type, v.value), model.date(v.from) + where), onDismiss = { deleting = null }) {
            v.id != null && model.act { books.rateRules.delete(v.id!!) } != null
        }
    }
}

/** One line of "In effect today": where ([label], null for a rule the same everywhere), the value and since when. */
@Composable
private fun InEffectLine(model: BooksModel, rule: Rule, label: String?, v: RuleValue?, own: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        if (label != null) Text(label, Modifier.width(260.dp))
        Text(v?.let { model.ruleValueText(rule.type, it.value) } ?: model.t("rateRules.none"), Modifier.weight(1f))
        Text(
            v?.let { model.t("rateRules.since", model.date(it.from)) + if (!own) " · " + model.t("rateRules.fromEverywhere") else "" }.orEmpty(),
            Modifier.width(300.dp), style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "Add a value", for an administrator: the date, the province, a value editor that fits the rule's type, a note. */
@Composable
private fun AddValueForm(model: BooksModel, rule: Rule) {
    val books = model.books
    val locale = model.language.locale
    val today = today()
    val current = remember(rule.key, model.revision) { books.rateRules.valueOn(rule.key, today, if (rule.perProvince) books.province else null) }
    var from by remember(rule.key) { mutableStateOf(today.toString()) }
    var province by remember(rule.key) { mutableStateOf<Province?>(null) }
    var text by remember(rule.key, model.revision) { mutableStateOf(current?.let { RateRuleInput.typed(rule.type, it.value, locale) }.orEmpty()) }
    val rows = remember(rule.key, model.revision) {
        mutableStateListOf<Pair<String, String>>().apply {
            if (rule.type == RuleType.BRACKETS) addAll(current?.let { RateRuleInput.typedBrackets(it.value, locale) }.orEmpty().ifEmpty { listOf("0" to "") })
        }
    }
    var note by remember(rule.key, model.revision) { mutableStateOf("") }
    var error by remember(rule.key) { mutableStateOf<String?>(null) }
    var done by remember(rule.key) { mutableStateOf<String?>(null) }

    val date = runCatching { LocalDate.parse(from.trim()) }.getOrNull()
    val stored = if (rule.type == RuleType.BRACKETS) RateRuleInput.storedBrackets(rows, locale) else RateRuleInput.stored(rule.type, text, locale)
    val fits = stored != null && runCatching { Rules.check(rule, stored) }.isSuccess

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DateInput(model.t("rateRules.from"), from, Modifier.width(200.dp)) { from = it; done = null }
        if (rule.perProvince) {
            Picker(
                model.t("rateRules.province"), listOf<Province?>(null) + Province.entries.sortedBy { model.t("province.$it") }, province,
                { p -> p?.let { model.t("province.$it") } ?: model.t("rateRules.allProvinces") }, Modifier.width(320.dp),
            ) { province = it; done = null }
        }
    }
    ValueEditor(model, rule.type, text, rows, invalid = !fits && (text.isNotBlank() || rule.type == RuleType.BRACKETS)) { text = it; done = null }
    TextInput(model.t("rateRules.noteField"), note, Modifier.width(520.dp)) { note = it }

    // What changes: the value before the date, and from it on.
    if (date != null) {
        val before = books.rateRules.valueOn(rule.key, date.minus(DatePeriod(days = 1)), province)
        val beforeText = before?.let { model.ruleValueText(rule.type, it.value) } ?: model.t("rateRules.none")
        val afterText = if (fits) model.ruleValueText(rule.type, stored) else "?"
        Text(model.t("rateRules.change", model.date(date), beforeText, afterText), style = MaterialTheme.typography.bodySmall)
        val next = books.rateRules.values(rule.key).filter { it.province == province && it.from > date }.minByOrNull { it.from }
        next?.let { Text(model.t("rateRules.changeUntil", model.date(it.from)), style = MaterialTheme.typography.bodySmall) }
        if (rule.perProvince && province == null) {
            val own = books.rateRules.values(rule.key).mapNotNull { it.province }.distinct().sortedBy { model.t("province.$it") }
            if (own.isNotEmpty()) Text(model.t("rateRules.allButOwn", own.joinToString(", ") { model.t("province.$it") }), style = MaterialTheme.typography.bodySmall)
        }
    }

    ErrorText(error)
    done?.let { Text(it) }
    Button(enabled = date != null, onClick = {
        error = null
        try {
            // A value that does not read is sent as typed, so the error is the one the rules give.
            books.rateRules.add(rule.key, province, date!!, stored ?: text, note)
            done = model.t("rateRules.added")
            model.changed()
        } catch (e: Exception) {
            error = model.describe(e)
        }
    }) { Text(model.t("rateRules.add")) }
}

/** The value editor that fits the rule's type; brackets are a small table of threshold and rate rows. */
@Composable
private fun ValueEditor(model: BooksModel, type: RuleType, text: String, rows: MutableList<Pair<String, String>>, invalid: Boolean, onChange: (String) -> Unit) {
    val error = if (invalid) model.t("error.ruleValue") else null
    when (type) {
        RuleType.YES_NO -> Picker(
            model.t("rateRules.valueYesNo"), listOf("true", "false"), text.takeIf { it == "true" || it == "false" },
            { model.t(if (it == "true") "common.yes" else "common.no") }, Modifier.width(200.dp),
        ) { onChange(it) }
        RuleType.BRACKETS -> {
            for ((i, row) in rows.withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextInput(model.t("rateRules.threshold"), row.first, Modifier.width(200.dp)) { rows[i] = it to row.second; onChange(text) }
                    TextInput(model.t("rateRules.rate"), row.second, Modifier.width(140.dp)) { rows[i] = row.first to it; onChange(text) }
                    TextButton(enabled = rows.size > 1, onClick = { rows.removeAt(i); onChange(text) }) { Text(model.t("rateRules.removeBracket")) }
                }
            }
            TextButton(onClick = { rows.add("" to ""); onChange(text) }) { Text(model.t("rateRules.addBracket")) }
            ErrorText(error)
        }
        else -> {
            val label = when (type) {
                RuleType.RATE -> "rateRules.valuePercent"
                RuleType.AMOUNT -> "rateRules.valueAmount"
                RuleType.DAYS -> "rateRules.valueDays"
                RuleType.MONTH_DAY -> "rateRules.valueMonthDay"
                RuleType.LIST -> "rateRules.valueList"
                else -> "rateRules.valueNumber"
            }
            TextInput(model.t(label), text, Modifier.width(if (type == RuleType.LIST) 520.dp else 240.dp), error = error) { onChange(it) }
        }
    }
}

/** A rule's stored value as the user reads it: percentages, dollars, days, dates in their language. */
fun BooksModel.ruleValueText(type: RuleType, value: String): String {
    val locale = language.locale
    return runCatching {
        when (type) {
            RuleType.RATE -> percent(BigDecimal(value))
            RuleType.AMOUNT -> dollars(BigDecimal(value))
            RuleType.NUMBER -> NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 6 }.format(BigDecimal(value))
            RuleType.DAYS -> t("rateRules.days", value.toInt())
            RuleType.YES_NO -> t(if (value == "true") "common.yes" else "common.no")
            RuleType.MONTH_DAY -> value.split('-').let { (m, d) -> MonthDay.of(m.toInt(), d.toInt()) }
                .format(DateTimeFormatter.ofPattern(if (locale.language == "fr") "d MMMM" else "MMMM d", locale))
            RuleType.LIST -> value.split(';').joinToString(" · ") { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 6 }.format(BigDecimal(it.trim())) }
            RuleType.BRACKETS -> value.split(';').joinToString(" · ") { b ->
                b.trim().split(':').let { (from, rate) -> t("rateRules.bracket", dollars(BigDecimal(from.trim())), percent(BigDecimal(rate.trim()))) }
            }
        }
    }.getOrDefault(value)
}

private fun BooksModel.percent(v: BigDecimal): String =
    NumberFormat.getPercentInstance(language.locale).apply { maximumFractionDigits = 4 }.format(v)

private fun BooksModel.dollars(v: BigDecimal): String =
    NumberFormat.getCurrencyInstance(language.locale).apply {
        currency = java.util.Currency.getInstance("CAD")
        val cents = v.stripTrailingZeros().scale() > 0
        minimumFractionDigits = if (cents) 2 else 0
        maximumFractionDigits = 2
    }.format(v)

private fun dateTime(model: BooksModel, millis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(model.language.locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
