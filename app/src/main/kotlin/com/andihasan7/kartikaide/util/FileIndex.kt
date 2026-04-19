/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import andihasan7.kartikaide.project.Project
import java.io.File

/**
 * This class represents an index of files stored in a cache directory for a given project.
 * It stores paths relative to the project root for better portability during renames.
 */
class FileIndex(private val project: Project) {
    private val filePath: File get() = project.cacheDir.resolve(FILE_NAME)

    private companion object {
        const val FILE_NAME = "files.json"
    }

    /**
     * Adds a list of files to the index and saves it to disk.
     *
     * @param currentIndex The index of the current file in the list.
     * @param files The list of files to add to the index.
     */
    fun putFiles(currentIndex: Int, files: List<File>) {
        val currentPath = filePath
        
        // Ensure cache directory exists
        if (!project.cacheDir.exists()) {
            project.cacheDir.mkdirs()
        }

        // If files is empty, save an empty list to clear the index
        if (files.isEmpty()) {
            try {
                currentPath.writeText("[]")
            } catch (e: Exception) {
                Log.e("FileIndex", "Failed to clear index", e)
            }
            return
        }

        // Safety check for index and files
        val safeFiles = files.filter { it.exists() }
        if (safeFiles.isEmpty()) {
             try {
                currentPath.writeText("[]")
            } catch (e: Exception) {
                Log.e("FileIndex", "Failed to clear index", e)
            }
            return
        }

        val rootPath = project.root.absolutePath
        val safeIndex = currentIndex.coerceIn(0, safeFiles.size - 1)
        
        // Reorder list so the selected file is at index 0 (legacy behavior for restoration)
        val filePaths =
            safeFiles.toMutableList()
                .apply { 
                    try {
                        val selected = removeAt(safeIndex)
                        add(0, selected)
                    } catch (e: Exception) {
                        Log.e("FileIndex", "Error reordering files", e)
                    }
                }
                .distinctBy { it.absolutePath } // Prevent duplicates
                .map { file ->
                    val absolutePath = file.absolutePath
                    if (absolutePath.startsWith(rootPath)) {
                        absolutePath.removePrefix(rootPath).removePrefix(File.separator)
                    } else {
                        absolutePath
                    }
                }

        try {
            val json = Gson().toJson(filePaths)
            currentPath.writeText(json)
        } catch (e: Exception) {
            Log.e("FileIndex", "Failed to save file index", e)
        }
    }

    /**
     * Gets a list of files from the index.
     *
     * @return A list of files from the index.
     */
    fun getFiles(): List<File> {
        val currentPath = filePath
        if (currentPath.exists().not()) {
            return listOf()
        }

        val json = try {
            currentPath.readText()
        } catch (e: Exception) {
            return listOf()
        }

        if (json.isBlank() || json == "[]") {
            return listOf()
        }

        val filePaths: List<String>
        try {
            filePaths = Gson().fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            Log.e("FileIndex", "Failed to parse file index: $json", e)
            return listOf()
        }

        if (filePaths.isNullOrEmpty()) {
            return listOf()
        }

        return filePaths.map { path ->
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                project.root.resolve(path)
            }
            file
        }.filter { it.exists() }
         .distinctBy { it.absolutePath } // Ensure no duplicates from storage
    }
}
