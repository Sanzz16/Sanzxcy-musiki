/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Sanzxcy Music: halaman Tentang dengan banner developer dan tombol media sosial.
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.metrolist.music.ui.component.IosAlertDialog as AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.rememberPreference
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import com.sanzxcy.music.com.BuildConfig
import com.metrolist.music.utils.Updater
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.sanzxcy.music.com.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain

/** Akun resmi developer (tetap, tidak bisa diubah dari aplikasi). */
private class SocialLink(
    val platform: String,
    val handle: String,
    val iconRes: Int,
    val url: String,
)

private val socialLinks = listOf(
    SocialLink("GitHub", "Sanzz16", R.drawable.github, "https://github.com/Sanzz16"),
    SocialLink("Instagram", "@ssannnxc", R.drawable.instagram, "https://www.instagram.com/ssannnxc?xtok=MTIzNGszY3M5a2Mwdw=="),
    SocialLink("TikTok", "@sanzxmzz", R.drawable.tiktok, "https://www.tiktok.com/@sanzxmzz?_r=1&_t=ZS-9Aq7mKZAycm"),
    SocialLink("WhatsApp", "Sanzxcy404 OFFC", R.drawable.whatsapp, "https://whatsapp.com/channel/0029VaqqHM8LdQeeGe29cZ2w"),
)

private const val LICENSE_URL = "https://github.com/Sanzz16/Sanzxcy/blob/main/LICENSE"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
) {
    val uriHandler = LocalUriHandler.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val outline = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top)))
        Spacer(Modifier.height(16.dp))

        // Kartu identitas aplikasi
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(24.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_static_foreground),
                    contentDescription = stringResource(R.string.sanzxcy_app_name),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black),
                )
                Spacer(Modifier.width(24.dp))
                Column {
                    Text(
                        text = stringResource(R.string.sanzxcy_app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Sanzxcy Music",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(BuildConfig.BASE_VERSION_NAME)
                        Badge(Updater.getCurrentArchitecture().uppercase())
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        DeveloperBanner()

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.sanzxcy_social_media),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        socialLinks.forEach { link ->
            SocialButton(link = link, onClick = { uriHandler.openUri(link.url) })
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Atribusi Metrolist (wajib dipertahankan oleh lisensi GPL-3.0)
        Text(
            text = stringResource(R.string.sanzxcy_based_on),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.kotlin_mark),
                contentDescription = "Kotlin",
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Ditulis dengan Kotlin",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "made with © By Sanzxmzz",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "This Project Gabut",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(48.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.sanzxcy_about_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
    )
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SocialButton(
    link: SocialLink,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                painter = painterResource(link.iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${link.platform}  ·  ${link.handle}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Banner developer. Video diambil dari res/raw/developer_banner.mp4 dan foto profil dari
 * res/drawable/developer_avatar (png/jpg/webp). Keduanya opsional: jika file belum ada,
 * banner memakai latar gradasi dan avatar berupa inisial, sehingga build tidak gagal.
 */
@Composable
private fun DeveloperBanner() {
    val context = LocalContext.current
    val videoRes = remember { context.resources.getIdentifier("developer_banner", "raw", context.packageName) }
    val avatarRes = remember { context.resources.getIdentifier("developer_avatar", "drawable", context.packageName) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A2A2A), Color(0xFF050505)),
                ),
            ),
    ) {
        if (videoRes != 0) {
            BannerVideo(videoRes = videoRes, modifier = Modifier.fillMaxSize())
        }

        // Overlay gelap agar teks tetap terbaca
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.sanzxcy_developer_name),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.sanzxcy_developer_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarRes != 0) {
                Image(
                    painter = painterResource(avatarRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "S",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Video latar tanpa suara. Satu ExoPlayer saja, dijeda saat halaman tidak aktif
 * (ON_PAUSE) dan dilepas saat composable keluar dari komposisi.
 */
@OptIn(UnstableApi::class)
@Composable
private fun BannerVideo(
    videoRes: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(videoRes) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$videoRes")))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).also { player.setVideoTextureView(it) }
        },
    )
}
