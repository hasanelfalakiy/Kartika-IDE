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

    val fragments = mutableMapOf<Long, CodeEditorFragment>()
    private var ids: List<Long> by Delegates.observable(emptyList()) { _, old, new ->
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return old.size
            }

            override fun getNewListSize(): Int {
                return new.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition] == new[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition] == new[newItemPosition]
            }
        }).dispatchUpdatesTo(this)
    }

    init {
        fileViewModel.files.observe(fragment.viewLifecycleOwner) { files ->
            ids = files.map { it.hashCode().toLong() }
        }
        System.loadLibrary("android-tree-sitter")
    }

    override fun getItemCount(): Int {
        return ids.size
    }

    override fun createFragment(position: Int): Fragment {
        Log.d("EditorAdapter", "createFragment: $position")
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

    fun removeItem(position: Int) {
        val id = ids.getOrNull(position) ?: return
        fragments.remove(id)?.apply {
            release()
            parentFragmentManager.beginTransaction().remove(this).commit()
        }
    }

    override fun getItemId(position: Int): Long {
        return ids[position]
    }

    override fun containsItem(itemId: Long): Boolean {
        return ids.contains(itemId)
    }

    fun saveAll() {
        fragments.values.forEach { it.save() }
    }

    fun releaseAll() {
        fragments.values.forEach { it.release() }
    }

    fun refreshSettings() {
        fragments.values.forEach { it.refreshSettings() }
    }

    class CodeEditorFragment : Fragment() {

        private lateinit var eventReceiver: SubscriptionReceipt<ContentChangeEvent>
        private lateinit var binding: EditorFragmentBinding
        lateinit var editor: IdeEditor
        val file by lazy { requireArguments().getSerializable("file") as File }

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
                    arrayOf(
                        "→",
                        "(",
                        ")",
                        "{",
                        "}",
                        "[",
                        "]",
                        ";",
                        ",",
                        ".",
                    ),
                    arrayOf(
                        "\t",
                        "(",
                        ")",
                        "{",
                        "}",
                        "[",
                        "]",
                        ";",
                        ",",
                        ".",
                    )
                )
            }
        }

        fun getHashCode(): Long {
            return file.hashCode().toLong()
        }

        private fun setEditorLanguage() {
            val project = ProjectHandler.getProject() ?: return
            when (file.extension) {
                "java" -> {
                    editor.setEditorLanguage(
                        TsLanguageJava.getInstance(
                            editor,
                            project,
                            file
                        )
                    )
                    eventReceiver =
                        editor.subscribeEvent(EditorDiagnosticsMarker(editor, file, project))
                }

                "kt", "kts" -> {
                    if (editor.editorLanguage is KotlinLanguage) return
                    editor.setEditorLanguage(
                        KotlinLanguage(
                            editor,
                            project,
                            file
                        )
                    )
                }

                "class" -> {
                    editor.setEditorLanguage(
                        TsLanguageJava.getInstance(
                            editor,
                            project,
                            file
                        )
                    )
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

            println("Reading file: ${file.absolutePath}")
            editor.setText(file.readText())
        }

        fun save() {
            if (!::editor.isInitialized) return
            if (file.extension == "class") return
            file.writeText(editor.text.toString())
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
