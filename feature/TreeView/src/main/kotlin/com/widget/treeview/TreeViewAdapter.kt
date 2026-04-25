/*
 * Copyright © 2022 Github Lzhiyong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.widget.treeview

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.unnamed.b.atv.R
import com.widget.treeview.TreeUtils.toNodeList
import java.io.File

interface OnTreeItemClickListener {
    fun onItemClick(view: View, position: Int)
    fun onItemLongClick(view: View, position: Int)
}

class TreeViewAdapter(
    private val context: Context,
    private var nodes: MutableList<Node<File>>,
    private val iconProvider: TreeIconProvider? = null
) : RecyclerView.Adapter<TreeViewAdapter.ViewHolder>() {

    private val fileIcon = ResourcesCompat.getDrawable(
        context.resources,
        R.drawable.outline_insert_drive_file_24,
        context.theme
    )
    private val folderIcon = ResourcesCompat.getDrawable(
        context.resources,
        R.drawable.outline_folder_24,
        context.theme
    )
    private val chevronRightIcon = ResourcesCompat.getDrawable(
        context.resources,
        R.drawable.round_chevron_right_24,
        context.theme
    )!!
    private val expandMoreIcon = ResourcesCompat.getDrawable(
        context.resources,
        R.drawable.round_expand_more_24,
        context.theme
    )!!

    // Gunakan lazy dan gunakan atribut dari androidx.appcompat atau android.R untuk stabilitas
    private val colorPrimary by lazy { 
        MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, ContextCompat.getColor(context, android.R.color.holo_blue_dark)) 
    }
    private val colorOnSurface by lazy { 
        MaterialColors.getColor(context, android.R.attr.textColorPrimary, ContextCompat.getColor(context, android.R.color.black)) 
    }
    private val colorOutline by lazy {
        MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, ContextCompat.getColor(context, android.R.color.darker_gray))
    }

    private var listener: OnTreeItemClickListener? = null

    fun setOnItemClickListener(listener: OnTreeItemClickListener?) {
        this.listener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_view_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = nodes[position]

        // Handle indentation and guide lines
        holder.indentContainer.removeAllViews()
        for (i in 0 until node.depth) {
            val indentView = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics).toInt(),
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            
            // Add vertical line
            val lineView = View(context).apply {
                val lineParams = LinearLayout.LayoutParams(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt(),
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                lineParams.marginStart = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics).toInt()
                layoutParams = lineParams
                setBackgroundColor(colorOutline)
                alpha = 0.3f
            }
            
            val frame = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics).toInt(),
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(lineView)
            }
            
            holder.indentContainer.addView(frame)
        }

        val activeColor = if (node.isExpanded) colorPrimary else colorOnSurface

        if (node.value.isDirectory) {
            holder.expandView.setImageDrawable(if (!node.isExpanded) chevronRightIcon else expandMoreIcon)
            ImageViewCompat.setImageTintList(holder.expandView, ColorStateList.valueOf(activeColor))
            
            holder.fileView.setPadding(0, 0, 0, 0)
            val customFolder = iconProvider?.getIconForFolder(node.value, node.isExpanded)
            holder.fileView.setImageDrawable(customFolder ?: folderIcon)
            ImageViewCompat.setImageTintList(holder.fileView, ColorStateList.valueOf(activeColor))
            
            holder.textView.setTextColor(activeColor)
        } else {
            holder.expandView.setImageDrawable(null)
            val customFile = iconProvider?.getIconForFile(node.value)
            holder.fileView.setImageDrawable(customFile ?: fileIcon)
            // Reset tint agar icon file asli tetap terlihat warnanya
            ImageViewCompat.setImageTintList(holder.fileView, null) 
            holder.textView.setTextColor(colorOnSurface)
        }

        holder.textView.text = node.value.name

        holder.itemView.setOnClickListener {
            if (node.value.isDirectory) {
                toggleDirectory(node, position)
            }
            listener?.onItemClick(it, position)
        }

        holder.itemView.setOnLongClickListener {
            listener?.onItemLongClick(it, position)
            true
        }
    }

    private fun toggleDirectory(node: Node<File>, position: Int) {
        if (!node.isExpanded) {
            expandDirectory(node, position)
        } else {
            collapseDirectory(node, position)
        }
        notifyItemChanged(position)
    }

    fun expandDirectory(node: Node<File>, position: Int) {
        if (node.isExpanded) return
        
        var parent = node
        var children: List<Node<File>>
        var index = position
        var count = 0
        do {
            children = parent.value.toNodeList()
            nodes.addAll(index + 1, children)
            TreeUtils.addChildren(parent, children)

            if (children.isNotEmpty()) {
                parent = children[0]
                count += children.size
                index++
            }
        } while (children.size == 1 && children[0].value.isDirectory)
        notifyItemRangeInserted(position + 1, count)
    }

    fun collapseDirectory(node: Node<File>, position: Int) {
        if (!node.isExpanded) return

        val descendants = TreeUtils.getDescendants(node)
        nodes.removeAll(descendants.toSet())
        TreeUtils.removeChildren(node)
        notifyItemRangeRemoved(position + 1, descendants.size)
    }

    override fun getItemViewType(position: Int) = position

    override fun getItemCount() = nodes.size

    fun getNodes(): List<Node<File>> = nodes

    fun setNodes(newNodes: List<Node<File>>) {
        this.nodes.clear()
        this.nodes.addAll(newNodes)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val indentContainer: LinearLayout = view.findViewById(R.id.indent_container)
        val expandView: ImageView = view.findViewById(R.id.expand)
        val fileView: ImageView = view.findViewById(R.id.file_view)
        val textView: TextView = view.findViewById(R.id.text_view)
    }
}
