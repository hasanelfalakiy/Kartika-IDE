/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import andihasan7.kartikaide.buildtools.BuildReporter
import andihasan7.kartikaide.buildtools.dex.D8Task
import andihasan7.kartikaide.common.BaseBindingFragment
import andihasan7.kartikaide.common.Prefs
import andihasan7.kartikaide.project.Language
import andihasan7.kartikaide.project.Project
import andihasan7.kartikaide.rewrite.util.MultipleDexClassLoader
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.andihasan7.kartikaide.FileProvider.openFileWithExternalApp
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.adapter.BottomDrawerAdapter
import com.andihasan7.kartikaide.adapter.EditorAdapter
import com.andihasan7.kartikaide.adapter.NavAdapter
import com.andihasan7.kartikaide.compile.Compiler
import com.andihasan7.kartikaide.databinding.FragmentEditorBinding
import com.andihasan7.kartikaide.databinding.NavigationElementsBinding
import com.andihasan7.kartikaide.databinding.NewDependencyBinding
import com.andihasan7.kartikaide.databinding.TextDialogBinding
import com.andihasan7.kartikaide.databinding.TreeviewContextActionDialogItemBinding
import com.andihasan7.kartikaide.editor.EditorInputStream
import com.andihasan7.kartikaide.editor.IdeEditor
import com.andihasan7.kartikaide.editor.formatter.GoogleJavaFormat
import com.andihasan7.kartikaide.editor.formatter.ktfmtFormatter
import com.andihasan7.kartikaide.editor.language.KotlinLanguage
import com.andihasan7.kartikaide.model.FileViewModel
import com.andihasan7.kartikaide.model.ProjectViewModel
import com.andihasan7.kartikaide.util.CommonUtils
import com.andihasan7.kartikaide.util.FileFactoryProvider
import com.andihasan7.kartikaide.util.FileIndex
import com.andihasan7.kartikaide.util.PreferenceKeys
import com.andihasan7.kartikaide.util.ProjectHandler
import com.andihasan7.kartikaide.util.makeDexReadOnlyIfNeeded
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.widget.treeview.OnTreeItemClickListener
import com.widget.treeview.TreeIconProvider
import com.widget.treeview.TreeUtils.toNodeList
import com.widget.treeview.TreeViewAdapter
import dev.pranav.navigation.KtNavigationProvider
import dev.pranav.navigation.NavigationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.EventReciever
import org.cosmic.ide.dependency.resolver.api.Repository
import org.cosmic.ide.dependency.resolver.eventReciever
import org.cosmic.ide.dependency.resolver.getArtifact
import org.cosmic.ide.dependency.resolver.repositories
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class EditorFragment : BaseBindingFragment<FragmentEditorBinding>() {
    private lateinit var fileIndex: FileIndex
    private val fileViewModel by activityViewModels<FileViewModel>()
    private val projectViewModel by activityViewModels<ProjectViewModel>()
    private lateinit var editorAdapter: EditorAdapter
    private lateinit var bottomDrawerAdapter: BottomDrawerAdapter
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private val project by lazy { requireArguments().getSerializable("project") as Project }

    private var isExecutionRunning: Boolean = false

    override var isBackHandled = true

    override fun getViewBinding() = FragmentEditorBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            fileViewModel.removeAll()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileIndex = FileIndex(project)
        ProjectHandler.setProject(project)

        configureToolbar()
        initViewModelListeners()
        initTreeView()
        initBottomDrawer()
        setupKeyboardListener()
        
        binding.included.projectName.text = project.name
        binding.included.drawerHeader.setOnLongClickListener {
            showTreeViewMenu(it, project.root)
            true
        }

        binding.pager.apply {
            editorAdapter = EditorAdapter(this@EditorFragment, fileViewModel)
            adapter = editorAdapter
            isUserInputEnabled = false
            isSaveEnabled = true
        }

        if (fileViewModel.files.value.isNullOrEmpty()) {
            fileViewModel.updateFiles(fileIndex.getFiles())
        }

        binding.tabLayout.isSmoothScrollingEnabled = false

        binding.included.refresher.apply {
            setOnRefreshListener {
                lifecycleScope.launch {
                    initTreeView()
                    binding.included.recycler.post {
                        updateDrawerLockState()
                        binding.included.refresher.isRefreshing = false
                    }
                }
            }
        }

        binding.drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                updateDrawerLockState()
            }

            override fun onDrawerClosed(drawerView: View) {
                binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
        })

        binding.included.hScroll.apply {
            setOnScrollChangeListener { _, _, _, _, _ ->
                updateDrawerLockState()
            }
            
            var lastX = 0f
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = event.x
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - lastX

                        if (deltaX < 0 && v.canScrollHorizontally(1)) {
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        } else if (deltaX > 0 && v.canScrollHorizontally(-1)) {
                            v.parent.requestDisallowInterceptTouchEvent(true)
                        } else {
                            v.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                false
            }
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                fileViewModel.setCurrentPosition(tab.position)
                updateUndoRedoStatus()
                // Bind SymbolInputView to the current editor
                getCurrentFragment()?.editor?.let {
                    view.findViewById<io.github.rosemoe.sora.widget.SymbolInputView>(R.id.symbol_view)?.bindEditor(it)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                editorAdapter.getItem(tab.position)?.let {
                    it.save()
                    it.hideWindows()
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                editorAdapter.saveAll()
                if (Prefs.doubleClickClose && fileViewModel.currentPosition.value == tab.position) {
                    fileViewModel.removeFile(tab.position)
                }
            }
        })

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    binding.apply {
                        if (drawer.isOpen) {
                            drawer.close()
                        } else if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        } else {
                            exitProject()
                        }
                    }
                }
            })

        TabLayoutMediator(binding.tabLayout, binding.pager, true, false) { tab, position ->
            val file = fileViewModel.files.value!![position]
            tab.text = file.name
            tab.icon = getFileIcon(file)
            tab.view.setOnLongClickListener {
                showMenu(it, R.menu.tab_menu, position)
                true
            }
        }.attach()

        parentFragmentManager.setFragmentResultListener("settings_changed", viewLifecycleOwner) { _, _ ->
            refreshAllEditors()
        }
    }

    private fun initBottomDrawer() {
        val bottomSheet = binding.root.findViewById<View>(R.id.bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        
        val pager = bottomSheet.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.bottom_drawer_pager)
        val tabs = bottomSheet.findViewById<com.google.android.material.tabs.TabLayout>(R.id.bottom_drawer_tabs)
        val flipper = bottomSheet.findViewById<android.widget.ViewFlipper>(R.id.header_flipper)
        
        bottomDrawerAdapter = BottomDrawerAdapter()
        pager.adapter = bottomDrawerAdapter
        
        TabLayoutMediator(tabs, pager) { tab, position ->
            when(position) {
                0 -> { tab.text = "Build Log"; tab.setIcon(R.drawable.ic_build_log) }
                1 -> { tab.text = "Output"; tab.setIcon(R.drawable.ic_output) }
                2 -> { tab.text = "Search"; tab.setIcon(R.drawable.ic_search_results) }
            }
        }.attach()

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when(newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        flipper.displayedChild = 2 // Full Header
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        // Check if keyboard is active via a flag or view height
                        if (isKeyboardVisible()) {
                            flipper.displayedChild = 1 // Symbol View
                        } else {
                            flipper.displayedChild = 0 // Status Build
                        }
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        // Handle clicks on header status to expand
        bottomSheet.findViewById<View>(R.id.header_status).setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        binding.fabClean.setOnClickListener {
            bottomDrawerAdapter.clearLog(pager.currentItem)
        }
    }

    private fun setupKeyboardListener() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - r.bottom

            val flipper = binding.root.findViewById<android.widget.ViewFlipper>(R.id.header_flipper)
            
            if (keypadHeight > screenHeight * 0.15) { // Keyboard is visible
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    flipper.displayedChild = 1 // Symbol View
                }
            } else { // Keyboard is hidden
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                    flipper.displayedChild = 0 // Status Build
                }
            }
        }
    }

    private fun isKeyboardVisible(): Boolean {
        val r = Rect()
        binding.root.getWindowVisibleDisplayFrame(r)
        val screenHeight = binding.root.rootView.height
        return (screenHeight - r.bottom) > screenHeight * 0.15
    }

    private fun getFileIcon(file: File): Drawable? {
        val resId = when (file.extension.lowercase()) {
            "kt" -> R.drawable.ic_kotlin
            "kts" -> R.drawable.ic_kotlin_kts
            "apk", "apks", "dex" -> R.drawable.ic_android
            "bin" -> R.drawable.ic_bin_file
            "cpp", "cxx", "cc" -> R.drawable.ic_cpp
            "c" -> R.drawable.ic_c
            "cs" -> R.drawable.ic_c_sharp
            "html", "htm" -> R.drawable.ic_html
            "css" -> R.drawable.ic_css
            "jar" -> R.drawable.ic_jar_icon
            "js" -> R.drawable.ic_javascript
            "json" -> R.drawable.ic_json
            "java", "class" -> R.drawable.ic_java
            "gitignore" -> R.drawable.ic_git
            "gradle" -> R.drawable.ic_gradle
            "gradlew", "bat" -> R.drawable.ic_termux
            "md" -> R.drawable.ic_markdown_m
            "py", "pyw", "pyi", "pyx", "pxd" -> R.drawable.ic_python
            "svg" -> R.drawable.ic_svg_icon
            "properties", "env", "ini", "cfg", "conf", "toml", "yaml", "yml" -> R.drawable.ic_file_config
            "xml", "xaml", "xsd", "xslt", "xsl", "plist", "rss", "atom" -> R.drawable.ic_xml
            "zip" -> R.drawable.baseline_folder_zip_24
            "license", "licence", "copying", "notice" -> R.drawable.baseline_copyright_24
            else -> if (file.name.startsWith(".")) {
                R.drawable.ic_git
            } else if (file.name.startsWith("gradlew")) {
                R.drawable.ic_termux
            } else if (file.name.startsWith("LICENSE")) {
                R.drawable.baseline_copyright_24
            } else if (file.name.startsWith("license.")) {
                R.drawable.baseline_copyright_24
            } else R.drawable.ic_file
        }
        return ContextCompat.getDrawable(requireContext(), resId)
    }

    private fun exitProject() {
        editorAdapter.saveAll()
        editorAdapter.releaseAll()

        fileIndex.putFiles(
            binding.pager.currentItem, fileViewModel.files.value!!
        )

        fileViewModel.files.removeObservers(viewLifecycleOwner)
        fileViewModel.currentPosition.removeObservers(viewLifecycleOwner)
        fileViewModel.removeAll()
        parentFragmentManager.popBackStack()
    }

    private fun updateDrawerLockState() {
        binding.drawer.post {
            if (binding.drawer.isDrawerOpen(GravityCompat.START)) {
                val hScroll = binding.included.hScroll
                if (hScroll.canScrollHorizontally(1)) {
                    binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN)
                } else {
                    binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }
        }
    }

    private fun refreshAllEditors() {
        editorAdapter.refreshSettings()
    }

    private fun initViewModelListeners() {
        fileViewModel.files.observe(viewLifecycleOwner, ::handleFilesUpdate)

        fileViewModel.currentPosition.observe(viewLifecycleOwner) { pos ->
            binding.toolbar.apply {
                if (pos == -1) {
                    menu.findItem(R.id.nav_items)?.isVisible = false
                    menu.findItem(R.id.undo)?.isVisible = false
                    menu.findItem(R.id.redo)?.isVisible = false
                    return@observe
                } else {
                    menu.findItem(R.id.nav_items)?.isVisible = true
                    menu.findItem(R.id.undo)?.isVisible = true
                    menu.findItem(R.id.redo)?.isVisible = true
                }
            }


            if (binding.drawer.isOpen) binding.drawer.close()
            val tab = binding.tabLayout.getTabAt(pos)
            if (tab != null && tab.isSelected.not()) {
                tab.select()
                binding.pager.currentItem = pos
            }
            updateUndoRedoStatus()
        }
        
        if (fileViewModel.files.value!!.isEmpty()) {
            binding.viewContainer.displayedChild = 1
        }
    }

    fun updateUndoRedoStatus() {
        val editor = getCurrentFragment()?.editor
        if (editor != null) {
            val canUndo = editor.canUndo()
            val canRedo = editor.canRedo()
            updateUndoRedoIcons(canUndo, canRedo)
        } else {
            updateUndoRedoIcons(canUndo = false, canRedo = false)
        }
    }

    private fun updateUndoRedoIcons(canUndo: Boolean, canRedo: Boolean) {
        binding.toolbar.menu.apply {
            findItem(R.id.undo)?.let {
                it.isEnabled = canUndo
                it.icon?.alpha = if (canUndo) 255 else 100
            }
            findItem(R.id.redo)?.let {
                it.isEnabled = canRedo
                it.icon?.alpha = if (canRedo) 255 else 100
            }
        }
    }

    private fun initTreeView() {
        binding.drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        val recyclerView = binding.included.recycler
        val currentAdapter = recyclerView.adapter as? TreeViewAdapter
        val expandedPaths = mutableSetOf<String>()
        
        currentAdapter?.getNodes()?.forEach { node ->
            if (node.isExpanded) {
                expandedPaths.add(node.value.absolutePath)
            }
        }

        val iconProvider = object : TreeIconProvider {
            override fun getIconForFile(file: File): Drawable? {
                return getFileIcon(file)
            }

            override fun getIconForFolder(file: File, isExpanded: Boolean): Drawable? {
                val resId = if (isExpanded) R.drawable.ic_filled_open_folder else R.drawable.ic_filled_folder
                return ContextCompat.getDrawable(requireContext(), resId)
            }
        }

        val nodes = project.root.toNodeList()
        val adapter = TreeViewAdapter(requireContext(), nodes, iconProvider)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        adapter.setOnItemClickListener(object : OnTreeItemClickListener {
            override fun onItemClick(view: View, position: Int) {
                val node = adapter.getNodes()[position]
                val file = node.value
                
                if (file.isDirectory) {
                    updateDrawerLockState()
                } else if (file.exists() && file.isFile) {
                    fileViewModel.addFile(file)
                }
            }

            override fun onItemLongClick(view: View, position: Int) {
                showTreeViewMenu(view, adapter.getNodes()[position].value)
            }
        })

        restoreExpandedState(adapter, expandedPaths)

        recyclerView.post {
            updateDrawerLockState()
        }
    }

    private fun restoreExpandedState(adapter: TreeViewAdapter, expandedPaths: Set<String>) {
        var i = 0
        while (i < adapter.itemCount) {
            val node = adapter.getNodes()[i]
            if (node.value.isDirectory && expandedPaths.contains(node.value.absolutePath)) {
                adapter.expandDirectory(node, i)
            }
            i++
        }
    }


    private fun handleFilesUpdate(files: List<File>) {
        binding.apply {
            if (files.isEmpty()) {
                tabLayout.visibility = View.GONE
                viewContainer.displayedChild = 1
            } else {
                tabLayout.visibility = View.VISIBLE
                viewContainer.displayedChild = 0
            }
        }
    }

    override fun onDestroyView() {
        fileIndex.putFiles(
            binding.pager.currentItem, fileViewModel.files.value!!
        )
        fileViewModel.files.removeObservers(viewLifecycleOwner)
        fileViewModel.currentPosition.removeObservers(viewLifecycleOwner)
        super.onDestroyView()
    }


    fun getCurrentFragment(): EditorAdapter.CodeEditorFragment? {
        return editorAdapter.getItem(binding.pager.currentItem)
    }

    private fun configureToolbar() {
        binding.toolbar.apply {
            title = ""
            setNavigationOnClickListener {
                editorAdapter.saveAll()
                binding.drawer.open()
            }

            if (Prefs.experimentsEnabled) {
                menu.findItem(R.id.action_chat)?.isVisible = true
            }

            setOnMenuItemClickListener { item ->
                getCurrentFragment()?.save()
                when (item.itemId) {
                    R.id.action_compile -> {
                        editorAdapter.saveAll()
                        getCurrentFragment()?.hideWindows()
                        
                        lifecycleScope.launch(Dispatchers.IO) {
                            val mains = findMainClasses()
                            val tests = findTestClasses()
                            withContext(Dispatchers.Main) {
                                if (mains.isNotEmpty() || tests.isNotEmpty()) {
                                    showSelectionDialog(mains, tests)
                                } else {
                                    startCompilationAndExecution(null)
                                }
                            }
                        }
                        true
                    }

                    R.id.action_settings -> {
                        getCurrentFragment()?.hideWindows()
                        navigateToSettingsFragment()
                        true
                    }

                    R.id.undo -> {
                        getCurrentFragment()?.editor?.undo()
                        updateUndoRedoStatus()
                        true
                    }

                    R.id.redo -> {
                        getCurrentFragment()?.editor?.redo()
                        updateUndoRedoStatus()
                        true
                    }

                    R.id.action_format -> {
                        if (editorAdapter.itemCount == 0) return@setOnMenuItemClickListener true
                        formatCodeAsync()
                        true
                    }

                    R.id.dependency_manager -> {
                        getCurrentFragment()?.hideWindows()
                        val sheet = BottomSheetDialog(requireContext())
                        val binding = NewDependencyBinding.inflate(layoutInflater)
                        binding.apply {
                            dependency.editText?.apply {
                                setSingleLine()
                                imeOptions = EditorInfo.IME_ACTION_DONE
                                setOnEditorActionListener { v, actionId, _ ->
                                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.hideSoftInputFromWindow(v.windowToken, 0)
                                        download.performClick()
                                        true
                                    } else false
                                }
                            }

                            download.setOnClickListener {
                                val dependencyText = dependency.editText?.text.toString().trim()
                                if (dependencyText.isEmpty()) return@setOnClickListener

                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(dependency.editText?.windowToken, 0)

                                eventReciever = object : EventReciever() {

                                    private fun log(text: String) {
                                        lifecycleScope.launch(Dispatchers.Main) {
                                            editor.appendText(text + "\n")
                                        }
                                    }

                                    override fun onDownloadStart(artifact: Artifact) {
                                        log("⬇️ Start download: $artifact")
                                    }

                                    override fun onDownloadEnd(artifact: Artifact) {
                                        log("✅ Finished: $artifact")
                                    }

                                    override fun onDownloadError(artifact: Artifact, error: Throwable) {
                                        log("❌ Error: ${error.message}")
                                    }

                                    override fun onResolving(artifact: Artifact, dependency: Artifact) {
                                        log("🔍 Resolving $dependency")
                                    }

                                    override fun onResolutionComplete(artifact: Artifact) {
                                        log("✔ Resolved ${artifact.artifactId}")
                                    }

                                    override fun onDependenciesNotFound(artifact: Artifact) {
                                        log("⚠ No dependencies for ${artifact.artifactId}")
                                    }

                                    override fun onInvalidPOM(artifact: Artifact) {
                                        log("❌ Invalid POM for ${artifact.artifactId}")
                                    }
                                }

                                val arr = dependencyText.split(":")
                                if (arr.size < 3) {
                                    Toast.makeText(context, "Invalid format. Use group:artifact:version", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }

                                lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        repositories.apply s@{
                                            if (Prefs.repositories.isBlank()) {
                                                return@s
                                            }
                                            clear()

                                            Prefs.repositories.lines().forEach { line ->
                                                if (line.isBlank()) return@forEach
                                                val split = line.split(":", limit = 2)
                                                if (split.size < 2) return@forEach

                                                val name = split[0].trim()
                                                val url = split[1].trim()

                                                add(object : Repository {
                                                    override fun getName() = name
                                                    override fun getURL() = url
                                                })
                                            }
                                        }
                                        val artifact = try {
                                            getArtifact(arr[0], arr[1], arr[2])
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                editor.appendText("\nError: ${e.message}\n")
                                            }
                                            return@launch
                                        }
                                        if (artifact == null) {
                                            withContext(Dispatchers.Main) {
                                                editor.appendText("\nError: Cannot find library\n")
                                            }
                                            return@launch
                                        }
                                        try {
                                            artifact.downloadArtifact(project.libDir)
                                            project.libDir.walk().filter { it.extension != "jar" }.forEach { it.delete() }
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(requireContext(), "Dependency downloaded successfully", Toast.LENGTH_SHORT).show()
                                                sheet.dismiss()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                editor.appendText("\nException: ${e.message}\n")
                                            }
                                        }
                                    } finally {
                                        eventReciever = EventReciever()
                                    }
                                }
                            }
                            editor.editable = false
                        }
                        sheet.setContentView(binding.root)
                        sheet.show()

                        true
                    }

                    R.id.action_chat -> {
                        parentFragmentManager.commit {
                            add(R.id.fragment_container, ChatFragment())
                            addToBackStack(null)
                        }
                        true
                    }

                    R.id.action_git -> {
                        parentFragmentManager.commit {
                            add(R.id.fragment_container, GitFragment())
                            addToBackStack(null)
                        }
                        true
                    }

                    R.id.nav_items -> {
                        showNavigationElements()
                        true
                    }

                    R.id.arguments -> {
                        val binding = TextDialogBinding.inflate(layoutInflater)
                        MaterialAlertDialogBuilder(context).setTitle("Enter program arguments")
                            .setView(binding.root).setPositiveButton("Save") { _, _ ->
                                binding.textInputLayout.hint = "Arguments"
                                binding.textInputLayout.editText?.setText(
                                    project.args.joinToString(
                                        " "
                                    )
                                )
                                val args = binding.textInputLayout.editText?.text.toString()

                                val argList = mutableListOf<String>()
                                var arg = ""
                                var inSingleQuote = false
                                var inDoubleQuote = false
                                args.forEach { c ->
                                    when (c) {
                                        '\'' -> {
                                            arg += '\''
                                            if (inSingleQuote) {
                                                argList.add(arg)
                                                arg = ""
                                            }
                                            inSingleQuote = !inSingleQuote
                                        }

                                        '"' -> {
                                            arg += '"'
                                            if (inDoubleQuote) {
                                                argList.add(arg)
                                                arg = ""
                                            }
                                            inDoubleQuote = !inDoubleQuote
                                        }

                                        ' ' -> {
                                            if (inSingleQuote || inDoubleQuote) {
                                                arg += c
                                            } else {
                                                if (arg.isNotEmpty()) {
                                                    argList.add(arg)
                                                    arg = ""
                                                }
                                            }
                                        }

                                        else -> arg += c
                                    }
                                }
                                if (arg.isNotEmpty()) argList.add(arg)
                                project.args = argList.filterNot { it.isBlank() }

                            }.setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }.show()
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun showNavigationElements() {
        val editor = getCurrentFragment()
        if (editor == null) {
            CommonUtils.showSnackBar(binding.root, "Open a file first")
            return
        }

        val language = when (editor.file.extension) {
            "java", "jav" -> Language.Java
            "kt" -> Language.Kotlin
            else -> {
                CommonUtils.showSnackBar(binding.root, "Unsupported file format")
                return
            }
        }

        var symbols = mutableListOf<NavigationProvider.NavigationItem>()

        when (language) {
            is Language.Java -> {
                val psiJavaFile = FileFactoryProvider.getPsiJavaFile(
                    editor.file.name, editor.editor.text.toString()
                )

                if (psiJavaFile.classes.isEmpty()) {
                    CommonUtils.showSnackBar(binding.root, "No navigation symbols found")
                    return
                }

                psiJavaFile.classes.forEach { psiClass ->
                    symbols.addAll(NavigationProvider.extractMethodsAndFields(psiClass))
                }
            }

            is Language.Kotlin -> {
                val ktEnv = (editor.editor.editorLanguage as KotlinLanguage).kotlinEnvironment

                val analysis = ktEnv.analysis
                if (analysis == null) {
                    CommonUtils.showSnackBar(binding.root, "File analysis not completed yet")
                    return
                }
                symbols = KtNavigationProvider.parseAnalysisContext(analysis)

                if (symbols.isEmpty()) {
                    CommonUtils.showSnackBar(binding.root, "No navigation symbols found")
                    return
                }
            }
        }
        showSymbols(symbols, editor.editor)
    }

    private fun showSymbols(navItems: List<NavigationProvider.NavigationItem>, editor: IdeEditor) {
        val binding = NavigationElementsBinding.inflate(layoutInflater)
        binding.elementList.adapter = NavAdapter(requireContext(), navItems, editor.text.indexer)
        val bottomSheet = BottomSheetDialog(requireContext())
        binding.elementList.setOnItemClickListener { _, _, position, _ ->
            val item = navItems[position]
            val pos = editor.text.indexer.getCharPosition(item.startPosition)
            editor.cursor.set(pos.line, pos.column)
            bottomSheet.dismiss()
        }

        bottomSheet.apply {
            setContentView(binding.root)
            show()
        }
    }

    override fun onPause() {
        super.onPause()
        editorAdapter.saveAll()
    }

    private fun formatCodeAsync() {
        val fragment = getCurrentFragment()
        if (fragment == null) {
            CommonUtils.showSnackBar(binding.root, "No file selected")
            return
        }
        val content = fragment.editor.text
        val text = content.toString()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val formatted = when (fileViewModel.currentFile?.extension) {
                    "java" -> {
                        GoogleJavaFormat.formatCode(text)
                    }

                    "kt" -> {
                        ktfmtFormatter.formatCode(text)
                    }

                    else -> {
                        ""
                    }
                }

                if (formatted.isNotEmpty() && formatted != text) {
                    withContext(Dispatchers.Main) {
                        val endLine = content.lineCount - 1
                        val endColumn = content.getColumnCount(endLine)
                        content.replace(0, 0, endLine, endColumn, formatted)
                        updateUndoRedoStatus()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        binding.root, "Failed to format code", Snackbar.LENGTH_SHORT
                    ).apply {
                        setAction("View Error") {
                            val sheet = BottomSheetDialog(requireContext())
                            sheet.setContentView(TextView(requireContext()).apply {
                                setText(e.stackTraceToString())
                            })
                            sheet.show()
                        }
                        show()
                    }
                }
                return@launch
            }
        }
    }

    private fun findMainClasses(): List<String> {
        val mains = mutableListOf<String>()
        val srcDir = project.srcDir
        if (!srcDir.exists()) return emptyList()
        val srcPath = srcDir.absolutePath + File.separator

        srcDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "java" || file.extension == "kt")) {
                try {
                    val content = file.readText()
                    val hasMain = if (file.extension == "java") {
                        content.contains("public static void main")
                    } else {
                        content.contains(Regex("""\bfun\s+main\b"""))
                    }

                    if (hasMain) {
                        mains.add(file.absolutePath.removePrefix(srcPath))
                    }
                } catch (e: Exception) {
                }
            }
        }
        return mains
    }

    private fun findTestClasses(): List<String> {
        val tests = mutableListOf<String>()
        
        val searchFolders = listOf(
            project.root.resolve("src/test/java"),
            project.root.resolve("src/test/kotlin"),
            project.root.resolve("app/src/test/java"),
            project.root.resolve("app/src/test/kotlin"),
            project.root.resolve("lib/src/test/java"),
            project.root.resolve("lib/src/test/kotlin"),
            project.srcDir 
        )

        searchFolders.filter { it.exists() }.forEach { folder ->
            val folderPath = folder.absolutePath + File.separator
            folder.walkTopDown().forEach { file ->
                if (file.isFile && (file.extension == "java" || file.extension == "kt")) {
                    try {
                        val content = file.readText()
                        if (content.contains("@Test")) {
                            val relativePath = if (file.absolutePath.contains("${File.separator}java${File.separator}")) {
                                file.absolutePath.substringAfter("${File.separator}java${File.separator}")
                            } else if (file.absolutePath.contains("${File.separator}kotlin${File.separator}")) {
                                file.absolutePath.substringAfter("${File.separator}kotlin${File.separator}")
                            } else {
                                file.absolutePath.removePrefix(project.srcDir.absolutePath + File.separator)
                            }
                            
                            tests.add(relativePath)
                        }
                    } catch (e: Exception) {}
                }
            }
        }
        
        return tests.distinct()
    }

    private fun showSelectionDialog(mains: List<String>, tests: List<String>) {
        val items = mutableListOf<String>()
        if (mains.isNotEmpty()) {
            items.add("--- Main Functions ---")
            items.addAll(mains)
        }
        if (tests.isNotEmpty()) {
            items.add("--- Unit Tests ---")
            items.addAll(tests)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select class to run:")
            .setItems(items.toTypedArray()) { _, which ->
                val selected = items[which]
                if (selected.startsWith("---")) return@setItems
                
                if (tests.contains(selected)) {
                     project.args = listOf("--test")
                } else {
                     project.args = emptyList()
                }
                startCompilationAndExecution(selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startCompilationAndExecution(clazz: String? = null) {
        ProjectHandler.clazz = clazz
        editorAdapter.saveAll()
        
        // Show Bottom Sheet and switch to Build Log
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        val pager = binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.bottom_drawer_pager)
        pager.currentItem = 0 
        bottomDrawerAdapter.clearLog(0)
        bottomDrawerAdapter.clearLog(1)

        val reporter = BuildReporter { report ->
            if (report.message.isEmpty()) return@BuildReporter
            lifecycleScope.launch(Dispatchers.Main) {
                bottomDrawerAdapter.appendLog(0, "${report.kind}: ${report.message}")
            }
        }

        val compiler = Compiler(project, reporter)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                compiler.compile()
                withContext(Dispatchers.Main) {
                    if (reporter.buildSuccess) {
                        bottomDrawerAdapter.appendLog(0, "Build Successful!")
                        // Switch to Output tab and run
                        pager.currentItem = 1
                        runAutoDetectedClass()
                    } else {
                        bottomDrawerAdapter.appendLog(0, "Build Failed.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    bottomDrawerAdapter.appendLog(0, "Error during compilation: ${e.message}")
                }
            }
        }
    }

    private fun runAutoDetectedClass() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dex = project.binDir.resolve("classes.dex")
            if (!dex.exists()) {
                withContext(Dispatchers.Main) { bottomDrawerAdapter.appendLog(1, "Error: classes.dex not found") }
                return@launch
            }

            val dexFile = try {
                val bufferedInputStream = dex.inputStream().buffered()
                val df = DexBackedDexFile.fromInputStream(Opcodes.forApi(33), bufferedInputStream)
                bufferedInputStream.close()
                df
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { bottomDrawerAdapter.appendLog(1, "Error: failed to read dex: ${e.message}") }
                return@launch
            }

            val runnableClasses = dexFile.classes.filter { classDef ->
                classDef.methods.any { method ->
                    val isMain = method.name == "main" && (
                        method.parameterTypes.isEmpty() || 
                        (method.parameterTypes.size == 1 && method.parameterTypes[0] == "[Ljava/lang/String;")
                    )
                    val hasTestAnnotation = method.annotations.any { annot ->
                        annot.type.contains("org/junit/Test") || annot.type.contains("org/junit/jupiter/api/Test")
                    }
                    isMain || hasTestAnnotation
                }
            }.map { it.type.substring(1, it.type.length - 1) }

            withContext(Dispatchers.Main) {
                var index: String? = null
                val targetFile = ProjectHandler.clazz
                
                if (targetFile != null) {
                    val targetName = targetFile.substringBeforeLast('.').replace('\\', '/')
                    index = runnableClasses.find { it == targetName || it == "${targetName}Kt" }
                        ?: runnableClasses.find { it.endsWith("/$targetName") || it.endsWith("/${targetName}Kt") }
                    if (index == null) index = targetName.replace('/', '.')
                }

                if (index == null) {
                    if (runnableClasses.isEmpty()) {
                        bottomDrawerAdapter.appendLog(1, "Error: No runnable classes or tests found")
                        return@withContext
                    }
                    index = runnableClasses.find { it.endsWith("/Main") || it == "Main" || it.endsWith("/MainKt") || it == "MainKt" }
                        ?: runnableClasses.find { it.endsWith("Test") }
                        ?: runnableClasses.firstOrNull()
                }

                if (index != null) {
                    runClass(index)
                } else {
                    bottomDrawerAdapter.appendLog(1, "Warning: Could not determine which class to run")
                }
            }
        }
    }

    private fun runClass(className: String) = lifecycleScope.launch(Dispatchers.IO) {
        if (isExecutionRunning) return@launch
        isExecutionRunning = true
        
        val projectRootPath = project.root.absolutePath
        val props = System.getProperties()
        val oldUserDir = props.getProperty("user.dir")
        val oldProjectDir = props.getProperty("project.dir")
        val oldTmpDir = props.getProperty("java.io.tmpdir")
        val oldProjectRoot = props.getProperty("PROJECT_ROOT")

        props.setProperty("user.dir", projectRootPath)
        props.setProperty("project.dir", projectRootPath)
        props.setProperty("PROJECT_ROOT", projectRootPath)
        if (!project.cacheDir.exists()) project.cacheDir.mkdirs()
        props.setProperty("java.io.tmpdir", project.cacheDir.absolutePath)

        val outputEditor = withContext(Dispatchers.Main) { bottomDrawerAdapter.getEditor(1) }
        if (outputEditor == null) {
            isExecutionRunning = false
            return@launch
        }

        val inputStream = EditorInputStream(outputEditor)
        val systemOut = PrintStream(object : OutputStream() {
            private val bos = ByteArrayOutputStream()
            override fun write(p0: Int) { bos.write(p0) }
            override fun write(b: ByteArray, off: Int, len: Int) { bos.write(b, off, len) }
            override fun flush() {
                val bytes = bos.toByteArray()
                if (bytes.isEmpty()) return
                val s = String(bytes, Charsets.UTF_8)
                bos.reset()
                lifecycleScope.launch(Dispatchers.Main) {
                    bottomDrawerAdapter.appendLog(1, s, addNewLine = false)
                    inputStream.updateOffset(outputEditor.text.length)
                }
            }
        }, true)
        
        val oldOut = System.out
        val oldErr = System.err
        val oldIn = System.`in`
        val oldContextClassLoader = Thread.currentThread().contextClassLoader
        
        System.setOut(systemOut)
        System.setErr(systemOut)
        System.setIn(inputStream)

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean(PreferenceKeys.CONSOLE_SHOW_ROOT_INFO, true)) {
            systemOut.println("Info: Project Root -> $projectRootPath")
            systemOut.println("Info: PROJECT_ROOT is initialized for this session.")
            systemOut.println(" ")
        }

        withContext(Dispatchers.Main) {
            bottomDrawerAdapter.appendLog(1, "--- Running $className ---")
        }

        val loader = MultipleDexClassLoader(classLoader = javaClass.classLoader!!)
        val mainDex = project.binDir.resolve("classes.dex")
        if (mainDex.exists()) loader.loadDex(makeDexReadOnlyIfNeeded(mainDex))
        project.buildDir.resolve("libs").listFiles()?.filter { it.extension == "dex" }?.forEach {
            loader.loadDex(makeDexReadOnlyIfNeeded(it))
        }
        if (project.resourcesDir.exists()) loader.addResourceDir(project.resourcesDir)

        Thread.currentThread().contextClassLoader = loader.loader

        runCatching {
            loader.loader.loadClass(className.replace('/', '.'))
        }.onSuccess { clazz ->
            val hasTestAnnotation = clazz.methods.any { m -> 
                m.annotations.any { 
                    val name = it.annotationClass.java.name
                    name.contains("org.junit.Test") || name.contains("org.junit.jupiter.api.Test") 
                }
            }
            val isTest = project.args.contains("--test") || hasTestAnnotation
            
            if (isTest) {
                systemOut.println("Running Unit Test: ${clazz.name}\n")
                try {
                    val junitCoreClass = loader.loader.loadClass("org.junit.runner.JUnitCore")
                    val runClassesMethod = junitCoreClass.getMethod("runClasses", Class.forName("[Ljava.lang.Class;"))
                    val result = runClassesMethod.invoke(null, arrayOf(clazz))
                    
                    val wasSuccessful = result.javaClass.getMethod("wasSuccessful").invoke(result) as Boolean
                    val failureCount = result.javaClass.getMethod("getFailureCount").invoke(result) as Int
                    val runCount = result.javaClass.getMethod("getRunCount").invoke(result) as Int
                    val ignoreCount = result.javaClass.getMethod("getIgnoreCount").invoke(result) as Int
                    val runTime = result.javaClass.getMethod("getRunTime").invoke(result) as Long
                    
                    systemOut.println("\n--- Test Results ---")
                    systemOut.println("Run count: $runCount")
                    systemOut.println("Failure count: $failureCount")
                    systemOut.println("Ignore count: $ignoreCount")
                    systemOut.println("Time: ${runTime}ms")
                    
                    if (!wasSuccessful) {
                        systemOut.println("\nFailures:")
                        val failures = result.javaClass.getMethod("getFailures").invoke(result) as List<*>
                        for (failure in failures) {
                            systemOut.println("- ${failure.toString()}")
                            val exception = failure?.javaClass?.getMethod("getException")?.invoke(failure) as Throwable
                            exception.printStackTrace(systemOut)
                        }
                    } else {
                        systemOut.println("\nAll tests passed!")
                    }
                } catch (e: Exception) {
                    systemOut.println("JUnit Error: ${e.message}")
                }
            } else {
                val mainMethod = findMainMethod(clazz)
                if (mainMethod != null) {
                    try {
                        if (Modifier.isStatic(mainMethod.modifiers)) {
                            if (mainMethod.parameterCount == 1) mainMethod.invoke(null, project.args.toTypedArray())
                            else mainMethod.invoke(null)
                        } else {
                            val instance = clazz.getDeclaredConstructor().newInstance()
                            if (mainMethod.parameterCount == 1) mainMethod.invoke(instance, project.args.toTypedArray())
                            else mainMethod.invoke(instance)
                        }
                    } catch (e: Throwable) {
                        val cause = e.cause ?: e
                        if (cause is java.io.FileNotFoundException && (cause.message?.contains("EROFS") == true || cause.message?.contains("Permission denied") == true)) {
                            val fileName = cause.message?.substringBefore(":")?.trim() ?: "file"
                            systemOut.println("\nError:--- ANDROID RESTRICTION ---")
                            systemOut.println("Android blocks relative writes to root '/'.")
                            systemOut.println("\nFIX: Use the injected PROJECT_ROOT property in your code:")
                            systemOut.println("val root = System.getProperty(\"PROJECT_ROOT\")")
                            systemOut.println("val file = File(root, \"$fileName\")")
                        }
                        cause.printStackTrace(systemOut)
                    }
                } else {
                    systemOut.println("Error: No valid main method found in $className")
                }
            }
        }.onFailure { e ->
            systemOut.println("Load Error: ${e.message}")
            e.printStackTrace(systemOut)
        }

        systemOut.flush()
        System.setOut(oldOut)
        System.setErr(oldErr)
        System.setIn(oldIn)
        Thread.currentThread().contextClassLoader = oldContextClassLoader
        
        System.setProperty("user.dir", oldUserDir ?: "/")
        System.setProperty("project.dir", oldProjectDir ?: "")
        System.setProperty("java.io.tmpdir", oldTmpDir ?: "/tmp")
        if (oldProjectRoot != null) System.setProperty("PROJECT_ROOT", oldProjectRoot) else System.clearProperty("PROJECT_ROOT")
        
        isExecutionRunning = false
        withContext(Dispatchers.Main) {
            bottomDrawerAdapter.appendLog(1, "--- Finished ---")
        }
    }

    private fun findMainMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.firstOrNull {
            it.name == "main" && (it.parameterCount == 0 || (it.parameterCount == 1 && it.parameterTypes[0] == Array<String>::class.java))
        }
    }

    private fun navigateToSettingsFragment() {
        parentFragmentManager.commit {
            add(R.id.fragment_container, SettingsFragment())
            addToBackStack(null)
        }
    }

    private fun showMenu(v: View, @MenuRes menuRes: Int, position: Int) {
        val popup = PopupMenu(requireContext(), v)
        popup.menuInflater.inflate(menuRes, popup.menu)

        popup.setOnMenuItemClickListener {
            popup.dismiss()
            val pos = position - 1

            when (it.itemId) {
                R.id.close_tab -> {
                    editorAdapter.saveAll()
                    fileViewModel.removeFile(position)
                    if (pos > fileViewModel.currentPosition.value!!) {
                        fileViewModel.setCurrentPosition(pos)
                    }
                }

                R.id.close_all_tab -> fileViewModel.removeAll()
                R.id.close_other_tab -> fileViewModel.removeOthers(fileViewModel.files.value!![position])
            }
            true
        }
        popup.show()
    }

    private fun showTreeViewMenu(v: View, file: File) {
        val popup = PopupMenu(v.context, v)
        popup.menuInflater.inflate(R.menu.treeview_menu, popup.menu)

        if (file.isDirectory) {
            popup.menu.removeItem(R.id.open_external)
        } else {
            popup.menu.removeItem(R.id.create_kotlin_class)
            popup.menu.removeItem(R.id.create_java_class)
            popup.menu.removeItem(R.id.create_folder)

            if (file.extension == "kt" || file.extension == "java") {
                popup.menu.findItem(R.id.execute).isVisible = true
            }
        }
        
        if (file == project.root) {
            popup.menu.removeItem(R.id.delete)
            popup.menu.removeItem(R.id.rename)
        }

        if (file.extension == "jar") {
            popup.menu.add("DEX").setOnMenuItemClickListener {
                val dex = file.resolveSibling("${file.nameWithoutExtension}.dex")
                dex.createNewFile()
                D8Task.compileJar(file.toPath(), dex.parentFile!!.toPath())
                true
            }
        }

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.execute -> {
                    editorAdapter.saveAll()
                    getCurrentFragment()?.hideWindows()
                    startCompilationAndExecution(
                        file.absolutePath.replace(
                            project.srcDir.absolutePath + File.separator, ""
                        )
                    )
                }

                R.id.create_kotlin_class -> {
                    val binding = TreeviewContextActionDialogItemBinding.inflate(layoutInflater)
                    binding.textInputLayout.suffixText = ".kt"
                    MaterialAlertDialogBuilder(v.context).setTitle("Create kotlin class")
                        .setView(binding.root).setPositiveButton("Create") { _, _ ->
                            val name = binding.textInputLayout.editText?.text.toString()
                            if (name.isEmpty()) return@setPositiveButton
                            val newFile = file.resolve("$name.kt")
                            newFile.createNewFile()
                            val packageName = getPackageName(file)
                            newFile.writeText(Language.Kotlin.simpleClassFileContent(name, packageName))
                            initTreeView()
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }

                R.id.create_java_class -> {
                    val binding = TreeviewContextActionDialogItemBinding.inflate(layoutInflater)
                    binding.textInputLayout.suffixText = ".java"
                    MaterialAlertDialogBuilder(v.context).setTitle("Create java class")
                        .setView(binding.root).setPositiveButton("Create") { _, _ ->
                            val name = binding.textInputLayout.editText?.text.toString()
                            if (name.isEmpty()) return@setPositiveButton
                            val newFile = file.resolve("$name.java")
                            newFile.createNewFile()
                            val packageName = getPackageName(file)
                            newFile.writeText(Language.Java.simpleClassFileContent(name, packageName))
                            initTreeView()
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }

                R.id.create_folder -> {
                    val binding = TreeviewContextActionDialogItemBinding.inflate(layoutInflater)
                    MaterialAlertDialogBuilder(v.context).setTitle("Create folder")
                        .setView(binding.root).setPositiveButton("Create") { _, _ ->
                            val name = binding.textInputLayout.editText?.text.toString()
                            if (name.isEmpty()) return@setPositiveButton
                            file.resolve(name).mkdirs()
                            initTreeView()
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }

                R.id.create_file -> {
                    val binding = TreeviewContextActionDialogItemBinding.inflate(layoutInflater)
                    MaterialAlertDialogBuilder(v.context).setTitle("Create file")
                        .setView(binding.root).setPositiveButton("Create") { _, _ ->
                            val name = binding.textInputLayout.editText?.text.toString()
                            if (name.isEmpty()) return@setPositiveButton
                            file.resolve(name).createNewFile()
                            initTreeView()
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }

                R.id.rename -> {
                    val binding = TreeviewContextActionDialogItemBinding.inflate(layoutInflater)
                    binding.textInputLayout.editText?.setText(file.name)
                    MaterialAlertDialogBuilder(v.context).setTitle("Rename").setView(binding.root)
                        .setPositiveButton("Rename") { _, _ ->
                            val name = binding.textInputLayout.editText?.text.toString()
                            if (name.isEmpty() || name == file.name) return@setPositiveButton
                            
                            val oldPath = file.absolutePath
                            val oldName = file.nameWithoutExtension
                            val newFile = file.parentFile!!.resolve(name)
                            val newName = newFile.nameWithoutExtension
                            
                            editorAdapter.saveAll()
                            
                            if (file.renameTo(newFile)) {
                                if (file == project.root) {
                                    project.root = newFile
                                    this.binding.included.projectName.text = name
                                    this.binding.toolbar.title = ""
                                    projectViewModel.loadProjects()
                                }
                                
                                fileViewModel.updatePaths(oldPath, newFile.absolutePath)
                                
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (newFile.isDirectory) {
                                        newFile.walkTopDown().forEach { child ->
                                            updatePackageDeclaration(child)
                                        }
                                    } else {
                                        updateClassDeclaration(newFile, oldName, newName)
                                        updatePackageDeclaration(newFile)
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        editorAdapter.reloadAll()
                                        initTreeView()
                                    }
                                }
                            }
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }

                R.id.delete -> {
                    MaterialAlertDialogBuilder(v.context).setTitle("Delete")
                        .setMessage("Are you sure you want to delete this file")
                        .setPositiveButton("Delete") { _, _ ->
                            val path = file.absolutePath
                            if (file.deleteRecursively()) {
                                fileViewModel.removePath(path)
                                initTreeView()
                            }
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                }

                R.id.open_external -> {
                    openFileWithExternalApp(v.context, file)
                }
            }
            true
        }
        popup.show()
    }

    private fun updatePackageDeclaration(file: File) {
        if (!file.isFile || (file.extension != "java" && file.extension != "kt")) return
        
        val newPackage = getPackageName(file)
        val content = try { file.readText() } catch (e: Exception) { return }
        val lines = content.lines().toMutableList()
        
        var packageLineIndex = -1
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("package ")) {
                packageLineIndex = i
                break
            }
            if (line.isNotEmpty() && !line.startsWith("/") && !line.startsWith("*")) {
                break
            }
        }
        
        val newPackageLine = if (file.extension == "java") {
            if (newPackage.isEmpty()) "" else "package $newPackage;"
        } else {
            if (newPackage.isEmpty()) "" else "package $newPackage"
        }
        
        if (packageLineIndex != -1) {
            if (newPackageLine.isEmpty()) {
                lines.removeAt(packageLineIndex)
            } else {
                lines[packageLineIndex] = newPackageLine
            }
            file.writeText(lines.joinToString("\n"))
        } else if (newPackage.isNotEmpty()) {
            var insertIndex = 0
            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("/") || line.startsWith("*") || line.isEmpty()) {
                    insertIndex = i + 1
                } else {
                    break
                }
            }
            lines.add(insertIndex, newPackageLine)
            if (insertIndex + 1 < lines.size && lines[insertIndex + 1].trim().isNotEmpty()) {
                lines.add(insertIndex + 1, "")
            }
            file.writeText(lines.joinToString("\n"))
        }
    }

    private fun updateClassDeclaration(file: File, oldName: String, newName: String) {
        if (!file.isFile || (file.extension != "java" && file.extension != "kt")) return
        if (oldName == newName) return

        val content = try {
            file.readText()
        } catch (e: Exception) {
            return
        }

        val newContent = if (file.extension == "java") {
            content.replace(Regex("""\b(class|interface|enum|@interface)\s+$oldName\b"""), "$1 $newName")
        } else {
            content.replace(Regex("""\b(class|interface|object)\s+$oldName\b"""), "$1 $newName")
        }

        if (content != newContent) {
            try {
                file.writeText(newContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getPackageName(file: File): String {
        val srcPath = project.srcDir.absolutePath
        val filePath = file.absolutePath
        if (filePath.startsWith(srcPath)) {
            val relativePath = filePath.substring(srcPath.length)
                .removePrefix(File.separator)
            
            if (relativePath.isEmpty()) return ""
            
            val parentPath = if (file.isDirectory) relativePath else File(relativePath).parent ?: ""
            
            return parentPath.replace(File.separatorChar, '.')
                .removePrefix(".")
                .removeSuffix(".")
        }
        return ""
    }

    companion object {
        @JvmStatic
        fun newInstance(project: Project) = EditorFragment().apply {
            arguments = Bundle().apply {
                putSerializable("project", project)
            }
        }
    }
}
