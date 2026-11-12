/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.os.Build
import android.os.Process
import com.sanzxcy.music.com.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val description: String,
    val releaseDate: String,
    val assets: List<ReleaseAsset>
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val architecture: String,
    val variant: String // "foss" or "gms"
)

object Updater {
    private val client = HttpClient()
    var lastCheckTime = -1L
        private set
    
    private var cachedReleaseInfo: ReleaseInfo? = null
    private var cachedAllReleases: List<ReleaseInfo> = emptyList()
    private var checkedNoStableRelease = false
    
    private const val CHECK_INTERVAL_MILLIS = 2 * 60 * 60 * 1000L // 2 hours
    private const val GITHUB_API_BASE = "https://api.github.com/repos/Sanzz16/Sanzxcymusic"
    private const val KMP_LATEST_RELEASE_URL = "$GITHUB_API_BASE/releases/latest"
    private const val KMP_APK_NAME = "Metrolist.apk"

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        val tagName = json.optString("tag_name").ifBlank { json.optString("name") }
        if (tagName.isBlank()) {
            val apiMessage = json.optString("message").takeIf { it.isNotBlank() }
            throw IllegalStateException(apiMessage ?: "GitHub belum menyediakan release dengan nama versi/tag.")
        }
        val versionName = json.optString("name").ifBlank { tagName.removePrefix("v") }
        return ReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            description = json.optString("body").takeUnless { json.isNull("body") }.orEmpty(),
            releaseDate = json.optString("published_at"),
            assets = parseAssets(json.optJSONArray("assets") ?: JSONArray()),
        )
    }

    private suspend fun HttpResponse.checkedBody(endpoint: String): String {
        val body = bodyAsText()
        if (status.value !in 200..299) {
            val message = runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()
            throw IllegalStateException(
                "GitHub API $endpoint gagal (HTTP ${status.value})${message.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}",
            )
        }
        return body
    }

    /**
     * Compares two version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = v2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val part1 = v1Parts.getOrNull(i) ?: 0
            val part2 = v2Parts.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    /**
     * Checks if the latest version is newer than the current version.
     * Returns true if an update is available (latestVersion > currentVersion)
     */
    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    /**
     * Get the current app's architecture and variant
     */
    fun getCurrentArchitecture(): String {
        val configured = BuildConfig.ARCHITECTURE
        if (!configured.equals("universal", ignoreCase = true)) return configured
        val processAbis = if (Process.is64Bit()) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS
        return processAbis.firstOrNull() ?: "universal"
    }

    private fun getCurrentAppVariant(): Pair<String, String> {
        val architecture = getCurrentArchitecture()
        val variant = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"
        return architecture to variant
    }

    /**
     * Parse release assets from GitHub API response
     */
    private fun parseAssets(assetsArray: JSONArray): List<ReleaseAsset> {
        val assets = mutableListOf<ReleaseAsset>()
        
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            
            // Skip non-APK files
            if (!name.endsWith(".apk")) continue
            
            val downloadUrl = asset.getString("browser_download_url")
            val size = asset.getLong("size")
            
            // Parse architecture and variant from filename
            val (arch, variant) = when {
                name == "Metrolist.apk" -> "universal" to "foss"
                name == "Metrolist-with-Google-Cast.apk" -> "universal" to "gms"
                name.startsWith("app-") && name.endsWith("-release.apk") -> {
                    val stem = name.removePrefix("app-").removeSuffix("-release.apk")
                    val arch = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
                        .firstOrNull { stem.endsWith(it) } ?: stem
                    val variant = when {
                        stem.startsWith("gms-") -> "gms"
                        stem.startsWith("izzy-") -> "izzy"
                        else -> "foss"
                    }
                    arch to variant
                }
                name.startsWith("app-") && name.endsWith("-with-Google-Cast.apk") -> {
                    val arch = name.removePrefix("app-").removeSuffix("-with-Google-Cast.apk")
                    arch to "gms"
                }
                else -> null to null
            }
            
            if (arch != null && variant != null) {
                assets.add(ReleaseAsset(name, downloadUrl, size, arch, variant))
            }
        }
        
        return assets
    }

    /**
     * Fetch latest release from GitHub API
     */
    suspend fun getLatestRelease(forceRefresh: Boolean = false): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                if (!forceRefresh && cachedReleaseInfo != null) return@runCatching cachedReleaseInfo
                if (!forceRefresh && checkedNoStableRelease && now - lastCheckTime < CHECK_INTERVAL_MILLIS) {
                    return@runCatching null
                }

                val latestResponse = client.get("$GITHUB_API_BASE/releases/latest")
                val releaseJson =
                    if (latestResponse.status.value == 404) {
                        // /releases/latest gives 404 when no stable release has been published.
                        // Query the releases list to distinguish that from an incorrect repository URL.
                        latestResponse.bodyAsText()
                        val releasesBody =
                            client.get("$GITHUB_API_BASE/releases?per_page=30")
                                .checkedBody("daftar release")
                        val releases = JSONArray(releasesBody)
                        val stableRelease =
                            (0 until releases.length())
                                .asSequence()
                                .map { releases.getJSONObject(it) }
                                .firstOrNull { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
                        if (stableRelease == null) {
                            cachedReleaseInfo = null
                            checkedNoStableRelease = true
                            lastCheckTime = now
                            return@runCatching null
                        }
                        stableRelease
                    } else {
                        JSONObject(latestResponse.checkedBody("latest release"))
                    }

                val releaseInfo = parseRelease(releaseJson)
                cachedReleaseInfo = releaseInfo
                checkedNoStableRelease = false
                lastCheckTime = now
                releaseInfo
            }
        }

    /**
     * Fetch all releases from GitHub API (paginated)
     */
    suspend fun getAllReleases(forceRefresh: Boolean = false): Result<List<ReleaseInfo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (cachedAllReleases.isNotEmpty() && !forceRefresh) {
                    return@runCatching cachedAllReleases
                }
                
                val releases = mutableListOf<ReleaseInfo>()
                var page = 1
                var hasMore = true
                
                while (hasMore && page <= 10) { // Limit to 10 pages
                    val response = client.get("$GITHUB_API_BASE/releases?page=$page&per_page=30")
                        .checkedBody("daftar release")
                    val json = JSONArray(response)
                    
                    if (json.length() == 0) {
                        hasMore = false
                        break
                    }
                    
                    for (i in 0 until json.length()) {
                        val releaseObj = json.getJSONObject(i)
                        runCatching { parseRelease(releaseObj) }.getOrNull()?.let(releases::add)
                    }
                    
                    page++
                }
                
                cachedAllReleases = releases
                releases
            }
        }

    internal fun parseKmpRelease(response: String): ReleaseInfo? {
        val release = JSONObject(response)
        val assets = parseAssets(release.optJSONArray("assets") ?: JSONArray()).filter { it.name == KMP_APK_NAME }
        val tagName = release.optString("tag_name").ifBlank { release.optString("name") }
        if (tagName.isBlank()) return null

        return ReleaseInfo(
            tagName = tagName,
            versionName = release.optString("name").ifBlank { tagName.removePrefix("v") },
            description = release.optString("body").takeUnless { release.isNull("body") }.orEmpty(),
            releaseDate = release.optString("published_at"),
            assets = assets,
        ).takeIf { assets.isNotEmpty() }
    }

    /**
     * Returns the latest stable KMP release when it includes an Android APK.
     */
    suspend fun getLatestKmpRelease(): Result<ReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                parseKmpRelease(client.get(KMP_LATEST_RELEASE_URL).checkedBody("KMP latest release"))
            }
        }

    /**
     * Get the download URL for the correct app variant
     */
    fun getDownloadUrlForCurrentVariant(releaseInfo: ReleaseInfo): String? {
        val (currentArch, currentVariant) = getCurrentAppVariant()
        
        return releaseInfo.assets
            .find { it.architecture == currentArch && it.variant == currentVariant }
            ?.downloadUrl
    }

    /**
     * Check if update is needed (respects 2-hour cache)
     */
    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<Pair<ReleaseInfo?, Boolean>> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Check if we should fetch (2 hour interval)
                val shouldFetch = forceRefresh || 
                    (System.currentTimeMillis() - lastCheckTime) > CHECK_INTERVAL_MILLIS
                
                if (!shouldFetch && (cachedReleaseInfo != null || checkedNoStableRelease)) {
                    val cached = cachedReleaseInfo
                    val hasUpdate = cached?.let {
                        isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, it.versionName)
                    } ?: false
                    return@runCatching cached to hasUpdate
                }

                val result = getLatestRelease(forceRefresh = true)
                if (result.isSuccess) {
                    val releaseInfo = result.getOrThrow()
                    val hasUpdate = releaseInfo?.let {
                        isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, it.versionName)
                    } ?: false
                    releaseInfo to hasUpdate
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            }
        }

    /**
     * Get the latest release info (cached)
     */
    fun getCachedLatestRelease(): ReleaseInfo? = cachedReleaseInfo
}
