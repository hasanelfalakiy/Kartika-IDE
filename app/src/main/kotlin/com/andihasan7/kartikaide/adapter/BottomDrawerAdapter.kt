/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.andihasan7.kartikaide.databinding.LayoutLogViewBinding
import com.andihasan7.kartikaide.editor.IdeEditor
import com.andihasan7.kartikaide.extension.setFont
import io.github.rosemoe.sora.langs.textmate.IdeLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry

class BottomDrawerAdapter : RecyclerView.Adapter<BottomDrawerAdapter.LogViewHolder>() {

    private val logContents = mutableMapOf<Int, StringBuilder>()
    private val viewHolders = mutableMapOf<Int, LogViewHolder>()

    init {
        logContents[0] = StringBuilder() // Build Log
        logContents[1] = StringBuilder() // Output
        logContents[2] = StringBuilder() // Search Result
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = LayoutLogViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.logEditor.apply {
            isLineNumberEnabled = true // Diaktifkan agar sesuai screenshot
            isWordwrap = false
            isEditable = false
            setFont()
            // WAJIB: Set ColorScheme agar warna TextMate muncul
            colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        }
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        viewHolders[position] = holder
        val content = logContents[position].toString()
        val editor = holder.binding.logEditor
        editor.setText(content)
        
        // Sembunyikan pesan kosong jika ada konten
        holder.binding.tvEmptyLog.visibility = if (content.isEmpty()) View.VISIBLE else View.GONE

        // Pastikan kursor berada di akhir saat bind agar output terbaru terlihat
        editor.post {
            val lineCount = editor.text.lineCount
            if (lineCount > 0) {
                val lastLine = lineCount - 1
                editor.setSelection(lastLine, editor.text.getColumnCount(lastLine))
            }
        }

        // Terapkan grammar source.build untuk Build Log dan Output
        if (position == 0 || position == 1) {
            val scopeName = "source.build"
            try {
                val registry = GrammarRegistry.getInstance()
                val grammar = registry.findGrammar(scopeName)
                if (grammar != null) {
                    editor.setEditorLanguage(
                        IdeLanguage(
                            grammar,
                            registry.findLanguageConfiguration(scopeName),
                            registry,
                            ThemeRegistry.getInstance()
                        )
                    )
                } else {
                    editor.setEditorLanguage(TextMateLanguage.create(scopeName, false))
                }
            } catch (e: Exception) {
                Log.e("BottomDrawerAdapter", "Failed to set language source.build", e)
                editor.setEditorLanguage(TextMateLanguage.create(scopeName, false))
            }
            editor.isEditable = (position == 1)
        } else {
            editor.isEditable = false
            editor.setEditorLanguage(null)
        }
    }

    override fun getItemCount(): Int = 3

    fun appendLog(position: Int, text: String, addNewLine: Boolean = true) {
        if (!logContents.containsKey(position)) return
        val suffix = if (addNewLine) "\n" else ""
        logContents[position]?.append(text)?.append(suffix)
        viewHolders[position]?.let { holder ->
            val editor = holder.binding.logEditor
            
            // Cek apakah kursor berada di akhir sebelum append untuk auto-scroll/posisi input
            val content = editor.text
            val isAtEnd = editor.cursor.leftLine == content.lineCount - 1 && 
                          editor.cursor.leftColumn == content.getColumnCount(editor.cursor.leftLine)
            
            editor.appendText(text + suffix)
            holder.binding.tvEmptyLog.visibility = View.GONE
            
            // Fix masalah kursor (Scanner/readLine) & Auto-scroll:
            // Pindah ke akhir teks jika sebelumnya di akhir, atau jika ini adalah Build Log (pos 0)
            if (isAtEnd || position == 0) {
                val lineCount = editor.text.lineCount
                if (lineCount > 0) {
                    editor.post {
                        val lastLine = lineCount - 1
                        val lastCol = editor.text.getColumnCount(lastLine)
                        editor.setSelection(lastLine, lastCol)
                    }
                }
            }
        }
    }

    fun clearLog(position: Int) {
        logContents[position]?.setLength(0)
        viewHolders[position]?.let { holder ->
            holder.binding.logEditor.setText("")
            holder.binding.logEditor.post {
                holder.binding.logEditor.setSelection(0, 0)
            }
            holder.binding.tvEmptyLog.visibility = View.VISIBLE
        }
    }
    
    fun getEditor(position: Int): IdeEditor? {
        return viewHolders[position]?.binding?.logEditor
    }

    class LogViewHolder(val binding: LayoutLogViewBinding) : RecyclerView.ViewHolder(binding.root)
}
