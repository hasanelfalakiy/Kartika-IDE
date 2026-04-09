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
 *
 * @property root The root directory of the project.
 * @property language The programming language used in the project.
 */
data class Project(
    val root: File,
    val language: Language
) : Serializable {

    /**
     * The name of the project, derived from the root directory.
     */
    val name: String = root.name

    /**
     * The source directory of the project, based on the language used.
     * Improved to detect standard structures even in subfolders (like Gradle projects).
     */
    val srcDir: File
        get() {
            // Priority 1: Standard Gradle/Maven structure at root
            val javaSrc = File(root, "src/main/java")
            if (javaSrc.exists()) return javaSrc
            val kotlinSrc = File(root, "src/main/kotlin")
            if (kotlinSrc.exists()) return kotlinSrc

            // Priority 2: Common modules like 'app' or 'lib'
            val modules = listOf("app", "lib", "library", "module")
            for (module in modules) {
                val moduleJava = File(root, "$module/src/main/java")
                if (moduleJava.exists()) return moduleJava
                val moduleKotlin = File(root, "$module/src/main/kotlin")
                if (moduleKotlin.exists()) return moduleKotlin
            }

            // Priority 3: Deep search for any src/main/java or src/main/kotlin
            val detected = root.walkTopDown().maxDepth(5)
                .filter { it.isDirectory && (it.path.endsWith("src${File.separator}main${File.separator}java") || it.path.endsWith("src${File.separator}main${File.separator}kotlin")) }
                .firstOrNull()

            return detected ?: root
        }

    /**
     * The build directory of the project.
     */
    val buildDir = File(root, "build")

    /**
     * The cache directory of the project.
     */
    val cacheDir = File(buildDir, "cache")

    /**
     * The binary directory of the project.
     */
    val binDir = File(buildDir, "bin")

    /**
     * The library directory of the project.
     */
    val libDir = File(root, "libs")

    var args = listOf<String>()
        get() {
            val f = cacheDir.resolve("args.txt")
            if (f.exists()) {
                return f.readLines().toMutableList()
            }

            return listOf()
        }
        set(value) {
            val f = cacheDir.resolve("args.txt")
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
}
