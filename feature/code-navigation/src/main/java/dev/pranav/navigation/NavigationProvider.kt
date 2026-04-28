/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.pranav.navigation

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod

object NavigationProvider {

    fun extractMethodsAndFields(javaFile: PsiClass, depth: Int = 0): List<NavigationItem> {
        var d = depth
        val navigationItems = mutableListOf<NavigationItem>()
        val name = buildString {
            append(javaFile.name)
            if (javaFile.superClass != null && javaFile.superClass?.qualifiedName != "java.lang.Object") {
                append(" : ")
                append(javaFile.superClass?.name)
            }
            if (javaFile.implementsList != null && javaFile.implementsList!!.referenceElements.isNotEmpty()) {
                append(" implements ")
                append(javaFile.implementsList?.referenceElements?.joinToString(", ") { it.text })
            }
        }
        
        // Use textRange for absolute offsets in the file
        val item = ClassNavigationKind(
            name,
            javaFile.modifierList?.text ?: "",
            javaFile.textRange.startOffset,
            javaFile.textRange.endOffset,
            d
        )
        if (d == 0) navigationItems.add(item)
        d++
        
        javaFile.children.forEach { child ->
            when (child) {
                is PsiMethod -> {
                    val modifiers = child.modifierList
                    val parameters = child.parameterList
                    val returnType = child.returnTypeElement?.text ?: "void"

                    val methodName = child.name + "(" + parameters.parameters.joinToString(", ") {
                        it.typeElement?.text ?: "void"
                    } + ") : $returnType"

                    val startPosition = child.textRange.startOffset
                    val endPosition = child.textRange.endOffset

                    val methodItem =
                        MethodNavigationItem(
                            methodName,
                            modifiers.text,
                            startPosition,
                            endPosition,
                            d
                        )
                    navigationItems.add(methodItem)
                }

                is PsiField -> {
                    val modifiers = child.modifierList ?: return@forEach
                    val startPosition = child.textRange.startOffset
                    val endPosition = child.textRange.endOffset
                    val type = child.typeElement?.text ?: "void"
                    val fieldName = child.name + " : $type"

                    val fieldItem =
                        FieldNavigationItem(fieldName, modifiers.text, startPosition, endPosition, d)
                    navigationItems.add(fieldItem)
                }

                is PsiClass -> {
                    val innerItems = extractMethodsAndFields(child, d)
                    navigationItems.addAll(innerItems)
                }
            }
        }

        return navigationItems
    }

    interface NavigationItem {
        val name: String
        val modifiers: String
        val startPosition: Int
        val endPosition: Int
        val kind: NavigationItemKind
        val depth: Int
            get() = 0
    }

    data class MethodNavigationItem(
        override val name: String,
        override val modifiers: String,
        override val startPosition: Int,
        override val endPosition: Int,
        override val depth: Int,
        override val kind: NavigationItemKind = NavigationItemKind.METHOD,
    ) : NavigationItem

    data class FieldNavigationItem(
        override val name: String,
        override val modifiers: String,
        override val startPosition: Int,
        override val endPosition: Int,
        override val depth: Int,
        override val kind: NavigationItemKind = NavigationItemKind.FIELD,
    ) : NavigationItem

    data class ClassNavigationKind(
        override val name: String,
        override val modifiers: String,
        override val startPosition: Int,
        override val endPosition: Int,
        override val depth: Int,
        override val kind: NavigationItemKind = NavigationItemKind.CLASS,
    ) : NavigationItem

    enum class NavigationItemKind {
        METHOD,
        FIELD,
        CLASS,
    }

}
