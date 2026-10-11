package com.example.encryptedvault.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pill-shaped single-choice control (a lightweight stand-in for a segmented button). */
@Composable
fun <T> SegmentedPills(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(option) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option), style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Centered icon + title + hint, shown when a list has nothing to display. */
@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .5f))
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge)
        Text(hint, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One toolbar action: an icon with an accessibility/label text. */
class BarAction(val icon: ImageVector, val label: String, val tint: Color? = null, val enabled: Boolean = true, val onClick: () -> Unit)

/** Top of the selection mode, like iOS: "Select All" · "N Selected" · "Done". */
@Composable
fun SelectionTopBar(count: Int, noun: String, onSelectAll: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSelectAll) { Text("Select All") }
        Text(
            if (count == 0) "Select Items" else "$count $noun${if (count == 1) "" else "s"} Selected",
            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        TextButton(onClick = onDone) { Text("Done", fontWeight = FontWeight.Bold) }
    }
}

/** Bottom toolbar shown during selection: [primary] actions as icons, [overflow] in a "more" menu. */
@Composable
fun SelectionToolbar(primary: List<BarAction>, overflow: List<BarAction>, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Row(Modifier.navigationBarsPadding().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            primary.forEach { action ->
                IconButton(onClick = action.onClick, enabled = action.enabled) {
                    Icon(action.icon, action.label, tint = (action.tint ?: MaterialTheme.colorScheme.primary).copy(alpha = if (action.enabled) 1f else .35f))
                }
            }
            if (overflow.isNotEmpty()) Box {
                IconButton(onClick = { menuOpen = true }, enabled = overflow.any { it.enabled }) { Icon(Icons.Filled.MoreVert, "More", tint = MaterialTheme.colorScheme.primary) }
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    overflow.forEach { action ->
                        DropdownMenuItem(text = { Text(action.label) }, leadingIcon = { Icon(action.icon, null) }, enabled = action.enabled, onClick = { menuOpen = false; action.onClick() })
                    }
                }
            }
        }
    }
}
