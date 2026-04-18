/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.util

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ThemeUtils {
    private val _themeState = MutableStateFlow("auto")
    val themeState: StateFlow<String> = _themeState

    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val savedTheme = prefs.getString(PreferenceKeys.APP_THEME, "auto") ?: "auto"
        _themeState.value = savedTheme
        applyThemeInternal(context, savedTheme)
    }

    fun setTheme(context: Context, theme: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(PreferenceKeys.APP_THEME, theme).apply()
        _themeState.value = theme
        applyThemeInternal(context, theme)
    }

    private fun applyThemeInternal(context: Context, theme: String) {
        val mode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        
        // Use setDefaultNightMode which is more stable for runtime changes
        AppCompatDelegate.setDefaultNightMode(mode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            val systemMode = when (theme) {
                "light" -> UiModeManager.MODE_NIGHT_NO
                "dark" -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            uiModeManager?.setApplicationNightMode(systemMode)
        }
    }
}
