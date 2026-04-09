/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.model

import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import andihasan7.kartikaide.project.Language
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.FileUtil
import java.io.File

class ProjectViewModel : ViewModel() {

    private val _projects = MutableLiveData<List<Project>>()
    val projects: LiveData<List<Project>> = _projects

    fun loadProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            val internalProjects = loadFromDir(FileUtil.projectDir)
            
            val externalDir = File(Environment.getExternalStorageDirectory(), "KartikaIDE")
            val externalProjects = if (externalDir.exists()) loadFromDir(externalDir) else emptyList()

            // Combine projects: internal first, then external
            val combined = internalProjects + externalProjects

            withContext(Dispatchers.Main) {
                _projects.value = combined
            }
        }
    }

    private fun loadFromDir(dir: File): List<Project> {
        return dir.listFiles { file -> file.isDirectory }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                val isJava = File(it, "src/main/java").exists() || 
                           it.walkTopDown().maxDepth(3).any { f -> f.isDirectory && f.path.endsWith("src/main/java") }
                
                if (isJava) {
                    Project(it, Language.Java)
                } else {
                    Project(it, Language.Kotlin)
                }
            }
            ?: emptyList()
    }

    companion object {
        private const val TAG = "ProjectViewModel"
    }
}
