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
import java.util.Collections
import java.util.Enumeration

/**
 * ClassLoader that supports multiple DEX files and resource searching in external directories.
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

            override fun findResources(name: String?): Enumeration<URL> {
                if (name == null) return Collections.emptyEnumeration()
                val urls = mutableListOf<URL>()
                for (dir in resourceDirs) {
                    val file = File(dir, name)
                    if (file.exists()) {
                        urls.add(file.toURI().toURL())
                    }
                }
                val parentResources = super.findResources(name)
                if (parentResources != null) {
                    while (parentResources.hasMoreElements()) {
                        urls.add(parentResources.nextElement())
                    }
                }
                return Collections.enumeration(urls)
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
        if (dir.exists() && dir.isDirectory && !resourceDirs.contains(dir)) {
            resourceDirs.add(dir)
        }
    }

    companion object {
        @JvmStatic
        val INSTANCE = MultipleDexClassLoader(classLoader = Companion::class.java.classLoader!!)
    }
}
