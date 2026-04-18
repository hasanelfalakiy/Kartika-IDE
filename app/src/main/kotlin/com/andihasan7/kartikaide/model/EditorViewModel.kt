/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.model

import andihasan7.kartikaide.project.Project
import com.andihasan7.kartikaide.util.FileIndex
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
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
    
    // Index of the currently selected tab
    var selectedTabIndex = mutableIntStateOf(-1)
    
    // File tree state
    private val _fileTreeNodes = MutableStateFlow<List<File>>(emptyList())
    val fileTreeNodes = _fileTreeNodes.asStateFlow()

    private var fileIndex: FileIndex? = null

    /**
     * Initializes the editor with a project.
     * Restores previously open files from FileIndex.
     */
    fun setProject(newProject: Project) {
        if (_project.value?.root?.absolutePath == newProject.root.absolutePath) return
        
        _project.value = newProject
        fileIndex = FileIndex(newProject)
        
        // Restore open files from index
        viewModelScope.launch {
            val savedFiles = fileIndex?.getFiles() ?: emptyList()
            openFiles.clear()
            openFiles.addAll(savedFiles)
            
            if (openFiles.isNotEmpty()) {
                selectedTabIndex.intValue = 0
            } else {
                selectedTabIndex.intValue = -1
            }
            
            refreshFileTree()
        }
    }

    /**
     * Adds a file to the tabs or selects it if already open.
     */
    fun openFile(file: File) {
        if (file.isDirectory) return
        
        val existingIndex = openFiles.indexOfFirst { it.absolutePath == file.absolutePath }
        if (existingIndex != -1) {
            selectedTabIndex.intValue = existingIndex
        } else {
            openFiles.add(file)
            selectedTabIndex.intValue = openFiles.lastIndex
            saveFilesToIndex()
        }
    }

    /**
     * Closes a tab at the given index.
     */
    fun closeFile(index: Int) {
        if (index !in openFiles.indices) return
        
        openFiles.removeAt(index)
        
        // Adjust selection
        if (openFiles.isEmpty()) {
            selectedTabIndex.intValue = -1
        } else {
            selectedTabIndex.intValue = selectedTabIndex.intValue.coerceIn(0, openFiles.lastIndex)
        }
        
        saveFilesToIndex()
    }

    fun closeOthers(file: File) {
        openFiles.removeIf { it.absolutePath != file.absolutePath }
        selectedTabIndex.intValue = if (openFiles.isEmpty()) -1 else 0
        saveFilesToIndex()
    }

    fun closeAll() {
        openFiles.clear()
        selectedTabIndex.intValue = -1
        saveFilesToIndex()
    }

    /**
     * Updates file paths when a file or directory is renamed.
     */
    fun updatePaths(oldPath: String, newPath: String) {
        for (i in openFiles.indices) {
            val file = openFiles[i]
            val path = file.absolutePath
            if (path == oldPath) {
                openFiles[i] = File(newPath)
            } else if (path.startsWith(oldPath + File.separator)) {
                openFiles[i] = File(newPath + path.substring(oldPath.length))
            }
        }
        refreshFileTree()
    }

    /**
     * Removes a path (and children) from tabs when deleted.
     */
    fun removePath(deletedPath: String) {
        val iterator = openFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file.absolutePath == deletedPath || file.absolutePath.startsWith(deletedPath + File.separator)) {
                iterator.remove()
            }
        }
        
        if (selectedTabIndex.intValue >= openFiles.size) {
            selectedTabIndex.intValue = openFiles.lastIndex
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
        val currentIdx = selectedTabIndex.intValue
        val files = openFiles.toList()
        
        viewModelScope.launch {
            FileIndex(proj).putFiles(if (currentIdx == -1) 0 else currentIdx, files)
        }
    }
}
