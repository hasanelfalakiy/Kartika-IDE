/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package andihasan7.kartikaide.project

import java.io.File
import java.io.Serializable

/**
 * Represents a project.
 */
class Project : Serializable {
    var root: File
    val language: Language

    constructor(root: File, language: Language) {
        this.root = root
        this.language = language
    }

    /**
     * The name of the project, derived from the root directory.
     */
    val name: String get() = root.name

    /**
     * Get all detected source directories (Java and Kotlin).
     */
    val allSrcDirs: List<File>
        get() {
            val dirs = mutableSetOf<File>()
            
            // Standard Gradle structure
            val standardPaths = listOf(
                "src/main/java", "src/main/kotlin",
                "src/java", "src/kotlin", // Common non-standard or simplified structures
                "app/src/main/java", "app/src/main/kotlin",
                "lib/src/main/java", "lib/src/main/kotlin"
            )
            
            for (path in standardPaths) {
                val f = File(root, path)
                if (f.exists() && f.isDirectory) {
                    dirs.add(f)
                }
            }

            // Fallback: If no standard directories found, use root or perform a deeper search
            if (dirs.isEmpty()) {
                val detected = root.walkTopDown().maxDepth(5)
                    .filter { it.isDirectory && (it.name == "java" || it.name == "kotlin") && it.path.contains("src") }
                    .toList()
                dirs.addAll(detected)
            }
            
            if (dirs.isEmpty()) {
                dirs.add(root)
            }
            
            return dirs.toList()
        }

    /**
     * The primary source directory of the project.
     * Maintained for backward compatibility.
     */
    val srcDir: File
        get() = allSrcDirs.firstOrNull() ?: root

    /**
     * The resources directory of the project.
     */
    val resourcesDir: File
        get() {
            val res = File(root, "src/main/resources")
            if (res.exists()) return res
            
            // Try in common modules
            listOf("app", "lib").forEach { module ->
                val moduleRes = File(root, "$module/src/main/resources")
                if (moduleRes.exists()) return moduleRes
            }
            
            return res // default
        }

    /**
     * The build directory of the project.
     */
    val buildDir get() = File(root, "build")

    /**
     * The cache directory of the project.
     */
    val cacheDir get() = File(buildDir, "cache")

    /**
     * The binary directory of the project.
     */
    val binDir get() = File(buildDir, "bin")

    /**
     * The classes directory where compiled class files are stored.
     */
    val classesDir get() = File(buildDir, "classes")

    /**
     * The primary library directory of the project.
     * Maintained for backward compatibility.
     */
    val libDir get() = File(root, "libs")

    /**
     * Get all library files (.jar and .aar) from common locations:
     * /libs, /lib/libs, and /src/libs.
     */
    val allLibFiles: List<File>
        get() {
            val libPaths = listOf("libs", "lib/libs", "src/libs")
            val libFiles = mutableListOf<File>()
            
            for (path in libPaths) {
                val dir = File(root, path)
                if (dir.exists() && dir.isDirectory) {
                    libFiles.addAll(dir.walk().filter { it.isFile && (it.extension == "jar" || it.extension == "aar") })
                }
            }
            return libFiles.distinct()
        }

    var args: List<String> = listOf()
        get() {
            val f = cacheDir.resolve("args.txt")
            if (f.exists()) {
                return f.readLines().toMutableList()
            }

            return listOf()
        }
        set(value) {
            val f = cacheDir.resolve("args.txt")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            f.writeText(value.joinToString("\n"))
            field = value
        }

    /**
     * Deletes the project directory.
     *
     * @throws IllegalStateException if the root directory is not a valid project directory.
     */
    fun delete() {
        if (root.isDirectory && root.name == name) {
            root.deleteRecursively()
        } else {
            throw IllegalStateException("Cannot delete directory: ${root.absolutePath}")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Project) return false
        if (root != other.root) return false
        if (language != other.language) return false
        return true
    }

    override fun hashCode(): Int {
        var result = root.hashCode()
        result = 31 * result + language.hashCode()
        return result
    }

    override fun toString(): String {
        return "Project(root=$root, language=$language)"
    }
}
