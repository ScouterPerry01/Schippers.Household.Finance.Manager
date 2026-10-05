package ca.schippers.hfm.desktop

import ca.schippers.hfm.calc.Province
import ca.schippers.hfm.calc.salestax.SalesTaxRate
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.text.NumberFormat

/** A rate as a fraction (0.09975) shown in percent for the user's language: "9.975" or "9,975". */
internal fun percentText(rate: BigDecimal, model: BooksModel): String =
    NumberFormat.getNumberInstance(model.language.locale).apply { maximumFractionDigits = 4; isGroupingUsed = false }.format(rate.movePointRight(2))

/** "In effect on <date> in <province>: GST 5 %, QST 9.975 %", for the sales taxes from Rates and rules. */
internal fun salesTaxesInEffect(model: BooksModel, on: LocalDate, province: Province, rates: List<SalesTaxRate>): String =
    model.t("salesTax.inEffect", model.date(on), model.t("province.${province.name}"), rates.joinToString(", ") { "${model.t("taxName.${it.label}")} ${percentText(it.rate, model)} %" })
