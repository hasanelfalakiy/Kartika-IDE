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
            
            // Update existing fragments paths BEFORE changing IDs
            if (files.size == ids.size) {
                ids.forEachIndexed { index, oldId ->
                    fragments[oldId]?.updateFile(files[index])
                }
            }

            val newIds = files.map { file ->
                if (project != null && file.absolutePath.startsWith(project.root.absolutePath)) {
                    file.absolutePath.removePrefix(project.root.absolutePath).hashCode().toLong()
                } else {
                    file.absolutePath.hashCode().toLong()
                }
            }
            
            ids = newIds
            
            // Sinkronisasi ulang fragmen yang masih ada
            fragments.forEach { (id, fragment) ->
                val index = ids.indexOf(id)
                if (index != -1) {
                    val file = files[index]
                    val oldPath = fragment.file.absolutePath
                    fragment.updateFile(file)
                    // Jika path berubah (karena rename), muat ulang teks untuk menampilkan package baru
                    if (oldPath != file.absolutePath) {
                        fragment.reloadText()
                    }
                }
            }
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

        private lateinit var eventReceiver: SubscriptionReceipt<ContentChangeEvent>
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
        }

        private fun setupSymbols() {
            binding.apply {
                if (Prefs.disableSymbolsView) {
                    symbolViewContainer.visibility = View.GONE
                    return
                }
                symbolViewContainer.visibility = View.VISIBLE
                symbolView.bindEditor(editor)
                symbolView.addSymbols(
                    arrayOf("→", "(", ")", "{", "}", "[", "]", ";", ",", "."),
                    arrayOf("\t", "(", ")", "{", "}", "[", "]", ";", ",", ".")
                )
            }
        }

        private fun setEditorLanguage() {
            val project = ProjectHandler.getProject() ?: return
            when (file.extension) {
                "java" -> {
                    editor.setEditorLanguage(TsLanguageJava.getInstance(editor, project, file))
                    eventReceiver = editor.subscribeEvent(EditorDiagnosticsMarker(editor, file, project))
                }
                "kt", "kts" -> {
                    if (editor.editorLanguage is KotlinLanguage) return
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
                setupSymbols()
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            if (::editor.isInitialized) {
                setColorScheme()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            release()
        }

        fun release() {
            if (::editor.isInitialized) {
                hideWindows()
                if (::eventReceiver.isInitialized) eventReceiver.unsubscribe()
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
