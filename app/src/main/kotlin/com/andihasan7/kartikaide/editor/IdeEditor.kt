/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.editor

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import com.google.android.material.R
import com.google.android.material.color.MaterialColors
import com.google.common.collect.ImmutableSet
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorDiagnosticTooltipWindow
import andihasan7.kartikaide.common.Prefs
import com.andihasan7.kartikaide.editor.language.TsLanguageJava
import com.andihasan7.kartikaide.extension.setCompletionLayout
import com.andihasan7.kartikaide.extension.setFont
import android.graphics.Typeface
import android.view.View

class IdeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : CodeEditor(context, attrs, defStyleAttr, defStyleRes) {

    private val ignoredPairEnds: Set<Char> = ImmutableSet.of(
        ')', ']', '}', '"', '>', '\'', ';'
    )

    private var currentFontPath: String? = null
    private var currentThemeName: String? = null

    init {
        setCompletionLayout()
        setTooltipImprovements()
        
        // PERFORMANCE: Pengaturan dasar untuk kecepatan scroll
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isHardwareAcceleratedDrawAllowed = true
        overScrollMode = View.OVER_SCROLL_NEVER
        
        // Mematikan animasi kursor yang berlebihan jika perlu untuk kelancaran
        // isAnimationEnabled = false // Opsional jika masih lag
        
        setInterceptParentHorizontalScrollIfNeeded(true)
    }

    fun updateSettings() {
        // Cek font agar tidak re-load jika sama (Operasi IO berat)
        if (currentFontPath != Prefs.editorFont) {
            setFont()
            currentFontPath = Prefs.editorFont
        }

        inputType = createInputFlags()
        updateNonPrintablePaintingFlags()
        
        if (textSizePx != Prefs.editorFontSize) {
            setTextSize(Prefs.editorFontSize)
        }
        
        if (tabWidth != Prefs.tabSize) {
            tabWidth = Prefs.tabSize
        }

        isLigatureEnabled = Prefs.useLigatures
        isWordwrap = Prefs.wordWrap
        setScrollBarEnabled(Prefs.scrollbarEnabled)
        isHardwareAcceleratedDrawAllowed = Prefs.hardwareAcceleration
        isLineNumberEnabled = Prefs.lineNumbers
        props.deleteEmptyLineFast = Prefs.quickDelete
        props.stickyScroll = Prefs.stickyScroll

        updateColorScheme()
    }

    private fun updateColorScheme() {
        val themeName = if (Prefs.editorColorScheme != "darcula") {
            Prefs.editorColorScheme
        } else {
            if ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) "darcula" else "QuietLight"
        }

        // Cek tema agar tidak re-load registry (Operasi berat)
        if (currentThemeName != themeName) {
            try {
                ThemeRegistry.getInstance().setTheme(themeName)
                colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                currentThemeName = themeName
                invalidate()
            } catch (e: Exception) {
                // Fallback
            }
        }
    }

    override fun commitText(text: CharSequence, applyAutoIndent: Boolean) {
        if (text.length == 1) {
            val currentChar = text.toString().getOrNull(cursor.left)
            val c = text[0]
            if (ignoredPairEnds.contains(c) && c == currentChar) {
                setSelection(cursor.leftLine, cursor.leftColumn + 1)
                return
            }
        }
        super.commitText(text, applyAutoIndent)
    }

    fun appendText(text: String): Int {
        val content = getText()
        if (lineCount <= 0) {
            return 0
        }
        var col = content.getColumnCount(lineCount - 1)
        if (col < 0) {
            col = 0
        }
        content.insert(lineCount - 1, col, text)
        return lineCount - 1
    }

    private fun updateNonPrintablePaintingFlags() {
        val flags = (FLAG_DRAW_WHITESPACE_LEADING
                or FLAG_DRAW_WHITESPACE_INNER
                or FLAG_DRAW_WHITESPACE_FOR_EMPTY_LINE)
        nonPrintablePaintingFlags = if (Prefs.nonPrintableCharacters) flags else 0
    }

    private fun createInputFlags(): Int {
        return EditorInfo.TYPE_CLASS_TEXT or
                EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    private fun setTooltipImprovements() {
        getComponent(EditorDiagnosticTooltipWindow::class.java).apply {
            setSize(500, 100)
            parentView.setBackgroundColor(
                MaterialColors.getColor(
                    context,
                    R.attr.colorErrorContainer,
                    null
                )
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateColorScheme()
        if (editorLanguage is TsLanguageJava) {
            (editorLanguage as TsLanguageJava).onConfigurationChanged()
        }
    }
}
