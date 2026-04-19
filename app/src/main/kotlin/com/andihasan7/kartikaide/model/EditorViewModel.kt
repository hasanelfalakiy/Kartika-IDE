/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.model

import andihasan7.kartikaide.project.Project
import com.andihasan7.kartikaide.util.FileIndex
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Modern EditorViewModel for Jetpack Compose migration.
 * Replaces FileViewModel logic with reactive StateFlow and Compose-friendly state.
 */
class EditorViewModel : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project = _project.asStateFlow()

    // Observable list of open files (tabs)
    val openFiles = mutableStateListOf<File>()
    
    // Currently selected file
    var selectedFile by mutableStateOf<File?>(null)

    // Derived index for components that still need it (like TabRow)
    // Ensures the index is always valid for the current openFiles list
    val selectedTabIndex by derivedStateOf {
        if (openFiles.isEmpty()) return@derivedStateOf -1
        val index = openFiles.indexOfFirst { it.absolutePath == selectedFile?.absolutePath }
        if (index == -1) 0 else index.coerceIn(0, openFiles.size - 1)
    }
    
    // File tree state
    private val _fileTreeNodes = MutableStateFlow<List<File>>(emptyList())
    val fileTreeNodes = _fileTreeNodes.asStateFlow()

    private var fileIndex: FileIndex? = null

    /**
     * Initializes the editor with a project.
     */
    fun setProject(newProject: Project) {
        if (_project.value?.root?.absolutePath == newProject.root.absolutePath) return
        
        _project.value = newProject
        fileIndex = FileIndex(newProject)
        
        viewModelScope.launch {
            val savedFiles = fileIndex?.getFiles() ?: emptyList()
            Snapshot.withMutableSnapshot {
                openFiles.clear()
                openFiles.addAll(savedFiles)
                selectedFile = openFiles.firstOrNull()
            }
            
            refreshFileTree()
        }
    }

    /**
     * Adds a file to the tabs or selects it if already open.
     */
    fun openFile(file: File) {
        if (file.isDirectory) return
        
        Snapshot.withMutableSnapshot {
            val existingIndex = openFiles.indexOfFirst { it.absolutePath == file.absolutePath }
            if (existingIndex != -1) {
                selectedFile = openFiles[existingIndex]
            } else {
                openFiles.add(file)
                selectedFile = file
                saveFilesToIndex()
            }
        }
    }

    /**
     * Closes a tab.
     */
    fun closeFile(file: File) {
        Snapshot.withMutableSnapshot {
            val index = openFiles.indexOfFirst { it.absolutePath == file.absolutePath }
            if (index == -1) return@withMutableSnapshot

            val isSelected = selectedFile?.absolutePath == file.absolutePath
            openFiles.removeAt(index)
            
            if (isSelected) {
                selectedFile = if (openFiles.isEmpty()) {
                    null
                } else {
                    val nextIndex = index.coerceAtMost(openFiles.lastIndex)
                    openFiles[nextIndex]
                }
            }
        }
        saveFilesToIndex()
    }

    /**
     * Support closing by index
     */
    fun closeFileAt(index: Int) {
        if (index in openFiles.indices) {
            closeFile(openFiles[index])
        }
    }

    fun closeOthers(file: File) {
        Snapshot.withMutableSnapshot {
            openFiles.removeIf { it.absolutePath != file.absolutePath }
            selectedFile = openFiles.firstOrNull()
        }
        saveFilesToIndex()
    }

    fun closeAll() {
        Snapshot.withMutableSnapshot {
            openFiles.clear()
            selectedFile = null
        }
        saveFilesToIndex()
    }

    fun updatePaths(oldPath: String, newPath: String) {
        Snapshot.withMutableSnapshot {
            var updatedSelectedFile: File? = selectedFile
            for (i in openFiles.indices) {
                val file = openFiles[i]
                val path = file.absolutePath
                if (path == oldPath) {
                    val newFile = File(newPath)
                    openFiles[i] = newFile
                    if (selectedFile?.absolutePath == path) updatedSelectedFile = newFile
                } else if (path.startsWith(oldPath + File.separator)) {
                    val newFile = File(newPath + path.substring(oldPath.length))
                    openFiles[i] = newFile
                    if (selectedFile?.absolutePath == path) updatedSelectedFile = newFile
                }
            }
            selectedFile = updatedSelectedFile
        }
        refreshFileTree()
    }

    fun removePath(deletedPath: String) {
        Snapshot.withMutableSnapshot {
            val wasSelectedRemoved = selectedFile?.absolutePath == deletedPath || 
                                   selectedFile?.absolutePath?.startsWith(deletedPath + File.separator) == true
            
            val iterator = openFiles.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                if (file.absolutePath == deletedPath || file.absolutePath.startsWith(deletedPath + File.separator)) {
                    iterator.remove()
                }
            }
            
            if (wasSelectedRemoved) {
                selectedFile = openFiles.firstOrNull()
            }
        }
        
        refreshFileTree()
        saveFilesToIndex()
    }

    fun refreshFileTree() {
        val root = _project.value?.root ?: return
        _fileTreeNodes.value = root.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    private fun saveFilesToIndex() {
        val proj = _project.value ?: return
        val currentIdx = selectedTabIndex
        val files = openFiles.toList()
        
        viewModelScope.launch {
            FileIndex(proj).putFiles(if (currentIdx == -1) 0 else currentIdx, files)
        }
    }
}
