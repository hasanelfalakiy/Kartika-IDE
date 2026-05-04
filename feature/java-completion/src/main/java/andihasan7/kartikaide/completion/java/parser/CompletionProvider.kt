/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package andihasan7.kartikaide.completion.java.parser

import andihasan7.kartikaide.completion.java.parser.cache.SymbolCacher
import andihasan7.kartikaide.completion.java.parser.cache.qualifiedName
import andihasan7.kartikaide.editor.EditorCompletionItem
import andihasan7.kartikaide.rewrite.util.FileUtil
import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.intellij.core.JavaCoreApplicationEnvironment
import com.intellij.core.JavaCoreProjectEnvironment
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiElementFinder
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiImportStaticReferenceElement
import com.intellij.psi.PsiImportStaticStatement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaToken
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import javassist.CtClass
import javassist.Modifier
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution

class CompletionProvider {

    companion object {
        private val logger = java.util.logging.Logger.getLogger(CompletionProvider::class.java.name)

        // Gunakan lazy agar inisialisasi lingkungan IntelliJ aman dari konflik thread dengan Kotlin
        val environment by lazy {
            val disposable = com.intellij.openapi.util.Disposer.newDisposable()
            // unitTestMode = false agar tidak membersihkan Extensions.RootArea yang dipakai Kotlin
            val appEnv = object : JavaCoreApplicationEnvironment(disposable, false) {}
            JavaCoreProjectEnvironment(disposable, appEnv)
        }
        
        val symbolCacher by lazy {
            SymbolCacher(FileUtil.classpathDir.resolve("android.jar")).apply {
                loadClassesFromJar()
            }
        }

        val fileFactory by lazy {
            PsiFileFactory.getInstance(environment.project)
        }

        val javaKeywords = arrayOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", 
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", 
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", 
            "interface", "long", "native", "new", "package", "private", "protected", "public", 
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", 
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null"
        )

        private fun suppressLoggerErrors() {
            try {
                val loggerClass = Logger::class.java
                val factoryField = loggerClass.getDeclaredField("ourFactory")
                factoryField.isAccessible = true
                val originalFactory = factoryField.get(null) as? Logger.Factory
                
                // Hindari pembungkusan ganda jika sudah menggunakan logger khusus dari proyek ini
                if (originalFactory?.javaClass?.name?.contains("kartikaide") == true) return

                val newFactory = object : Logger.Factory {
                    override fun getLoggerInstance(category: String): Logger {
                        val baseLogger = originalFactory?.getLoggerInstance(category) 
                            ?: DefaultLogger(category)
                        return object : DefaultLogger(category) {
                            override fun error(message: String?, t: Throwable?, vararg details: String?) {
                                if (message?.contains("Listeners not allowed") == true || 
                                    message?.contains("Missing extension point") == true ||
                                    message?.contains("KotlinBinaryClassCache") == true) return
                                baseLogger.error(message, t, *details)
                            }
                            override fun warn(message: String?, t: Throwable?) {
                                if (message?.contains("Listeners not allowed") == true) return
                                baseLogger.warn(message, t)
                            }
                        }
                    }
                }
                factoryField.set(null, newFactory)
                
                // Bersihkan cache logger agar menggunakan factory baru
                try {
                    val ourLoggersField = loggerClass.getDeclaredField("ourLoggers")
                    ourLoggersField.isAccessible = true
                    val ourLoggers = ourLoggersField.get(null) as? MutableMap<*, *>
                    ourLoggers?.clear()
                } catch (e: Exception) {}
            } catch (e: Throwable) {}
        }

        private fun registerEP(area: com.intellij.openapi.extensions.ExtensionsArea, name: String, className: String) {
            try {
                if (area.hasExtensionPoint(name)) {
                    val ep = area.getExtensionPoint<Any>(name)
                    forceAllowListeners(ep)
                } else {
                    area.registerExtensionPoint(name, className, ExtensionPoint.Kind.INTERFACE)
                    forceAllowListeners(area.getExtensionPoint<Any>(name))
                }
            } catch (e: Throwable) {}
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

        @Suppress("DEPRECATION")
        fun registerExtensions(extensionArea: ExtensionsAreaImpl) {
            registerEP(extensionArea, "com.intellij.virtualFileManagerListener", VirtualFileManagerImpl::class.java.name)
            registerEP(extensionArea, "com.intellij.java.elementFinder", PsiElementFinder::class.java.name)
            registerEP(extensionArea, "com.intellij.psi.classFileDecompiler", "com.intellij.psi.ClassFileDecompiler")

            val rootArea = try { Extensions.getRootArea() } catch (e: Throwable) { null }
            if (rootArea != null) {
                registerEP(rootArea, "com.intellij.treeCopyHandler", TreeCopyHandler::class.java.name)
                registerEP(rootArea, "com.intellij.codeStyleManager", CodeStyleManager::class.java.name)
                registerEP(rootArea, "com.intellij.psiElementFactory", PsiElementFactory::class.java.name)
                registerEP(rootArea, "com.intellij.lang.psiAugmentProvider", PsiAugmentProvider::class.java.name)
                registerEP(rootArea, "com.intellij.psiElementFinder", PsiElementFinder::class.java.name)
                registerEP(rootArea, "com.intellij.psi.classFileDecompiler", "com.intellij.psi.ClassFileDecompiler")
            }
        }
    }

    init {
        try {
            suppressLoggerErrors()
            setIdeaIoUseFallback()
            setupIdeaStandaloneExecution()
            registerExtensions(environment.project.extensionArea as ExtensionsAreaImpl)
            if (FileUtil.isInitialized) {
                environment.addJarToClassPath(FileUtil.classpathDir.resolve("android.jar"))
            }
        } catch (e: Throwable) {
            logger.severe("Failed to initialize CompletionProvider: ${e.message}")
        }
    }

    private fun getElementType(psiElement: PsiElement): String {
        return psiElement.javaClass.simpleName
    }

    private fun getFullyQualifiedName(referenceExpression: PsiReferenceExpression): String? {
        val referenceNameElement = referenceExpression.referenceNameElement
        if (referenceNameElement != null) {
            val resolvedElement = referenceNameElement.reference!!.resolve()
            if (resolvedElement is PsiField) {
                val containingClass = resolvedElement.containingClass
                if (containingClass != null) {
                    return containingClass.qualifiedName
                }
            }
        }
        return null
    }

    fun complete(source: String?, fileName: String?, index: Int): List<EditorCompletionItem> {
        val psiFile = fileFactory.createFileFromText(fileName!!, JavaLanguage.INSTANCE, source!!)

        // Find the element at the specified position
        val element = findElementAtOffset(psiFile, index)
        if (element == null) {
            return emptyList()
        }

        val completionItems = mutableListOf<EditorCompletionItem>()
        val isImported = isImportedClass(element)
        
        if (isImportStatementContext(element)) {
            val packageName =
                getFullyQualifiedPackageName(element)!! + if (element.text.endsWith(".")) "." else ""
            if (packageName.endsWith('.')) {
                val mPackage = packageName.substringBeforeLast('.')
                symbolCacher.getPackages()
                    .filter {
                        val parentPkgName = it.key.substringBeforeLast('.')
                        parentPkgName == mPackage
                    }
                    .map {
                        val toAdd = it.key.substringAfterLast('.')
                        completionItems.add(
                            EditorCompletionItem(
                                toAdd,
                                it.key.substringBeforeLast('.'),
                                0,
                                toAdd
                            ).kind(CompletionItemKind.Module)
                        )
                    }
            }
            symbolCacher.getClasses()
                .filter {
                    val bool = it.key.startsWith(packageName) && it.key.substringAfter(packageName)
                        .contains('.').not()
                    bool
                }.map {
                    completionItems.add(
                        EditorCompletionItem(
                            it.value.qualifiedName(),
                            it.key.substringBeforeLast('.'),
                            0,
                            it.value.qualifiedName()
                        ).kind(CompletionItemKind.Class)
                    )
                }
            return completionItems
        }

        if (element.text.endsWith('.')) {
            if (element.text.first().isUpperCase()) {
                if (element.text.count { it == '.' } > 1) {
                    if (element is PsiReferenceExpression) {
                        val className = element.text.substringBefore('.')
                        val qualified =
                            if (isImported.first) isImported.second else "java.lang.$className"

                        val ctClass = symbolCacher.getClass(qualified)
                        if (ctClass != null) {
                            val field = element.text.substringAfter("$className.").substringBefore('.')
                            if (element.text.endsWith(").")) {
                                val methodName =
                                    element.text.substringAfter("$className.").substringBefore('(')
                                ctClass.methods.find { it.name == methodName }?.let {
                                    addAllFieldAndMethods(it.returnType, completionItems)
                                }
                            }
                            ctClass.fields.find { it.name == field }?.let {
                                addAllFieldAndMethods(it.type, completionItems)
                            }
                        }
                    }
                }
                val className = element.text.substring(0, element.text.length - 1)
                val qualified = if (isImported.first) isImported.second else "java.lang.$className"

                val clazz = symbolCacher.getClass(qualified)
                if (clazz != null) {
                    addAllFieldAndMethods(clazz, completionItems, true)
                }
            } else {
                val packageName = element.text.substringBeforeLast('.')
                if (packageName.isNotEmpty()) {
                    val clazz = symbolCacher.getClass(packageName)
                    if (clazz != null) {
                        addAllFieldAndMethods(clazz, completionItems)
                    }
                }
            }
            return completionItems
        }

        val items = symbolCacher.filterClassNames(element.text)
        for (clazz in items) {
            val item = EditorCompletionItem(
                clazz.value,
                clazz.key,
                element.textLength,
                clazz.value
            ).kind(CompletionItemKind.Class)
            completionItems.add(item)
        }

        for (keyword in javaKeywords) {
            if (keyword.startsWith(element.text)) {
                completionItems.add(
                    EditorCompletionItem(
                        keyword,
                        "Keyword",
                        element.textLength,
                        keyword
                    ).kind(CompletionItemKind.Keyword)
                )
            }
        }
        return completionItems
    }

    private fun addAllFieldAndMethods(
        ctClass: CtClass,
        completionItems: MutableList<EditorCompletionItem>,
        isStatic: Boolean = false
    ) {
        ctClass.fields.forEach { field ->
            if (Modifier.isStatic(field.modifiers) || (isStatic.not() && Modifier.isPublic(field.modifiers))) {
                completionItems.add(
                    EditorCompletionItem(
                        field.name,
                        field.type.name.substringAfterLast('.'),
                        0,
                        field.name
                    ).kind(CompletionItemKind.Field)
                )
            }
        }
        ctClass.methods.forEach { method ->
            if ((isStatic.not() && Modifier.isPublic(method.modifiers)) || Modifier.isStatic(method.modifiers)) {
                completionItems.add(
                    EditorCompletionItem(
                        method.name + method.parameterTypes.joinToString(", ", "(", ")") {
                            it.simpleName
                        },
                        method.returnType.name.substringAfterLast('.'),
                        0,
                        method.name
                    ).kind(CompletionItemKind.Method)
                )
            }
        }
    }


    private fun isImportedClass(element: PsiElement): Pair<Boolean, String> {
        val psiFile = element.containingFile
        if (psiFile is PsiJavaFile) {
            val importList = psiFile.importList
            if (importList != null) {
                val className = element.text
                for (importStatement in importList.importStatements) {
                    val importedClassName = importStatement.qualifiedName
                    if (importedClassName?.substringAfterLast('.') == className.substringBefore('.')) {
                        return Pair(true, importedClassName)
                    }
                }
            }
        }
        return Pair(false, "")
    }

    fun isImportStatementContext(element: PsiElement): Boolean {
        return element is PsiImportStatement || element.parent is PsiImportList || element.parent is PsiImportStaticStatement || element.parent is PsiImportStaticReferenceElement
    }

    fun formatCode(content: String): String {
        val psiFile = fileFactory.createFileFromText("temp.java", JavaLanguage.INSTANCE, content)
        CodeStyleManager.getInstance(environment.project).reformat(psiFile)
        return psiFile.text
    }

    private fun findElementAtOffset(file: PsiFile, offset: Int): PsiElement? {
        var element = file.findElementAt(offset)
        if (element is PsiWhiteSpace || element is PsiComment) element = file.findElementAt(offset - 1)
        if (element is PsiJavaToken && element.tokenType == JavaTokenType.DOT) element = element.parent
        return element
    }

    private fun getFullyQualifiedPackageName(element: PsiElement): String? {
        if (element is PsiImportStatement) return element.importReference?.qualifiedName
        return null
    }

    fun addImport(psiFile: PsiFile, importStatement: String): String {
        val typeSolver = CombinedTypeSolver()
        val config = ParserConfiguration().setSymbolResolver(JavaSymbolSolver(typeSolver))
        val parser = JavaParser(config)
        val parsed = parser.parse(psiFile.text)
        if (parsed.result.isPresent) {
            val cu = parsed.result.get()
            val imports = cu.imports
            imports.add(ImportDeclaration(importStatement, false, false))
            cu.setImports(imports)
            return cu.toString(DefaultPrinterConfiguration())
        }
        return psiFile.text
    }
}
