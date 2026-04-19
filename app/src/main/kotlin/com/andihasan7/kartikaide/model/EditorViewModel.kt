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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.util.Log

/**
 * Atomic state for the editor to prevent synchronization issues between tabs and content.
 */
data class EditorState(
    val openFiles: List<File> = emptyList(),
    val selectedFile: File? = null
) {
    /**
     * Safely derives the current selected index, ensuring it never exceeds the current list bounds.
     */
    val selectedIndex: Int
        get() {
            if (openFiles.isEmpty() || selectedFile == null) return 0
            val index = openFiles.indexOfFirst { it.absolutePath == selectedFile.absolutePath }
            return if (index == -1) 0 else index.coerceIn(0, (openFiles.size - 1).coerceAtLeast(0))
        }
}

/**
 * Modern EditorViewModel for Jetpack Compose migration.
 * Uses a single atomic state object to prevent IndexOutOfBounds exceptions in UI components.
 */
class EditorViewModel : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project = _project.asStateFlow()

    // Single source of truth for all editor related state
    // Initialized with a stable empty state
    var editorState by mutableStateOf(EditorState())
        private set
    
    // Convenience properties for easier migration
    val openFiles: List<File> get() = editorState.openFiles
    
    var selectedFile: File? 
        get() = editorState.selectedFile
        set(value) {
            editorState = editorState.copy(selectedFile = value)
            saveFilesToIndex()
        }

    val selectedTabIndex: Int get() = editorState.selectedIndex
    
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
            try {
                val savedFiles = fileIndex?.getFiles() ?: emptyList()
                val filtered = savedFiles.filter { it.exists() && it.isFile }.distinctBy { it.absolutePath }
                
                editorState = EditorState(
                    openFiles = filtered,
                    selectedFile = filtered.firstOrNull()
                )
                
                refreshFileTree()
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Failed to load project files", e)
            }
        }
    }

    /**
     * Adds a file to the tabs or selects it if already open.
     */
    fun openFile(file: File) {
        if (file.isDirectory) return
        
        val currentState = editorState
        val currentFiles = currentState.openFiles
        val existing = currentFiles.find { it.absolutePath == file.absolutePath }
        
        if (existing != null) {
            editorState = currentState.copy(selectedFile = existing)
        } else {
            // Add to end and update atomically
            editorState = currentState.copy(
                openFiles = (currentFiles + file).distinctBy { it.absolutePath },
                selectedFile = file
            )
        }
        saveFilesToIndex()
    }

    /**
     * Closes a tab with atomic state update.
     */
    fun closeFile(file: File) {
        val currentState = editorState
        val currentFiles = currentState.openFiles
        val index = currentFiles.indexOfFirst { it.absolutePath == file.absolutePath }
        if (index == -1) return

        val newList = currentFiles.toMutableList().apply { removeAt(index) }
        val wasSelected = currentState.selectedFile?.absolutePath == file.absolutePath
        
        val newSelectedFile = if (wasSelected) {
            if (newList.isNotEmpty()) {
                newList[index.coerceIn(0, newList.lastIndex)]
            } else {
                null
            }
        } else {
            currentState.selectedFile
        }

        editorState = EditorState(
            openFiles = newList,
            selectedFile = newSelectedFile
        )
        saveFilesToIndex()
    }

    fun closeFileAt(index: Int) {
        val current = openFiles
        if (index in current.indices) {
            closeFile(current[index])
        }
    }

    fun closeOthers(file: File) {
        editorState = EditorState(
            openFiles = listOf(file),
            selectedFile = file
        )
        saveFilesToIndex()
    }

    fun closeAll() {
        editorState = EditorState()
        saveFilesToIndex()
    }

    fun updatePaths(oldPath: String, newPath: String) {
        val currentState = editorState
        val currentFiles = currentState.openFiles
        val newList = currentFiles.map { file ->
            val path = file.absolutePath
            if (path == oldPath) {
                File(newPath)
            } else if (path.startsWith(oldPath + File.separator)) {
                File(newPath + path.substring(oldPath.length))
            } else {
                file
            }
        }
        
        var newSelected = currentState.selectedFile
        newSelected?.let { selected ->
            val path = selected.absolutePath
            if (path == oldPath) {
                newSelected = File(newPath)
            } else if (path.startsWith(oldPath + File.separator)) {
                newSelected = File(newPath + path.substring(oldPath.length))
            }
        }
        
        editorState = EditorState(
            openFiles = newList,
            selectedFile = newSelected
        )
        refreshFileTree()
    }

    fun removePath(deletedPath: String) {
        val currentState = editorState
        val currentFiles = currentState.openFiles
        val newList = currentFiles.filterNot { 
            it.absolutePath == deletedPath || it.absolutePath.startsWith(deletedPath + File.separator) 
        }
        
        val wasSelectedRemoved = currentState.selectedFile?.absolutePath == deletedPath || 
                               currentState.selectedFile?.absolutePath?.startsWith(deletedPath + File.separator) == true
        
        val newSelected = if (wasSelectedRemoved) {
            newList.firstOrNull()
        } else {
            currentState.selectedFile
        }
        
        editorState = EditorState(
            openFiles = newList,
            selectedFile = newSelected
        )
        
        refreshFileTree()
        saveFilesToIndex()
    }

    fun refreshFileTree() {
        val root = _project.value?.root ?: return
        viewModelScope.launch {
            val nodes = root.listFiles()?.toList()?.sortedWith(
                compareBy({ !it.isDirectory }, { it.name.lowercase() })
            ) ?: emptyList()
            _fileTreeNodes.value = nodes
        }
    }

    private fun saveFilesToIndex() {
        val proj = _project.value ?: return
        val currentState = editorState
        val currentFiles = currentState.openFiles
        val currentIndex = currentState.selectedIndex
        
        viewModelScope.launch {
            try {
                FileIndex(proj).putFiles(if (currentIndex == -1) 0 else currentIndex, currentFiles)
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Failed to save file index", e)
            }
        }
    }
}
