/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: changelog diambil dari GitHub milik developer
 * (https://raw.githubusercontent.com/Sanzz16/Sanzxcy/main/changelog.md), dengan cadangan dari server.
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.metrolist.music.community.CommunityApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onDismiss: () -> Unit) {
    var md by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val fromGithub = CommunityApi.githubChangelog()
        val text = fromGithub ?: CommunityApi.appStatus()?.changelogMd?.takeIf { it.isNotBlank() }
        if (text != null) md = text else failed = true
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Changelog", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Tutup") }
            }
            when {
                md != null -> Column(Modifier.fillMaxWidth().height(560.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { SimpleMarkdown(md!!) }
                failed -> Text("Changelog belum bisa dimuat. Periksa koneksi internet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 32.dp))
                else -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

private fun inline(text: String): AnnotatedString =
    buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { i, p -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p) }
    }

/** Markdown sederhana: judul (#, ##, ###), daftar (- / *), tebal (**), dan paragraf. */
@Composable
private fun SimpleMarkdown(md: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Box(Modifier.height(4.dp))
                line.startsWith("### ") -> Text(inline(line.drop(4)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                line.startsWith("## ") -> Text(inline(line.drop(3)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                line.startsWith("# ") -> Text(inline(line.drop(2)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") ->
                    Row { Text("•  ", color = MaterialTheme.colorScheme.primary); Text(inline(line.trimStart().drop(2)), style = MaterialTheme.typography.bodyMedium) }
                else -> Text(inline(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
