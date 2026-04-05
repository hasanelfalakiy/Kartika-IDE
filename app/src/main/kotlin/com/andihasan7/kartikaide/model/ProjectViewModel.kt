/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.model

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
            val projectsList = FileUtil.projectDir.listFiles { file -> file.isDirectory }
                ?.sortedByDescending { it.lastModified() }
                ?.map {
                    if (File(it, "src/main/java").exists()) {
                        Project(it, Language.Java)
                    } else {
                        Project(it, Language.Kotlin)
                    }
                }
                ?: emptyList()

            withContext(Dispatchers.Main) {
                _projects.value = projectsList
            }
        }
    }

    companion object {
        private const val TAG = "ProjectViewModel"
    }
}
