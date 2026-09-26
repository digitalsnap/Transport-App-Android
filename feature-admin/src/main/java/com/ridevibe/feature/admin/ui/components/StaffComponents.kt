package com.ridevibe.feature.admin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridevibe.core.domain.format.PhTime
import com.ridevibe.feature.admin.ui.formatIsoDayLong

/*
 * Shared building blocks for the staff console. Only MaterialTheme.colorScheme
 * roles are used — the app's brand constants are not visible from a feature module.
 * Charcoal chrome = inverseSurface, teal accent = primary, gold = tertiary.
 */

/** Width from which the console swaps the bottom bar for a navigation rail and widens grids. */
val StaffWideLayoutMinWidth = 600.dp

/** Charcoal top bar chrome matching the passenger app, through theme roles only. */
@Composable
fun staffTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.inverseSurface,
    titleContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
    actionIconContentColor = MaterialTheme.colorScheme.inverseOnSurface,
)

/** The `pagehead` block of every dashboard view: H1 plus a one-line subtitle. */
@Composable
fun PageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Uppercase micro-label used above field values and as card titles. */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = modifier,
    )
}

/** White rounded card with an uppercase title, the dashboards' `.card > h2`. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                MicroLabel(title)
                Spacer(modifier = Modifier.height(10.dp))
            }
            content()
        }
    }
}

/**
 * A stat tile: micro-label + big number. [accent] = gold left rule ("premium"),
 * [alert] = error tint. A value that would not fit (a peso total) steps down
 * a size and ellipsises; long-pressing the tile shows the whole value in a
 * tooltip. [onClick] turns the tile into a shortcut (Overview → Support).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    alert: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val valueColor = when {
        alert -> MaterialTheme.colorScheme.error
        accent -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val valueStyle = if (value.length > 8) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text("$label: $value") } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (accent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { contentDescription = "$label: $value" },
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                MicroLabel(label)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    value,
                    style = valueStyle,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Two tiles per row on a phone, four on a tablet — the responsive `.tiles` grid. */
@Composable
fun StatTileGrid(tiles: List<StatTileSpec>, modifier: Modifier = Modifier, columns: Int = 2) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowTiles.forEach { tile ->
                    StatTile(
                        label = tile.label,
                        value = tile.value,
                        accent = tile.accent,
                        alert = tile.alert,
                        onClick = tile.onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

data class StatTileSpec(
    val label: String,
    val value: String,
    val accent: Boolean = false,
    val alert: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/** Tinted status pill tones, mapped from the dashboards' `.pill.*` classes. */
enum class PillTone { PLAIN, OK, WARN, BAD, MINT, GOLD }

@Composable
fun Pill(text: String, tone: PillTone = PillTone.PLAIN, modifier: Modifier = Modifier) {
    val (container, content) = when (tone) {
        PillTone.PLAIN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        PillTone.OK -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        PillTone.MINT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        PillTone.WARN, PillTone.GOLD -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PillTone.BAD -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Monospace seat label chip; a row of them wraps like the dashboards' `.seatchip`. */
@Composable
fun SeatChips(labels: List<String>, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) {
        Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        labels.forEach { label ->
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Monospace id / QR payload text on a dashed-looking chip. */
@Composable
fun CodeText(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/** `.detail .f` — micro-label above a value. */
@Composable
fun LabeledValue(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier = modifier.padding(bottom = 10.dp)) {
        MicroLabel(label)
        Spacer(modifier = Modifier.height(2.dp))
        value()
    }
}

@Composable
fun LabeledText(label: String, value: String, modifier: Modifier = Modifier) {
    LabeledValue(label, modifier) { Text(value, style = MaterialTheme.typography.bodyMedium) }
}

/** Key on the left, value right-aligned — the Overview "Network data" table. */
@Composable
fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp),
    )
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Inline error line under a form or list — failures carry a human-readable message. */
@Composable
fun InlineError(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

/** Error line plus a retry button — for a list that failed to load at all. */
@Composable
fun ErrorWithRetry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        InlineError(message)
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Small spinner that replaces a button label while a mutation runs. */
@Composable
fun ButtonSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onPrimary,
    )
}

/** Gold notecard — the dashboards' `.notecard` / `.robanner`. */
@Composable
fun NoteCard(text: String, modifier: Modifier = Modifier, bold: String? = null) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp).padding(top = 1.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                if (bold != null) {
                    Text(
                        bold,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

/**
 * `‹ 2026-09-03 ›` day stepper. Tapping the date opens a calendar and the
 * "Today" link jumps back; PH calendar arithmetic stays in the view model,
 * which receives the picked ISO day through [onPick].
 */
@Composable
fun DateSelector(
    dateIso: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: (isoDay: String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Date (PH)",
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val isToday = dateIso == PhTime.todayIso()
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MicroLabel(label)
            if (!isToday) {
                TextButton(onClick = { onPick(PhTime.todayIso()) }) { Text("Today") }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = { showPicker = true })
                    .semantics { contentDescription = "Date ${formatIsoDayLong(dateIso)}, tap to pick another" }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateIso, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    if (isToday) "Today · ${formatIsoDayLong(dateIso)}" else formatIsoDayLong(dateIso),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
        }
    }
    if (showPicker) {
        StaffDatePickerDialog(
            initialIsoDay = dateIso,
            onPick = { picked ->
                showPicker = false
                onPick(picked)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Generic yes/no dialog for destructive or irreversible staff actions. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            if (destructive) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
            } else {
                Button(onClick = onConfirm) { Text(confirmLabel, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shows a one-time secret (generated password, partner token, QR payload) with a
 * copy-to-clipboard button. The dashboards copy and toast; here the value stays
 * on screen until dismissed because it is never shown again.
 */
@Composable
fun SecretRevealDialog(
    title: String,
    message: String,
    secret: String,
    onDismiss: () -> Unit,
    secretLabel: String = "Shown once",
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                MicroLabel(secretLabel)
                Spacer(modifier = Modifier.height(4.dp))
                CodeText(secret, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(secret)) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy to clipboard")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

/** Selected/unselected toggle chip used for hours, bus class and ride kind pickers. */
@Composable
fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) container else container.copy(alpha = 0.5f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Transparent placeholder to keep a legend swatch consistent. */
@Composable
fun LegendSwatch(color: Color, bordered: Boolean = false) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .background(color, RoundedCornerShape(4.dp))
            .then(
                if (bordered) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)) else Modifier,
            ),
    )
}
