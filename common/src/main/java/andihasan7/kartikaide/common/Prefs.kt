/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package andihasan7.kartikaide.common

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager

/**
 * A utility object to access shared preferences easily.
 */
object Prefs {

    private lateinit var prefs: SharedPreferences

    /**
     * Initializes shared preferences.
     * @param context The context of the application.
     */
    fun init(context: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    val isInitialized: Boolean
        get() = this::prefs.isInitialized

    val appTheme: String
        get() = prefs.getString(PreferenceKeys.APP_THEME, "auto") ?: "auto"

    val useFastJarFs: Boolean
        get() = prefs.getBoolean(PreferenceKeys.COMPILER_USE_FJFS, true)

    val stickyScroll: Boolean
        get() = prefs.getBoolean(PreferenceKeys.STICKY_SCROLL, true)

    val useLigatures: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_LIGATURES_ENABLE, true)

    val wordWrap: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_WORDWRAP_ENABLE, false)

    val scrollbarEnabled: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_SCROLLBAR_SHOW, true)

    val hardwareAcceleration: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_HW_ENABLE, true)

    val nonPrintableCharacters: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_NON_PRINTABLE_SYMBOLS_SHOW, false)

    val ktfmtStyle: String
        get() = prefs.getString(PreferenceKeys.FORMATTER_KTFMT_STYLE, "google") ?: "google"

    val ktfmtMaxWidth: Int
        get() = prefs.getInt(PreferenceKeys.KTFMT_MAX_WIDTH, 100)

    val ktfmtBlockIndent: Int
        get() = prefs.getInt(PreferenceKeys.KTFMT_BLOCK_INDENT, 4)

    val ktfmtContinuationIndent: Int
        get() = prefs.getInt(PreferenceKeys.KTFMT_CONTINUATION_INDENT, 4)

    val ktfmtRemoveUnusedImports: Boolean
        get() = prefs.getBoolean(PreferenceKeys.KTFMT_REMOVE_UNUSED_IMPORTS, true)

    val ktfmtManageTrailingCommas: Boolean
        get() = prefs.getBoolean(PreferenceKeys.KTFMT_MANAGE_TRAILING_COMMAS, false)

    val googleJavaFormatOptions: Set<String>?
        get() = prefs.getStringSet(PreferenceKeys.FORMATTER_GJF_OPTIONS, setOf())

    val googleJavaFormatStyle: String
        get() = prefs.getString(PreferenceKeys.FORMATTER_GJF_STYLE, "aosp") ?: "aosp"
    val lineNumbers: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_LINE_NUMBERS_SHOW, true)

    val useSpaces: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_USE_SPACES, false)

    val tabSize: Int
        get() = prefs.getInt(PreferenceKeys.EDITOR_TAB_SIZE, 4)

    val bracketPairAutocomplete: Boolean
        get() = prefs.getBoolean(PreferenceKeys.BRACKET_PAIR_AUTOCOMPLETE, true)

    val quickDelete: Boolean
        get() = prefs.getBoolean(PreferenceKeys.QUICK_DELETE, false)

    val javacFlags: String
        get() = prefs.getString(PreferenceKeys.COMPILER_JAVAC_FLAGS, "") ?: ""

    val compilerJavaVersion: Int
        get() = Integer.parseInt(prefs.getString(PreferenceKeys.COMPILER_JAVA_VERSIONS, "17") ?: "17")

    val kotlinVersion: String
        get() = prefs.getString(PreferenceKeys.COMPILER_KOTLIN_VERSION, "2.1") ?: "2.1"

    val analyticsEnabled: Boolean
        get() = prefs.getBoolean("analytics_preference", true)

    val doubleClickClose: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_DOUBLE_CLICK_CLOSE, false)

    val disableSymbolsView: Boolean
        get() = prefs.getBoolean(PreferenceKeys.DISABLE_SYMBOLS_VIEW, false)

    val customSymbols: String
        get() = prefs.getString(PreferenceKeys.EDITOR_CUSTOM_SYMBOLS, "→,\t,(,),{,},[,],;,.,,") ?: "→,\t,(,),{,},[,],;,.,,"

    val experimentalJavaCompletion: Boolean
        get() = prefs.getBoolean(PreferenceKeys.EDITOR_EXP_JAVA_COMPLETION, false)

    val gitUsername: String
        get() = prefs.getString(PreferenceKeys.GIT_USERNAME, "") ?: ""

    val gitEmail: String
        get() = prefs.getString(PreferenceKeys.GIT_EMAIL, "") ?: ""

    val gitApiKey: String
        get() = prefs.getString(PreferenceKeys.GIT_API_KEY, "") ?: ""

    val kotlinRealtimeErrors: Boolean
        get() = prefs.getBoolean(PreferenceKeys.KOTLIN_REALTIME_ERRORS, false)

    val experimentsEnabled: Boolean
        get() = prefs.getBoolean("experiments_enabled", false)


    val editorFont: String
        get() = prefs.getString(PreferenceKeys.EDITOR_FONT, "") ?: ""

    val editorColorScheme: String
        get() = prefs.getString(PreferenceKeys.EDITOR_COLOR_SCHEME, "darcula") ?: "darcula"

    val repositories: String
        get() = prefs.getString("repos", "") ?: """
            Maven Central: https://repo1.maven.org/maven2
            Google Maven: https://maven.google.com
            Jitpack: https://jitpack.io
            Sonatype Snapshots: https://s01.oss.sonatype.org/content/repositories/snapshots
            JCenter: https://jcenter.bintray.com
        """.trimIndent()

    val pluginRepository: String
        get() = prefs.getString(
            PreferenceKeys.PLUGIN_REPOSITORY,
            "https://raw.githubusercontent.com/hasanelfalakiy/plugins-repo/main/plugins.json"
        ) ?: "https://raw.githubusercontent.com/hasanelfalakiy/plugins-repo/main/plugins.json"

    val editorFontSize: Float
        get() = runCatching {
            prefs.getString(PreferenceKeys.EDITOR_FONT_SIZE, "14")?.toFloatOrNull()?.coerceIn(1f, 32f) ?: 14f
        }.getOrElse { 16f }

    val geminiApiKey: String
        get() = prefs.getString(PreferenceKeys.GEMINI_API_KEY, "") ?: ""

    val geminiModel: String
        get() = prefs.getString(PreferenceKeys.GEMINI_MODEL, "gemini-3-flash-preview") ?: "gemini-3-flash-preview"

    val temperature: Float
        get() = runCatching {
            prefs.getString(PreferenceKeys.TEMPERATURE, "0.9")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.9f
        }.getOrElse { 0.9f }

    val topP: Float
        get() = runCatching {
            prefs.getString(PreferenceKeys.TOP_P, "1.0")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1.0f
        }.getOrElse { 1.0f }

    val topK: Float
        get() = prefs.getInt(PreferenceKeys.TOP_K, 40).coerceIn(1, 100).toFloat()

    val maxTokens: Int
        get() = prefs.getInt(PreferenceKeys.MAX_TOKENS, 1024).coerceIn(60, 2048)

    val clientName: String
        get() = prefs.getString("client_name", null)?.replace(" ", "") ?: Build.ID

    var lastExtractedVersion: Int
        get() = prefs.getInt("last_extracted_version", -1)
        set(value) = prefs.edit().putInt("last_extracted_version", value).apply()
}
