package com.example.encryptedvault.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Small grey caps heading that groups the rows of an overflow menu. */
@Composable
fun OverflowMenuHeading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * One row of an overflow menu. The icon is tinted when the row is [selected], which also shows a check mark at the end.
 * [trailing] shows a short current value (such as the active time range) for rows that open a sub-menu.
 */
@Composable
fun OverflowMenuItem(
    label: String,
    icon: ImageVector?,
    selected: Boolean = false,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) },
        leadingIcon = if (icon != null) ({ Icon(icon, null, tint = tint) }) else null,
        trailingIcon =
            when {
                selected -> ({ Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) })
                trailing != null -> ({ Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                else -> null
            },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp),
    )
}
