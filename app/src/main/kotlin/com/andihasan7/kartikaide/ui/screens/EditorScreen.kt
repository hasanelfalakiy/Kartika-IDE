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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
    viewModel: EditorViewModel = viewModel()
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val openFiles = viewModel.openFiles
    val selectedFile = viewModel.selectedFile
    val selectedTabIndex = viewModel.selectedTabIndex
    
    // PagerState for visual transitions
    val pagerState = rememberPagerState(pageCount = { openFiles.size })
    
    // Track current active editor for global actions
    var activeEditor by remember { mutableStateOf<IdeEditor?>(null) }

    // Initialize project
    LaunchedEffect(project) {
        viewModel.setProject(project)
        ProjectHandler.setProject(project)
    }

    // Sync Pager position when selectedFile changes
    LaunchedEffect(selectedFile, openFiles.size) {
        val index = openFiles.indexOfFirst { it.absolutePath == selectedFile?.absolutePath }
        if (index != -1 && index != pagerState.currentPage) {
            pagerState.scrollToPage(index)
        }
    }
    
    // Sync back to ViewModel if pager is scrolled
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage < openFiles.size) {
            val fileAtPage = openFiles[pagerState.currentPage]
            if (viewModel.selectedFile?.absolutePath != fileAtPage.absolutePath) {
                viewModel.selectedFile = fileAtPage
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
                    title = {
                        selectedFile?.let { file ->
                            Text(
                                file.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        EditorActionButtons(
                            onUndo = { activeEditor?.undo() },
                            onRedo = { activeEditor?.redo() },
                            onCompile = { /* TODO: Implement Run Logic */ },
                            onAction = { action ->
                                when(action) {
                                    "Settings" -> { /* TODO: Navigate to Settings */ }
                                    "Git" -> { /* TODO: Open Git */ }
                                    // Handle other menu actions here
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
                        onTabSelected = { index ->
                            viewModel.selectedFile = openFiles.getOrNull(index)
                        },
                        onCloseTab = { index ->
                            viewModel.closeFileAt(index)
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
}

@Composable
fun EditorTabs(
    files: List<File>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    onCloseTab: (Int) -> Unit
) {
    if (files.isEmpty()) return

    val safeSelectedIndex = selectedIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))

    ScrollableTabRow(
        selectedTabIndex = safeSelectedIndex,
        edgePadding = 0.dp,
        divider = {}
    ) {
        files.forEachIndexed { index, file ->
            Tab(
                selected = safeSelectedIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp),
                            tint = if (safeSelectedIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { onCloseTab(index) },
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
        key = { page -> if (page < files.size) files[page].absolutePath else page }
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
                    onClick = { expanded = false; onAction("AI") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp)) }
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
    var expandedDirs by remember { mutableStateOf(setOf(project.root.absolutePath)) }

    Column(modifier = Modifier.fillMaxSize()) {
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

        LazyColumn(modifier = Modifier.fillMaxHeight().fillMaxWidth()) {
            item {
                FileTreeItem(
                    file = project.root,
                    level = 0,
                    isExpanded = expandedDirs.contains(project.root.absolutePath),
                    onToggle = {
                        expandedDirs = if (expandedDirs.contains(it)) expandedDirs - it else expandedDirs + it
                    },
                    onFileClick = onFileClick
                )
            }
            
            if (expandedDirs.contains(project.root.absolutePath)) {
                renderDirectoryContent(
                    directory = project.root,
                    level = 1,
                    expandedDirs = expandedDirs,
                    onToggle = {
                        expandedDirs = if (expandedDirs.contains(it)) expandedDirs - it else expandedDirs + it
                    },
                    onFileClick = onFileClick
                )
            }
        }
    }
}

private fun LazyListScope.renderDirectoryContent(
    directory: File,
    level: Int,
    expandedDirs: Set<String>,
    onToggle: (String) -> Unit,
    onFileClick: (File) -> Unit
) {
    val files = directory.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    
    files.forEach { file ->
        item(key = file.absolutePath) {
            FileTreeItem(
                file = file,
                level = level,
                isExpanded = expandedDirs.contains(file.absolutePath),
                onToggle = onToggle,
                onFileClick = onFileClick
            )
        }
        
        if (file.isDirectory && expandedDirs.contains(file.absolutePath)) {
            renderDirectoryContent(
                directory = file,
                level = level + 1,
                expandedDirs = expandedDirs,
                onToggle = onToggle,
                onFileClick = onFileClick
            )
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
            .clickable {
                if (file.isDirectory) {
                    onToggle(file.absolutePath)
                } else {
                    onFileClick(file)
                }
            }
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .padding(start = (level * 16).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
