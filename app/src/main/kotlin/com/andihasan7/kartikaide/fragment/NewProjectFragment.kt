/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.andihasan7.kartikaide.R
import andihasan7.kartikaide.common.BaseBindingFragment
import com.andihasan7.kartikaide.databinding.FragmentNewProjectBinding
import com.andihasan7.kartikaide.model.ProjectViewModel
import andihasan7.kartikaide.project.Language
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.FileUtil
import andihasan7.kartikaide.rewrite.util.PermissionUtils
import com.andihasan7.kartikaide.util.CommonUtils
import java.io.File
import java.io.IOException

class NewProjectFragment : BaseBindingFragment<FragmentNewProjectBinding>() {
    private val viewModel: ProjectViewModel by activityViewModels()
    private var selectedPath: String? = null

    private val directoryPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val path = CommonUtils.getPathFromTreeUri(uri)
                if (path != null) {
                    selectedPath = path
                    binding.etProjectPath.setText(path)
                    binding.locationToggle.check(R.id.btn_custom)
                } else {
                    Toast.makeText(requireContext(), "Could not resolve path from URI", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun getViewBinding() = FragmentNewProjectBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Set default selection based on permission
        if (PermissionUtils.hasStoragePermission(requireContext())) {
            val kartikaDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
            if (!kartikaDir.exists()) kartikaDir.mkdirs()
            selectedPath = kartikaDir.absolutePath
            binding.etProjectPath.setText(selectedPath)
            binding.locationToggle.check(R.id.btn_external)
        } else {
            selectedPath = FileUtil.projectDir.absolutePath
            binding.etProjectPath.setText(selectedPath)
            binding.locationToggle.check(R.id.btn_internal)
        }

        binding.locationToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_internal -> {
                        selectedPath = FileUtil.projectDir.absolutePath
                        binding.etProjectPath.setText(selectedPath)
                    }
                    R.id.btn_external -> {
                        if (!PermissionUtils.hasStoragePermission(requireContext())) {
                            checkStoragePermission()
                            // Temporarily switch back if permission denied/canceled? 
                            // Or just wait for them to grant it.
                        }
                        val kartikaDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
                        selectedPath = kartikaDir.absolutePath
                        binding.etProjectPath.setText(selectedPath)
                    }
                    R.id.btn_custom -> {
                        if (!PermissionUtils.hasStoragePermission(requireContext())) {
                            checkStoragePermission()
                        }
                    }
                }
            }
        }

        binding.etProjectPath.setOnClickListener {
            if (!PermissionUtils.hasStoragePermission(requireContext())) {
                checkStoragePermission()
            } else {
                directoryPickerLauncher.launch(null)
            }
        }

        binding.projectPath.setEndIconOnClickListener {
            if (!PermissionUtils.hasStoragePermission(requireContext())) {
                checkStoragePermission()
            } else {
                directoryPickerLauncher.launch(null)
            }
        }

        binding.btnCreate.setOnClickListener {
            val projectName = binding.projectName.editText?.text.toString()
            val packageName = binding.packageName.editText?.text.toString()

            if (projectName.isEmpty()) {
                binding.projectName.error = "Project name cannot be empty"
                return@setOnClickListener
            }

            if (packageName.isEmpty()) {
                binding.packageName.error = "Package name cannot be empty"
                return@setOnClickListener
            }

            if (!projectName.matches(Regex("^[а-яА-Яa-zA-Z0-9]+$"))) {
                binding.projectName.error = "Project name contains invalid characters"
                return@setOnClickListener
            }

            if (!packageName.matches(Regex("^[a-zA-Z0-9.]+$"))) {
                binding.packageName.error = "Package name contains invalid characters"
                return@setOnClickListener
            }

            if (selectedPath == null) {
                Toast.makeText(requireContext(), "Please select a project location", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Final check for external storage permission
            if (isExternalPath(File(selectedPath!!)) && !PermissionUtils.hasStoragePermission(requireContext())) {
                checkStoragePermission()
                return@setOnClickListener
            }

            val language = when {
                binding.useKotlin.isChecked -> Language.Kotlin
                else -> Language.Java
            }

            val success = createProject(language, projectName, packageName)

            if (success) {
                parentFragmentManager.commit {
                    remove(this@NewProjectFragment)
                }
            }
        }
    }

    private fun checkStoragePermission() {
        if (PermissionUtils.hasStoragePermission(requireContext())) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Permission Required")
                .setMessage("KartikaIDE needs access to all files to manage projects in external storage.")
                .setPositiveButton("Grant") { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:${requireContext().packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            requestPermissions(permissions, 100)
        }
    }

    private fun createProject(
        language: Language,
        name: String,
        packageName: String
    ): Boolean {
        return try {
            val projectName = name.replace("\\.", "")
            val projectRoot = File(selectedPath!!).resolve(projectName)
            
            if (!projectRoot.exists() && !projectRoot.mkdirs()) {
                throw IOException("Could not create project directory. Check permissions.")
            }
            
            val project = Project(root = projectRoot, language = language)
            
            val srcMain = projectRoot.resolve("src").resolve("main")
            val srcDir = if (language is Language.Kotlin) {
                srcMain.resolve("kotlin")
            } else {
                srcMain.resolve("java")
            }
            srcDir.mkdirs()

            val packageDir = srcDir.resolve(packageName.replace('.', File.separatorChar))
            packageDir.mkdirs()
            
            val mainFile = packageDir.resolve("Main.${language.extension}")
            mainFile.createMainFile(language, packageName)
            
            // Create resources dir too
            srcMain.resolve("resources").mkdirs()

            viewModel.loadProjects()
            navigateToEditorFragment(project)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(
                requireView(),
                "Failed to create project: ${e.message}",
                Snackbar.LENGTH_LONG
            ).show()
            false
        }
    }

    private fun navigateToEditorFragment(project: Project) {
        parentFragmentManager.commit {
            add(R.id.fragment_container, EditorFragment.newInstance(project))
            addToBackStack(null)
        }
    }

    private fun File.createMainFile(language: Language, packageName: String) {
        val content = language.classFileContent(name = "Main", packageName = packageName)
        writeText(content)
    }

    private fun isExternalPath(file: File): Boolean {
        return file.absolutePath.startsWith(Environment.getExternalStorageDirectory().absolutePath)
    }
}
