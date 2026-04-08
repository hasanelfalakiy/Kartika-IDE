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

package com.andihasan7.kartikaide.editor.formatter

import android.util.Log
import com.facebook.ktfmt.cli.Main
import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions
import andihasan7.kartikaide.common.Prefs
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ktfmtFormatter {
    fun formatCode(code: String): String {
        val style = Prefs.ktfmtStyle
        
        // If it's a standard style and NOT custom/ktlint, we can use the CLI Main for simplicity
        if (style != "custom" && style != "ktlint") {
            return formatWithCli(code, style)
        }

        // For custom and ktlint, we use the Formatter API directly for better control
        return try {
            val options = when (style) {
                "ktlint" -> {
                    // ktlint standard: 4 spaces indent, 100 max width
                    FormattingOptions(
                        style = FormattingOptions.Style.GOOGLE,
                        blockIndent = 4,
                        continuationIndent = 4,
                        maxWidth = 100,
                        removeUnusedImports = Prefs.ktfmtRemoveUnusedImports,
                        manageTrailingCommas = Prefs.ktfmtManageTrailingCommas
                    )
                }
                "custom" -> {
                    FormattingOptions(
                        style = FormattingOptions.Style.GOOGLE,
                        blockIndent = Prefs.ktfmtBlockIndent,
                        continuationIndent = Prefs.ktfmtContinuationIndent,
                        maxWidth = Prefs.ktfmtMaxWidth,
                        removeUnusedImports = Prefs.ktfmtRemoveUnusedImports,
                        manageTrailingCommas = Prefs.ktfmtManageTrailingCommas
                    )
                }
                else -> Formatter.GOOGLE_FORMAT
            }
            
            Formatter.format(options, code)
        } catch (e: Exception) {
            Log.e("ktfmtFormatter", "Formatting error", e)
            code
        }
    }

    private fun formatWithCli(code: String, style: String): String {
        val file = createTempFile("file", ".kt").apply { writeText(code) }
        val args = mutableListOf<String>()
        
        when (style) {
            "google" -> args.add("--google-style")
            "kotlinlang" -> args.add("--kotlinlang-style")
            "dropbox" -> args.add("--dropbox-style")
            else -> args.add("--google-style")
        }
        
        args.add(file.toAbsolutePath().toString())

        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val main = Main(System.`in`, PrintStream(out), PrintStream(err), args.toTypedArray())
        main.run()
        
        val errorOutput = err.toString()
        if (errorOutput.isNotEmpty()) {
            Log.e("ktfmtFormatter", "CLI Formatting error: $errorOutput")
        }

        val formattedCode = file.readText()
        file.deleteIfExists()

        return formattedCode
    }
}
