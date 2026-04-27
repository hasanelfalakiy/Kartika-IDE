/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.editor

import android.graphics.Color
import android.util.Log
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHintsContainer
import io.github.rosemoe.sora.graphics.inlayHint.TextInlayHintRenderer
import andihasan7.kartikaide.project.Project
import dev.pranav.navigation.NavigationProvider
import dev.pranav.navigation.KtNavigationProvider
import com.andihasan7.kartikaide.util.FileFactoryProvider
import com.andihasan7.kartikaide.editor.language.KotlinLanguage
import java.io.File
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent
import io.github.rosemoe.sora.widget.subscribeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import kotlinx.coroutines.*

/**
 * Manages inlay hints for the editor, showing labels at the end of closing braces
 * for classes, methods, objects, enums, interfaces, and data classes.
 */
class InlayHintManager(private val editor: CodeEditor) : ContentListener {

    private var lastFile: File? = null
    private var lastProject: Project? = null
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var colorSchemeReceiver: SubscriptionReceipt<ColorSchemeUpdateEvent>? = null

    init {
        try {
            // Register the default text inlay hint renderer to the editor
            editor.registerInlayHintRenderer(TextInlayHintRenderer.DefaultInstance)
            updateHintColors()
            
            // Listen for theme changes to keep inlay hints consistent
            colorSchemeReceiver = editor.subscribeEvent(ColorSchemeUpdateEvent::class.java) { _, _ ->
                editor.post { updateHintColors() }
            }
        } catch (e: Throwable) {
            Log.e("InlayHintManager", "Failed to initialize InlayHintManager", e)
        }
    }

    /**
     * Updates the colors of the inlay hints to match the current theme.
     * This ensures the hints are always visible regardless of theme changes.
     */
    fun updateHintColors() {
        val scheme = editor.colorScheme
        val commentColor = scheme.getColor(EditorColorScheme.COMMENT)
        val normalTextColor = scheme.getColor(EditorColorScheme.TEXT_NORMAL)
        
        // Foreground color: Prefer comment color, then normal text, fallback to light gray
        var fg = if (commentColor != 0) commentColor else normalTextColor
        if (fg == 0) fg = Color.LTGRAY
        
        // Ensure foreground is fully opaque for clarity
        fg = fg or 0xFF000000.toInt()
        
        // Background color: Try line number background or whole background
        var bg = scheme.getColor(EditorColorScheme.LINE_NUMBER_BACKGROUND)
        if (bg == 0) bg = scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)
        
        if (bg == 0) {
            // Fallback for dark themes
            bg = Color.DKGRAY
        }
        
        // Apply a consistent alpha to the background for better contrast
        val finalBg = Color.argb(160, Color.red(bg), Color.green(bg), Color.blue(bg))
        
        scheme.setColor(EditorColorScheme.TEXT_INLAY_HINT_BACKGROUND, finalBg)
        scheme.setColor(EditorColorScheme.TEXT_INLAY_HINT_FOREGROUND, fg)
        
        // Request a redraw to apply color changes to existing hints
        editor.invalidate()
    }

    /**
     * Initializes the manager with the current file and project.
     */
    fun setup(file: File, project: Project) {
        lastFile = file
        lastProject = project
        
        // Ensure we are listening to the current text content
        editor.text.removeContentListener(this)
        editor.text.addContentListener(this)
        
        // Update colors immediately during setup
        updateHintColors()
        updateHints()
    }

    /**
     * Updates the inlay hints asynchronously.
     */
    fun updateHints() {
        val file = lastFile ?: return
        val text = editor.text.toString()
        val extension = file.extension

        updateJob?.cancel()
        updateJob = scope.launch {
            // Debounce updates to avoid excessive analysis
            delay(200)
            
            val symbols = mutableListOf<NavigationProvider.NavigationItem>()
            try {
                when (extension) {
                    "java" -> {
                        val psiJavaFile = FileFactoryProvider.getPsiJavaFile(file.name, text)
                        psiJavaFile.classes.forEach { psiClass ->
                            symbols.addAll(NavigationProvider.extractMethodsAndFields(psiClass))
                        }
                    }
                    "kt", "kts" -> {
                        val language = editor.editorLanguage
                        if (language is KotlinLanguage) {
                            val ktFile = language.kotlinEnvironment.kotlinFiles[file.absolutePath]?.kotlinFile
                            if (ktFile != null) {
                                // Use parseKtFile for accurate offsets
                                symbols.addAll(KtNavigationProvider.parseKtFile(ktFile))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore analysis errors
            }

            if (!isActive) return@launch

            withContext(Dispatchers.Main) {
                // Re-apply colors before setting hints
                updateHintColors()
                
                if (symbols.isEmpty()) {
                    editor.setInlayHints(null)
                    return@withContext
                }

                val container = InlayHintsContainer()
                val indexer = editor.text.indexer

                symbols.forEach { item ->
                    if (item.kind == NavigationProvider.NavigationItemKind.CLASS || 
                        item.kind == NavigationProvider.NavigationItemKind.METHOD) {
                        
                        val endOffset = item.endPosition
                        if (endOffset > 0 && endOffset <= text.length) {
                            // Check if the character at endOffset - 1 is a closing brace '}'
                            val charBefore = text.getOrNull(endOffset - 1)
                            if (charBefore == '}') {
                                val pos = indexer.getCharPosition(endOffset)
                                val startPos = indexer.getCharPosition(item.startPosition)
                                
                                // Show hint only if the block spans more than 2 lines
                                if (pos.line - startPos.line > 2) {
                                    var rawName = item.name.substringBefore('(')
                                        .substringBefore(" :")
                                        .substringBefore(" implements")
                                        .substringBefore(" ->")
                                        .trim()
                                        .substringAfterLast('.')
                                    
                                    // Fix for "companion object" which might not have a proper name in symbols
                                    if (rawName.isEmpty() && item.modifiers.contains("companion", ignoreCase = true)) {
                                        rawName = "companion object"
                                    }

                                    val prefix = when (item.kind) {
                                        NavigationProvider.NavigationItemKind.CLASS -> {
                                            val mods = item.modifiers.lowercase()
                                            val content = item.name.lowercase()
                                            when {
                                                mods.contains("interface") || content.contains("interface") -> "interface "
                                                mods.contains("enum") || content.contains("enum") -> "enum "
                                                mods.contains("companion") || content.contains("companion object") -> "" // already handled in name
                                                mods.contains("object") || content.contains("object") -> "object "
                                                mods.contains("data") || content.contains("data class") -> "data class "
                                                else -> "class "
                                            }
                                        }
                                        NavigationProvider.NavigationItemKind.METHOD -> "fun "
                                        else -> ""
                                    }

                                    if (rawName.isNotEmpty()) {
                                        val label = "$prefix$rawName"
                                        container.add(TextInlayHint(pos.line, pos.column, label))
                                    }
                                }
                            }
                        }
                    }
                }

                try {
                    editor.setInlayHints(container)
                } catch (e: Exception) {
                    Log.e("InlayHintManager", "Failed to set inlay hints", e)
                }
            }
        }
    }

    override fun beforeReplace(content: Content) {}

    override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, inserted: CharSequence) {
        updateHints()
    }

    override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deleted: CharSequence) {
        updateHints()
    }

    /**
     * Releases resources and removes listeners.
     */
    fun release() {
        updateJob?.cancel()
        scope.cancel()
        colorSchemeReceiver?.unsubscribe()
        try {
            editor.text.removeContentListener(this)
        } catch (e: Exception) {}
        lastFile = null
        lastProject = null
    }
}
