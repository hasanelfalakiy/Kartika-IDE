/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import andihasan7.kartikaide.common.BaseBindingFragment
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.MultipleDexClassLoader
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.databinding.FragmentCompileInfoBinding
import com.andihasan7.kartikaide.editor.EditorInputStream
import com.andihasan7.kartikaide.extension.setFont
import com.andihasan7.kartikaide.util.PreferenceKeys
import com.andihasan7.kartikaide.util.ProjectHandler
import com.andihasan7.kartikaide.util.makeDexReadOnlyIfNeeded
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class ProjectOutputFragment : BaseBindingFragment<FragmentCompileInfoBinding>() {
    val project: Project = ProjectHandler.getProject()
        ?: throw IllegalStateException("No project set")
    var isRunning: Boolean = false
    private var currentRunningClass: String? = null

    override fun getViewBinding() = FragmentCompileInfoBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.inflateMenu(R.menu.output_menu)
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.reload -> {
                    if (isRunning) {
                        currentRunningClass?.let { className ->
                            binding.infoEditor.text.insert(
                                binding.infoEditor.text.lineCount - 1,
                                0,
                                "\n--- Restarting ---\n"
                            )
                            runClass(className)
                        }
                    } else {
                        checkClasses()
                    }
                    true
                }

                R.id.cancel -> {
                    parentFragmentManager.commit {
                        remove(this@ProjectOutputFragment)
                    }
                    true
                }

                else -> false
            }
        }

        binding.infoEditor.apply {
            setEditorLanguage(TextMateLanguage.create("source.build", false))
            setFont()
            isEditable = true
        }

        binding.toolbar.title = "Running ${project.name}"
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.commit {
                remove(this@ProjectOutputFragment)
            }
        }

        binding.infoEditor.postDelayed(::checkClasses, 250)
    }

    fun checkClasses() {
        val dex = project.binDir.resolve("classes.dex")
        if (!dex.exists()) {
            binding.infoEditor.setText("Error: classes.dex not found")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val dexFile = try {
                val bufferedInputStream = dex.inputStream().buffered()
                val df = DexBackedDexFile.fromInputStream(Opcodes.forApi(33), bufferedInputStream)
                bufferedInputStream.close()
                df
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.infoEditor.setText("Error: failed to read dex: ${e.message}")
                }
                return@launch
            }

            // Mencari kelas yang memiliki main() ATAU metode dengan anotasi @Test
            val runnableClasses = dexFile.classes.filter { classDef ->
                classDef.methods.any { method ->
                    // Cek fungsi main
                    val isMain = method.name == "main" && (
                        method.parameterTypes.isEmpty() || 
                        (method.parameterTypes.size == 1 && method.parameterTypes[0] == "[Ljava/lang/String;")
                    )
                    
                    // Cek anotasi @Test (JUnit 4: Lorg/junit/Test; JUnit 5: Lorg/junit/jupiter/api/Test;)
                    val hasTestAnnotation = method.annotations.any { annot ->
                        annot.type.contains("org/junit/Test") || annot.type.contains("org/junit/jupiter/api/Test")
                    }

                    isMain || hasTestAnnotation
                }
            }.map { it.type.substring(1, it.type.length - 1) }

            withContext(Dispatchers.Main) {
                var index: String? = null
                val targetFile = ProjectHandler.clazz
                
                if (targetFile != null) {
                    val targetName = targetFile.substringBeforeLast('.').replace('\\', '/')
                    // Try exact match or with Kt suffix for Kotlin
                    index = runnableClasses.find { it == targetName || it == "${targetName}Kt" }
                        ?: runnableClasses.find { it.endsWith("/$targetName") || it.endsWith("/${targetName}Kt") }
                    
                    // Jika tidak ditemukan di runnableClasses tapi user sedang membuka file tersebut, 
                    // paksa gunakan itu (mungkin dex belum update atau ada alasan lain)
                    if (index == null) {
                        index = targetName.replace('/', '.')
                    }
                    
                    currentRunningClass = index
                }

                if (index == null) {
                    if (runnableClasses.isEmpty()) {
                        binding.infoEditor.setText("Error: No runnable classes or tests found")
                        return@withContext
                    }
                    // Prioritas: Main, lalu Test, lalu apa saja
                    index = runnableClasses.find { it.endsWith("/Main") || it == "Main" || it.endsWith("/MainKt") || it == "MainKt" }
                        ?: runnableClasses.find { it.endsWith("Test") }
                        ?: runnableClasses.firstOrNull()
                    currentRunningClass = index
                }

                if (index != null) {
                    runClass(index)
                } else {
                    binding.infoEditor.setText("Warning: Could not determine which class to run")
                }
            }
        }
    }

    fun runClass(className: String) = lifecycleScope.launch(Dispatchers.IO) {
        // Set properti SECEPAT MUNGKIN, sebelum memuat kelas apa pun
        val projectRootPath = project.root.absolutePath
        
        // Simpan state lama
        val props = System.getProperties()
        val oldUserDir = props.getProperty("user.dir")
        val oldProjectDir = props.getProperty("project.dir")
        val oldTmpDir = props.getProperty("java.io.tmpdir")
        val oldProjectRoot = props.getProperty("PROJECT_ROOT")

        // Suntikkan Properti Kustom yang aman dan stabil di Android
        props.setProperty("user.dir", projectRootPath)
        props.setProperty("project.dir", projectRootPath)
        props.setProperty("PROJECT_ROOT", projectRootPath)
        
        if (!project.cacheDir.exists()) project.cacheDir.mkdirs()
        props.setProperty("java.io.tmpdir", project.cacheDir.absolutePath)

        val inputStream = EditorInputStream(binding.infoEditor)
        val systemOut = PrintStream(object : OutputStream() {
            private val bos = ByteArrayOutputStream()

            override fun write(p0: Int) {
                bos.write(p0)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                bos.write(b, off, len)
            }

            override fun flush() {
                val bytes = bos.toByteArray()
                if (bytes.isEmpty()) return
                val s = String(bytes, Charsets.UTF_8)
                bos.reset()
                lifecycleScope.launch(Dispatchers.Main) {
                    val text = binding.infoEditor.text
                    val line = text.lineCount - 1
                    val column = text.getColumnCount(line)
                    text.insert(line, column, s)
                    inputStream.updateOffset(text.length)
                }
            }
        }, true)
        
        val oldOut = System.out
        val oldErr = System.err
        val oldIn = System.`in`
        val oldContextClassLoader = Thread.currentThread().contextClassLoader
        
        System.setOut(systemOut)
        System.setErr(systemOut)
        System.setIn(inputStream)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean(PreferenceKeys.CONSOLE_SHOW_ROOT_INFO, true)) {
            systemOut.println("Info: Project Root -> $projectRootPath")
            systemOut.println("Info: PROJECT_ROOT is initialized for this session.")
            systemOut.println(" ")
        }

        val loader = MultipleDexClassLoader(classLoader = javaClass.classLoader!!)

        val mainDex = project.binDir.resolve("classes.dex")
        if (mainDex.exists()) {
            loader.loadDex(makeDexReadOnlyIfNeeded(mainDex))
        }

        project.buildDir.resolve("libs").listFiles()?.filter { it.extension == "dex" }?.forEach {
            loader.loadDex(makeDexReadOnlyIfNeeded(it))
        }
        
        if (project.resourcesDir.exists()) {
            loader.addResourceDir(project.resourcesDir)
        }

        Thread.currentThread().contextClassLoader = loader.loader

        runCatching {
            loader.loader.loadClass(className.replace('/', '.'))
        }.onSuccess { clazz ->
            isRunning = true
            
            // Anggap ini test jika parameter --test ada ATAU jika kelas mengandung anotasi @Test
            val hasTestAnnotation = clazz.methods.any { m -> 
                m.annotations.any { 
                    val name = it.annotationClass.java.name
                    name.contains("org.junit.Test") || name.contains("org.junit.jupiter.api.Test") 
                }
            }
            val isTest = project.args.contains("--test") || hasTestAnnotation
            
            if (isTest) {
                systemOut.println("Running Unit Test: ${clazz.name}\n")
                try {
                    val junitCoreClass = loader.loader.loadClass("org.junit.runner.JUnitCore")
                    val runClassesMethod = junitCoreClass.getMethod("runClasses", Class.forName("[Ljava.lang.Class;"))
                    val result = runClassesMethod.invoke(null, arrayOf(clazz))
                    
                    val wasSuccessfulMethod = result.javaClass.getMethod("wasSuccessful")
                    val getFailureCountMethod = result.javaClass.getMethod("getFailureCount")
                    val getRunCountMethod = result.javaClass.getMethod("getRunCount")
                    val getIgnoreCountMethod = result.javaClass.getMethod("getIgnoreCount")
                    val getRunTimeMethod = result.javaClass.getMethod("getRunTime")
                    val getFailuresMethod = result.javaClass.getMethod("getFailures")
                    
                    val wasSuccessful = wasSuccessfulMethod.invoke(result) as Boolean
                    val failureCount = getFailureCountMethod.invoke(result) as Int
                    val runCount = getRunCountMethod.invoke(result) as Int
                    val ignoreCount = getIgnoreCountMethod.invoke(result) as Int
                    val runTime = getRunTimeMethod.invoke(result) as Long
                    
                    systemOut.println("\n--- Test Results ---")
                    systemOut.println("Run count: $runCount")
                    systemOut.println("Failure count: $failureCount")
                    systemOut.println("Ignore count: $ignoreCount")
                    systemOut.println("Time: ${runTime}ms")
                    
                    if (!wasSuccessful) {
                        systemOut.println("\nFailures:")
                        val failures = getFailuresMethod.invoke(result) as List<*>
                        for (failure in failures) {
                            systemOut.println("- ${failure.toString()}")
                            val getExceptionMethod = failure?.javaClass?.getMethod("getException")
                            val exception = getExceptionMethod?.invoke(failure) as Throwable
                            exception.printStackTrace(systemOut)
                        }
                    } else {
                        systemOut.println("\nAll tests passed!")
                    }
                } catch (e: ClassNotFoundException) {
                    systemOut.println("Error: JUnit not found in classpath.")
                } catch (e: Throwable) {
                    systemOut.println("\nError: --- Test Execution Error ---\n")
                    (e.cause ?: e).printStackTrace(systemOut)
                }
            } else {
                val mainMethod = findMainMethod(clazz)
                if (mainMethod != null) {
                    // Pastikan properti tetap ada tepat sebelum pemanggilan main
                    System.setProperty("PROJECT_ROOT", projectRootPath)
                    System.setProperty("user.dir", projectRootPath)
                    
                    try {
                        if (Modifier.isStatic(mainMethod.modifiers)) {
                            if (mainMethod.parameterCount == 1) {
                                mainMethod.invoke(null, project.args.toTypedArray())
                            } else {
                                mainMethod.invoke(null)
                            }
                        } else {
                            val instance = clazz.getDeclaredConstructor().newInstance()
                            if (mainMethod.parameterCount == 1) {
                                mainMethod.invoke(instance, project.args.toTypedArray())
                            } else {
                                mainMethod.invoke(instance)
                            }
                        }
                    } catch (e: Throwable) {
                        val cause = e.cause ?: e
                        if (cause is java.io.FileNotFoundException && (cause.message?.contains("EROFS") == true || cause.message?.contains("Permission denied") == true)) {
                            val fileName = cause.message?.substringBefore(":")?.trim() ?: "file"
                            systemOut.println("\nError:--- ANDROID RESTRICTION ---")
                            systemOut.println("Android blocks relative writes to root '/'.")
                            systemOut.println("\nFIX: Use the injected PROJECT_ROOT property in your code:")
                            systemOut.println("val root = System.getProperty(\"PROJECT_ROOT\")")
                            systemOut.println("val file = File(root, \"$fileName\")")
                            systemOut.println("\nThis will dynamically use your current project folder.\n")
                        }
                        systemOut.println("\nError: --- Execution Error ---\n")
                        cause.printStackTrace(systemOut)
                    }
                } else {
                    systemOut.println("Error: No valid main method found in $className")
                }
            }
        }.onFailure { e ->
            systemOut.println("Error: --- Execution Error ---")
            systemOut.println("Error loading class $className: ${e.message}")
            e.printStackTrace(systemOut)
        }.also {
            // Restore original environment
            System.setProperty("user.dir", oldUserDir ?: "/")
            System.setProperty("project.dir", oldProjectDir ?: "")
            System.setProperty("java.io.tmpdir", oldTmpDir ?: "/tmp")
            if (oldProjectRoot != null) System.setProperty("PROJECT_ROOT", oldProjectRoot) else System.clearProperty("PROJECT_ROOT")

            systemOut.flush()
            System.setOut(oldOut)
            System.setErr(oldErr)
            System.setIn(oldIn)
            Thread.currentThread().contextClassLoader = oldContextClassLoader
            isRunning = false
        }
    }

    private fun findMainMethod(clazz: Class<*>): Method? {
        try {
            val m = clazz.getDeclaredMethod("main", Array<String>::class.java)
            if (Modifier.isPublic(m.modifiers)) return m
        } catch (e: NoSuchMethodException) {}

        try {
            val m = clazz.getDeclaredMethod("main")
            if (Modifier.isPublic(m.modifiers)) return m
        } catch (e: NoSuchMethodException) {}

        return clazz.declaredMethods.firstOrNull {
            it.name == "main" && 
            (it.parameterCount == 0 || (it.parameterCount == 1 && it.parameterTypes[0] == Array<String>::class.java))
        }
    }
}
