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
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.databinding.FragmentCompileInfoBinding
import com.andihasan7.kartikaide.editor.EditorInputStream
import com.andihasan7.kartikaide.util.ProjectHandler
import com.andihasan7.kartikaide.util.makeDexReadOnlyIfNeeded
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                        // Just re-run the same class
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
            binding.infoEditor.setText("classes.dex not found")
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
                    binding.infoEditor.setText("Error reading dex: ${e.message}")
                }
                return@launch
            }

            // Find classes that have a main method (either parameterless or with String[] args)
            val mainClasses = dexFile.classes.filter { classDef ->
                classDef.methods.any { method ->
                    method.name == "main" && (
                        method.parameterTypes.isEmpty() || 
                        (method.parameterTypes.size == 1 && method.parameterTypes[0] == "[Ljava/lang/String;")
                    )
                }
            }.map { it.type.substring(1, it.type.length - 1) }

            withContext(Dispatchers.Main) {
                if (mainClasses.isEmpty()) {
                    binding.infoEditor.setText("No classes with main method found")
                    return@withContext
                }

                Log.d("ProjectOutput", "Classes with main: $mainClasses")

                var index: String? = null
                val targetFile = ProjectHandler.clazz
                
                if (targetFile != null) {
                    // targetFile is something like "com/test/Say.kt" or "Main.java"
                    val targetName = targetFile.substringBeforeLast('.').replace('\\', '/')
                    Log.d("ProjectOutput", "Searching for target: $targetName")

                    // Match by full path or just the class name suffix
                    index = mainClasses.find { it == targetName || it == "${targetName}Kt" }
                        ?: mainClasses.find { it.endsWith("/$targetName") || it.endsWith("/${targetName}Kt") }
                    
                    // Keep the target class for reloads, but only if found
                    if (index != null) {
                        currentRunningClass = index
                    }
                }

                if (index == null) {
                    // Fallback to Main or MainKt
                    index = mainClasses.find { it.endsWith("/Main") || it == "Main" || it.endsWith("/MainKt") || it == "MainKt" }
                        ?: mainClasses.firstOrNull()
                    currentRunningClass = index
                }

                if (index != null) {
                    Log.d("ProjectOutput", "Selected class to run: $index")
                    runClass(index)
                } else {
                    binding.infoEditor.setText("Could not determine which class to run")
                }
            }
        }
    }

    fun runClass(className: String) = lifecycleScope.launch(Dispatchers.IO) {
        val inputStream = EditorInputStream(binding.infoEditor)
        val systemOut = PrintStream(object : OutputStream() {
            override fun write(p0: Int) {
                val text = binding.infoEditor.text
                lifecycleScope.launch {
                    text.insert(
                        text.lineCount - 1,
                        text.getColumnCount(text.lineCount - 1),
                        p0.toChar().toString()
                    )
                    // Update input stream offset so it doesn't read the output as input
                    inputStream.updateOffset(text.length)
                }
            }
        })
        System.setOut(systemOut)
        System.setErr(systemOut)
        System.setIn(inputStream)

        val loader = MultipleDexClassLoader(classLoader = javaClass.classLoader!!)

        loader.loadDex(makeDexReadOnlyIfNeeded(project.binDir.resolve("classes.dex")))

        project.buildDir.resolve("libs").listFiles()?.filter { it.extension == "dex" }?.forEach {
            loader.loadDex(makeDexReadOnlyIfNeeded(it))
        }

        runCatching {
            loader.loader.loadClass(className.replace('/', '.'))
        }.onSuccess { clazz ->
            isRunning = true
            System.setProperty("project.dir", project.root.absolutePath)
            
            val mainMethod = findMainMethod(clazz)
            
            if (mainMethod != null) {
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
                    e.printStackTrace()
                }
            } else {
                System.err.println("No valid main method found in $className")
            }
        }.onFailure { e ->
            System.err.println("Error loading class $className: ${e.message}")
        }.also {
            systemOut.close()
            System.`in`.close()
            isRunning = false
        }
    }

    private fun findMainMethod(clazz: Class<*>): Method? {
        // 1. Coba cari public static void main(String[] args) - Java Standard
        try {
            val m = clazz.getDeclaredMethod("main", Array<String>::class.java)
            if (Modifier.isPublic(m.modifiers)) return m
        } catch (e: NoSuchMethodException) {}

        // 2. Coba cari public static void main() - Kotlin parameterless main
        try {
            val m = clazz.getDeclaredMethod("main")
            if (Modifier.isPublic(m.modifiers)) return m
        } catch (e: NoSuchMethodException) {}

        // 3. Cari method bernama "main" secara manual (untuk kasus non-static atau visibility lain yang mungkin)
        return clazz.declaredMethods.firstOrNull {
            it.name == "main" && 
            (it.parameterCount == 0 || (it.parameterCount == 1 && it.parameterTypes[0] == Array<String>::class.java))
        }
    }
}
