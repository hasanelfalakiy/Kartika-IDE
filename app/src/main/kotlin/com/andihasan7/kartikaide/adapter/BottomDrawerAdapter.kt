/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.andihasan7.kartikaide.databinding.LayoutLogViewBinding
import com.andihasan7.kartikaide.editor.IdeEditor

class BottomDrawerAdapter : RecyclerView.Adapter<BottomDrawerAdapter.LogViewHolder>() {

    private val logContents = mutableMapOf<Int, StringBuilder>()
    private val viewHolders = mutableMapOf<Int, LogViewHolder>()

    init {
        logContents[0] = StringBuilder() // Build Log
        logContents[1] = StringBuilder() // Output
        logContents[2] = StringBuilder() // Search Result
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = LayoutLogViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.logEditor.apply {
            isLineNumberEnabled = false
        }
        return LogViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        viewHolders[position] = holder
        val content = logContents[position].toString()
        holder.binding.logEditor.setText(content)
        holder.binding.tvEmptyLog.visibility = if (content.isEmpty()) View.VISIBLE else View.GONE
        
        // Tab Output (1) dibuat editable agar mendukung input (System.in)
        holder.binding.logEditor.isEditable = (position == 1)
    }

    override fun getItemCount(): Int = 3

    fun appendLog(position: Int, text: String, addNewLine: Boolean = true) {
        if (!logContents.containsKey(position)) return
        
        val suffix = if (addNewLine) "\n" else ""
        logContents[position]?.append(text)?.append(suffix)
        viewHolders[position]?.let { holder ->
            holder.binding.logEditor.appendText(text + suffix)
            holder.binding.tvEmptyLog.visibility = View.GONE
        }
    }

    fun clearLog(position: Int) {
        logContents[position]?.setLength(0)
        viewHolders[position]?.let { holder ->
            holder.binding.logEditor.setText("")
            holder.binding.tvEmptyLog.visibility = View.VISIBLE
        }
    }
    
    fun getEditor(position: Int): IdeEditor? {
        return viewHolders[position]?.binding?.logEditor
    }

    class LogViewHolder(val binding: LayoutLogViewBinding) : RecyclerView.ViewHolder(binding.root)
}
