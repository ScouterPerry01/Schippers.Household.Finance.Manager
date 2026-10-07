package ca.schippers.hfm.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The width of an Actions column that holds one text button (NAV-04). */
internal val ACTIONS_WIDTH = 130.dp

/** The width of an Actions column that holds one symbol button, such as ✕ (NAV-04). */
internal val ICON_ACTIONS_WIDTH = 72.dp

/**
 * NAV-04: the heading row of a list with columns, with a line under it. Each [ColumnHeading] takes
 * the same width or weight (and the same [spacing]) as the cells of the rows below, so the headings
 * stand over their columns.
 */
@Composable
fun HeadingRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    divider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.Bottom,
            content = content,
        )
        if (divider) HorizontalDivider()
    }
}

/**
 * The headings of the usual tracker list: a date (100 dp), what was recorded ([whatKey]) and a ✕
 * button to delete (meter and tank readings, deliveries, hours, chores).
 */
@Composable
internal fun DatedHeadings(model: BooksModel, whatKey: String) {
    HeadingRow {
        ColumnHeading(model.t("register.date"), Modifier.width(100.dp))
        ColumnHeading(model.t(whatKey), Modifier.weight(1f))
        ColumnHeading(model.t("table.actions"), Modifier.width(ICON_ACTIONS_WIDTH), TextAlign.Center)
    }
}

/**
 * One column's heading: announced as a heading by screen readers. [spoken] replaces a symbol
 * heading (such as ✓) with words.
 */
@Composable
fun ColumnHeading(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, spoken: String? = null) {
    Text(
        text,
        modifier = modifier.then(
            if (spoken != null) Modifier.clearAndSetSemantics { heading(); contentDescription = spoken } else Modifier.semantics { heading() },
        ),
        style = MaterialTheme.typography.labelLarge,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
