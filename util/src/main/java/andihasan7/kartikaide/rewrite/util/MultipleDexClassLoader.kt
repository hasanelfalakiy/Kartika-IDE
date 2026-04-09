/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package andihasan7.kartikaide.rewrite.util

import dalvik.system.BaseDexClassLoader
import java.io.File
import java.net.URL

/**
 * Basically a class to load multiple dex files
 *
 * @source https://github.com/Blokkok/blokkok-modsys/blob/main/module-system/src/main/java/com/blokkok/modsys/MultipleDexClassLoader.kt
 * Basically a class to load multiple dex files and resources from directories
 */
class MultipleDexClassLoader(
    private val librarySearchPath: String? = null,
    classLoader: ClassLoader = ClassLoader.getSystemClassLoader()
) {
    val resourceDirs = mutableListOf<File>()

    val loader: BaseDexClassLoader by lazy {
        object : BaseDexClassLoader("", null, librarySearchPath, classLoader) {
            override fun findResource(name: String?): URL? {
                if (name == null) return null
                for (dir in resourceDirs) {
                    val file = File(dir, name)
                    if (file.exists()) {
                        return file.toURI().toURL()
                    }
                }
                return super.findResource(name)
            }
        }
    }

    private val addDexPath = BaseDexClassLoader::class.java
        .getMethod("addDexPath", String::class.java)

    fun loadDex(dexPath: String): BaseDexClassLoader {
        addDexPath.invoke(loader, dexPath)
        return loader
    }

    fun loadDex(dexFile: File) {
        loadDex(dexFile.absolutePath)
    }

    fun addResourceDir(dir: File) {
        resourceDirs.add(dir)
    }

    companion object {
        @JvmStatic
        val INSTANCE = MultipleDexClassLoader(classLoader = Companion::class.java.classLoader!!)
    }
}
