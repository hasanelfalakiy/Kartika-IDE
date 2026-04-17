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

package com.andihasan7.kartikaide.fragment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import andihasan7.kartikaide.rewrite.util.FileUtil
import com.andihasan7.kartikaide.util.Download
import com.andihasan7.kartikaide.util.ResourceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InstallResourcesViewModel : ViewModel() {

    private val rawUrl = "https://github.com/Cosmic-Ide/binaries/raw/main/"

    private val _uiState = MutableStateFlow<InstallUiState>(InstallUiState.Idle)
    val uiState: StateFlow<InstallUiState> = _uiState.asStateFlow()

    fun startInstallation(onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val missing = ResourceUtil.missingResources()
            if (missing.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onFinished()
                }
                return@launch
            }

            for (res in missing) {
                _uiState.value = InstallUiState.Downloading(
                    resourceName = res,
                    progress = 0,
                    isIndeterminate = true,
                    statusText = "Preparing to download resource $res"
                )

                val success = installResource(res)
                if (!success) return@launch
            }
            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }

    private suspend fun installResource(res: String): Boolean {
        return try {
            val url = rawUrl + res.substringAfterLast('/')
            val file = FileUtil.dataDir.resolve(res)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()

            Download(url) { percent ->
                val currentState = _uiState.value
                if (currentState is InstallUiState.Downloading && currentState.resourceName == res) {
                    if (percent == -1) {
                        _uiState.value = currentState.copy(
                            isIndeterminate = true,
                            statusText = "Downloading..."
                        )
                    } else {
                        _uiState.value = currentState.copy(
                            isIndeterminate = false,
                            progress = percent,
                            statusText = "$percent%"
                        )
                    }
                }
            }.start(file)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = InstallUiState.Error("Failed to download resource $res: ${e.message}")
            false
        }
    }

    sealed class InstallUiState {
        object Idle : InstallUiState()
        data class Downloading(
            val resourceName: String,
            val progress: Int,
            val isIndeterminate: Boolean,
            val statusText: String
        ) : InstallUiState()
        data class Error(val message: String) : InstallUiState()
    }
}