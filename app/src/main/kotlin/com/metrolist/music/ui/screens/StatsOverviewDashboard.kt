package com.metrolist.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.constants.StatPeriod
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.DailyListeningStats
import com.metrolist.music.db.entities.SongWithStats
import java.util.Locale

internal object StatsScreenColors {
    val background = Color(0xFF5E4A57)
    val card = Color(0xFF130D14)
    val cardSoft = Color(0xFF1A111B)
    val primaryText = Color(0xFFFFF5FD)
    val secondaryText = Color(0xFFD3C4D1)
    val selected = Color(0xFFF0D6ED)
    val selectedText = Color(0xFF241B24)
}

private val statsPalette = listOf(
    Color(0xFF8B2BE2),
    Color(0xFF00A5E6),
    Color(0xFF35C94A),
    Color(0xFFFF8A00),
    Color(0xFFFF1C19),
)

@Composable
internal fun StatsPeriodSelector(
    selectedOption: OptionStats,
    selectedIndex: Int,
    onSelect: (OptionStats, Int) -> Unit,
) {
    val periods = listOf(
        "Continuous" to StatPeriod.ALL.ordinal,
        "1 week" to StatPeriod.WEEK_1.ordinal,
        "1 month" to StatPeriod.MONTH_1.ordinal,
        "3 months" to StatPeriod.MONTH_3.ordinal,
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        periods.forEach { (label, periodIndex) ->
            val isSelected = selectedOption == OptionStats.CONTINUOUS && selectedIndex == periodIndex
            Surface(
                color = if (isSelected) StatsScreenColors.selected else StatsScreenColors.card,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).clickable {
                    onSelect(OptionStats.CONTINUOUS, periodIndex)
                },
            ) {
                Text(
                    text = if (label == "Continuous") "Continuous  ⌄" else label,
                    color = if (isSelected) StatsScreenColors.selectedText else StatsScreenColors.primaryText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
                )
            }
        }
    }
}

@Composable
internal fun StatsOverviewDashboard(
    totalListeningMs: Long,
    totalPlays: Int,
    uniqueSongs: Int,
    uniqueArtists: Int,
    topArtist: Artist?,
    topSong: SongWithStats?,
    artists: List<Artist>,
    topSongs: List<SongWithStats>,
    dailyStats: List<DailyListeningStats>,
    onShuffle: () -> Unit,
    onSongClick: (SongWithStats) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = StatsScreenColors.card, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                Text("Total Time Listened", style = MaterialTheme.typography.titleSmall, color = StatsScreenColors.primaryText, fontWeight = FontWeight.SemiBold)
                Text(
                    formatStatsTime(totalListeningMs),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = StatsScreenColors.primaryText,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatsMetricCard("$totalPlays", "Total Plays", Modifier.weight(1f))
            StatsMetricCard("$uniqueSongs", "Unique Songs", Modifier.weight(1f))
            StatsMetricCard("$uniqueArtists", "Unique Artists", Modifier.weight(1f))
        }

        FeaturedStatsCard(
            label = "Your Favourite Artist",
            title = topArtist?.artist?.name ?: "Belum ada data artis",
            subtitle = topArtist?.let { "${it.songCount} songs • ${formatStatsTime(it.timeListened?.toLong() ?: 0L)}" } ?: "Putar musik untuk membuat statistik",
            imageUrl = topArtist?.artist?.thumbnailUrl,
        )
        FeaturedStatsCard(
            label = "Your Favourite Song",
            title = topSong?.title ?: "Belum ada data lagu",
            subtitle = topSong?.let { "${it.songCountListened} time • ${formatStatsTime(it.timeListened ?: 0L)}" } ?: "Lagu yang sering diputar tampil di sini",
            imageUrl = topSong?.thumbnailUrl,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        ) {
            ArtistDonutChart(artists, Modifier.size(150.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                if (artists.isEmpty()) {
                    Text("Artis akan muncul setelah ada riwayat dengar.", style = MaterialTheme.typography.bodySmall, color = StatsScreenColors.primaryText)
                } else {
                    val total = artists.sumOf { (it.timeListened ?: 0).toLong().coerceAtLeast(0L) }.coerceAtLeast(1L)
                    artists.take(5).forEachIndexed { index, artist ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(statsPalette[index % statsPalette.size]))
                            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                                Text(artist.artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = StatsScreenColors.primaryText)
                                val percent = ((artist.timeListened ?: 0).toLong().coerceAtLeast(0L) * 100L / total).toInt()
                                Text("$percent%", style = MaterialTheme.typography.labelSmall, color = StatsScreenColors.secondaryText)
                            }
                        }
                    }
                }
                Text(formatStatsTime(totalListeningMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = StatsScreenColors.primaryText)
                Text("Total Time Listened", style = MaterialTheme.typography.bodySmall, color = StatsScreenColors.secondaryText)
            }
        }

        Text(
            "Have LiveStats",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = StatsScreenColors.primaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Listening by Day", style = MaterialTheme.typography.titleMedium, color = StatsScreenColors.primaryText, fontWeight = FontWeight.SemiBold)
                Text("😍  😍", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = onShuffle,
                enabled = totalPlays > 0,
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StatsScreenColors.selected, contentColor = StatsScreenColors.selectedText),
            ) {
                Text("⤨   Shuffle", color = StatsScreenColors.selectedText)
            }
        }
        DailyListeningChart(dailyStats)

        if (topSongs.isEmpty()) {
            Text("Belum ada lagu untuk periode ini.", color = StatsScreenColors.primaryText, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            topSongs.take(10).forEach { song ->
                StatsSongRow(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun StatsMetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = StatsScreenColors.card, shape = RoundedCornerShape(22.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StatsScreenColors.primaryText)
            Text(label, style = MaterialTheme.typography.labelMedium, color = StatsScreenColors.secondaryText, maxLines = 2)
        }
    }
}

@Composable
private fun FeaturedStatsCard(label: String, title: String, subtitle: String, imageUrl: String?) {
    Surface(color = StatsScreenColors.card, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            Box(Modifier.size(86.dp).clip(RoundedCornerShape(20.dp)).background(StatsScreenColors.cardSoft)) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(model = imageUrl, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = StatsScreenColors.primaryText, fontWeight = FontWeight.SemiBold)
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StatsScreenColors.primaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = StatsScreenColors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ArtistDonutChart(artists: List<Artist>, modifier: Modifier = Modifier) {
    val values = artists.take(5).map { (it.timeListened ?: 0).coerceAtLeast(0).toFloat() }
    val total = values.sum()
    Canvas(modifier) {
        var startAngle = -90f
        val stroke = size.minDimension * 0.22f
        if (total <= 0f) {
            drawArc(
                color = StatsScreenColors.card,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
        } else {
            values.forEachIndexed { index, value ->
                val sweep = 360f * value / total
                if (sweep > 0f) {
                    drawArc(
                        color = statsPalette[index % statsPalette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
                startAngle += sweep
            }
        }
    }
}

@Composable
private fun DailyListeningChart(stats: List<DailyListeningStats>) {
    if (stats.isEmpty()) {
        Text("Tidak ada data untuk hari-hari ini.", color = StatsScreenColors.secondaryText, style = MaterialTheme.typography.bodySmall)
        return
    }
    val visible = stats.takeLast(14)
    val maxTime = visible.maxOfOrNull { it.playTime }?.coerceAtLeast(1L) ?: 1L
    val barColor = StatsScreenColors.selected
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(92.dp)) {
            val gap = 6.dp.toPx()
            val barWidth = ((size.width - gap * (visible.size - 1)) / visible.size).coerceAtLeast(2.dp.toPx())
            visible.forEachIndexed { index, day ->
                val fraction = (day.playTime.toFloat() / maxTime).coerceIn(0f, 1f)
                val barHeight = (size.height * fraction).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = barColor.copy(alpha = 0.48f + 0.52f * fraction),
                    topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            visible.forEach { day ->
                Text(
                    text = day.day.takeLast(2).trimStart('0').ifBlank { "0" },
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = StatsScreenColors.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun StatsSongRow(song: SongWithStats, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(StatsScreenColors.cardSoft)) {
            if (!song.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(model = song.thumbnailUrl, contentDescription = song.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title, color = StatsScreenColors.primaryText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${song.artistName.orEmpty()} · ${song.songCountListened} plays",
                color = StatsScreenColors.secondaryText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatStatsTime(song.timeListened ?: 0L), color = StatsScreenColors.secondaryText, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatStatsTime(milliseconds: Long): String {
    val safeMs = milliseconds.coerceAtLeast(0L)
    val hours = safeMs / 3_600_000L
    val minutes = (safeMs / 60_000L) % 60L
    val seconds = (safeMs / 1_000L) % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d", hours, minutes)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", safeMs / 60_000L, seconds)
    }
}
