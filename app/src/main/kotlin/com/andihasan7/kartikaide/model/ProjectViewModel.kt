/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.model

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import andihasan7.kartikaide.project.Language
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.FileUtil
import andihasan7.kartikaide.rewrite.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val _internalProjects = MutableStateFlow<List<Project>>(emptyList())
    val internalProjects: StateFlow<List<Project>> = _internalProjects.asStateFlow()

    private val _externalProjects = MutableStateFlow<List<Project>>(emptyList())
    val externalProjects: StateFlow<List<Project>> = _externalProjects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val internal = loadFromDir(FileUtil.projectDir)
            
            var external = emptyList<Project>()
            if (PermissionUtils.hasStoragePermission(getApplication())) {
                val externalDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
                if (!externalDir.exists()) {
                    try {
                        externalDir.mkdirs()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (externalDir.exists()) {
                    external = loadFromDir(externalDir)
                }
            }

            withContext(Dispatchers.Main) {
                _internalProjects.value = internal
                _externalProjects.value = external
                _isLoading.value = false
            }
        }
    }

    private fun loadFromDir(dir: File): List<Project> {
        return try {
            dir.listFiles { file -> file.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                ?.map {
                    // Improved language detection: check for kotlin or java source folders deep in structure
                    val hasJava = it.walkTopDown().maxDepth(5).any { f -> 
                        f.isDirectory && f.path.contains("src${File.separator}main${File.separator}java") 
                    }
                    
                    if (hasJava) {
                        Project(it, Language.Java)
                    } else {
                        Project(it, Language.Kotlin)
                    }
                }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ProjectViewModel"
    }
}
