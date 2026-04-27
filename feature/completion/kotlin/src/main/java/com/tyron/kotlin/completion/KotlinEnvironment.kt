/*
 *  This file is part of CodeAssist.
 *
 *  CodeAssist is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  CodeAssist is distributed in the hope that it under the terms of the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with CodeAssist.  If not, see <https://www.gnu.org/licenses/>.
 */
@file:OptIn(FrontendInternals::class)

package com.tyron.kotlin.completion

import android.util.Log
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.tyron.kotlin.completion.codeInsight.ReferenceVariantsHelper
import com.tyron.kotlin.completion.model.Analysis
import com.tyron.kotlin.completion.util.IdeDescriptorRenderersScripting
import com.tyron.kotlin.completion.util.getResolutionScope
import com.tyron.kotlin.completion.util.importableFqName
import com.tyron.kotlin.completion.util.isVisible
import com.tyron.kotlin.completion.util.logTime
import com.tyron.kotlin_completion.util.PsiUtils
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import andihasan7.kartikaide.common.Prefs
import andihasan7.kartikaide.editor.EditorCompletionItem
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.FileUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.CommonCompilerPerformanceManager
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.kotlin.KotlinBinaryClassCache
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisContext
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.util.KotlinFrontEndException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

data class KotlinEnvironment(
    val kotlinEnvironment: KotlinCoreEnvironment
) {
    val kotlinFiles = ConcurrentHashMap<String, KotlinFile>()
    private val analysisLock = Any()

    fun updateKotlinFile(name: String, contents: String): KotlinFile {
        val kotlinFile = KotlinFile.from(kotlinEnvironment.project, name, contents)
        kotlinFiles[name] = kotlinFile
        return kotlinFile
    }

    private data class DescriptorInfo(
        val isTipsManagerCompletion: Boolean,
        val descriptors: List<DeclarationDescriptor>
    )

    private val renderer =
        IdeDescriptorRenderersScripting.SOURCE_CODE.withOptions {
            classifierNamePolicy = ClassifierNamePolicy.SHORT
            typeNormalizer = IdeDescriptorRenderersScripting.APPROXIMATE_FLEXIBLE_TYPES
            parameterNameRenderingPolicy = ParameterNameRenderingPolicy.ALL
            typeNormalizer = { if (it.isFlexible()) it.asFlexibleType().upperBound else it }
        }


    data class CodeIssue(
        val startOffset: Int,
        val endOffset: Int,
        val message: String,
        val severity: CompilerMessageSeverity
    )

    private var issueListener = { _: CodeIssue -> }

    fun addIssueListener(listener: (issue: CodeIssue) -> Unit) {
        issueListener = listener
    }

    private val messageCollector = object : MessageCollector {
        private var hasError = false
        override fun clear() {}

        override fun hasErrors() = hasError

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            if (location == null) {
                Log.d("KotlinEnvironment", message)
                return
            }
            if (severity.isError) {
                hasError = true
            }
            val kotlinFile = kotlinFiles[location.path.substring(1)]
            if (kotlinFile == null) {
                Log.d("KotlinEnvironment", "no kotlin file for ${location.path}")
                return
            }
            val issue = CodeIssue(
                kotlinFile.offsetFor(location.line - 1, location.column - 1),
                kotlinFile.offsetFor(location.lineEnd - 1, location.columnEnd - 1),
                message,
                severity
            )
            issueListener(issue)
        }
    }

    init {
        kotlinEnvironment.configuration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY) ?: run {
            kotlinEnvironment.configuration.put(
                CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                messageCollector
            )
        }
    }

    var analysis: TopDownAnalysisContext? = null

    /**
     * Tries to get the prefix based on PSI element, falls back to text-based prefix if PSI fails.
     */
    private fun getPrefix(element: PsiElement?, file: KotlinFile, line: Int, character: Int): String {
        // Method 1: PSI-based
        var prefix = ""
        if (element != null) {
            val text = (element as? KtSimpleNameExpression)?.text
                ?: PsiUtils.findParent(element)?.text
                ?: element.text
            prefix = (text ?: "").substringBefore(COMPLETION_SUFFIX)
            if (prefix.endsWith(".")) prefix = ""
        }

        // Method 2: Text-based fallback if PSI prefix is empty but we know there's text before cursor
        if (prefix.isEmpty()) {
            val fullText = file.kotlinFile.text
            val offset = file.offsetFor(line, character)
            var i = offset - 1
            val sb = StringBuilder()
            while (i >= 0 && i < fullText.length) {
                val c = fullText[i]
                if (Character.isJavaIdentifierPart(c)) {
                    sb.insert(0, c)
                    i--
                } else {
                    break
                }
            }
            prefix = sb.toString()
        }
        
        return prefix
    }

    var currentItemCount = 0

    fun complete(file: KotlinFile, line: Int, character: Int): List<CompletionItem> {
        // Ensure extension points are registered for this project area specifically
        // sometimes they are lost when switching between files/tabs
        registerExtensionPoints(kotlinEnvironment.project.extensionArea)
        
        currentItemCount = 0
        val originalFile = file.kotlinFile
        val inserted = file.insert(COMPLETION_SUFFIX, line, character)
        
        val filesToAnalyze = kotlinFiles.values.map {
            if (it.kotlinFile.name == originalFile.name) inserted.kotlinFile else it.kotlinFile
        }

        val position = inserted.elementAt(line, character)
        val prefix = getPrefix(position, inserted, line, character)
        
        Log.d("KotlinEnvironment", "Completing at element: ${position?.text} of type ${position?.javaClass?.simpleName}, detected prefix: '$prefix'")

        val list = try {
            val items = mutableListOf<CompletionItem>()
            
            position?.let { element ->
                val descriptorInfo = descriptorsFrom(element, inserted.kotlinFile, filesToAnalyze)
                Log.d("KotlinEnvironment", "Found ${descriptorInfo.descriptors.size} descriptors")
                
                items.addAll(descriptorInfo.descriptors.mapNotNull { descriptor ->
                    completionVariantFor(prefix, descriptor)
                })
            } ?: run {
                Log.d("KotlinEnvironment", "No element found at $line:$character")
            }

            val keywords = keywordsCompletionVariants(prefix)
            items.addAll(keywords)
            
            val totalItems = items.sortedWith { a, b ->
                val labelA = a.label.toString()
                val labelB = b.label.toString()
                
                val aExact = labelA.startsWith(prefix)
                val bExact = labelB.startsWith(prefix)
                
                val aStartsNoCase = labelA.startsWith(prefix, ignoreCase = true)
                val bStartsNoCase = labelB.startsWith(prefix, ignoreCase = true)

                // 1. Exact case match at the start
                if (aExact && !bExact) return@sortedWith -1
                if (!aExact && bExact) return@sortedWith 1

                // 2. Case-insensitive match at the start
                if (aStartsNoCase && !bStartsNoCase) return@sortedWith -1
                if (!aStartsNoCase && bStartsNoCase) return@sortedWith 1
                
                // 3. Lowercase first if prefix is lowercase
                if (prefix.isNotEmpty() && prefix[0].isLowerCase()) {
                    val aFirstLower = labelA.isNotEmpty() && labelA[0].isLowerCase()
                    val bFirstLower = labelB.isNotEmpty() && labelB[0].isLowerCase()
                    if (aFirstLower && !bFirstLower) return@sortedWith -1
                    if (!aFirstLower && bFirstLower) return@sortedWith 1
                }

                // 4. Kind priority
                val priorityA = getKindPriority(a.kind)
                val priorityB = getKindPriority(b.kind)
                if (priorityA != priorityB) return@sortedWith priorityA - priorityB
                
                // 5. Shorter labels first
                if (labelA.length != labelB.length) return@sortedWith labelA.length - labelB.length

                // 6. Alphabetical
                labelA.compareTo(labelB, ignoreCase = true)
            }

            if (totalItems.size > 100) totalItems.subList(0, 100) else totalItems

        } catch (e: Exception) {
            if (e is ProcessCanceledException || e is InterruptedException) {
                emptyList()
            } else {
                Log.e("KotlinEnvironment", "Failed to get descriptors", e)
                emptyList()
            }
        }
        return list
    }

    private fun getKindPriority(kind: CompletionItemKind?): Int {
        return when (kind) {
            CompletionItemKind.Keyword -> 0
            CompletionItemKind.Variable, CompletionItemKind.Field, CompletionItemKind.Property -> 1
            CompletionItemKind.Method, CompletionItemKind.Function -> 2
            CompletionItemKind.Class, CompletionItemKind.Interface, CompletionItemKind.Enum -> 3
            else -> 4
        }
    }

    private fun completionVariantFor(
        prefix: String,
        descriptor: DeclarationDescriptor
    ): CompletionItem? {
        val (name, tail) = descriptor.presentableName()

        var completionText = name
        val position = completionText.indexOf('(')
        if (position != -1) {
            completionText = StringBuilder(completionText).apply {
                replace(position, completionText.length, "()")
            }.toString()
        }

        val colonPosition = completionText.indexOf(":")
        if (colonPosition != -1) {
            completionText = completionText.take(colonPosition - 1)
        }

        var tailName = tail
        val spacePosition = tail.lastIndexOf(" ")
        if (spacePosition != -1) {
            tailName = tail.substring(spacePosition + 1)
        }

        return if (name.startsWith(prefix, ignoreCase = true)) {
            EditorCompletionItem(name, tailName, prefix.length, completionText).kind(
                when (descriptor) {
                    is ClassDescriptor -> CompletionItemKind.Class
                    is ConstructorDescriptor -> CompletionItemKind.Constructor
                    is FunctionDescriptor -> CompletionItemKind.Method
                    is PropertyDescriptor -> CompletionItemKind.Property
                    is VariableDescriptor -> CompletionItemKind.Variable
                    is PackageViewDescriptor -> CompletionItemKind.Module
                    else -> CompletionItemKind.Text
                }
            )
        } else {
            null
        }
    }

    private fun keywordsCompletionVariants(
        prefix: String
    ): List<CompletionItem> {
        // Return an empty list if the prefix is empty
        if (prefix.isEmpty()) return emptyList()

        val result = mutableListOf<CompletionItem>()

        // Iterate over the keywords and add the ones that match the prefix to the result
        for (token in KtTokens.KEYWORDS.types) {
            if (token is KtKeywordToken && token.value.startsWith(prefix, ignoreCase = true)) {
                result.add(
                    EditorCompletionItem(
                        token.value,
                        "Keyword",
                        prefix.length,
                        token.value
                    ).kind(CompletionItemKind.Keyword)
                )
            }
        }

        return result
    }

    private fun descriptorsFrom(element: PsiElement, current: KtFile, files: List<KtFile>): DescriptorInfo {
        val analysis = analysisOf(files, current) ?: return DescriptorInfo(false, emptyList())

        return with(analysis) {
            logTime("referenceVariants") {
                val parent = element.parent
                val descriptors = (referenceVariantsFrom(element)
                    ?: parent?.let { referenceVariantsFrom(it) })
                
                if (descriptors != null) {
                    return@logTime DescriptorInfo(true, descriptors)
                }

                // Fallback to top-level descriptors if outside class or no variants found
                val bindingContext = analysisResult.bindingContext
                val resolutionFacade = KotlinResolutionFacade(
                    project = element.project,
                    componentProvider = componentProvider,
                    moduleDescriptor = analysisResult.moduleDescriptor
                )
                
                val scope = try {
                    element.getResolutionScope(bindingContext, resolutionFacade)
                } catch (e: Exception) {
                    null
                }

                val contributedDescriptors = scope?.getContributedDescriptors(
                    DescriptorKindFilter.ALL,
                    MemberScope.ALL_NAME_FILTER
                )?.toList() ?: emptyList()

                DescriptorInfo(
                    isTipsManagerCompletion = false,
                    descriptors = contributedDescriptors.ifEmpty {
                        try {
                            when (parent) {
                                is KtQualifiedExpression -> {
                                    analysisResult.bindingContext.get(
                                        BindingContext.EXPRESSION_TYPE_INFO,
                                        parent.receiverExpression
                                    )?.type?.let { expressionType ->
                                        analysisResult.bindingContext.get(
                                            BindingContext.LEXICAL_SCOPE,
                                            parent.receiverExpression
                                        )?.let {
                                            expressionType.memberScope.getContributedDescriptors(
                                                DescriptorKindFilter.ALL,
                                                MemberScope.ALL_NAME_FILTER
                                            )
                                        }
                                    }?.toList() ?: emptyList()
                                }

                                else -> (element as? KtExpression)?.let { expr ->
                                    analysisResult.bindingContext.get(
                                        BindingContext.LEXICAL_SCOPE,
                                        expr
                                    )?.getContributedDescriptors(
                                        DescriptorKindFilter.ALL,
                                        MemberScope.ALL_NAME_FILTER
                                    )?.toList()
                                } ?: emptyList()
                            }
                        } catch (e: Exception) {
                            if (e is ProcessCanceledException || e is InterruptedException) {
                                emptyList()
                            } else {
                                Log.e("KotlinEnvironment", "Failed to get descriptors from lexical scope", e)
                                emptyList()
                            }
                        }
                    }
                )
            }
        }
    }

    private val analyzerWithCompilerReport by lazy {
        AnalyzerWithCompilerReport(kotlinEnvironment.configuration)
    }


    fun analysisOf(files: List<KtFile>, current: KtFile): Analysis? = synchronized(analysisLock) {
        if (files.isEmpty()) return null
        
        val project = files.first().project
        val bindingTrace = CliBindingTrace(project)
        var componentProvider: ComponentProvider? = null
        try {
            analyzerWithCompilerReport.analyzeAndReport(files) {
                componentProvider = logTime("componentProvider") {
                    TopDownAnalyzerFacadeForJVM.createContainer(
                        kotlinEnvironment.project,
                        listOf(),
                        bindingTrace,
                        kotlinEnvironment.configuration,
                        kotlinEnvironment::createPackagePartProvider,
                        { storageManager, _ ->
                            FileBasedDeclarationProviderFactory(
                                storageManager,
                                files
                            )
                        }
                    )
                }
                
                val cp = componentProvider!!
                logTime("analyzeDeclarations") {
                    analysis = cp
                        .getService(LazyTopDownAnalyzer::class.java)
                        .analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files)
                }

                val moduleDescriptor = cp.getService(ModuleDescriptor::class.java)
                AnalysisHandlerExtension.getInstances(project).find {
                    it.analysisCompleted(
                        project,
                        moduleDescriptor,
                        bindingTrace,
                        listOf(current)
                    ) != null
                }

                return@analyzeAndReport AnalysisResult.success(
                    bindingTrace.bindingContext,
                    moduleDescriptor
                )
            }
            
            val cp = componentProvider ?: return null
            if (analyzerWithCompilerReport.hasErrors()) {
                Log.w("KotlinEnvironment", "Analysis reported errors, completion might be incomplete")
            }
            return Analysis(cp, analyzerWithCompilerReport.analysisResult)
        } catch (e: Throwable) {
            // Ignore expected cancellations
            if (e is ProcessCanceledException || e is InterruptedException) {
                return null
            }
            
            // KotlinFrontEndException can be common with invalid/incomplete code, log as warning
            if (e is KotlinFrontEndException || e.toString().contains("KotlinFrontEndException")) {
                Log.w("KotlinEnvironment", "Kotlin analysis failed (FrontEndException): ${e.message}", e.cause ?: e)
            } else {
                Log.e("KotlinEnvironment", "Exception during analysisOf", e)
            }
            return null
        }
    }

    @OptIn(FrontendInternals::class)
    private fun Analysis.referenceVariantsFrom(element: PsiElement): List<DeclarationDescriptor>? {
        val prefix = getPrefix(element, KotlinFile("", element.containingFile as KtFile), 0, 0) // Placeholder
        val elementKt = element as? KtElement ?: return null
        val bindingContext = analysisResult.bindingContext
        val resolutionFacade =
            KotlinResolutionFacade(
                project = element.project,
                componentProvider = componentProvider,
                moduleDescriptor = analysisResult.moduleDescriptor
            )
        val inDescriptor = try {
            elementKt.getResolutionScope(bindingContext, resolutionFacade).ownerDescriptor
        } catch (e: Exception) {
            if (e is ProcessCanceledException || e is InterruptedException) {
                return null
            }
            Log.e("KotlinEnvironment", "Failed to get resolution scope", e)
            return null
        }
        return when (element) {
            is KtSimpleNameExpression ->
                try {
                    ReferenceVariantsHelper(
                        analysisResult.bindingContext,
                        resolutionFacade = resolutionFacade,
                        moduleDescriptor = analysisResult.moduleDescriptor,
                        visibilityFilter =
                        VisibilityFilter(
                            inDescriptor,
                            bindingContext,
                            element,
                            resolutionFacade
                        )
                    )
                        .getReferenceVariants(
                            element,
                            DescriptorKindFilter.ALL,
                            nameFilter = { name ->
                                if (prefix.isNotEmpty()) {
                                    name.asString().startsWith(prefix, ignoreCase = true)
                                } else {
                                    true
                                }
                            },
                            filterOutJavaGettersAndSetters = true,
                            filterOutShadowed = true,
                            excludeNonInitializedVariable = true,
                            useReceiverType = null
                        )
                        .toList()
                } catch (e: Exception) {
                    if (e is ProcessCanceledException || e is InterruptedException) {
                        return null
                    }
                    Log.e("KotlinEnvironment", "Failed to get reference variants", e)
                    null
                }

            else -> null
        }
    }

    private fun DeclarationDescriptor.presentableName() =
        when (this) {
            is FunctionDescriptor ->
                name.asString() + renderer.renderFunctionParameters(this) to
                        when {
                            returnType != null -> renderer.renderType(returnType!!)
                            else ->
                                extensionReceiverParameter?.let { param ->
                                    " for ${renderer.renderType(param.type)} in ${
                                        DescriptorUtils.getFqName(
                                            containingDeclaration
                                        )
                                    }"
                                }
                                    ?: ""
                        }

            else ->
                name.asString() to
                        when (this) {
                            is VariableDescriptor -> renderer.renderType(type)
                            is ClassDescriptor ->
                                " (${DescriptorUtils.getFqName(containingDeclaration)})"

                            else -> renderer.render(this)
                        }
        }

    private class VisibilityFilter(
        private val inDescriptor: DeclarationDescriptor,
        private val bindingContext: BindingContext,
        private val element: KtElement,
        private val resolutionFacade: KotlinResolutionFacade
    ) : (DeclarationDescriptor) -> Boolean {
        @OptIn(FrontendInternals::class)
        override fun invoke(descriptor: DeclarationDescriptor): Boolean {
            if (descriptor is TypeParameterDescriptor && !isTypeParameterVisible(descriptor))
                return false

            if (descriptor is DeclarationDescriptorWithVisibility) {
                return descriptor.isVisible(element, null, bindingContext, resolutionFacade)
            }

            return !descriptor.isInternalImplementationDetail()
        }

        private fun isTypeParameterVisible(typeParameter: TypeParameterDescriptor): Boolean {
            val owner = typeParameter.containingDeclaration
            var parent: DeclarationDescriptor? = inDescriptor
            while (parent != null) {
                if (parent == owner) return true
                if ((parent is ClassDescriptor) && !parent.isInner) return false
                parent = parent.containingDeclaration
            }
            return true
        }

        private fun DeclarationDescriptor.isInternalImplementationDetail(): Boolean =
            importableFqName?.asString() in excludedFromCompletion
    }

    companion object {
        private const val COMPLETION_SUFFIX = "æ"

        private val excludedFromCompletion: List<String> =
            listOf(
                "kotlin.jvm.internal",
                "kotlin.coroutines.experimental.intrinsics",
                "kotlin.coroutines.intrinsics",
                "kotlin.coroutines.experimental.jvm.internal",
                "kotlin.coroutines.jvm.internal",
                "kotlin.reflect.jvm.internal"
            )

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
                Log.i("KotlinEnvironment", "Injected custom Logger factory to suppress EP errors")
            } catch (e: Throwable) {
                Log.e("KotlinEnvironment", "Failed to suppress logger errors", e)
            }
        }

        private fun registerExtensionPoints(additionalArea: com.intellij.openapi.extensions.ExtensionsArea? = null) {
            val areas = mutableSetOf<com.intellij.openapi.extensions.ExtensionsArea>()
            try {
                @Suppress("DEPRECATION")
                Extensions.getRootArea()?.let { areas.add(it) }
            } catch (e: Throwable) {}
            
            try {
                ApplicationManager.getApplication()?.extensionArea?.let { areas.add(it) }
            } catch (e: Throwable) {}

            additionalArea?.let { areas.add(it) }

            for (area in areas) {
                registerEP(area, "com.intellij.psi.classFileDecompiler", "com.intellij.psi.ClassFileDecompiler")
                registerEP(area, "com.intellij.psi.treeCopyHandler", "com.intellij.psi.impl.PsiTreeCopyHandler")
                registerEP(area, "com.intellij.lang.meta.documentationKindProvider", "com.intellij.lang.meta.DocumentationKindProvider")
                registerEP(area, "com.intellij.openapi.fileTypes.FileTypeDetector", "com.intellij.openapi.fileTypes.FileTypeDetector")
                registerEP(area, "com.intellij.lang.braceMatcher", "com.intellij.lang.PairedBraceMatcher")
                registerEP(area, "com.intellij.psi.clsCustomNavigationPolicy", "com.intellij.psi.impl.compiled.ClsCustomNavigationPolicy")
                registerEP(area, "com.intellij.psi.augmentProvider", "com.intellij.psi.augment.PsiAugmentProvider")
                registerEP(area, "com.intellij.psi.shortNamesCache", "com.intellij.psi.search.PsiShortNamesCache")
            }
        }

        private fun registerEP(area: com.intellij.openapi.extensions.ExtensionsArea, name: String, className: String) {
            try {
                if (area.hasExtensionPoint(name)) {
                    val ep = area.getExtensionPoint<Any>(name)
                    forceAllowListeners(ep)
                } else {
                    try {
                        val method = area.javaClass.methods.find {
                            it.name == "registerExtensionPoint" && it.parameterCount == 4 &&
                                    it.parameterTypes[3] == Boolean::class.javaPrimitiveType
                        }
                        if (method != null) {
                            method.invoke(area, name, className, ExtensionPoint.Kind.INTERFACE, true)
                            Log.i("KotlinEnvironment", "Registered $name with allowListeners=true in area ${area.javaClass.simpleName}")
                        } else {
                            area.registerExtensionPoint(name, className, ExtensionPoint.Kind.INTERFACE)
                            forceAllowListeners(area.getExtensionPoint<Any>(name))
                        }
                    } catch (e: Throwable) {
                        Log.e("KotlinEnvironment", "Failed to register EP $name in area ${area.javaClass.simpleName}", e)
                    }
                }
            } catch (e: Throwable) {
                Log.e("KotlinEnvironment", "Error in registerEP for $name in area ${area.javaClass.simpleName}", e)
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

        private fun registerKotlinServices(project: com.intellij.openapi.project.Project) {
            val application = ApplicationManager.getApplication()
            if (application != null) {
                registerServiceSafe(application, KotlinBinaryClassCache::class.java, KotlinBinaryClassCache())
            }
            registerServiceSafe(project, KotlinBinaryClassCache::class.java, KotlinBinaryClassCache())
        }
        
        private fun registerServiceSafe(container: Any, serviceInterface: Class<*>, instance: Any) {
            try {
                val getServiceMethod = container.javaClass.getMethod("getService", Class::class.java)
                if (getServiceMethod.invoke(container, serviceInterface) != null) return
                
                val registerMethod = container.javaClass.methods.find { 
                    it.name == "registerService" && it.parameterCount >= 2 && 
                    it.parameterTypes[0] == Class::class.java 
                }
                
                if (registerMethod != null) {
                    registerMethod.isAccessible = true
                    val args = arrayOfNulls<Any>(registerMethod.parameterCount)
                    args[0] = serviceInterface
                    args[1] = if (registerMethod.parameterTypes[1].isInstance(instance)) instance else instance.javaClass
                    if (registerMethod.parameterCount > 2) args[2] = false
                    registerMethod.invoke(container, *args)
                    Log.i("KotlinEnvironment", "Registered ${serviceInterface.simpleName} service in ${container.javaClass.simpleName}")
                }
            } catch (e: Exception) {
                Log.e("KotlinEnvironment", "Failed to register service ${serviceInterface.name}", e)
            }
        }

        fun with(classpath: List<File>): KotlinEnvironment {
            suppressLoggerErrors()
            setIdeaIoUseFallback()
            setupIdeaStandaloneExecution()
            
            // Register extension points BEFORE creating the environment
            registerExtensionPoints()

            val disposable = Disposer.newDisposable()

            val config = CompilerConfiguration().apply {
                logTime("compilerConfig") {
                    put(JVMConfigurationKeys.NO_JDK, true)
                    put(JVMConfigurationKeys.NO_REFLECT, true)
                    put(
                        CLIConfigurationKeys.PERF_MANAGER,
                        object : CommonCompilerPerformanceManager("Profiling") {
                            override fun notifyAnalysisStarted() {
                                Log.i("Profiling", "Analysis started")
                            }

                            override fun notifyAnalysisFinished() {
                                Log.i("Profiling", "Analysis finished")
                            }
                        })
                    put(
                        CommonConfigurationKeys.MODULE_NAME,
                        JvmProtoBufUtil.DEFAULT_MODULE_NAME
                    )
                    // Fix: Use PSI instead of binary reading to avoid KotlinBinaryClassCache NPE on Android.
                    put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)
                    put(JVMConfigurationKeys.VALIDATE_IR, false)
                    put(JVMConfigurationKeys.DISABLE_CALL_ASSERTIONS, true)
                    put(JVMConfigurationKeys.DISABLE_PARAM_ASSERTIONS, true)
                    put(JVMConfigurationKeys.DISABLE_RECEIVER_ASSERTIONS, true)
                    put(CommonConfigurationKeys.INCREMENTAL_COMPILATION, false)
                    put(JVMConfigurationKeys.USE_FAST_JAR_FILE_SYSTEM, Prefs.useFastJarFs)
                    // Disable FIR for now as we are using FE1.0 APIs (BindingContext, etc.)
                    put(CommonConfigurationKeys.USE_FIR, false)
                    put(CommonConfigurationKeys.USE_LIGHT_TREE, false)
                    put(CommonConfigurationKeys.PARALLEL_BACKEND_THREADS, 10)
                    put(CommonConfigurationKeys.USE_FIR_EXTENDED_CHECKERS, false)

                    // enable all language features
                    val langFeatures =
                        mutableMapOf<LanguageFeature, LanguageFeature.State>()
                    for (langFeature in LanguageFeature.entries) {
                        langFeatures[langFeature] = LanguageFeature.State.ENABLED
                    }

                    val languageVersion =
                        LanguageVersion.fromVersionString(Prefs.kotlinVersion)!!
                    val languageVersionSettings = LanguageVersionSettingsImpl(
                        languageVersion,
                        ApiVersion.createByLanguageVersion(languageVersion),
                        mapOf(
                            AnalysisFlags.extendedCompilerChecks to false,
                            AnalysisFlags.ideMode to true,
                            AnalysisFlags.skipMetadataVersionCheck to true,
                            AnalysisFlags.skipPrereleaseCheck to true,
                        ),
                        langFeatures
                    )
                    put(
                        CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                        languageVersionSettings
                    )
                    addJvmClasspathRoots(
                        classpath
                    )
                }
            }

            val kotlinCoreEnv = KotlinCoreEnvironment.createForProduction(
                disposable,
                config,
                configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

            // Register extension points again AFTER creating the environment, including Project area
            registerExtensionPoints(kotlinCoreEnv.project.extensionArea)

            // Register services AFTER creating the environment
            registerKotlinServices(kotlinCoreEnv.project)

            return KotlinEnvironment(kotlinCoreEnv)
        }

        private val cachedEnvironments = ConcurrentHashMap<Project, KotlinEnvironment>()

        fun get(module: Project): KotlinEnvironment {
            return cachedEnvironments.getOrPut(module) {
                val jars = module.libDir.walk().filter { it.extension == "jar" }.toMutableList()
                jars.addAll(FileUtil.classpathDir.walk().filter { it.extension == "jar" })
                val environment = with(jars)
                environment.kotlinEnvironment.updateClasspath(
                    jars.map { JvmClasspathRoot(it) }
                )
                module.srcDir.walk()
                    .filter { it.extension == "kt" }
                    .forEach {
                        environment.updateKotlinFile(it.absolutePath, it.readText())
                    }
                environment
            }
        }
    }
}
