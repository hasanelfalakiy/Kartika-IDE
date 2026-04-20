/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.adapter

import andihasan7.kartikaide.build.Javap
import andihasan7.kartikaide.common.Prefs
import andihasan7.kartikaide.editor.analyzers.EditorDiagnosticsMarker
import com.andihasan7.kartikaide.fragment.EditorFragment
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.andihasan7.kartikaide.databinding.EditorFragmentBinding
import com.andihasan7.kartikaide.editor.IdeEditor
import com.andihasan7.kartikaide.editor.language.KotlinLanguage
import com.andihasan7.kartikaide.editor.language.TsLanguageJava
import com.andihasan7.kartikaide.extension.setFont
import com.andihasan7.kartikaide.model.FileViewModel
import com.andihasan7.kartikaide.util.ProjectHandler
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SubscriptionReceipt
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.subscribeEvent
import io.github.rosemoe.sora.text.ContentListener
import io.github.rosemoe.sora.text.Content
import java.io.File
import kotlin.properties.Delegates

class EditorAdapter(val fragment: Fragment, val fileViewModel: FileViewModel) :
    FragmentStateAdapter(fragment) {

    private val fragments = mutableMapOf<Long, CodeEditorFragment>()
    private var ids: List<Long> by Delegates.observable(emptyList()) { _, old, new ->
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = new.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean = old[oldPos] == new[newPos]
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean = old[oldPos] == new[newPos]
        }).dispatchUpdatesTo(this)
    }

    init {
        fileViewModel.files.observe(fragment.viewLifecycleOwner) { files ->
            val project = ProjectHandler.getProject()
            val oldIds = ids
            val newIds = files.map { file ->
                if (project != null && file.absolutePath.startsWith(project.root.absolutePath)) {
                    file.absolutePath.removePrefix(project.root.absolutePath).hashCode().toLong()
                } else {
                    file.absolutePath.hashCode().toLong()
                }
            }

            val newFragments = mutableMapOf<Long, CodeEditorFragment>()
            if (files.size == oldIds.size) {
                oldIds.forEachIndexed { index, oldId ->
                    fragments[oldId]?.let { frag ->
                        val newId = newIds[index]
                        val newFile = files[index]
                        val oldPath = frag.file.absolutePath
                        
                        frag.updateFile(newFile)
                        newFragments[newId] = frag
                        
                        if (oldPath != newFile.absolutePath) {
                            frag.reloadText()
                        }
                    }
                }
            } else {
                newIds.forEachIndexed { index, newId ->
                    val frag = fragments[newId] ?: fragments.values.find { it.file.absolutePath == files[index].absolutePath }
                    if (frag != null) {
                        frag.updateFile(files[index])
                        newFragments[newId] = frag
                    }
                }
            }
            
            fragments.clear()
            fragments.putAll(newFragments)
            ids = newIds
        }
        System.loadLibrary("android-tree-sitter")
    }

    override fun getItemCount(): Int = ids.size

    override fun createFragment(position: Int): Fragment {
        val id = getItemId(position)
        val fragment = CodeEditorFragment().apply {
            arguments = Bundle().apply {
                putSerializable("file", fileViewModel.files.value!![position])
            }
        }
        fragments[id] = fragment
        return fragment
    }

    fun getItem(position: Int): CodeEditorFragment? {
        val id = ids.getOrNull(position) ?: return null
        return fragments[id]
    }

    override fun getItemId(position: Int): Long = ids[position]

    override fun containsItem(itemId: Long): Boolean = ids.contains(itemId)

    fun saveAll() {
        fragments.values.forEach { it.save() }
    }

    fun releaseAll() {
        fragments.values.forEach { it.release() }
    }

    fun refreshSettings() {
        fragments.values.forEach { it.refreshSettings() }
    }

    fun reloadAll() {
        fragments.values.forEach { it.reloadText() }
    }

    class CodeEditorFragment : Fragment() {

        private var diagReceiver: SubscriptionReceipt<ContentChangeEvent>? = null
        private lateinit var binding: EditorFragmentBinding
        lateinit var editor: IdeEditor
        
        private var _file: File? = null
        val file: File get() = _file ?: (requireArguments().getSerializable("file") as File)

        fun updateFile(newFile: File) {
            if (_file?.absolutePath != newFile.absolutePath) {
                _file = newFile
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            binding = EditorFragmentBinding.inflate(inflater, container, false)
            editor = binding.editor
            return binding.root
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setupSymbols()
            setText()
            editor.setFont()
            setColorScheme()
            editor.isDisableSoftKbdIfHardKbdAvailable = true
            setEditorLanguage()
            attachUndoListener()
        }

        private fun attachUndoListener() {
            // Re-attach listener ke objek Content yang baru
            editor.text.addContentListener(object : ContentListener {
                override fun beforeReplace(content: Content) {}
                
                override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, inserted: CharSequence) {
                    notifyStatus()
                }
                
                override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deleted: CharSequence) {
                    notifyStatus()
                }

                private fun notifyStatus() {
                    view?.post {
                        (parentFragment as? EditorFragment)?.updateUndoRedoStatus()
                    }
                }
            })
        }

        private fun setupSymbols() {
            binding.apply {
                if (Prefs.disableSymbolsView) {
                    symbolViewContainer.visibility = View.GONE
                    return
                }
                symbolViewContainer.visibility = View.VISIBLE
                symbolView.bindEditor(editor)
                symbolView.removeSymbols()
                
                val rawSymbols = Prefs.customSymbols.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                val symbols = rawSymbols.map { if (it == "→") "\t" else it }.toTypedArray()
                val displays = rawSymbols.toTypedArray()
                
                symbolView.addSymbols(displays, symbols)
            }
        }

        private fun setEditorLanguage() {
            val project = ProjectHandler.getProject() ?: return
            when (file.extension) {
                "java" -> {
                    editor.setEditorLanguage(TsLanguageJava.getInstance(editor, project, file))
                    diagReceiver?.unsubscribe()
                    diagReceiver = editor.subscribeEvent(EditorDiagnosticsMarker(editor, file, project))
                }
                "kt", "kts" -> {
                    editor.setEditorLanguage(KotlinLanguage(editor, project, file))
                }
                "class" -> {
                    editor.setEditorLanguage(TsLanguageJava.getInstance(editor, project, file))
                }
                else -> {
                    editor.setEditorLanguage(EmptyLanguage())
                }
            }
        }

        private fun setColorScheme() {
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
        }

        private fun setText() {
            val isBinary = file.extension == "class"
            if (isBinary) {
                editor.setText(Javap.disassemble(file.absolutePath))
                return
            }
            if (file.exists()) {
                editor.setText(file.readText())
            }
        }

        fun reloadText() {
            if (::editor.isInitialized && file.exists()) {
                val isBinary = file.extension == "class"
                if (isBinary) {
                    editor.setText(Javap.disassemble(file.absolutePath))
                } else {
                    editor.setText(file.readText())
                }
                
                // PENTING: setText mengganti objek Content, jadi pasang ulang listener-nya
                attachUndoListener()
                editor.isUndoEnabled = true
                
                view?.post {
                    (parentFragment as? EditorFragment)?.updateUndoRedoStatus()
                }
            }
        }

        fun save() {
            if (!::editor.isInitialized) return
            if (file.extension == "class") return
            
            try {
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                file.writeText(editor.text.toString())
            } catch (e: Exception) {
                Log.e("CodeEditorFragment", "Failed to save file: ${file.absolutePath}", e)
            }
        }

        fun refreshSettings() {
            if (::editor.isInitialized) {
                editor.updateSettings()
                setEditorLanguage()
                setupSymbols()
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            if (::editor.isInitialized) {
                setColorScheme()
                setEditorLanguage()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            release()
        }

        fun release() {
            if (::editor.isInitialized) {
                hideWindows()
                diagReceiver?.unsubscribe()
                editor.release()
            }
        }

        fun hideWindows() {
            if (::editor.isInitialized) {
                editor.hideEditorWindows()
                editor.hideAutoCompleteWindow()
            }
        }
    }
}
