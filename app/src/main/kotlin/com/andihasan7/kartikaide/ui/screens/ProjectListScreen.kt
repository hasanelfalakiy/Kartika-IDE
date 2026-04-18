/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.ui.screens

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import andihasan7.kartikaide.project.Project
import com.andihasan7.kartikaide.model.ProjectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectViewModel,
    onProjectClick: (Project) -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onNewProjectClick: () -> Unit,
    onImportClick: () -> Unit,
    onGitCloneClick: () -> Unit,
    onOpenExternalClick: () -> Unit
) {
    val internalProjects by viewModel.internalProjects.collectAsStateWithLifecycle()
    val externalProjects by viewModel.externalProjects.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    
    var showFabMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kartika IDE") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onAboutClick) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = showFabMenu,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FabMenuItem(
                            label = "New Project",
                            icon = Icons.Default.Create,
                            onClick = { 
                                showFabMenu = false
                                onNewProjectClick() 
                            }
                        )
                        FabMenuItem(
                            label = "Import Zip",
                            icon = Icons.Default.FileDownload,
                            onClick = { 
                                showFabMenu = false
                                onImportClick() 
                            }
                        )
                        FabMenuItem(
                            label = "Git Clone",
                            icon = Icons.Default.Share, // Using Share as a placeholder for Git
                            onClick = { 
                                showFabMenu = false
                                onGitCloneClick() 
                            }
                        )
                        FabMenuItem(
                            label = "Open Folder",
                            icon = Icons.Default.FolderOpen,
                            onClick = { 
                                showFabMenu = false
                                onOpenExternalClick() 
                            }
                        )
                    }
                }
                
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    val rotation by animateFloatAsState(if (showFabMenu) 45f else 0f)
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (showFabMenu) "Close Menu" else "Add Options",
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }
    ) { padding ->
        if (isLoading && internalProjects.isEmpty() && externalProjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (internalProjects.isEmpty() && externalProjects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No projects found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (internalProjects.isNotEmpty()) {
                    item { ProjectHeader("Internal Storage") }
                    items(internalProjects) { project ->
                        ProjectItem(
                            project = project,
                            onClick = { onProjectClick(project) },
                            onRename = { /* TODO */ },
                            onDelete = { /* TODO */ },
                            onBackup = { /* TODO */ }
                        )
                    }
                }

                if (externalProjects.isNotEmpty()) {
                    item { ProjectHeader("External Storage (KartikaIDE)") }
                    items(externalProjects) { project ->
                        ProjectItem(
                            project = project,
                            onClick = { onProjectClick(project) },
                            onRename = { /* TODO */ },
                            onDelete = { /* TODO */ },
                            onBackup = { /* TODO */ }
                        )
                    }
                }
                
                // Extra padding for FAB
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun FabMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
fun ProjectHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectItem(
    project: Project,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBackup: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = project.root.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Backup (Zip)") },
                        onClick = {
                            showMenu = false
                            onBackup()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
