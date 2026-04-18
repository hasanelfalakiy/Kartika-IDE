/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.PermissionUtils
import com.andihasan7.kartikaide.model.ProjectViewModel
import andihasan7.kartikaide.rewrite.util.FileUtil
import andihasan7.kartikaide.rewrite.util.compressToZip
import andihasan7.kartikaide.rewrite.util.unzip
import com.andihasan7.kartikaide.util.CommonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.documentfile.provider.DocumentFile
import andihasan7.kartikaide.project.Language
import andihasan7.kartikaide.common.Prefs
import dev.pranav.jgit.tasks.Credentials
import dev.pranav.jgit.tasks.cloneRepository
import java.io.OutputStream
import java.io.PrintWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectViewModel,
    onProjectClick: (Project) -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onNewProjectClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val internalProjects by viewModel.internalProjects.collectAsStateWithLifecycle()
    val externalProjects by viewModel.externalProjects.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    var showFabMenu by remember { mutableStateOf(false) }
    var projectToBackup by remember { mutableStateOf<Project?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var newProjectName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var showGitCloneDialog by remember { mutableStateOf(false) }
    var gitUrl by remember { mutableStateOf("") }
    var isCloning by remember { mutableStateOf(false) }
    var cloneLog by remember { mutableStateOf("") }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            viewModel.loadProjects()
            pendingPermissionAction?.invoke()
        }
        pendingPermissionAction = null
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionUtils.hasStoragePermission(context)) {
            viewModel.loadProjects()
            pendingPermissionAction?.invoke()
        }
        pendingPermissionAction = null
    }

    fun checkStoragePermission(onGranted: () -> Unit) {
        if (PermissionUtils.hasStoragePermission(context)) {
            onGranted()
        } else {
            pendingPermissionAction = onGranted
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission Required") },
            text = { Text("KartikaIDE needs access to all files to manage projects. Please grant the permission.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            manageStorageLauncher.launch(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            manageStorageLauncher.launch(intent)
                        }
                    } else {
                        storagePermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                }) { Text("Grant") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showPermissionDialog = false
                    pendingPermissionAction = null
                }) { Text("Cancel") }
            }
        )
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            val root = projectToBackup?.root ?: return@let
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it).use { output ->
                        root.compressToZip(output!!)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup successful", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                isImporting = true
                try {
                    val name = DocumentFile.fromSingleUri(context, it)?.name?.substringBeforeLast(".") ?: "ImportedProject"
                    val target = FileUtil.projectDir.resolve(name)
                    if (target.exists()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Project already exists", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        target.mkdirs()
                        context.contentResolver.openInputStream(it)?.unzip(target)
                        viewModel.loadProjects()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    isImporting = false
                }
            }
        }
    }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = CommonUtils.getPathFromTreeUri(it)
            if (path != null) {
                val file = File(path)
                if (file.isDirectory) {
                    val language = if (file.walkTopDown().maxDepth(5).any { f -> f.path.contains("src${File.separator}main${File.separator}java") }) Language.Java else Language.Kotlin
                    onProjectClick(Project(file, language))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    if (projectToDelete != null) {
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project") },
            text = { Text("Are you sure you want to delete '${projectToDelete?.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    projectToDelete?.root?.deleteRecursively()
                    viewModel.loadProjects()
                    projectToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = projectToRename ?: return@TextButton
                    if (newProjectName.isNotEmpty() && newProjectName != p.name) {
                        val newRoot = p.root.parentFile!!.resolve(newProjectName)
                        if (p.root.renameTo(newRoot)) {
                            viewModel.loadProjects()
                            showRenameDialog = false
                        } else {
                            Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showGitCloneDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCloning) showGitCloneDialog = false },
            title = { Text("Git Clone") },
            text = {
                Column {
                    if (isCloning) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(8.dp))
                        Text(cloneLog, style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = gitUrl,
                            onValueChange = { gitUrl = it },
                            label = { Text("Git URL") },
                            placeholder = { Text("https://github.com/user/repo.git") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                if (!isCloning) {
                    TextButton(onClick = {
                        if (Prefs.gitUsername.isEmpty() || Prefs.gitApiKey.isEmpty()) {
                            Toast.makeText(context, "Please set Git credentials in settings", Toast.LENGTH_LONG).show()
                            return@TextButton
                        }
                        
                        val repoName = gitUrl.substringAfterLast("/").removeSuffix(".git")
                        val folder = FileUtil.projectDir.resolve(repoName)
                        
                        if (folder.exists()) {
                            Toast.makeText(context, "Project directory already exists", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        
                        isCloning = true
                        cloneLog = "Starting clone..."
                        scope.launch(Dispatchers.IO) {
                            try {
                                folder.cloneRepository(
                                    gitUrl,
                                    PrintWriter(object : OutputStream() {
                                        override fun write(b: Int) {
                                            scope.launch(Dispatchers.Main) {
                                                cloneLog += b.toChar()
                                            }
                                        }
                                        override fun write(b: ByteArray, off: Int, len: Int) {
                                            scope.launch(Dispatchers.Main) {
                                                cloneLog += String(b, off, len)
                                            }
                                        }
                                    }),
                                    Credentials(Prefs.gitUsername, Prefs.gitApiKey)
                                )
                                viewModel.loadProjects()
                                withContext(Dispatchers.Main) {
                                    isCloning = false
                                    showGitCloneDialog = false
                                    Toast.makeText(context, "Clone successful", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isCloning = false
                                    Toast.makeText(context, "Clone failed: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }) { Text("Clone") }
                }
            },
            dismissButton = {
                if (!isCloning) {
                    TextButton(onClick = { showGitCloneDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kartika IDE") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onAboutClick) {
                        Icon(Icons.Default.Info, contentDescription = "About", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(
                    visible = showFabMenu,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FabMenuItem("New Project", Icons.Default.Create) {
                            showFabMenu = false
                            onNewProjectClick()
                        }
                        FabMenuItem("Import Zip", Icons.Default.FileDownload) {
                            showFabMenu = false
                            importLauncher.launch(arrayOf("application/zip"))
                        }
                        FabMenuItem("Git Clone", Icons.Default.Share) {
                            showFabMenu = false
                            showGitCloneDialog = true
                        }
                        FabMenuItem("Open Folder", Icons.Default.FolderOpen) {
                            showFabMenu = false
                            checkStoragePermission { directoryPickerLauncher.launch(null) }
                        }
                    }
                }
                
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    val rotation by animateFloatAsState(if (showFabMenu) 45f else 0f)
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.rotate(rotation))
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.loadProjects() },
            modifier = Modifier.padding(padding)
        ) {
            if (isImporting || (isLoading && internalProjects.isEmpty() && externalProjects.isEmpty())) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (internalProjects.isEmpty() && externalProjects.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No projects found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (internalProjects.isNotEmpty()) {
                        item { ProjectHeader("Internal Storage") }
                        items(internalProjects) { project ->
                            ProjectItem(
                                project = project,
                                onClick = { onProjectClick(project) },
                                onRename = { 
                                    projectToRename = project
                                    newProjectName = project.name
                                    showRenameDialog = true
                                },
                                onDelete = { projectToDelete = project },
                                onBackup = { 
                                    projectToBackup = project
                                    backupLauncher.launch("${project.name}.zip")
                                }
                            )
                        }
                    }

                    if (externalProjects.isNotEmpty()) {
                        item { ProjectHeader("External Storage (KartikaIDE)") }
                        items(externalProjects) { project ->
                            ProjectItem(
                                project = project,
                                onClick = { 
                                    checkStoragePermission { onProjectClick(project) }
                                },
                                onRename = { 
                                    checkStoragePermission {
                                        projectToRename = project
                                        newProjectName = project.name
                                        showRenameDialog = true
                                    }
                                },
                                onDelete = { 
                                    checkStoragePermission { projectToDelete = project }
                                },
                                onBackup = { 
                                    checkStoragePermission {
                                        projectToBackup = project
                                        backupLauncher.launch("${project.name}.zip")
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun FabMenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 4.dp)) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 4.dp) {
            Text(text = label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
fun ProjectHeader(title: String) {
    Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectItem(project: Project, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onBackup: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { showMenu = true })) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = project.root.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Backup (Zip)") }, onClick = { showMenu = false; onBackup() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}
