/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment.settings

import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.andihasan7.kartikaide.MainActivity
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.util.PreferenceKeys
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import de.Maxr1998.modernpreferences.helpers.onSelectionChange
import de.Maxr1998.modernpreferences.helpers.singleChoice
import de.Maxr1998.modernpreferences.helpers.switch
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import io.github.andihasan.colorktx.ColorKtx
import io.github.andihasan.colorktx.Themes
import java.util.Locale

class AppearanceSettings(private val activity: MainActivity) : SettingsProvider {

    override fun provideSettings(builder: PreferenceScreen.Builder) {
        val colorKtx = ColorKtx.getInstance(activity)

        builder.apply {
            icon = ResourcesCompat.getDrawable(
                activity.resources,
                R.drawable.baseline_color_lens_24,
                activity.theme
            )

            // 1. App Theme (System UI / Dark Mode)
            val themeItems = listOf(
                SelectionItem("SYSTEM", activity.getString(R.string.pref_theme_auto)),
                SelectionItem("LIGHT", activity.getString(R.string.pref_theme_light)),
                SelectionItem("DARK", activity.getString(R.string.pref_theme_dark))
            )

            singleChoice(PreferenceKeys.APP_THEME, themeItems) {
                title = activity.getString(R.string.system_ui)
                initialSelection = when (colorKtx.themeMode) {
                    1 -> "LIGHT"
                    2 -> "DARK"
                    else -> "SYSTEM"
                }
                icon = ResourcesCompat.getDrawable(
                    activity.resources,
                    R.drawable.outline_dark_mode_24,
                    activity.theme
                )
                onSelectionChange { newValue ->
                    colorKtx.themeMode = when (newValue) {
                        "LIGHT" -> 1
                        "DARK" -> 2
                        else -> 0
                    }
                    activity.recreate()
                    true
                }
            }

            // 2. Color Theme (ColorKTX integration)
            val colorThemes = Themes.values().map { theme ->
                val title = theme.name.lowercase(Locale.ROOT).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
                SelectionItem(theme.name, title)
            }
            
            singleChoice(PreferenceKeys.COLOR_THEME, colorThemes) {
                title = "Color Theme"
                initialSelection = colorKtx.staticTheme.name
                icon = ResourcesCompat.getDrawable(
                    activity.resources,
                    R.drawable.baseline_color_lens_24,
                    activity.theme
                )
                onSelectionChange { newValue ->
                    colorKtx.staticTheme = Themes.valueOf(newValue)
                    activity.recreate()
                    true
                }
            }

            // 3. Pure Black
            switch(PreferenceKeys.PURE_BLACK) {
                title = "Pure Black Background"
                defaultValue = colorKtx.isTrueBlack
                icon = ResourcesCompat.getDrawable(
                    activity.resources,
                    R.drawable.outline_invert_colors_24,
                    activity.theme
                )
                onCheckedChange { checked ->
                    colorKtx.isTrueBlack = checked
                    activity.recreate()
                    true
                }
            }

            // 4. Dynamic Theme (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                switch(PreferenceKeys.DYNAMIC_COLOR) {
                    title = "Dynamic Theme"
                    summary = "Use colors from your wallpaper"
                    defaultValue = colorKtx.isDynamicTheme
                    icon = ResourcesCompat.getDrawable(
                        activity.resources,
                        R.drawable.baseline_color_lens_24,
                        activity.theme
                    )
                    onCheckedChange { checked ->
                        colorKtx.isDynamicTheme = checked
                        activity.recreate()
                        true
                    }
                }
            }
        }
    }
}
