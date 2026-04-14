/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.util

import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.noties.prism4j.annotations.PrismBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.andihasan7.kartikaide.App
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

@PrismBundle(
    include = ["kotlin", "java", "clike", "json", "markup", "python", "yaml"],
    grammarLocatorClassName = "com.andihasan7.kartikaide.util.MyGrammarLocator"
)
object CommonUtils {
    suspend fun showSnackbarError(view: View, text: String, error: Throwable) =
        withContext(Dispatchers.Main) {
            Snackbar.make(
                view,
                text,
                Snackbar.LENGTH_INDEFINITE
            ).setAction("Show error") {
                MaterialAlertDialogBuilder(view.context)
                    .setTitle(error.message)
                    .setMessage(error.stackTraceToString())
                    .setPositiveButton("OK") { _, _ -> }
            }.show()
        }

    fun showError(view: View, text: String, error: Throwable) {
        Snackbar.make(
            view,
            text,
            Snackbar.LENGTH_INDEFINITE
        ).setAction("Show error") {
            MaterialAlertDialogBuilder(view.context)
                .setTitle(error.message)
                .setMessage(error.stackTraceToString())
                .setPositiveButton("OK") { _, _ -> }
        }.show()
    }

    fun showSnackBar(view: View, text: String) {
        Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
    }

    fun getMarkwon(): Markwon {
        // MyGrammarLocator akan di-generate otomatis oleh kapt setelah proses Build
        // Jika masih merah, silakan lakukan Build > Make Project
        val prism4j = Prism4j(MyGrammarLocator())

        return Markwon
            .builder(App.instance.get()!!.applicationContext)
            .usePlugin(CorePlugin.create())
            .usePlugin(MovementMethodPlugin.create(LinkMovementMethod.getInstance()))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
            .build()
    }

    fun Activity.isShizukuGranted(): Boolean {
        if (Shizuku.pingBinder().not()) {
            Log.d("Shizuku", "Shizuku not installed")
            return false
        }
        return if (Shizuku.isPreV11()) {
            checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getPathFromTreeUri(uri: Uri): String? {
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val split = treeId.split(":")
        val type = split[0]
        return if ("primary".equals(type, ignoreCase = true)) {
            "/storage/emulated/0/" + if (split.size > 1) split[1] else ""
        } else {
            "/storage/$type/" + if (split.size > 1) split[1] else ""
        }
    }
}
