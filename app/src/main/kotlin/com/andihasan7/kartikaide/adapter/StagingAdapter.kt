/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.andihasan7.kartikaide.databinding.StagingItemBinding
import org.eclipse.jgit.api.Status

class StagingAdapter(val rootPath: String) : RecyclerView.Adapter<StagingAdapter.ViewHolder>() {

    val files = mutableListOf<File>()

    fun updateStatus(status: Status?) {
        files.clear()
        status?.apply {
            files.addAll(added.map { File(it, FileStatus.ADDED) })
            files.addAll(changed.map { File(it, FileStatus.CHANGED) })
            files.addAll(removed.map { File(it, FileStatus.REMOVED) })
            files.addAll(missing.map { File(it, FileStatus.MISSING) })
            files.addAll(modified.map { File(it, FileStatus.MODIFIED) })
            files.addAll(conflicting.map { File(it, FileStatus.CONFLICTING) })
            files.addAll(untracked.map { File(it, FileStatus.UNTRACKED) })
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        return ViewHolder(
            StagingItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size

    fun getSelectedFiles() = files.filter { it.isSelected }

    inner class ViewHolder(
        private val binding: StagingItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(file: File) {
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = file.isSelected
            binding.checkbox.setOnCheckedChangeListener { _, isChecked ->
                file.isSelected = isChecked
            }

            binding.filePath.text = file.path
            binding.fileStatus.text = file.status.name
            
            val color = when (file.status) {
                FileStatus.ADDED -> Color.GREEN
                FileStatus.CHANGED, FileStatus.MODIFIED -> Color.YELLOW
                FileStatus.REMOVED, FileStatus.MISSING, FileStatus.CONFLICTING -> Color.RED
                FileStatus.UNTRACKED -> Color.WHITE
            }
            binding.fileStatus.setTextColor(color)
            binding.filePath.setTextColor(color)
        }
    }

    data class File(
        val path: String,
        val status: FileStatus,
        var isSelected: Boolean = false
    )

    enum class FileStatus {
        ADDED, CHANGED, REMOVED, MISSING, MODIFIED, CONFLICTING, UNTRACKED
    }
}
