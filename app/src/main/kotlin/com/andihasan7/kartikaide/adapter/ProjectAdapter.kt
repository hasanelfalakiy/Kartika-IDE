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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.andihasan7.kartikaide.databinding.ProjectItemBinding
import com.andihasan7.kartikaide.databinding.ProjectHeaderBinding
import andihasan7.kartikaide.project.Project
import kotlin.properties.Delegates

class ProjectAdapter(private val listener: OnProjectEventListener) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<Item> by Delegates.observable(emptyList()) { _, oldList, newList ->
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldList[oldItemPosition] == newList[newItemPosition]

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldList[oldItemPosition] == newList[newItemPosition]
        }).dispatchUpdatesTo(this)
    }

    sealed class Item {
        data class Header(val title: String) : Item()
        data class ProjectItem(val project: Project) : Item()
    }

    interface OnProjectEventListener {
        fun onProjectClicked(project: Project)
        fun onProjectLongClicked(project: Project, v: View): Boolean
    }

    fun submitProjects(internal: List<Project>, external: List<Project>) {
        val newList = mutableListOf<Item>()
        if (internal.isNotEmpty()) {
            newList.add(Item.Header("Internal Storage"))
            newList.addAll(internal.map { Item.ProjectItem(it) })
        }
        if (external.isNotEmpty()) {
            newList.add(Item.Header("External Storage (KartikaIDE)"))
            newList.addAll(external.map { Item.ProjectItem(it) })
        }
        this.items = newList
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Item.Header -> 0
            is Item.ProjectItem -> 1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            HeaderViewHolder(ProjectHeaderBinding.inflate(inflater, parent, false))
        } else {
            ProjectViewHolder(ProjectItemBinding.inflate(inflater, parent, false), listener)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is Item.Header) {
            holder.bind(item.title)
        } else if (holder is ProjectViewHolder && item is Item.ProjectItem) {
            holder.bind(item.project)
        }
    }

    override fun getItemCount() = items.size

    inner class HeaderViewHolder(private val binding: ProjectHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.headerTitle.text = title
        }
    }

    inner class ProjectViewHolder(
        private val binding: ProjectItemBinding,
        private val listener: OnProjectEventListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: Project) {
            binding.projectTitle.text = data.name
            binding.projectPath.text = data.root.absolutePath
            binding.root.setOnClickListener { listener.onProjectClicked(data) }
            binding.root.setOnLongClickListener {
                listener.onProjectLongClicked(data, binding.root)
            }
        }
    }
}
