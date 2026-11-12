package com.metrolist.music.ui.screens.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.sanzxcy.music.com.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DonationSupportCard() {
    val context = LocalContext.current
    var showDonation by remember { mutableStateOf(false) }
    val placeholder by produceState<ImageBitmap?>(initialValue = null, showDonation) {
        if (showDonation) {
            value = withContext(Dispatchers.IO) {
                try {
                    context.assets.open("qris.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    Material3SettingsGroup(
        title = stringResource(R.string.donation_section_title),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.ic_heart),
                title = { Text(stringResource(R.string.donation_entry_title)) },
                description = { Text(stringResource(R.string.donation_entry_description)) },
                onClick = { showDonation = true },
            ),
        ),
    )

    if (showDonation) {
        Dialog(onDismissRequest = { showDonation = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.donation_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    if (placeholder != null) {
                        Image(
                            bitmap = placeholder!!,
                            contentDescription = stringResource(R.string.donation_placeholder_image_desc),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.donation_placeholder_image_unavailable))
                        }
                    }
                    Text(
                        text = stringResource(R.string.donation_placeholder_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { showDonation = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.donation_close))
                    }
                }
            }
        }
    }
}
