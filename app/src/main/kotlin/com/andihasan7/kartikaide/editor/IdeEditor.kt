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

class IdeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : CodeEditor(context, attrs, defStyleAttr, defStyleRes) {

    private val ignoredPairEnds: Set<Char> = ImmutableSet.of(
        ')', ']', '}', '"', '>', '\'', ';'
    )

    init {
        setCompletionLayout()
        setTooltipImprovements()
        updateSettings()
        setInterceptParentHorizontalScrollIfNeeded(true)
        
        // Aktifkan UndoManager di awal
        isUndoEnabled = true
    }

    fun updateSettings() {
        setFont()
        inputType = createInputFlags()
        updateNonPrintablePaintingFlags()
        updateTextSize()
        updateTabSize()
        isLigatureEnabled = Prefs.useLigatures
        isWordwrap = Prefs.wordWrap
        setScrollBarEnabled(Prefs.scrollbarEnabled)
        isHardwareAcceleratedDrawAllowed = Prefs.hardwareAcceleration
        isLineNumberEnabled = Prefs.lineNumbers
        setPinLineNumber(Prefs.pinLineNumber)
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

        ThemeRegistry.getInstance().setTheme(themeName)
        colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        invalidate()
    }

    override fun commitText(text: CharSequence, applyAutoIndent: Boolean) {
        // Optimized auto-pair skipping
        if (text.length == 1 && !cursor.isSelected) {
            val c = text[0]
            if (ignoredPairEnds.contains(c)) {
                val left = cursor.left
                val editorText = getText()
                if (left >= 0 && left < editorText.length) {
                    val pos = editorText.indexer.getCharPosition(left)
                    if (editorText.charAt(pos.line, pos.column) == c) {
                        val line = cursor.leftLine
                        val col = cursor.leftColumn
                        if (col + 1 <= editorText.getColumnCount(line)) {
                            setSelection(line, col + 1)
                            return
                        }
                    }
                }
            }
        }
        super.commitText(text, applyAutoIndent)
    }

    fun appendText(text: String): Int {
        val content = getText()
        val line = content.lineCount - 1
        if (line < 0) {
            return 0
        }
        val col = content.getColumnCount(line)
        content.insert(line, col, text)
        return line
    }

    private fun updateTextSize() {
        setTextSize(Prefs.editorFontSize)
    }

    private fun updateTabSize() {
        tabWidth = Prefs.tabSize
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
