/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.editor

import io.github.rosemoe.sora.widget.CodeEditor
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue

class EditorInputStream(private val editor: CodeEditor) : InputStream() {
    private val queue = LinkedBlockingQueue<Int>()
    @Volatile
    private var lastTextLength = editor.text.length

    fun updateOffset(newOffset: Int) {
        lastTextLength = newOffset
    }

    override fun read(): Int {
        if (queue.isEmpty()) {
            readLineToBuffer()
        }
        return queue.take()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        
        // Membaca satu byte pertama (memblokir jika queue kosong)
        val firstByte = read()
        if (firstByte == -1) return -1
        
        b[off] = firstByte.toByte()
        var count = 1
        
        // Mengambil sisa byte yang sudah ada di queue tanpa memblokir lagi
        while (count < len) {
            val nextByte = queue.poll() ?: break
            b[off + count] = nextByte.toByte()
            count++
        }
        
        return count
    }

    private fun readLineToBuffer() {
        while (true) {
            val content = editor.text
            val currentLength = content.length
            
            if (currentLength > lastTextLength) {
                val textSinceLast = content.subSequence(lastTextLength, currentLength).toString()
                // Jika mengandung newline, kita anggap input baris selesai
                if (textSinceLast.contains('\n')) {
                    textSinceLast.forEach { queue.put(it.code) }
                    lastTextLength = currentLength
                    break
                }
            } else if (currentLength < lastTextLength) {
                // Jika teks dihapus, reset offset ke posisi sekarang
                lastTextLength = currentLength
            }
            
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }
}
