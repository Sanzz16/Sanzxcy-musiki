package com.metrolist.music.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun PollMessageCard(
    poll: ChatPoll,
    username: String,
    canClose: Boolean,
    onVote: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val total = poll.votes.size
    val myVote = poll.votes.firstOrNull { it.username == username }?.option
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("POLL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(poll.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (poll.imageUrl.startsWith("https://", ignoreCase = true)) {
                AsyncImage(model = poll.imageUrl, contentDescription = "Gambar poll", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(14.dp)))
            }
            poll.options.forEachIndexed { index, option ->
                val number = index + 1
                val count = poll.votes.count { it.option == number }
                val fraction = if (total == 0) 0f else count.toFloat() / total
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(enabled = !poll.closed, onClick = { onVote(number) }),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).heightIn(min = 44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.17f)))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (myVote == number) "✓ " else "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(option, modifier = Modifier.weight(1f), maxLines = 2)
                        if (total > 0) Text("${(fraction * 100).toInt()}% · $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$total suara${if (poll.closed) " · Ditutup" else if (myVote != null) " · Suaramu tersimpan" else " · Pilih jawaban"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (canClose && !poll.closed) TextButton(onClick = onClose) { Text("Tutup poll") }
            }
        }
    }
}
