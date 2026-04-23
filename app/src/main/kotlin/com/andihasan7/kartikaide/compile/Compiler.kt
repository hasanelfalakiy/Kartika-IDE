/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.compile

import android.util.Log
import com.andihasan7.kartikaide.App
import com.andihasan7.kartikaide.R
import andihasan7.kartikaide.buildtools.BuildReporter
import andihasan7.kartikaide.buildtools.Task
import andihasan7.kartikaide.buildtools.dex.D8Task
import andihasan7.kartikaide.buildtools.java.JarTask
import andihasan7.kartikaide.buildtools.java.JavaCompileTask
import andihasan7.kartikaide.buildtools.kotlin.KotlinCompiler
import andihasan7.kartikaide.project.Project
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.load.kotlin.KotlinBinaryClassCache

/**
 * A class responsible for compiling Java and Kotlin code and converting class files to dex format.
 *
 * @property project The project to be compiled.
 * @property reporter The [BuildReporter] to report the build progress and status.
 */
class Compiler(
    private val project: Project,
    private val reporter: BuildReporter
) {
    companion object {
        private const val TAG = "Compiler"

        /**
         * A listener to be called when a compiler starts or finishes compiling.
         */
        @JvmStatic
        var compileListener: (Class<*>, BuildStatus) -> Unit = { _, _ -> }

        /**
         * Initializes the cache of compiler instances.
         */
        @JvmStatic
        fun initializeCache(project: Project) {
            ensureKotlinServicesRegistered()
            CompilerCache.saveCache(JavaCompileTask(project))
            CompilerCache.saveCache(KotlinCompiler(project))
            CompilerCache.saveCache(D8Task(project))
            CompilerCache.saveCache(JarTask(project))
        }

        /**
         * Memastikan service Kotlin terdaftar untuk menghindari NullPointerException
         * pada KotlinBinaryClassCache di Android.
         */
        private fun ensureKotlinServicesRegistered() {
            try {
                val application = ApplicationManager.getApplication()
                if (application != null) {
                    if (application.getService(KotlinBinaryClassCache::class.java) == null) {
                        val componentManagerClass = Class.forName("com.intellij.openapi.components.ComponentManager")
                        val registerServiceMethod = componentManagerClass.getDeclaredMethod(
                            "registerService", 
                            Class::class.java, 
                            Class::class.java
                        )
                        registerServiceMethod.isAccessible = true
                        registerServiceMethod.invoke(
                            application, 
                            KotlinBinaryClassCache::class.java, 
                            KotlinBinaryClassCache::class.java
                        )
                        Log.i(TAG, "Successfully registered KotlinBinaryClassCache via reflection")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register KotlinBinaryClassCache: ${e.message}")
            }
        }
    }

    private val context = App.instance.get()!!

    init {
        initializeCache(project)
    }

    /**
     * Compiles Kotlin and Java code and converts class files to dex format.
     */
    fun compile(release: Boolean = false) {
        ensureKotlinServicesRegistered() // Pastikan lagi sebelum kompilasi dimulai
        compileKotlinCode()
        compileJavaCode()
        convertClassFilesToDexFormat()
        if (release) {
            compileJar()
        }
        reporter.reportSuccess()
    }

    /**
     * Compiles a task.
     *
     * @param T The type of the task to be compiled.
     * @param message The message to be reported when the task starts compiling.
     */
    private inline fun <reified T : Task> compileTask(message: String) {
        var taskInstance = CompilerCache.getCache<T>()

        with(reporter) {
            if (failure) return
            reportInfo(message)
            compileListener(T::class.java, BuildStatus.STARTED)
            try {
                taskInstance.execute(this)
            } catch (e: Exception) {
                val errorStr = e.stackTraceToString()
                // Tangani error spesifik Kotlin Binary Cache NPE atau kegagalan incremental
                if (T::class == KotlinCompiler::class && 
                    (e.message?.contains("Incremental compilation failed") == true || 
                     errorStr.contains("KotlinBinaryClassCache") ||
                     errorStr.contains("NullPointerException"))) {
                    
                    reportInfo("Incremental compilation failed or compiler state corrupted, performing clean build...")
                    
                    // Membersihkan seluruh folder build
                    try {
                        project.buildDir.deleteRecursively()
                        project.buildDir.mkdirs()
                        project.binDir.mkdirs()
                        project.classesDir.mkdirs()
                    } catch (ioe: Exception) {
                        reportError("Failed to clean build directory: ${ioe.message}")
                    }
                    
                    // Paksa registrasi ulang service sebelum mencoba lagi
                    ensureKotlinServicesRegistered()
                    
                    // Re-inisialisasi task dengan instance baru
                    val newTask = KotlinCompiler(project)
                    CompilerCache.saveCache(newTask)
                    
                    try {
                        newTask.execute(this)
                    } catch (e2: Exception) {
                        throw e2
                    }
                } else {
                    throw e
                }
            }
            compileListener(T::class.java, BuildStatus.FINISHED)

            if (failure) {
                reportOutput(context.getString(R.string.failed_to_compile, T::class.simpleName))
            }

            reportInfo(context.getString(R.string.successfully_run, T::class.simpleName))
        }
    }

    private fun compileJavaCode() {
        compileTask<JavaCompileTask>(context.getString(R.string.compiling_java))
    }

    private fun compileJar() {
        compileTask<JarTask>(context.getString(R.string.assembling_jar))
    }

    private fun compileKotlinCode() {
        compileTask<KotlinCompiler>(context.getString(R.string.compiling_kotlin))
    }

    private fun convertClassFilesToDexFormat() {
        compileTask<D8Task>(context.getString(R.string.compiling_class_files_to_dex))
    }

    sealed class BuildStatus {
        data object STARTED : BuildStatus()
        data object FINISHED : BuildStatus()
    }
}
