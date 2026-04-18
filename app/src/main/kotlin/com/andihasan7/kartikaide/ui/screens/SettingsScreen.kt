/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.andihasan7.kartikaide.BuildConfig
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.extension.copyToClipboard
import andihasan7.kartikaide.common.Prefs
import andihasan7.kartikaide.common.PreferenceKeys
import org.jetbrains.kotlin.config.LanguageVersion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val context = LocalContext.current
        val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
        var experimentsEnabled by remember { mutableStateOf(Prefs.experimentsEnabled) }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { PreferenceCategory(title = "General") }
            item {
                PreferenceItem(
                    title = "Appearance",
                    summary = "Theme and UI customization",
                    icon = painterResource(R.drawable.baseline_color_lens_24),
                    onClick = { onNavigate("appearance") }
                )
            }
            item {
                PreferenceItem(
                    title = "Code Editor",
                    summary = "Font, indentation, and behavior",
                    icon = painterResource(R.drawable.baseline_mode_edit_24),
                    onClick = { onNavigate("editor") }
                )
            }
            item {
                PreferenceItem(
                    title = "Compiler",
                    summary = "Java/Kotlin versions and build flags",
                    icon = painterResource(R.drawable.outline_build_24),
                    onClick = { onNavigate("compiler") }
                )
            }
            item {
                PreferenceItem(
                    title = "Formatter",
                    summary = "Code formatting styles and rules",
                    icon = painterResource(R.drawable.outline_edit_note_24),
                    onClick = { onNavigate("formatter") }
                )
            }
            item {
                PreferenceItem(
                    title = "Plugins",
                    summary = "Extend functionality with plugins",
                    icon = painterResource(R.drawable.outline_extension_24),
                    onClick = { onNavigate("plugins") }
                )
            }
            item {
                PreferenceItem(
                    title = "Git",
                    summary = "Username, email, and API key",
                    icon = painterResource(R.drawable.github),
                    onClick = { onNavigate("git") }
                )
            }
            item {
                PreferenceItem(
                    title = "Gemini AI",
                    summary = "API key and model configuration",
                    icon = painterResource(R.drawable.outline_forum_24),
                    onClick = { onNavigate("gemini") }
                )
            }

            item { PreferenceCategory(title = "System") }
            item {
                var clickCount by remember { mutableIntStateOf(0) }
                PreferenceItem(
                    title = "App version",
                    summary = BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (${BuildConfig.GIT_COMMIT})" else "",
                    iconVector = Icons.Default.Android,
                    onClick = {
                        clickCount++
                        if (clickCount == 1) context.copyToClipboard(BuildConfig.VERSION_NAME)
                        if (clickCount == 7) {
                            val newValue = !experimentsEnabled
                            prefs.edit { putBoolean("experiments_enabled", newValue) }
                            experimentsEnabled = newValue
                            Toast.makeText(context, if (newValue) "Developer mode enabled" else "Developer mode disabled", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            item {
                PreferenceItem(
                    title = "Manage storage permission",
                    summary = "Grant access to all files",
                    iconVector = Icons.Default.Storage,
                    onClick = { openStorageSettings(context) }
                )
            }

            item { PreferenceCategory(title = "Support") }
            item {
                PreferenceItem(
                    title = "About Kartika IDE",
                    summary = "Version, license, and social links",
                    icon = painterResource(R.drawable.round_info_outline_24),
                    onClick = onAboutClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var theme by remember { mutableStateOf(prefs.getString(PreferenceKeys.APP_THEME, "auto") ?: "auto") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ListPreferenceItem(
                    title = "App Theme",
                    summary = theme.replaceFirstChar { it.uppercase() },
                    options = listOf("auto" to "System Default", "light" to "Light", "dark" to "Dark"),
                    selectedKey = theme,
                    onSelected = { newValue ->
                        theme = newValue
                        prefs.edit().putString(PreferenceKeys.APP_THEME, newValue).apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var fontSize by remember { mutableFloatStateOf(prefs.getInt(PreferenceKeys.EDITOR_FONT_SIZE, 14).toFloat()) }
    var tabSize by remember { mutableFloatStateOf(prefs.getInt(PreferenceKeys.EDITOR_TAB_SIZE, 4).toFloat()) }
    var useSpaces by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_USE_SPACES, true)) }
    var wordWrap by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_WORDWRAP_ENABLE, false)) }
    var lineNumbers by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_LINE_NUMBERS_SHOW, true)) }
    var stickyScroll by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.STICKY_SCROLL, true)) }
    var bracketAuto by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.BRACKET_PAIR_AUTOCOMPLETE, true)) }
    var ligatures by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_LIGATURES_ENABLE, false)) }
    var scrollbar by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_SCROLLBAR_SHOW, true)) }
    var fastDelete by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.QUICK_DELETE, true)) }
    var hwAccel by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_HW_ENABLE, true)) }
    var nonPrintable by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_NON_PRINTABLE_SYMBOLS_SHOW, false)) }
    var doubleClickClose by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_DOUBLE_CLICK_CLOSE, false)) }
    var disableSymbols by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.DISABLE_SYMBOLS_VIEW, false)) }
    var customSymbols by remember { mutableStateOf(prefs.getString(PreferenceKeys.EDITOR_CUSTOM_SYMBOLS, "→,(,),{,},[,],;,.,,") ?: "") }
    var expJavaComp by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.EDITOR_EXP_JAVA_COMPLETION, false)) }
    var ktRealtimeErrors by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.KOTLIN_REALTIME_ERRORS, false)) }
    var editorFont by remember { mutableStateOf(prefs.getString(PreferenceKeys.EDITOR_FONT, "") ?: "") }
    var colorScheme by remember { mutableStateOf(prefs.getString(PreferenceKeys.EDITOR_COLOR_SCHEME, "darcula") ?: "darcula") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Code Editor") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { PreferenceCategory("Appearance") }
            item { SliderPreferenceItem(title = "Font Size", value = fontSize, valueRange = 12f..32f, steps = 20, onValueChange = { fontSize = it; prefs.edit { putInt(PreferenceKeys.EDITOR_FONT_SIZE, it.toInt()) } }) }
            item { SliderPreferenceItem(title = "Tab Size", value = tabSize, valueRange = 2f..8f, steps = 6, onValueChange = { tabSize = it; prefs.edit { putInt(PreferenceKeys.EDITOR_TAB_SIZE, it.toInt()) } }) }
            item { ListPreferenceItem(title = "Color Scheme", summary = colorScheme, options = listOf("darcula" to "Darcula", "dracula_2" to "Dracula 2", "onedark" to "OneDark Pro"), selectedKey = colorScheme, onSelected = { colorScheme = it; prefs.edit { putString(PreferenceKeys.EDITOR_COLOR_SCHEME, it) } }) }
            item { EditTextPreferenceItem(title = "Editor Font Path", value = editorFont, onValueChange = { editorFont = it; prefs.edit { putString(PreferenceKeys.EDITOR_FONT, it) } }) }
            item { SwitchPreferenceItem(title = "Font ligatures", checked = ligatures, onCheckedChange = { ligatures = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_LIGATURES_ENABLE, it) } }) }
            item { SwitchPreferenceItem(title = "Show line numbers", checked = lineNumbers, onCheckedChange = { lineNumbers = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_LINE_NUMBERS_SHOW, it) } }) }
            item { SwitchPreferenceItem(title = "Show scrollbar", checked = scrollbar, onCheckedChange = { scrollbar = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_SCROLLBAR_SHOW, it) } }) }
            item { SwitchPreferenceItem(title = "Show non-printable characters", checked = nonPrintable, onCheckedChange = { nonPrintable = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_NON_PRINTABLE_SYMBOLS_SHOW, it) } }) }
            
            item { PreferenceCategory("Behavior") }
            item { SwitchPreferenceItem(title = "Use spaces instead of tabs", checked = useSpaces, onCheckedChange = { useSpaces = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_USE_SPACES, it) } }) }
            item { SwitchPreferenceItem(title = "Word wrap", checked = wordWrap, onCheckedChange = { wordWrap = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_WORDWRAP_ENABLE, it) } }) }
            item { SwitchPreferenceItem(title = "Sticky scroll", checked = stickyScroll, onCheckedChange = { stickyScroll = it; prefs.edit { putBoolean(PreferenceKeys.STICKY_SCROLL, it) } }) }
            item { SwitchPreferenceItem(title = "Bracket auto-completion", checked = bracketAuto, onCheckedChange = { bracketAuto = it; prefs.edit { putBoolean(PreferenceKeys.BRACKET_PAIR_AUTOCOMPLETE, it) } }) }
            item { SwitchPreferenceItem(title = "Fast delete blank lines", checked = fastDelete, onCheckedChange = { fastDelete = it; prefs.edit { putBoolean(PreferenceKeys.QUICK_DELETE, it) } }) }
            item { SwitchPreferenceItem(title = "Double click to close tab", checked = doubleClickClose, onCheckedChange = { doubleClickClose = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_DOUBLE_CLICK_CLOSE, it) } }) }
            item { SwitchPreferenceItem(title = "Disable symbols view", checked = disableSymbols, onCheckedChange = { disableSymbols = it; prefs.edit { putBoolean(PreferenceKeys.DISABLE_SYMBOLS_VIEW, it) } }) }
            item { EditTextPreferenceItem(title = "Custom symbols", value = customSymbols, onValueChange = { customSymbols = it; prefs.edit { putString(PreferenceKeys.EDITOR_CUSTOM_SYMBOLS, it) } }) }
            
            item { PreferenceCategory("Experimental") }
            item { SwitchPreferenceItem(title = "Experimental Java completion", checked = expJavaComp, onCheckedChange = { expJavaComp = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_EXP_JAVA_COMPLETION, it) } }) }
            item { SwitchPreferenceItem(title = "Kotlin real-time errors", summary = "Checking errors while typing (may cause lag)", checked = ktRealtimeErrors, onCheckedChange = { ktRealtimeErrors = it; prefs.edit { putBoolean(PreferenceKeys.KOTLIN_REALTIME_ERRORS, it) } }) }
            
            item { PreferenceCategory("Performance") }
            item { SwitchPreferenceItem(title = "Hardware acceleration", summary = "Speed up rendering (may use more memory)", checked = hwAccel, onCheckedChange = { hwAccel = it; prefs.edit { putBoolean(PreferenceKeys.EDITOR_HW_ENABLE, it) } }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilerSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    var javaVersion by remember { mutableStateOf(prefs.getString(PreferenceKeys.COMPILER_JAVA_VERSIONS, "17") ?: "17") }
    var kotlinVersion by remember { mutableStateOf(prefs.getString(PreferenceKeys.COMPILER_KOTLIN_VERSION, LanguageVersion.KOTLIN_2_1.versionString) ?: LanguageVersion.KOTLIN_2_1.versionString) }
    var useK2 by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.COMPILER_USE_K2, false)) }
    var useFJFS by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.COMPILER_USE_FJFS, false)) }
    var useSSVM by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.COMPILER_USE_SSVM, false)) }
    var javacFlags by remember { mutableStateOf(prefs.getString(PreferenceKeys.COMPILER_JAVAC_FLAGS, "") ?: "") }
    
    val defaultRepos = """
        Maven Central: https://repo1.maven.org/maven2
        Google Maven: https://maven.google.com
        Jitpack: https://jitpack.io
        Sonatype Snapshots: https://s01.oss.sonatype.org/content/repositories/snapshots
        JCenter: https://jcenter.bintray.com
    """.trimIndent()
    
    var mavenRepos by remember { mutableStateOf(prefs.getString("repos", defaultRepos) ?: defaultRepos) }

    val kotlinVersions = listOf("1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.0", "2.1")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compiler") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { PreferenceCategory("General Build") }
            item { SwitchPreferenceItem(title = "K2 Compiler", summary = "Enable experimental K2 compiler", checked = useK2, onCheckedChange = { useK2 = it; prefs.edit { putBoolean(PreferenceKeys.COMPILER_USE_K2, it) } }) }
            item { SwitchPreferenceItem(title = "Fast Jar File System", summary = "Experimental: Speed up jar reading", checked = useFJFS, onCheckedChange = { useFJFS = it; prefs.edit { putBoolean(PreferenceKeys.COMPILER_USE_FJFS, it) } }) }
            item { SwitchPreferenceItem(title = "Use SSVM", summary = "Experimental: Use Simple Static Virtual Machine", checked = useSSVM, onCheckedChange = { useSSVM = it; prefs.edit { putBoolean(PreferenceKeys.COMPILER_USE_SSVM, it) } }) }
            
            item { PreferenceCategory("Versions") }
            item { ListPreferenceItem(title = "Java Version", summary = "Java $javaVersion", options = listOf("8" to "Java 8", "11" to "Java 11", "17" to "Java 17", "21" to "Java 21"), selectedKey = javaVersion, onSelected = { javaVersion = it; prefs.edit { putString(PreferenceKeys.COMPILER_JAVA_VERSIONS, it) } }) }
            item { ListPreferenceItem(title = "Kotlin Version", summary = "Kotlin $kotlinVersion", options = kotlinVersions.map { it to "Kotlin $it" }, selectedKey = kotlinVersion, onSelected = { kotlinVersion = it; prefs.edit { putString(PreferenceKeys.COMPILER_KOTLIN_VERSION, it) } }) }
            
            item { PreferenceCategory("Library Manager") }
            item { EditTextPreferenceItem(title = "Repositories", summary = "Current repositories list", value = mavenRepos, onValueChange = { mavenRepos = it; prefs.edit { putString("repos", it) } }) }
            
            item { PreferenceCategory("Advanced") }
            item { EditTextPreferenceItem(title = "Javac Flags", summary = "Additional flags for Java compiler", value = javacFlags, onValueChange = { javacFlags = it; prefs.edit { putString(PreferenceKeys.COMPILER_JAVAC_FLAGS, it) } }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatterSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    var ktfmtStyle by remember { mutableStateOf(prefs.getString(PreferenceKeys.FORMATTER_KTFMT_STYLE, "google") ?: "google") }
    var maxWidth by remember { mutableFloatStateOf(prefs.getInt(PreferenceKeys.KTFMT_MAX_WIDTH, 100).toFloat()) }
    var blockIndent by remember { mutableFloatStateOf(prefs.getInt(PreferenceKeys.KTFMT_BLOCK_INDENT, 4).toFloat()) }
    var continuationIndent by remember { mutableFloatStateOf(prefs.getInt(PreferenceKeys.KTFMT_CONTINUATION_INDENT, 4).toFloat()) }
    var removeUnused by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.KTFMT_REMOVE_UNUSED_IMPORTS, true)) }
    var manageCommas by remember { mutableStateOf(prefs.getBoolean(PreferenceKeys.KTFMT_MANAGE_TRAILING_COMMAS, false)) }
    
    var gjfStyle by remember { mutableStateOf(prefs.getString(PreferenceKeys.FORMATTER_GJF_STYLE, "aosp") ?: "aosp") }
    var gjfOptions by remember { mutableStateOf(prefs.getStringSet(PreferenceKeys.FORMATTER_GJF_OPTIONS, setOf("--skip-javadoc-formatting")) ?: setOf("--skip-javadoc-formatting")) }

    val ktfmtStyles = listOf("google" to "Google", "kotlinlang" to "Kotlinlang", "dropbox" to "Dropbox", "ktlint" to "Ktlint", "custom" to "Custom")
    val gjfOptionList = listOf(
        "--fix-imports-only" to "Fix imports only",
        "--skip-sorting-imports" to "Skip sorting imports",
        "--skip-removing-unused-imports" to "Skip removing unused imports",
        "--skip-reflowing-long-strings" to "Skip reflowing long strings",
        "--skip-javadoc-formatting" to "Skip javadoc formatting"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Formatter") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { PreferenceCategory("Kotlin Formatter (ktfmt)") }
            item { ListPreferenceItem(title = "Style", summary = ktfmtStyle.replaceFirstChar { it.uppercase() }, options = ktfmtStyles, selectedKey = ktfmtStyle, onSelected = { ktfmtStyle = it; prefs.edit { putString(PreferenceKeys.FORMATTER_KTFMT_STYLE, it) } }) }
            item { SliderPreferenceItem(title = "Max Width", value = maxWidth, valueRange = 40f..200f, steps = 160, onValueChange = { maxWidth = it; prefs.edit { putInt(PreferenceKeys.KTFMT_MAX_WIDTH, it.toInt()) } }) }
            item { SliderPreferenceItem(title = "Block Indent", value = blockIndent, valueRange = 2f..8f, steps = 6, onValueChange = { blockIndent = it; prefs.edit { putInt(PreferenceKeys.KTFMT_BLOCK_INDENT, it.toInt()) } }) }
            item { SliderPreferenceItem(title = "Continuation Indent", value = continuationIndent, valueRange = 2f..8f, steps = 6, onValueChange = { continuationIndent = it; prefs.edit { putInt(PreferenceKeys.KTFMT_CONTINUATION_INDENT, it.toInt()) } }) }
            item { SwitchPreferenceItem(title = "Remove unused imports", checked = removeUnused, onCheckedChange = { removeUnused = it; prefs.edit { putBoolean(PreferenceKeys.KTFMT_REMOVE_UNUSED_IMPORTS, it) } }) }
            item { SwitchPreferenceItem(title = "Manage trailing commas", checked = manageCommas, onCheckedChange = { manageCommas = it; prefs.edit { putBoolean(PreferenceKeys.KTFMT_MANAGE_TRAILING_COMMAS, it) } }) }
            
            item { PreferenceCategory("Java Formatter (GJF)") }
            item { ListPreferenceItem(title = "Style", summary = gjfStyle.uppercase(), options = listOf("aosp" to "AOSP", "google" to "Google"), selectedKey = gjfStyle, onSelected = { gjfStyle = it; prefs.edit { putString(PreferenceKeys.FORMATTER_GJF_STYLE, it) } }) }
            item {
                MultiChoicePreferenceItem(
                    title = "GJF Options",
                    summary = if (gjfOptions.isEmpty()) "None" else gjfOptions.joinToString(", "),
                    options = gjfOptionList,
                    selectedKeys = gjfOptions,
                    onSelected = { newSet ->
                        gjfOptions = newSet
                        prefs.edit { putStringSet(PreferenceKeys.FORMATTER_GJF_OPTIONS, newSet) }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsSettingsScreen(
    onBackClick: () -> Unit,
    onAvailablePluginsClick: () -> Unit,
    onInstalledPluginsClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var repo by remember { mutableStateOf(prefs.getString(PreferenceKeys.PLUGIN_REPOSITORY, "https://raw.githubusercontent.com/hasanelfalakiy/plugins-repo/main/plugins.json") ?: "https://raw.githubusercontent.com/hasanelfalakiy/plugins-repo/main/plugins.json") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugins") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { PreferenceCategory("Store") }
            item { PreferenceItem(title = "Available plugins", summary = "Browse and download new plugins", onClick = onAvailablePluginsClick) }
            item { PreferenceItem(title = "Installed plugins", summary = "Manage your installed extensions", onClick = onInstalledPluginsClick) }
            
            item { PreferenceCategory("Configuration") }
            item { EditTextPreferenceItem(title = "Repository", summary = "Primary plugin source", value = repo, onValueChange = { repo = it; prefs.edit { putString(PreferenceKeys.PLUGIN_REPOSITORY, it) } }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    var username by remember { mutableStateOf(prefs.getString(PreferenceKeys.GIT_USERNAME, "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString(PreferenceKeys.GIT_EMAIL, "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString(PreferenceKeys.GIT_API_KEY, "") ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { EditTextPreferenceItem(title = "Username", value = username, onValueChange = { username = it; prefs.edit { putString(PreferenceKeys.GIT_USERNAME, it) } }) }
            item { EditTextPreferenceItem(title = "Email", value = email, onValueChange = { email = it; prefs.edit { putString(PreferenceKeys.GIT_EMAIL, it) } }) }
            item { EditTextPreferenceItem(title = "Personal Access Token", value = token, isPassword = true, onValueChange = { token = it; prefs.edit { putString(PreferenceKeys.GIT_API_KEY, it) } }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiSettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    
    var apiKey by remember { mutableStateOf(prefs.getString(PreferenceKeys.GEMINI_API_KEY, "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString(PreferenceKeys.GEMINI_MODEL, "gemini-3-flash-preview") ?: "gemini-3-flash-preview") }
    var temperature by remember { mutableStateOf(prefs.getString(PreferenceKeys.TEMPERATURE, "0.9") ?: "0.9") }
    var maxTokens by remember { mutableFloatStateOf(prefs.getInt(PreferenceKeys.MAX_TOKENS, 1024).toFloat()) }
    var topP by remember { mutableStateOf(prefs.getString(PreferenceKeys.TOP_P, "1.0") ?: "1.0") }
    var topK by remember { mutableFloatStateOf(prefs.getInt(PreferenceKeys.TOP_K, 1).toFloat()) }

    val tempValues = listOf("0.0", "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini AI") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { PreferenceCategory("API") }
            item { EditTextPreferenceItem(title = "API Key", value = apiKey, isPassword = true, onValueChange = { apiKey = it; prefs.edit { putString(PreferenceKeys.GEMINI_API_KEY, it) } }) }
            item { EditTextPreferenceItem(title = "Model", value = model, onValueChange = { model = it; prefs.edit { putString(PreferenceKeys.GEMINI_MODEL, it) } }) }
            
            item { PreferenceCategory("Model Parameters") }
            item { ListPreferenceItem(title = "Temperature", summary = temperature, options = tempValues.map { it to it }, selectedKey = temperature, onSelected = { temperature = it; prefs.edit { putString(PreferenceKeys.TEMPERATURE, it) } }) }
            item { ListPreferenceItem(title = "Top P", summary = topP, options = tempValues.map { it to it }, selectedKey = topP, onSelected = { topP = it; prefs.edit { putString(PreferenceKeys.TOP_P, it) } }) }
            item { SliderPreferenceItem(title = "Top K", value = topK, valueRange = 1f..60f, steps = 60, onValueChange = { topK = it; prefs.edit { putInt(PreferenceKeys.TOP_K, it.toInt()) } }) }
            item { SliderPreferenceItem(title = "Max Tokens", value = maxTokens, valueRange = 60f..2048f, steps = 100, onValueChange = { maxTokens = it; prefs.edit { putInt(PreferenceKeys.MAX_TOKENS, it.toInt()) } }) }
        }
    }
}

// UI Components
@Composable
fun PreferenceCategory(title: String) {
    Text(text = title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
fun PreferenceItem(
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    iconVector: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null || iconVector != null) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SwitchPreferenceItem(title: String, summary: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (summary != null) {
                    Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun SliderPreferenceItem(title: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "$title: ${value.toInt()}", style = MaterialTheme.typography.bodyLarge)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@Composable
fun ListPreferenceItem(title: String, summary: String, options: List<Pair<String, String>>, selectedKey: String, onSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    PreferenceItem(title = title, summary = summary, onClick = { showDialog = true })
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth().clickable { onSelected(key); showDialog = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = (key == selectedKey), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun MultiChoicePreferenceItem(
    title: String,
    summary: String,
    options: List<Pair<String, String>>,
    selectedKeys: Set<String>,
    onSelected: (Set<String>) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var tempKeys by remember { mutableStateOf(selectedKeys) }

    PreferenceItem(title = title, summary = summary, onClick = {
        tempKeys = selectedKeys
        showDialog = true
    })

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                LazyColumn {
                    items(count = options.size) { index ->
                        val (key, label) = options[index]
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                tempKeys = if (tempKeys.contains(key)) tempKeys - key else tempKeys + key
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = tempKeys.contains(key), onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelected(tempKeys)
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EditTextPreferenceItem(title: String, summary: String? = null, value: String, isPassword: Boolean = false, onValueChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var tempValue by remember { mutableStateOf(value) }
    var passwordVisible by remember { mutableStateOf(false) }

    val currentSummary = summary ?: if (value.isEmpty()) "Not set" else if (isPassword) "••••••••" else value
    
    PreferenceItem(title = title, summary = currentSummary, onClick = { tempValue = value; showDialog = true })
    
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = tempValue,
                    onValueChange = { tempValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (title == "Repositories") 5 else 1,
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon = if (isPassword) {
                        {
                            val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(icon, contentDescription = "Toggle password visibility")
                            }
                        }
                    } else null
                )
            },
            confirmButton = {
                TextButton(onClick = { onValueChange(tempValue); showDialog = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun openStorageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    } else {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
}
