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

package com.andihasan7.kartikaide.util

import android.util.Log
import com.intellij.core.JavaCoreApplicationEnvironment
import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import andihasan7.kartikaide.completion.java.parser.CompletionProvider
import andihasan7.kartikaide.common.Prefs
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.load.kotlin.KotlinBinaryClassCache
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtFile

object FileFactoryProvider {
    const val TAG = "FileFactoryProvider"

    val env = JavaCoreProjectEnvironment(
        {
            Log.i(TAG, "JavaCoreProjectEnvironment disposed")
        },
        JavaCoreApplicationEnvironment {
            Log.i(TAG, "JavaCoreApplicationEnvironment disposed")
        })

    val kotlinEnv = KotlinCoreEnvironment.createForProduction(
        {
            Log.i(TAG, "KotlinCoreEnvironment disposed")
        },
        CompilerConfiguration().apply {
            put(
                CommonConfigurationKeys.MODULE_NAME,
                JvmProtoBufUtil.DEFAULT_MODULE_NAME
            )
            // Fix: Use PSI instead of binary reading to avoid KotlinBinaryClassCache NPE on Android
            put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)
            val languageVersionSettings = LanguageVersionSettingsImpl(
                LanguageVersion.fromVersionString(Prefs.kotlinVersion)!!,
                ApiVersion.createByLanguageVersion(LanguageVersion.fromVersionString(Prefs.kotlinVersion)!!),
                mapOf(
                    AnalysisFlags.extendedCompilerChecks to false,
                    AnalysisFlags.ideMode to true,
                    AnalysisFlags.skipMetadataVersionCheck to true,
                    AnalysisFlags.skipPrereleaseCheck to true
                )
            )
            put(
                CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                languageVersionSettings
            )
        },
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    ).apply {
        // Fix: Initialize KotlinBinaryClassCache manually if not already present in the application
        val application = ApplicationManager.getApplication()
        if (application != null) {
            if (application.getService(KotlinBinaryClassCache::class.java) == null) {
                try {
                    val componentManagerClass = Class.forName("com.intellij.openapi.components.ComponentManager")
                    val registerServiceMethod = componentManagerClass.getDeclaredMethod("registerService", Class::class.java, Class::class.java)
                    registerServiceMethod.isAccessible = true
                    registerServiceMethod.invoke(application, KotlinBinaryClassCache::class.java, KotlinBinaryClassCache::class.java)
                    Log.i(TAG, "Registered KotlinBinaryClassCache application service")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register KotlinBinaryClassCache service", e)
                }
            }
        }
    }

    val fileFactory by lazy {
        PsiFileFactory.getInstance(env.project)
    }

    init {
        CompletionProvider.registerExtensions(env.project.extensionArea)
    }

    fun getPsiJavaFile(fileName: String, code: String): PsiJavaFile {
        return fileFactory.createFileFromText(fileName, JavaLanguage.INSTANCE, code) as PsiJavaFile
    }

    fun getKtPsiFile(fileName: String, code: String): KtFile {
        return PsiManager.getInstance(kotlinEnv.project)
            .findFile(
                LightVirtualFile(fileName, KotlinFileType.INSTANCE, code)
            ) as KtFile
    }
}
