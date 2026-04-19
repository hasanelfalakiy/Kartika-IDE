/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.ui.screens

import andihasan7.kartikaide.project.Project
import com.andihasan7.kartikaide.model.EditorViewModel
import com.andihasan7.kartikaide.util.ProjectHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andihasan7.kartikaide.editor.IdeEditor
import com.andihasan7.kartikaide.editor.language.KotlinLanguage
import com.andihasan7.kartikaide.editor.language.TsLanguageJava
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    project: Project,
    onBackClick: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: EditorViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Use a consistent snapshot of the editor state
    val editorState = viewModel.editorState
    val openFiles = editorState.openFiles
    val selectedFile = editorState.selectedFile
    val selectedTabIndex = editorState.selectedIndex
    
    // PagerState for visual transitions
    val pagerState = rememberPagerState(pageCount = { openFiles.size })
    
    // Track current active editor for global actions
    var activeEditor by remember { mutableStateOf<IdeEditor?>(null) }
    
    // Run Logic State
    var showMainSelector by remember { mutableStateOf(false) }
    var mainFunctions by remember { mutableStateOf<List<File>>(emptyList()) }
    var showBuildLog by remember { mutableStateOf(false) }
    var buildLogText by remember { mutableStateOf("") }

    // Initialize project
    LaunchedEffect(project) {
        viewModel.setProject(project)
        ProjectHandler.setProject(project)
    }

    // Sync Pager position when selectedTabIndex changes
    LaunchedEffect(selectedTabIndex, openFiles.size) {
        if (openFiles.isNotEmpty() && selectedTabIndex in 0 until openFiles.size) {
            if (pagerState.currentPage != selectedTabIndex) {
                if (selectedTabIndex < pagerState.pageCount) {
                    pagerState.scrollToPage(selectedTabIndex)
                }
            }
        }
    }
    
    // Sync back to ViewModel if pager is scrolled manually
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage < openFiles.size) {
            val fileAtPage = openFiles[pagerState.currentPage]
            if (viewModel.selectedFile?.absolutePath != fileAtPage.absolutePath) {
                viewModel.selectedFile = fileAtPage
            }
        }
    }

    // Function to save current file
    val saveCurrentFile = {
        activeEditor?.let { editor ->
            val file = viewModel.selectedFile
            if (file != null) {
                val content = editor.text.toString()
                scope.launch(Dispatchers.IO) {
                    try {
                        file.writeText(content)
                        Log.d("EditorScreen", "Manually saved: ${file.name}")
                    } catch (e: Exception) {
                        Log.e("EditorScreen", "Failed manual save", e)
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FileTreeView(
                    project = project,
                    onFileClick = { file ->
                        // Save current before switching
                        saveCurrentFile()
                        viewModel.openFile(file)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { /* Teks file dihapus sesuai permintaan */ },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        EditorActionButtons(
                            onUndo = { activeEditor?.undo() },
                            onRedo = { activeEditor?.redo() },
                            onCompile = {
                                saveCurrentFile()
                                // Logic runner: cari main
                                scope.launch(Dispatchers.IO) {
                                    val mains = findMainFunctions(project.root)
                                    withContext(Dispatchers.Main) {
                                        if (mains.isEmpty()) {
                                            buildLogText = "No main function found in project."
                                            showBuildLog = true
                                        } else if (mains.size == 1) {
                                            runMain(mains[0]) { log ->
                                                buildLogText = log
                                                showBuildLog = true
                                            }
                                        } else {
                                            mainFunctions = mains
                                            showMainSelector = true
                                        }
                                    }
                                }
                            },
                            onAction = { action ->
                                when(action) {
                                    "Settings" -> onNavigateToSettings()
                                    "Git" -> { /* TODO: Open Git */ }
                                }
                            }
                        )
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (openFiles.isNotEmpty()) {
                    EditorTabs(
                        files = openFiles,
                        selectedIndex = selectedTabIndex,
                        onTabSelected = { file ->
                            saveCurrentFile()
                            viewModel.selectedFile = file
                        },
                        onCloseTab = { file ->
                            saveCurrentFile()
                            viewModel.closeFile(file)
                        }
                    )
                    
                    EditorPager(
                        files = openFiles,
                        pagerState = pagerState,
                        onEditorActive = { activeEditor = it }
                    )
                } else {
                    EmptyEditorPlaceholder()
                }
            }
        }
    }

    // Main Function Selector Dialog
    if (showMainSelector) {
        AlertDialog(
            onDismissRequest = { showMainSelector = false },
            title = { Text("Select Main Function") },
            text = {
                LazyColumn {
                    items(mainFunctions.size) { index ->
                        val file = mainFunctions[index]
                        ListItem(
                            headlineContent = { Text(file.name) },
                            supportingContent = { Text(file.absolutePath.removePrefix(project.root.absolutePath)) },
                            modifier = Modifier.clickable {
                                showMainSelector = false
                                runMain(file) { log ->
                                    buildLogText = log
                                    showBuildLog = true
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMainSelector = false }) { Text("Cancel") }
            }
        )
    }

    // Build Log Dialog
    if (showBuildLog) {
        AlertDialog(
            onDismissRequest = { showBuildLog = false },
            title = { Text("Build & Run Log") },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn {
                        item {
                            Text(
                                buildLogText,
                                style = MaterialTheme.typography.bodySmall,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBuildLog = false }) { Text("OK") }
            }
        )
    }
}

// Helper to find main functions
private fun findMainFunctions(root: File): List<File> {
    val result = mutableListOf<File>()
    root.walkTopDown().forEach { file ->
        if (file.isFile && (file.extension == "kt" || file.extension == "java")) {
            val content = file.readText()
            if (file.extension == "kt") {
                if (content.contains("fun main(")) result.add(file)
            } else if (file.extension == "java") {
                if (content.contains("public static void main")) result.add(file)
            }
        }
    }
    return result
}

// Mock runner function
private fun runMain(file: File, onFinished: (String) -> Unit) {
    // This should call your compiler/runner module
    onFinished("Building ${file.name}...\n\n[INFO] Starting execution of ${file.name}\n\nHello, World!\n\n[SUCCESS] Execution finished.")
}

@Composable
fun EditorTabs(
    files: List<File>,
    selectedIndex: Int,
    onTabSelected: (File) -> Unit,
    onCloseTab: (File) -> Unit
) {
    if (files.isEmpty()) return

    // Ensure index is always valid for the CURRENT list size during this recomposition
    val safeIndex = selectedIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))

    // Wrapping ScrollableTabRow in a key(files.size) forces the component to be fully re-created 
    // when the number of tabs changes. This is a robust workaround for the common 
    // IndexOutOfBoundsException in TabRow's internal SubcomposeLayout.
    key(files.size) {
        ScrollableTabRow(
            selectedTabIndex = safeIndex,
            edgePadding = 0.dp,
            divider = {}
        ) {
            files.forEachIndexed { index, file ->
                // Use key for each Tab to help Compose track state correctly
                key(file.absolutePath) {
                    val isSelected = index == safeIndex
                    Tab(
                        selected = isSelected,
                        onClick = { onTabSelected(file) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    file.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onCloseTab(file) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EditorPager(
    files: List<File>,
    pagerState: PagerState,
    onEditorActive: (IdeEditor?) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
        key = { page -> if (page < files.size) files[page].absolutePath else "fallback_$page" }
    ) { page ->
        if (page < files.size) {
            val file = files[page]
            CodeEditorView(
                file = file,
                isActive = page == pagerState.currentPage,
                onEditorCreated = { if (page == pagerState.currentPage) onEditorActive(it) }
            )
        }
    }
}

@Composable
fun CodeEditorView(
    file: File, 
    isActive: Boolean,
    onEditorCreated: (IdeEditor?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var editorInstance by remember { mutableStateOf<IdeEditor?>(null) }
    
    LaunchedEffect(isActive, editorInstance) {
        if (isActive && editorInstance != null) {
            onEditorCreated(editorInstance)
        }
    }

    // Ensure content is saved when the editor is removed from composition
    DisposableEffect(file.absolutePath) {
        onDispose {
            editorInstance?.let { editor ->
                val content = editor.text.toString()
                scope.launch(Dispatchers.IO) {
                    try {
                        if (file.exists() && file.canWrite()) {
                            file.writeText(content)
                        }
                    } catch (e: Exception) {
                        Log.e("CodeEditorView", "Failed auto-save: ${file.name}", e)
                    }
                }
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            IdeEditor(ctx).apply {
                editorInstance = this
                
                val project = ProjectHandler.getProject()
                if (project != null) {
                    try {
                        val extension = file.extension.lowercase()
                        when (extension) {
                            "java" -> setEditorLanguage(TsLanguageJava.getInstance(this, project, file))
                            "kt", "kts" -> setEditorLanguage(KotlinLanguage(this, project, file))
                            else -> {
                                val scopeName = when (extension) {
                                    "smali" -> "source.smali"
                                    "gradle" -> "source.groovy.gradle"
                                    "xml" -> "text.xml"
                                    "json" -> "source.json"
                                    "md" -> "text.html.markdown"
                                    else -> null
                                }
                                
                                if (scopeName != null) {
                                    val grammarRegistry = GrammarRegistry.getInstance()
                                    val themeRegistry = ThemeRegistry.getInstance()
                                    setEditorLanguage(TextMateLanguage.create(scopeName, grammarRegistry, themeRegistry, true))
                                } else {
                                    setEditorLanguage(EmptyLanguage())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CodeEditorView", "Failed to set language for ${file.name}", e)
                        setEditorLanguage(EmptyLanguage())
                    }
                }
                
                scope.launch(Dispatchers.IO) {
                    try {
                        if (file.exists() && file.isFile) {
                            val content = ContentIO.createFrom(FileInputStream(file))
                            withContext(Dispatchers.Main) {
                                setText(content)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CodeEditorView", "Failed to load ${file.name}", e)
                        withContext(Dispatchers.Main) {
                            setText(Content())
                        }
                    }
                }
            }
        },
        update = { editor ->
            editor.updateSettings()
        }
    )
}

@Composable
fun EditorActionButtons(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompile: () -> Unit,
    onAction: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
        IconButton(onClick = onRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
        
        IconButton(onClick = onCompile) { 
            Icon(
                imageVector = Icons.Default.PlayArrow, 
                contentDescription = "Run", 
                tint = Color(0xFF4CAF50) // Green color
            ) 
        }

        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // Advanced Menu Item
                DropdownMenuItem(
                    text = { Text("Advanced") },
                    onClick = {
                        expanded = false
                        onAction("Advanced")
                    },
                    trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp)) }
                )

                DropdownMenuItem(
                    text = { Text("Navigation Element") },
                    onClick = { expanded = false; onAction("Navigation") }
                )
                
                DropdownMenuItem(
                    text = { Text("Chat with AI") },
                    onClick = { expanded = false; onAction("AI") }
                )
                
                DropdownMenuItem(
                    text = { Text("Dependency Manager") },
                    onClick = { expanded = false; onAction("Dependencies") }
                )
                
                DropdownMenuItem(
                    text = { Text("Git") },
                    onClick = { expanded = false; onAction("Git") }
                )
                
                DropdownMenuItem(
                    text = { Text("Format") },
                    onClick = { expanded = false; onAction("Format") }
                )
                
                HorizontalDivider()
                
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = { expanded = false; onAction("Settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
}

@Composable
fun EmptyEditorPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Code, 
                contentDescription = null, 
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text("Select a file to start coding", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FileTreeView(
    project: Project,
    onFileClick: (File) -> Unit
) {
    // Fungsi bantuan untuk auto-expand folder secara rekursif
    fun getAutoExpandedPaths(folder: File, currentSet: Set<String>): Set<String> {
        val expanded = currentSet.toMutableSet()
        var current: File? = folder
        while (current != null && current.isDirectory) {
            expanded.add(current.absolutePath)
            // Filter file tersembunyi
            val children = current.listFiles()?.filter { !it.name.startsWith(".") } ?: emptyList()
            
            // Berhenti jika:
            // 1. Kosong (children.isEmpty())
            // 2. Ada lebih dari 1 item (misal: 2 folder, atau 1 folder + 1 file)
            // 3. Item tunggal adalah file
            if (children.size == 1 && children[0].isDirectory) {
                current = children[0]
            } else {
                current = null
            }
        }
        return expanded
    }

    // Inisialisasi dengan auto-expand dari root
    var expandedDirs by remember { mutableStateOf(getAutoExpandedPaths(project.root, emptySet())) }

    val onToggle: (String) -> Unit = { path ->
        if (expandedDirs.contains(path)) {
            // Jika sudah terbuka, maka tutup (remove dari set)
            expandedDirs = expandedDirs - path
        } else {
            // Jika dibuka, gunakan logika auto-expand
            expandedDirs = getAutoExpandedPaths(File(path), expandedDirs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding() // Menangani keyboard agar tidak menutupi treeview
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Project: ${project.name}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        HorizontalDivider()

        // Mendukung scroll horizontal dan vertikal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .width(IntrinsicSize.Max) // Mengikuti lebar item terlebar agar horizontal scroll aktif
            ) {
                FileTreeItem(
                    file = project.root,
                    level = 0,
                    isExpanded = expandedDirs.contains(project.root.absolutePath),
                    onToggle = onToggle,
                    onFileClick = onFileClick
                )
                
                if (expandedDirs.contains(project.root.absolutePath)) {
                    RenderDirectoryContent(
                        directory = project.root,
                        level = 1,
                        expandedDirs = expandedDirs,
                        onToggle = onToggle,
                        onFileClick = onFileClick
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderDirectoryContent(
    directory: File,
    level: Int,
    expandedDirs: Set<String>,
    onToggle: (String) -> Unit,
    onFileClick: (File) -> Unit
) {
    val files = remember(directory) {
        directory.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }
    
    files.forEach { file ->
        key(file.absolutePath) {
            FileTreeItem(
                file = file,
                level = level,
                isExpanded = expandedDirs.contains(file.absolutePath),
                onToggle = onToggle,
                onFileClick = onFileClick
            )
            
            if (file.isDirectory && expandedDirs.contains(file.absolutePath)) {
                RenderDirectoryContent(
                    directory = file,
                    level = level + 1,
                    expandedDirs = expandedDirs,
                    onToggle = onToggle,
                    onFileClick = onFileClick
                )
            }
        }
    }
}

@Composable
fun FileTreeItem(
    file: File,
    level: Int,
    isExpanded: Boolean,
    onToggle: (String) -> Unit,
    onFileClick: (File) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // Critical for VerticalDivider to fill height
            .clickable {
                if (file.isDirectory) {
                    onToggle(file.absolutePath)
                } else {
                    onFileClick(file)
                }
            }
            .padding(vertical = 4.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Scope lines
        repeat(level) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(16.dp),
                contentAlignment = Alignment.Center
            ) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight().width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }

        if (file.isDirectory) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Spacer(modifier = Modifier.size(20.dp))
        }
        
        Spacer(Modifier.width(8.dp))
        
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (file.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
        )
        
        Spacer(Modifier.width(8.dp))
        
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
