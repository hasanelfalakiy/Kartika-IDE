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

/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.andihasan7.kartikaide.R
import andihasan7.kartikaide.build.BuildReporter
import andihasan7.kartikaide.common.BaseBindingFragment
import com.andihasan7.kartikaide.compile.Compiler
import com.andihasan7.kartikaide.databinding.FragmentCompileInfoBinding
import andihasan7.kartikaide.project.Project
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.andihasan7.kartikaide.databinding.DialogCompileLogBinding
import com.andihasan7.kartikaide.util.ProjectHandler

/**
 * A dialog fragment for displaying information about the compilation process.
 */
class CompileInfoFragment : DialogFragment() {
    private var _binding: DialogCompileLogBinding? = null
    private val binding get() = _binding!!
    val project: Project = ProjectHandler.getProject()
        ?: throw IllegalStateException("No project set")

    val reporter by lazy {
        BuildReporter { report ->
            if (report.message.isEmpty()) return@BuildReporter

            lifecycleScope.launch {
                val text = binding.logEditor.text
                text.insert(
                    text.lineCount - 1,
                    0,
                    "${report.kind}: ${report.message}\n"
                )
            }
        }
    }
    val compiler: Compiler = Compiler(project, reporter)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCompileLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.logEditor.apply {
            setEditorLanguage(TextMateLanguage.create("source.build", false))
            editable = false
            isLineNumberEnabled = false
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("logs", binding.logEditor.text.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                compiler.compile()
                if (reporter.buildSuccess) {
                    withContext(Dispatchers.Main) {
                        navigateToProjectOutputFragment()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val text = binding.logEditor.text
                    text.insert(
                        text.lineCount - 1,
                        0,
                        "Build failed: ${e.message}\n"
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Set dialog height to 85% of screen height to ensure IdeEditor with weight=1 can expand
        val displayMetrics = resources.displayMetrics
        val height = (displayMetrics.heightPixels * 0.85).toInt()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
    }

    override fun onDestroyView() {
        binding.logEditor.release()
        super.onDestroyView()
        _binding = null
    }

    private fun navigateToProjectOutputFragment() {
        dismiss()
        parentFragmentManager.commit {
            add(R.id.fragment_container, ProjectOutputFragment())
            addToBackStack(null)
        }
    }
}
