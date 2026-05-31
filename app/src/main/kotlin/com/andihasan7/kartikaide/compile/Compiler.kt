/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, either WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
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
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
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
            setupEnvironment()
            ensureKotlinServicesRegistered()
            CompilerCache.saveCache(JavaCompileTask(project))
            CompilerCache.saveCache(KotlinCompiler(project))
            CompilerCache.saveCache(D8Task(project))
            CompilerCache.saveCache(JarTask(project))
        }

        private fun setupEnvironment() {
            try {
                suppressLoggerErrors()
                setIdeaIoUseFallback()
                setupIdeaStandaloneExecution()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to setup Kotlin environment: ${e.message}")
            }
        }

        /**
         * Memastikan service Kotlin terdaftar untuk menghindari NullPointerException
         * pada KotlinBinaryClassCache di Android.
         */
        private fun ensureKotlinServicesRegistered() {
            registerExtensionPoints()
            try {
                val application = ApplicationManager.getApplication()
                if (application != null) {
                    if (application.getService(KotlinBinaryClassCache::class.java) == null) {
                        // Fix: Use implementation class instead of interface to find registerService method
                        // as it might not be defined in the ComponentManager interface in some versions.
                        val registerServiceMethod = application.javaClass.methods.find {
                            it.name == "registerService" && it.parameterCount >= 2 &&
                                    it.parameterTypes[0] == Class::class.java &&
                                    it.parameterTypes[1] == Class::class.java
                        }

                        if (registerServiceMethod != null) {
                            registerServiceMethod.isAccessible = true
                            val args = if (registerServiceMethod.parameterCount == 2) {
                                arrayOf(KotlinBinaryClassCache::class.java, KotlinBinaryClassCache::class.java)
                            } else {
                                arrayOf(KotlinBinaryClassCache::class.java, KotlinBinaryClassCache::class.java, false)
                            }
                            registerServiceMethod.invoke(application, *args)
                            Log.i(TAG, "Successfully registered KotlinBinaryClassCache via reflection")
                        } else {
                            Log.w(TAG, "registerService method not found on ${application.javaClass.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register KotlinBinaryClassCache: ${e.message}")
            }
        }

        private fun suppressLoggerErrors() {
            try {
                val factoryField = Logger::class.java.getDeclaredField("ourFactory")
                factoryField.isAccessible = true
                val originalFactory = factoryField.get(null) as? Logger.Factory
                
                val newFactory = object : Logger.Factory {
                    override fun getLoggerInstance(category: String): Logger {
                        val baseLogger = originalFactory?.getLoggerInstance(category) 
                            ?: DefaultLogger(category)
                        
                        return object : DefaultLogger(category) {
                            override fun isDebugEnabled(): Boolean = baseLogger.isDebugEnabled
                            override fun debug(message: String?) = baseLogger.debug(message)
                            override fun debug(t: Throwable?) = baseLogger.debug(t)
                            override fun debug(message: String?, t: Throwable?) = baseLogger.debug(message, t)
                            override fun info(message: String?) = baseLogger.info(message)
                            override fun info(message: String?, t: Throwable?) = baseLogger.info(message, t)
                            override fun warn(message: String?, t: Throwable?) = baseLogger.warn(message, t)
                            override fun error(message: String?, t: Throwable?, vararg details: String?) {
                                if (message?.contains("Listeners not allowed") == true || 
                                    message?.contains("Missing extension point") == true ||
                                    message?.contains("KotlinBinaryClassCache") == true) {
                                    return
                                }
                                baseLogger.error(message, t, *details)
                            }
                        }
                    }
                }
                factoryField.set(null, newFactory)
                Log.i(TAG, "Injected custom Logger factory to suppress EP errors")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to suppress logger errors", e)
            }
        }

        private fun registerExtensionPoints() {
            try {
                @Suppress("DEPRECATION")
                val rootArea = try { Extensions.getRootArea() } catch (e: Throwable) { null }
                val appArea = try { ApplicationManager.getApplication()?.extensionArea } catch (e: Throwable) { null }
                
                val areas = mutableSetOf<Any>()
                if (rootArea != null) areas.add(rootArea)
                if (appArea != null) areas.add(appArea)
                
                for (area in areas) {
                    registerEP(area, "com.intellij.psi.classFileDecompiler", "com.intellij.psi.ClassFileDecompiler")
                    registerEP(area, "com.intellij.psi.treeCopyHandler", "com.intellij.psi.impl.PsiTreeCopyHandler")
                    registerEP(area, "com.intellij.lang.meta.documentationKindProvider", "com.intellij.lang.meta.DocumentationKindProvider")
                    registerEP(area, "com.intellij.openapi.fileTypes.FileTypeDetector", "com.intellij.openapi.fileTypes.FileTypeDetector")
                    registerEP(area, "com.intellij.psi.clsCustomNavigationPolicy", "com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy")
                    registerEP(area, "com.intellij.psi.augmentProvider", "com.intellij.psi.augment.PsiAugmentProvider")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to register extension points: ${e.message}")
            }
        }

        private fun registerEP(area: Any, name: String, className: String) {
            try {
                val areaClass = area.javaClass
                val hasEPMethod = areaClass.getMethod("hasExtensionPoint", String::class.java)
                val getEPMethod = areaClass.getMethod("getExtensionPoint", String::class.java)
                
                if (hasEPMethod.invoke(area, name) == true) {
                    val ep = getEPMethod.invoke(area, name)
                    if (ep != null) {
                        forceAllowListeners(ep)
                    }
                } else {
                    val registerMethod = areaClass.methods.find {
                        it.name == "registerExtensionPoint" && it.parameterCount >= 3
                    }
                    if (registerMethod != null) {
                        registerMethod.isAccessible = true
                        val kindClass = Class.forName("com.intellij.openapi.extensions.ExtensionPoint\$Kind")
                        val interfaceKind = kindClass.getField("INTERFACE").get(null)
                        
                        when (registerMethod.parameterCount) {
                            3 -> registerMethod.invoke(area, name, className, interfaceKind)
                            4 -> registerMethod.invoke(area, name, className, interfaceKind, true)
                        }
                        
                        val ep = getEPMethod.invoke(area, name)
                        if (ep != null) {
                            forceAllowListeners(ep)
                        }
                    }
                }
            } catch (e: Throwable) {
                // Ignore errors during EP registration
            }
        }

        private fun forceAllowListeners(ep: Any) {
            try {
                var current: Class<*>? = ep.javaClass
                while (current != null) {
                    for (field in current.declaredFields) {
                        if (field.name == "myAllowListeners" || field.name == "allowListeners") {
                            field.isAccessible = true
                            field.set(ep, true)
                        }
                    }
                    current = current.superclass
                }
            } catch (e: Throwable) {}
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
        setupEnvironment() // Pastikan lingkungan siap sebelum kompilasi
        ensureKotlinServicesRegistered()
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
                     errorStr.contains("NullPointerException") ||
                     errorStr.contains("AssertionError: Listeners not allowed") ||
                     errorStr.contains("Listeners not allowed"))) {
                    
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
                    setupEnvironment()
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
