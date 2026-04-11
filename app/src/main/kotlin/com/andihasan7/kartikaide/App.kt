/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.itsaky.androidide.config.JavacConfigProvider
import de.robv.android.xposed.XC_MethodHook
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import andihasan7.kartikaide.common.Analytics
import andihasan7.kartikaide.common.Prefs
import com.andihasan7.kartikaide.fragment.PluginsFragment
import andihasan7.kartikaide.rewrite.plugin.api.Hook
import andihasan7.kartikaide.rewrite.plugin.api.HookManager
import andihasan7.kartikaide.rewrite.plugin.api.PluginLoader
import andihasan7.kartikaide.rewrite.util.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.sui.Sui
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.math.BigInteger
import java.net.URL
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone
import java.util.logging.Logger

class App : Application() {

    companion object {
        @JvmStatic
        lateinit var instance: WeakReference<App>
    }

    override fun onCreate() {
        super.onCreate()

        if (FileUtil.isInitialized.not()) return

        Analytics.init(this@App)
        instance = WeakReference(this)
        HookManager.context = WeakReference(this)

        // 1. Immediate UI/System setup
        Sui.init(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
        DynamicColors.applyToActivitiesIfAvailable(this)
        disableModules()
        setupNightMode()

        // 2. Launch background tasks
        val clientName = Prefs.clientName
        val appTheme = Prefs.appTheme
        val language = Locale.getDefault().language
        val timezone = TimeZone.getDefault().id
        val sdk = Build.VERSION.SDK_INT.toString() + " (" + Build.SUPPORTED_ABIS.joinToString(", ") + ")"
        val device = Build.DEVICE + " " + Build.PRODUCT
        val version = BuildConfig.VERSION_NAME + if (BuildConfig.GIT_COMMIT.isNotEmpty()) " (${BuildConfig.GIT_COMMIT})" else ""
        val appStartTime = ZonedDateTime.now().toString()

        CoroutineScope(Dispatchers.Main).launch {
            // Priority 1: Apply Theme immediately
            if (Prefs.isInitialized) {
                applyThemeBasedOnConfiguration()
            }

            // Priority 2: IO Tasks in background
            launch(Dispatchers.IO) {
                // Parallelize Network and File IO
                launch {
                    val ip = getPublicIp()
                    Analytics.logEvent("user_metrics", "name" to clientName, "ip" to ip, "theme" to appTheme, "language" to language, "timezone" to timezone, "sdk" to sdk, "device" to device, "version" to version)
                    Analytics.logEvent("app_start", "time" to appStartTime)
                }

                extractFiles()
                loadTextmateTheme()
            }

            // Priority 3: Delayed Hook initialization
            delay(1000) // Reduced delay for better feel
            try {
                setupHooks()
                loadPlugins()
            } catch (t: Throwable) {
                Log.e("App", "Failed to initialize hooks/plugins", t)
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, p1: Bundle?) {
                (activity as? ComponentActivity)?.enableEdgeToEdge()
            }
            override fun onActivityStarted(p0: Activity) {}
            override fun onActivityResumed(p0: Activity) {}
            override fun onActivityPaused(p0: Activity) {}
            override fun onActivityStopped(p0: Activity) {}
            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
            override fun onActivityDestroyed(p0: Activity) {}
        })

        Analytics.setAnalyticsCollectionEnabled(Prefs.analyticsEnabled)
    }

    private fun setupNightMode() {
        val theme = getTheme(Prefs.appTheme)
        val uiModeManager = getSystemService(UiModeManager::class.java)
        if (uiModeManager != null) {
            if (uiModeManager.nightMode != theme) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    uiModeManager.setApplicationNightMode(theme)
                } else {
                    AppCompatDelegate.setDefaultNightMode(if (theme == UiModeManager.MODE_NIGHT_AUTO) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else theme)
                }
            }
        }
    }

    fun getTheme(theme: String): Int {
        return when (theme) {
            "light" -> UiModeManager.MODE_NIGHT_NO
            "dark" -> UiModeManager.MODE_NIGHT_YES
            else -> UiModeManager.MODE_NIGHT_AUTO
        }
    }

    fun extractFiles() {
        // Optimized: Check version instead of calculating checksums on every startup
        val currentVersion = BuildConfig.VERSION_CODE
        val lastExtractedVersion = Prefs.lastExtractedVersion
        val forceUpdate = currentVersion != lastExtractedVersion

        extractAsset("kotlin-stdlib-1.9.0.jar", FileUtil.classpathDir.resolve("kotlin-stdlib-1.9.0.jar"), forceUpdate)
        extractAsset("kotlin-stdlib-common-1.9.0.jar", FileUtil.classpathDir.resolve("kotlin-stdlib-common-1.9.0.jar"), forceUpdate)
        extractAsset("android.jar", FileUtil.classpathDir.resolve("android.jar"), forceUpdate)
        extractAsset("core-lambda-stubs.jar", FileUtil.classpathDir.resolve("core-lambda-stubs.jar"), forceUpdate)
        
        if (forceUpdate) {
            Prefs.lastExtractedVersion = currentVersion
        }
    }

    fun extractAsset(assetName: String, targetFile: File, force: Boolean) {
        try {
            if (force && targetFile.exists()) {
                targetFile.delete()
            }
            if (!targetFile.exists()) {
                assets.open(assetName).use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("App", "Failed to extract asset: $assetName", e)
        }
    }

    // Removed the heavy calculateChecksum function from the startup path

    fun disableModules() {
        JavacConfigProvider.disableModules()
    }

    fun loadTextmateTheme() {
        val fileProvider = AssetsFileResolver(assets)
        FileProviderRegistry.getInstance().addFileProvider(fileProvider)
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }

    private fun setupHooks() {
        try {
            HookManager.registerHook(object : Hook("exit", Int::class.java, type = System::class.java) {
                override fun before(param: XC_MethodHook.MethodHookParam) {
                    System.err.println("System.exit() intercepted!")
                    param.result = null
                }
            })

            HookManager.registerHook(object : Hook("onLayoutChildren", RecyclerView.Recycler::class.java, RecyclerView.State::class.java, type = LinearLayoutManager::class.java) {
                override fun before(param: XC_MethodHook.MethodHookParam) {
                    try {
                        HookManager.invokeOriginal(param.method, param.thisObject, param.args[0], param.args[1])
                    } catch (e: Exception) {
                        Log.e("App", "Error in onLayoutChildren hook", e)
                    }
                    param.result = null
                }
            })
        } catch (e: Throwable) {
            Log.e("App", "Error setting up specific hooks", e)
        }
    }

    private fun getPublicIp(): String {
        return try {
            URL("https://api.ipify.org").readText()
        } catch (e: Exception) {
            ""
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Prefs.isInitialized) {
            applyThemeBasedOnConfiguration()
        }
    }

    fun applyThemeBasedOnConfiguration() {
        val themeName = when (getTheme(Prefs.appTheme)) {
            AppCompatDelegate.MODE_NIGHT_YES -> "darcula"
            AppCompatDelegate.MODE_NIGHT_NO -> "QuietLight"
            else -> {
                if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "darcula" else "QuietLight"
            }
        }
        ThemeRegistry.getInstance().setTheme(themeName)
    }

    fun loadPlugins() {
        try {
            PluginsFragment.getPlugins().forEach { plugin ->
                if (plugin.isEnabled) {
                    PluginLoader.loadPlugin(FileUtil.pluginDir.resolve(plugin.name), plugin)
                }
            }
        } catch (e: Exception) {
            Log.e("App", "Failed to load plugins", e)
        }
    }
}
