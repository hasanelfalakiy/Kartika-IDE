/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package andihasan7.kartikaide.buildtools.dex

import android.os.Build
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import andihasan7.kartikaide.buildtools.BuildReporter
import andihasan7.kartikaide.buildtools.Task
import andihasan7.kartikaide.buildtools.util.getSystemClasspath
import andihasan7.kartikaide.project.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.nameWithoutExtension

/**
 * Task to compile the class files of a project to a Dalvik Executable (Dex) file using D8.
 *
 * @property project The project to compile.
 */
class D8Task(val project: Project) : Task {

    companion object {
        const val MIN_API_LEVEL = Build.VERSION_CODES.O

        val COMPILATION_MODE = CompilationMode.DEBUG

        /**
         * Compiles a jar file to a directory of dex files.
         *
         * @param jarFile The jar file to compile.
         * @param outputDir The directory to output the dex files to.
         * @param reporter The BuildReporter instance to report any errors to.
         */
        fun compileJar(jarFile: Path, outputDir: Path, reporter: BuildReporter? = null) {
            try {
                // If it's an AAR, we need to extract classes.jar or handle it specifically.
                // For now, let's assume it's a JAR or that D8 can handle it if it contains classes.
                if (jarFile.toString().endsWith(".aar")) {
                    // Simple extraction of classes.jar from AAR for dexing
                    val aarZip = ZipFile(jarFile.toFile())
                    val classesJarEntry = aarZip.getEntry("classes.jar")
                    if (classesJarEntry != null) {
                        val tempClassesJar = Files.createTempFile("classes", ".jar")
                        aarZip.getInputStream(classesJarEntry).use { input ->
                            Files.copy(input, tempClassesJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        }

                        D8.run(
                            D8Command.builder()
                                .setMinApiLevel(MIN_API_LEVEL)
                                .setMode(COMPILATION_MODE)
                                .addClasspathFiles(getSystemClasspath().map { it.toPath() })
                                .addProgramFiles(tempClassesJar)
                                .setOutput(outputDir, OutputMode.DexIndexed)
                                .build()
                        )
                        Files.deleteIfExists(tempClassesJar)
                    } else {
                        return
                    }
                } else {
                    val zipFile = ZipFile(jarFile.toFile())
                    // If the jar has no files with the .class extension, skip it
                    if (zipFile.use { zip ->
                            zip.entries().asSequence()
                                .none { it.name.startsWith("META-INF").not() && it.name.endsWith(".class") }
                        }) {
                        return
                    }

                    D8.run(
                        D8Command.builder()
                            .setMinApiLevel(MIN_API_LEVEL)
                            .setMode(COMPILATION_MODE)
                            .addClasspathFiles(getSystemClasspath().map { it.toPath() })
                            .addProgramFiles(jarFile)
                            .setOutput(outputDir, OutputMode.DexIndexed)
                            .build()
                    )
                }

                // D8 outputs classes.dex, rename it to be unique to this jar
                val outputDex = outputDir.resolve("classes.dex")
                if (Files.exists(outputDex)) {
                    Files.move(
                        outputDex,
                        outputDir.resolve(jarFile.nameWithoutExtension + ".dex"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } catch (e: Throwable) {
                reporter?.reportError("Failed to dex ${jarFile.fileName}: ${e.message}")
            }
        }
    }

    /**
     * Compiles the project classes to a Dex file.
     *
     * @param reporter The BuildReporter instance to report any errors to.
     */
    override fun execute(reporter: BuildReporter) {
        val classes = getClassFiles(project.binDir.resolve("classes"))
        if (classes.isEmpty()) {
            // It's possible there are no classes but only resources, or it's a Kotlin-only project
            // that hasn't populated bin/classes yet (though KotlinCompiler should have).
            // But if it's empty, we just skip project dexing.
            reporter.reportInfo("No project classes found to dex.")
        } else {
            D8.run(
                D8Command.builder()
                    .setMinApiLevel(MIN_API_LEVEL)
                    .setMode(COMPILATION_MODE)
                    .addClasspathFiles(getSystemClasspath().map { it.toPath() })
                    .addProgramFiles(classes)
                    .setOutput(project.binDir.toPath(), OutputMode.DexIndexed)
                    .build()
            )
        }

        // Compile all libraries from all detected locations
        val allLibs = project.allLibFiles
        if (allLibs.isNotEmpty()) {
            val libDexDir = project.buildDir.resolve("libs").apply { mkdirs() }
            allLibs.forEach { libFile ->
                val targetDex = libDexDir.resolve(libFile.nameWithoutExtension + ".dex")
                // Only compile if dex doesn't exist or is older than the library
                if (!targetDex.exists() || targetDex.lastModified() < libFile.lastModified()) {
                    reporter.reportInfo("Dexing library: ${libFile.name}")
                    compileJar(libFile.toPath(), libDexDir.toPath(), reporter)
                }
            }
        }
    }

    /**
     * Returns a list of paths to all class files recursively in a directory.
     *
     * @param root The directory to search in.
     * @return A list of paths to all class files in the directory.
     */
    fun getClassFiles(root: File): List<Path> {
        if (!root.exists()) return emptyList()
        return root.walk().filter { it.extension == "class" }.map { it.toPath() }.toList()
    }
}
