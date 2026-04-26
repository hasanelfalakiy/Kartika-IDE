/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.editor

import android.util.Log
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.lang.styling.inlayHint.TextInlayHint
import io.github.rosemoe.sora.lang.styling.inlayHint.InlayHint
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

    init {
        try {
            // Register the default text inlay hint renderer to the editor
            editor.registerInlayHintRenderer(TextInlayHintRenderer.DefaultInstance)
        } catch (e: Throwable) {
            Log.e("InlayHintManager", "Failed to register TextInlayHintRenderer", e)
        }
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
        
        updateHints()
    }

    /**
     * Updates the inlay hints asynchronously.
     */
    fun updateHints() {
        val file = lastFile ?: return
        val project = lastProject ?: return
        val text = editor.text.toString()
        val extension = file.extension

        updateJob?.cancel()
        updateJob = scope.launch {
            // Debounce updates for better performance
            delay(500)
            
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
                                // Use parseKtFile because it provides accurate offsets
                                symbols.addAll(KtNavigationProvider.parseKtFile(ktFile))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore analysis errors
            }

            if (symbols.isEmpty()) {
                withContext(Dispatchers.Main) {
                    editor.setInlayHints(null)
                }
                return@launch
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
                                // Extract name part (handle supertypes/interfaces in the string)
                                val rawName = item.name.substringBefore('(')
                                    .substringBefore(" :")
                                    .substringBefore(" implements")
                                    .substringBefore(" ->")
                                    .trim()
                                    .substringAfterLast('.')
                                
                                val prefix = when (item.kind) {
                                    NavigationProvider.NavigationItemKind.CLASS -> {
                                        val mods = item.modifiers.lowercase()
                                        val nameLow = item.name.lowercase()
                                        when {
                                            mods.contains("interface") || nameLow.contains("interface") -> "interface "
                                            mods.contains("enum") || nameLow.contains("enum") -> "enum "
                                            mods.contains("object") || nameLow.contains("object") -> "object "
                                            mods.contains("data") -> "data class "
                                            else -> "class "
                                        }
                                    }
                                    NavigationProvider.NavigationItemKind.METHOD -> "fun "
                                    else -> ""
                                }

                                if (rawName.isNotEmpty()) {
                                    val label = " // $prefix$rawName"
                                    // Add hint to the container at the closing brace position
                                    container.add(TextInlayHint(pos.line, pos.column, label))
                                }
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    // Ensure colors are set in the scheme
                    val scheme = editor.colorScheme
                    if (scheme.getColor(EditorColorScheme.TEXT_INLAY_HINT_BACKGROUND) == 0) {
                        scheme.setColor(EditorColorScheme.TEXT_INLAY_HINT_BACKGROUND, 0x33888888)
                    }
                    if (scheme.getColor(EditorColorScheme.TEXT_INLAY_HINT_FOREGROUND) == 0) {
                        scheme.setColor(EditorColorScheme.TEXT_INLAY_HINT_FOREGROUND, 0x000000)
                    }

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
        try {
            editor.text.removeContentListener(this)
        } catch (e: Exception) {}
        lastFile = null
        lastProject = null
    }
}
