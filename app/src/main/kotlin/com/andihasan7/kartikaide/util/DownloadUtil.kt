/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.util

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import andihasan7.kartikaide.common.Analytics
import java.io.File
import kotlin.math.min

class Download(val url: String, val callback: (percent: Int) -> Unit) {

    private var totalBytes = -1L
    private var downloadedBytes = 0L

    fun start(file: File) {
        Analytics.logEvent("download", mapOf("url" to url, "file" to file.absolutePath))
        val request = Request.Builder()
            .url(url)
            .build()

        val response = OkHttpClient().newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Unexpected code $response")
        }

        val body = response.body ?: throw Exception("Empty response body")
        totalBytes = body.contentLength()
        
        Log.d("Download", "Downloading $url to $file (Total: $totalBytes bytes)")
        
        var lastPercent = -1

        file.outputStream().use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
                        val cappedProgress = min(100, progress)
                        
                        if (cappedProgress != lastPercent) {
                            lastPercent = cappedProgress
                            callback(cappedProgress)
                        }
                    } else {
                        // If total size is unknown, send -1 to indicate indeterminate progress
                        callback(-1)
                    }
                }
            }
        }
        
        // Ensure we send 100% at the end if we knew the total size
        if (totalBytes > 0 && lastPercent < 100) {
            callback(100)
        }
    }

    companion object {
        private const val BUFFER_SIZE = 8192
    }
}
