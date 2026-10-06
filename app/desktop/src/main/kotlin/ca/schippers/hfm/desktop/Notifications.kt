package ca.schippers.hfm.desktop

/**
 * BILL-04: the text of a computer notification for [lines]. With [details], up to four reminders
 * as the banner shows them ("Hydro due in 3 days", "Ventolin refill"). Without, only how many
 * there are of each kind, by the screen they belong to ("Bills (2)", "Health (1)"), so that a
 * screen others can see names no medication, person, appointment or bill.
 */
fun notificationBody(lines: List<BooksModel.ReminderLine>, details: Boolean, sectionName: (Section) -> String): String =
    if (details) {
        lines.take(4).joinToString("\n") { it.text }
    } else {
        lines.groupBy { it.section }.entries.joinToString("\n") { (section, of) -> "${sectionName(section)} (${of.size})" }
    }
