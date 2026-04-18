/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import andihasan7.kartikaide.project.Language
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.FileUtil
import andihasan7.kartikaide.rewrite.util.PermissionUtils
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.model.ProjectViewModel
import com.andihasan7.kartikaide.util.CommonUtils
import java.io.File
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    viewModel: ProjectViewModel,
    onBackClick: () -> Unit,
    onProjectCreated: (Project) -> Unit
) {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var selectedPath by remember { mutableStateOf("") }
    var useKotlin by remember { mutableStateOf(true) }
    var locationType by remember { mutableIntStateOf(0) } // Default to 0: Internal

    var projectNameError by remember { mutableStateOf<String?>(null) }
    var packageNameError by remember { mutableStateOf<String?>(null) }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            val kartikaDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
            if (!kartikaDir.exists()) kartikaDir.mkdirs()
            selectedPath = kartikaDir.absolutePath
            locationType = 1
        } else {
            Toast.makeText(context, "Storage permission is required to save projects externally", Toast.LENGTH_SHORT).show()
        }
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            val kartikaDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
            if (!kartikaDir.exists()) kartikaDir.mkdirs()
            selectedPath = kartikaDir.absolutePath
            locationType = 1
        } else {
            Toast.makeText(context, "All files access is required to save projects externally", Toast.LENGTH_SHORT).show()
        }
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = CommonUtils.getPathFromTreeUri(it)
            if (path != null) {
                selectedPath = path
                locationType = 2
            }
        }
    }

    // Initial path setup: Default to Storage if permission is already granted
    LaunchedEffect(Unit) {
        if (PermissionUtils.hasStoragePermission(context)) {
            val kartikaDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
            if (!kartikaDir.exists()) kartikaDir.mkdirs()
            selectedPath = kartikaDir.absolutePath
            locationType = 1
        } else {
            selectedPath = FileUtil.projectDir.absolutePath
            locationType = 0
        }
    }

    fun requestStoragePermission(onSuccess: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            } else {
                onSuccess()
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                storagePermissionLauncher.launch(missing.toTypedArray())
            } else {
                onSuccess()
            }
        }
    }

    fun validate(): Boolean {
        var isValid = true
        if (projectName.isEmpty()) {
            projectNameError = "Project name cannot be empty"
            isValid = false
        } else if (!projectName.matches(Regex("^[a-zA-Z0-9]+$"))) {
            projectNameError = "Invalid characters in project name"
            isValid = false
        } else {
            projectNameError = null
        }

        if (packageName.isEmpty()) {
            packageNameError = "Package name cannot be empty"
            isValid = false
        } else if (!packageName.matches(Regex("^[a-z0-9.]+$"))) {
            packageNameError = "Invalid characters in package name"
            isValid = false
        } else {
            packageNameError = null
        }
        return isValid
    }

    fun handleCreate() {
        if (!validate()) return

        val language = if (useKotlin) Language.Kotlin else Language.Java
        try {
            val finalProjectName = projectName.replace(".", "")
            val projectRoot = File(selectedPath).resolve(finalProjectName)
            
            if (!projectRoot.exists() && !projectRoot.mkdirs()) {
                throw IOException("Could not create project directory")
            }
            
            val project = Project(root = projectRoot, language = language)
            
            val srcMain = projectRoot.resolve("src").resolve("main")
            val srcDir = if (language is Language.Kotlin) srcMain.resolve("kotlin") else srcMain.resolve("java")
            srcDir.mkdirs()

            val packageDir = srcDir.resolve(packageName.replace('.', File.separatorChar))
            packageDir.mkdirs()
            
            val mainFile = packageDir.resolve("Main.${language.extension}")
            mainFile.writeText(language.classFileContent("Main", packageName))
            
            srcMain.resolve("resources").mkdirs()

            viewModel.loadProjects()
            onProjectCreated(project)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_project)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text(stringResource(R.string.enter_project_name)) },
                modifier = Modifier.fillMaxWidth(),
                isError = projectNameError != null,
                supportingText = projectNameError?.let { { Text(it) } },
                singleLine = true
            )

            OutlinedTextField(
                value = packageName,
                onValueChange = { packageName = it },
                label = { Text(stringResource(R.string.enter_package_name)) },
                modifier = Modifier.fillMaxWidth(),
                isError = packageNameError != null,
                supportingText = packageNameError?.let { { Text(it) } },
                singleLine = true
            )

            Text("Storage Location", style = MaterialTheme.typography.labelLarge)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = locationType == 0,
                    onClick = { 
                        locationType = 0
                        selectedPath = FileUtil.projectDir.absolutePath
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text("Internal")
                }
                SegmentedButton(
                    selected = locationType == 1,
                    onClick = { 
                        requestStoragePermission {
                            val kartikaDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
                            if (!kartikaDir.exists()) kartikaDir.mkdirs()
                            selectedPath = kartikaDir.absolutePath
                            locationType = 1
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text("Storage")
                }
                SegmentedButton(
                    selected = locationType == 2,
                    onClick = { 
                        requestStoragePermission {
                            directoryPickerLauncher.launch(null)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text("Custom")
                }
            }

            OutlinedTextField(
                value = selectedPath,
                onValueChange = { },
                readOnly = true,
                label = { Text("Selected Path") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { 
                        requestStoragePermission {
                            directoryPickerLauncher.launch(null)
                        }
                    }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Select Folder")
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.use_kotlin_template),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = useKotlin,
                    onCheckedChange = { useKotlin = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { handleCreate() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.create_project))
            }
        }
    }
}
