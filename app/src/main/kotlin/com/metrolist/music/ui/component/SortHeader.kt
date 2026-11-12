/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanzxcy.music.com.R
import com.metrolist.music.constants.PlaylistSongSortType

@Composable
inline fun <reified T : Enum<T>> SortHeader(
    sortType: T,
    sortDescending: Boolean,
    crossinline onSortTypeChange: (T) -> Unit,
    crossinline onSortDescendingChange: (Boolean) -> Unit,
    crossinline sortTypeText: (T) -> Int,
    modifier: Modifier = Modifier,
    showDescending: Boolean? = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (sortType == PlaylistSongSortType.CUSTOM) {
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    ) { menuExpanded = !menuExpanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            } else {
                Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false),
                    ) { menuExpanded = !menuExpanded }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            },
        ) {
            if (sortType == PlaylistSongSortType.CUSTOM) {
                Icon(
                    painter = painterResource(R.drawable.custom_order),
                    contentDescription = "Urutan kustom. Seret lagu untuk mengatur urutan",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp).padding(end = 4.dp),
                )
            }
            Text(
                text = stringResource(sortTypeText(sortType)),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            if (sortType == PlaylistSongSortType.CUSTOM) {
                Text(
                    text = "Geser untuk susun",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = 172.dp),
        ) {
            enumValues<T>().forEach { type ->
                DropdownMenuItem(
                    leadingIcon = if (type == PlaylistSongSortType.CUSTOM) {
                        { Icon(painterResource(R.drawable.custom_order), contentDescription = "Urutan kustom", modifier = Modifier.size(22.dp)) }
                    } else {
                        null
                    },
                    text = {
                        Text(
                            text = stringResource(sortTypeText(type)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            painter =
                            painterResource(
                                if (sortType ==
                                    type
                                ) {
                                    R.drawable.radio_button_checked
                                } else {
                                    R.drawable.radio_button_unchecked
                                },
                            ),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onSortTypeChange(type)
                        menuExpanded = false
                    },
                )
            }
        }

        if (sortType != PlaylistSongSortType.CUSTOM && showDescending == true) {
            ResizableIconButton(
                icon = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                Modifier
                    .size(32.dp)
                    .padding(8.dp),
                onClick = { onSortDescendingChange(!sortDescending) },
            )
        }
    }
}
