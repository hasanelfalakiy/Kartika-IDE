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

/**
 * EditorInputStream menghubungkan teks di CodeEditor ke System.in.
 * Ia melacak posisi teks terakhir agar tidak membaca output program sebagai input.
 */
class EditorInputStream(private val editor: CodeEditor) : InputStream() {
    private val queue = LinkedBlockingQueue<Int>()
    
    @Volatile
    private var lastTextLength = editor.text.length

    /**
     * Beritahu stream bahwa akan ada output sebanyak [length] karakter.
     * Dipanggil sebelum teks ditambahkan ke editor untuk memastikan stream melewatinya.
     */
    @Synchronized
    fun expectOutput(length: Int) {
        lastTextLength += length
    }

    /**
     * Reset posisi pembacaan ke posisi tertentu (misal setelah Clear Log).
     */
    @Synchronized
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
        
        val firstByte = read()
        if (firstByte == -1) return -1
        
        b[off] = firstByte.toByte()
        var count = 1
        
        while (count < len) {
            val nextByte = queue.poll() ?: break
            b[off + count] = nextByte.toByte()
            count++
        }
        
        return count
    }

    private fun readLineToBuffer() {
        // Paksa flush output agar semua prompt terkirim dan lastTextLength terupdate
        // sebelum kita mulai menunggu input dari user.
        System.out.flush()
        System.err.flush()
        
        while (true) {
            val content = editor.text
            val currentLength = content.length
            val marker = lastTextLength
            
            if (currentLength > marker) {
                val textSinceLast = content.subSequence(marker, currentLength).toString()
                // Input hanya dikirim ke program setelah user menekan Enter (\n)
                if (textSinceLast.contains('\n')) {
                    textSinceLast.forEach { queue.put(it.code) }
                    synchronized(this) {
                        lastTextLength = currentLength
                    }
                    break
                }
            } else if (currentLength < marker) {
                // Jika teks dihapus secara signifikan (misal Clear Log), sinkronkan ulang marker.
                // Kita hanya melakukan ini jika panjangnya 0 untuk menghindari masalah race condition
                // saat output sedang dalam proses append (dimana currentLength < marker sementara).
                if (currentLength == 0) {
                    synchronized(this) {
                        lastTextLength = 0
                    }
                }
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
