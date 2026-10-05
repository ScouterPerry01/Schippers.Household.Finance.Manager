package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.schippers.hfm.books.AssetService
import ca.schippers.hfm.calc.rules.LeadTimes
import ca.schippers.hfm.money.Money
import ca.schippers.hfm.ocr.desktop.PdfPages
import java.io.File
import javax.swing.JFileChooser

/**
 * Section 12, assets and warranties (AST-04, WAR-02, INS-02): the inventory with values, the
 * warranties ending soon, what no policy covers, and the inventory with photos as a PDF for an
 * insurer after a loss.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssetsReport(model: BooksModel) {
    val books = model.books
    val today = today()
    val assets = remember(model.revision) { books.assets.list() }
    val names = assets.associate { it.id to it.name }
    val coverage = remember(model.revision) { assets.associate { it.id to books.assets.coverage(it.id, today) } }
    val expiring = remember(model.revision) { books.assets.renewals(today, LeadTimes.assetsReport(today)) }
    val uninsured = remember(model.revision) { books.insurance.uninsured(today) }
    val base = books.rates.baseCurrency
    val zero = Money.zero(base)
    fun inBase(m: Money?) = m?.let { books.rates.convert(it, base, today) }
    val paid = assets.mapNotNull { inBase(it.purchasePrice) }.fold(zero, Money::plus)
    val worth = assets.mapNotNull { inBase(it.valueOn(today)) }.fold(zero, Money::plus)

    Text(model.t("report.ASSETS"), style = MaterialTheme.typography.titleLarge)
    Text(model.t("assetsReport.subtitle", model.date(today)), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        Stat(model.t("assetsReport.items"), assets.size.toString())
        Stat(model.t("assetsReport.paid"), model.money(paid))
        Stat(model.t("assetsReport.worth"), model.money(worth))
    }
    OutlinedButton(onClick = {
        val chooser = JFileChooser().apply { dialogTitle = model.t("assetsReport.inventoryPdf"); selectedFile = File(model.t("assetsReport.inventoryFile", today.toString()) + ".pdf") }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return@OutlinedButton
        model.act {
            val items = assets.sortedWith(compareBy({ it.location.orEmpty() }, { it.name.lowercase() })).map { a ->
                val photos = books.documents.documentsFor(AssetService.ENTITY, a.id).filter { it.mimeType.startsWith("image/") }.map { books.documents.content(it.id) }
                listOf(
                    a.name,
                    listOfNotNull(model.t("assetKind.${a.kind}"), a.make, a.model, a.serialNumber?.let { model.t("assetsReport.serial", it) }).joinToString(" · "),
                    listOfNotNull(a.location, a.parentId?.let(names::get)?.let { model.t("assetsReport.in", it) }).joinToString(" · "),
                    listOfNotNull(
                        a.purchaseDate?.let { model.t("assetsReport.bought", model.date(it)) },
                        a.purchasePrice?.let { model.t("assetsReport.for", model.money(it)) },
                        a.valueOn(today)?.let { model.t("assetsReport.worthNow", model.money(it)) },
                    ).joinToString(" · "),
                ).filter { it.isNotBlank() } to photos
            }
            val pdf = PdfPages.inventory(model.t("assetsReport.inventoryTitle", model.date(today)), listOf(model.t("assetsReport.inventorySummary", assets.size, model.money(paid), model.money(worth))), items)
            val file = chooser.selectedFile.let { if (it.extension.equals("pdf", true)) it else File(it.path + ".pdf") }
            file.writeBytes(pdf)
        }
    }) { Text(model.t("assetsReport.inventoryPdf")) }

    TableView(
        model,
        ReportTable(
            model.t("report.ASSETS"), model.date(today),
            listOf(model.t("assets.name"), model.t("assets.kind"), model.t("assets.partOf"), model.t("assets.location"), model.t("assets.purchaseDate"), model.t("assets.price"), model.t("assets.value"), model.t("assetsReport.coveredUntil")),
            assets.sortedBy { it.name.lowercase() }.map { a ->
                val until = coverage[a.id].orEmpty().filter { it.active }.mapNotNull { it.until }.maxOrNull()
                listOf(a.name, model.t("assetKind.${a.kind}"), a.parentId?.let(names::get).orEmpty(), a.location.orEmpty(), a.purchaseDate, a.purchasePrice, a.valueOn(today), until)
            },
        ),
        startOpen = true,
    )

    Text(model.t("assetsReport.expiring"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    if (expiring.isEmpty()) Text(model.t("assetsReport.noneExpiring"), style = MaterialTheme.typography.bodySmall)
    for (r in expiring) Text("${r.subjectName}${r.detail?.let { " · $it" }.orEmpty()} · ${model.t("assets.until", model.date(r.date))}", style = MaterialTheme.typography.bodySmall)

    if (uninsured.isNotEmpty()) {
        Text(model.t("insurance.uninsured"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        for (u in uninsured) Text(u.name + (u.value?.let { " · " + model.money(it) }.orEmpty()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
