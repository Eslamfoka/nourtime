package com.nourtime.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageItemInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.nourtime.app.core.locale.AppLocales
import com.nourtime.app.core.security.AnswerNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String)

/**
 * Apps the child can open from the launcher. Visibility comes from the `<queries>` launcher-intent
 * filter in the manifest, so the app doesn't need QUERY_ALL_PACKAGES.
 */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pm: PackageManager = context.packageManager
    private val icons = LruCache<String, ImageBitmap>(ICON_CACHE_SIZE)

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val collator = Collator.getInstance(appLocale())
        infos
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { InstalledApp(it.activityInfo.packageName, localizedLabel(it.activityInfo) { it.loadLabel(pm) }) }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** Keyed by language too, so switching Nour Time's language shows the new names. */
    private val labels = LruCache<String, String>(ICON_CACHE_SIZE)

    /** Display name for [packageName], or the package name itself if it isn't visible. */
    suspend fun label(packageName: String): String {
        val key = "${appLocale().toLanguageTag()}|$packageName"
        labels.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val info = pm.getApplicationInfo(packageName, 0)
                localizedLabel(info) { pm.getApplicationLabel(info) }
            }
                .getOrDefault(packageName)
                .also { labels.put(key, it) }
        }
    }

    /** Nour Time's own language: the in-app choice, or the phone's when it follows the phone. */
    private fun appLocale(): Locale = AppLocales.locale(context)

    /**
     * The label in Nour Time's own language (which can differ from the phone's), read from the label
     * resource with that locale so it doesn't depend on which configuration PackageManager uses;
     * [fallback] covers apps without a label resource.
     */
    private fun localizedLabel(item: PackageItemInfo, fallback: () -> CharSequence): String = runCatching {
        item.nonLocalizedLabel?.let { return@runCatching it.toString() }
        val appInfo = (item as? ComponentInfo)?.applicationInfo ?: item as ApplicationInfo
        val labelRes = item.labelRes.takeIf { it != 0 } ?: appInfo.labelRes.takeIf { it != 0 }
            ?: return@runCatching fallback().toString()
        val config = Configuration(context.resources.configuration).apply { setLocale(appLocale()) }
        context.createPackageContext(appInfo.packageName, 0).createConfigurationContext(config).getString(labelRes)
    }.getOrElse { fallback().toString() }

    suspend fun icon(packageName: String): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }
                .getOrNull()
                ?.also { icons.put(packageName, it) }
        }
    }

    private companion object {
        const val ICON_CACHE_SIZE = 200
        const val ICON_PX = 108
    }
}

/**
 * Search by name or package, ignoring case, spaces and Arabic letter variants. Apps in [pinned]
 * (the ones already limited when the list was opened) come first so toggling doesn't reorder rows.
 */
fun filterApps(apps: List<InstalledApp>, query: String, pinned: Set<String>): List<InstalledApp> {
    val q = AnswerNormalizer.normalize(query)
    val matches = if (q.isEmpty()) apps else apps.filter {
        AnswerNormalizer.normalize(it.label).contains(q) || it.packageName.lowercase().contains(q)
    }
    return matches.sortedBy { it.packageName !in pinned }
}
