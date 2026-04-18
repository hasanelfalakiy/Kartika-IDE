/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide

import andihasan7.kartikaide.common.Analytics
import andihasan7.kartikaide.common.PreferenceKeys
import andihasan7.kartikaide.common.Prefs
import andihasan7.kartikaide.rewrite.plugin.api.Hook
import andihasan7.kartikaide.rewrite.plugin.api.HookManager
import andihasan7.kartikaide.rewrite.plugin.api.PluginLoader
import andihasan7.kartikaide.rewrite.util.FileUtil
import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andihasan7.kartikaide.fragment.PluginsFragment
import com.google.android.material.color.DynamicColors
import com.itsaky.androidide.config.JavacConfigProvider
import de.robv.android.xposed.XC_MethodHook
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.tm4e.core.registry.IThemeSource
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.sui.Sui
import java.io.File
import java.lang.ref.WeakReference
import java.net.URL
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

class App : Application() {

    companion object {
        @JvmStatic
        lateinit var instance: WeakReference<App>
    }

    private var themeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate() {
        super.onCreate()

        instance = WeakReference(this)
        
        // Ensure Prefs and FileUtil are initialized immediately
        Prefs.init(this)
        if (FileUtil.isInitialized.not()) {
            try {
                FileUtil.init(getExternalFilesDir(null)!!)
            } catch (e: Exception) {
                Log.e("App", "Failed to initialize FileUtil", e)
            }
        }

        Analytics.init(this@App)
        HookManager.context = WeakReference(this)

        // 1. Immediate UI/System setup
        Sui.init(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
        DynamicColors.applyToActivitiesIfAvailable(this)
        disableModules()
        updateNightMode()

        // Register listener for theme changes with a strong reference
        themeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PreferenceKeys.APP_THEME) {
                updateNightMode()
                if (Prefs.isInitialized) {
                    applyThemeBasedOnConfiguration()
                }
            }
        }
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(themeListener)

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
            // Apply Theme immediately
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

                if (FileUtil.isInitialized) {
                    extractFiles()
                }
                loadTextmateTheme()
            }

            // Priority 3: Delayed Hook initialization
            delay(1000) 
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

    private fun updateNightMode() {
        val theme = getTheme(Prefs.appTheme)
        
        // Use a small delay or launch on main to ensure stability
        CoroutineScope(Dispatchers.Main).launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val uiModeManager = getSystemService(UiModeManager::class.java)
                uiModeManager?.setApplicationNightMode(theme)
            } else {
                val mode = when (theme) {
                    UiModeManager.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_NO
                    UiModeManager.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppCompatDelegate.setDefaultNightMode(mode)
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

    fun disableModules() {
        JavacConfigProvider.disableModules()
    }

    fun loadTextmateTheme() {
        val fileProvider = AssetsFileResolver(assets)
        FileProviderRegistry.getInstance().addFileProvider(fileProvider)
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

        val registry = ThemeRegistry.getInstance()
        
        fun safeLoad(assetPath: String, themeName: String) {
            try {
                registry.loadTheme(ThemeModel(
                    IThemeSource.fromInputStream(assets.open(assetPath), assetPath, null),
                    themeName
                ))
            } catch (e: Throwable) {
                Log.e("App", "Failed to load theme: $themeName from $assetPath. Crash prevented.", e)
            }
        }

        safeLoad("textmate/darcula.json", "darcula")
        safeLoad("textmate/dracula_2.json", "dracula_2")
        safeLoad("textmate/onedark.json", "onedark")
        
        if (assets.list("textmate")?.contains("QuietLight.tmTheme.json") == true) {
            safeLoad("textmate/QuietLight.tmTheme.json", "QuietLight")
        }
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
        val themeName = if (Prefs.editorColorScheme != "darcula") {
            Prefs.editorColorScheme
        } else {
            when (getTheme(Prefs.appTheme)) {
                UiModeManager.MODE_NIGHT_YES -> "darcula"
                UiModeManager.MODE_NIGHT_NO -> "QuietLight"
                else -> {
                    if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "darcula" else "QuietLight"
                }
            }
        }
        try {
            ThemeRegistry.getInstance().setTheme(themeName)
        } catch (e: Exception) {
            Log.e("App", "Failed to apply theme: $themeName", e)
        }
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

    override fun onTerminate() {
        themeListener?.let {
            PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        super.onTerminate()
    }
}
