package ca.schippers.hfm.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ca.schippers.hfm.books.ValidationException
import ca.schippers.hfm.domain.PermissionLevel
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.text.NumberFormat

// Shared by the Utilities, Volunteer hours, hours worked (Side income) and chores (Family money) screens.

internal fun trackerDate(text: String): LocalDate = runCatching { LocalDate.parse(text.trim()) }.getOrElse { throw ValidationException("error.invalidDate") }

/** A number typed with a comma or a point; null when empty. */
internal fun trackerNumber(text: String, error: String = "error.invalidNumber"): BigDecimal? =
    text.filterNot { it == ' ' || it == ' ' || it == ' ' }.replace(',', '.').ifEmpty { null }?.let { it.toBigDecimalOrNull() ?: throw ValidationException(error) }

/** A quantity as the user's language writes it, with up to [decimals] decimals: 1,234.5 or 1 234,5. */
internal fun BooksModel.quantity(v: BigDecimal, decimals: Int = 1): String =
    NumberFormat.getNumberInstance(language.locale).apply { maximumFractionDigits = decimals }.format(v)

/** Minutes as hours and minutes: "3 h 05". */
internal fun BooksModel.duration(minutes: Int): String = t("tracker.duration", minutes / 60, "%02d".format(minutes % 60))

/** Minutes typed as "1:30" or decimal hours ("1.5", "1,5"). */
internal fun parseDuration(text: String): Int =
    ca.schippers.hfm.sync.HoursTimer.parse(text) ?: throw ValidationException("error.workMinutes")

/** CAL-06 and HH-11: where a new record is kept, when the user may edit more than one account group. */
@Composable
internal fun StoreInPicker(model: BooksModel, groupId: String, onPick: (String) -> Unit) {
    val groups = remember { model.editableGroups() }
    if (groups.size > 1) Picker(model.t("calendar.storeIn"), groups, groups.firstOrNull { it.id == groupId }, { it.name }) { onPick(it.id) }
}

/** The group new tracker records go in by default: the first shared group the user may edit. */
internal fun BooksModel.trackerGroup(): String = defaultGroupForContacts()?.id ?: defaultDocumentGroup() ?: books.groups().first().id

/**
 * What the signed-in user may do in each account group, so a screen greys out or hides the buttons
 * the books would refuse (a viewer, or a member with view-only access to a group): [canCreate] new
 * records (an editor somewhere), [mayEdit] a record kept in a group, [mayAdd] to one (a tick, a
 * reading, hours: capture is enough).
 */
internal class GroupAccess(private val levels: Map<String, PermissionLevel>) {
    val canCreate: Boolean get() = levels.values.any { it == PermissionLevel.EDIT }
    val canAdd: Boolean get() = levels.values.any { it.allows(PermissionLevel.CAPTURE_ONLY) }
    fun mayEdit(groupId: String): Boolean = levels[groupId] == PermissionLevel.EDIT
    fun mayAdd(groupId: String): Boolean = levels[groupId]?.allows(PermissionLevel.CAPTURE_ONLY) == true
}

/** [GroupAccess] read once per change of the books. */
@Composable
internal fun rememberAccess(model: BooksModel): GroupAccess =
    remember(model.revision) { GroupAccess(model.books.groups().associate { it.id to it.level }) }
