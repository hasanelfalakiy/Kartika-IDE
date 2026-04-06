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

package com.andihasan7.kartikaide.editor

import android.util.Log
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue


class EditorInputStream(private val editor: CodeEditor) : InputStream() {
    private val queue = LinkedBlockingQueue<Int>()
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

    private fun readLineToBuffer() {
        while (true) {
            val content = editor.text
            val currentLength = content.length
            
            if (currentLength > lastTextLength) {
                // Check if user pressed Enter (last line is now empty or cursor moved to next line)
                // In a console, user input usually ends with a newline.
                val textSinceLast = content.subSequence(lastTextLength, currentLength).toString()
                if (textSinceLast.endsWith('\n')) {
                    textSinceLast.forEach { queue.put(it.code) }
                    lastTextLength = currentLength
                    break
                }
            } else if (currentLength < lastTextLength) {
                // If text was cleared or deleted, reset offset to current end
                lastTextLength = currentLength
            }
            
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                break
            }
        }
    }
}
