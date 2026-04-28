/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.pranav.jgit.api.Author
import dev.pranav.jgit.tasks.Credentials
import dev.pranav.jgit.tasks.Repository
import dev.pranav.jgit.tasks.createRepository
import dev.pranav.jgit.tasks.execGit
import dev.pranav.jgit.tasks.toRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.adapter.GitAdapter
import com.andihasan7.kartikaide.adapter.StagingAdapter
import com.andihasan7.kartikaide.databinding.FragmentGitBinding
import com.andihasan7.kartikaide.databinding.GitCommandBinding
import com.andihasan7.kartikaide.databinding.DialogGitDiffBinding
import com.andihasan7.kartikaide.databinding.DialogGitProgressBinding
import andihasan7.kartikaide.common.Analytics
import andihasan7.kartikaide.common.BaseBindingFragment
import andihasan7.kartikaide.common.Prefs
import com.andihasan7.kartikaide.util.ProjectHandler
import java.io.OutputStream
import java.io.PrintStream
import java.io.Writer

class GitFragment : BaseBindingFragment<FragmentGitBinding>() {

    private lateinit var repository: Repository

    override fun getViewBinding() = FragmentGitBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    private fun setupUI() {
        val root = ProjectHandler.getProject()?.root ?: return

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.recyclerview.apply {
            adapter = GitAdapter()
            layoutManager = LinearLayoutManager(context)
        }

        val gitFolder = root.resolve(".git")
        Log.d("GitFragment", "Checking git folder at: ${gitFolder.absolutePath}")
        
        if (gitFolder.exists()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    repository = root.toRepository()
                    withContext(Dispatchers.Main) {
                        setup()
                    }
                } catch (e: Exception) {
                    Log.e("GitFragment", "Failed to open repository", e)
                    withContext(Dispatchers.Main) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Error Opening Repository")
                            .setMessage("JGit failed to open the existing repository.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        } else {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Git repository not found")
                .setMessage("Do you want to initialize a new repository?").setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            repository =
                                root.createRepository(Author(Prefs.gitUsername, Prefs.gitEmail))
                            repository.git.repository.config.apply {
                                setString("remote", "origin", "password", Prefs.gitApiKey)
                                save()
                            }
                            lifecycleScope.launch(Dispatchers.Main) {
                                (binding.recyclerview.adapter as GitAdapter).updateCommits(repository.getCommitList())
                                Snackbar.make(binding.root, "Initialized and committed", Snackbar.LENGTH_SHORT).show()
                                setup()
                            }
                        } catch (e: Exception) {
                            Log.e("GitFragment", "Failed to initialize repository", e)
                            withContext(Dispatchers.Main) {
                                Snackbar.make(binding.root, "Failed to initialize: ${e.message}", Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }.setNegativeButton("No") { _, _ ->
                    parentFragmentManager.popBackStack()
                }.show()
        }
    }

    fun setup() {
        if (!this::repository.isInitialized) return

        if (Prefs.gitUsername.isEmpty() || Prefs.gitEmail.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Git username or email not set")
                .setMessage("Do you want to set it now?").setCancelable(false)
                .setPositiveButton("Yes") { _, _ ->
                    parentFragmentManager.popBackStack()
                    parentFragmentManager.commit {
                        replace(R.id.fragment_container, SettingsFragment()).addToBackStack(null)
                    }
                }.setNegativeButton("No") { _, _ ->
                    parentFragmentManager.popBackStack()
                }.show()
            return
        }

        binding.staging.apply {
            adapter = StagingAdapter(ProjectHandler.getProject()!!.root.absolutePath) { file ->
                showDiff(file)
            }
            layoutManager = LinearLayoutManager(context)
        }

        binding.stagedList.apply {
            adapter = StagingAdapter(ProjectHandler.getProject()!!.root.absolutePath) { file ->
                showDiff(file)
            }
            layoutManager = LinearLayoutManager(context)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val commits = repository.getCommitList()
            withContext(Dispatchers.Main) {
                (binding.recyclerview.adapter as GitAdapter).updateCommits(commits)
            }
        }

        updateStatus()

        lifecycleScope.launch(Dispatchers.IO) {
            val remoteUrl = repository.git.repository.config.getString("remote", "origin", "url")
            withContext(Dispatchers.Main) {
                remoteUrl?.let { binding.remote.setText(it) }
            }
        }

        binding.addAll.text = "Add Selected"
        binding.addAll.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val selectedFiles = (binding.staging.adapter as StagingAdapter).getSelectedFiles()
                    if (selectedFiles.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Snackbar.make(binding.root, "No files selected", Snackbar.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    
                    for (file in selectedFiles) {
                        when (file.status) {
                            StagingAdapter.FileStatus.REMOVED, StagingAdapter.FileStatus.MISSING -> repository.git.rm {
                                addFilepattern(file.path)
                            }
                            else -> repository.add(file.path)
                        }
                    }
                    updateStatus()
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Added ${selectedFiles.size} files", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.remote.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val remote = binding.remote.text.toString()
                if (remote.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            repository.git.repository.config.apply {
                                setString("remote", "origin", "url", remote)
                                save()
                            }
                        } catch (e: Exception) {
                            Log.e("GitFragment", "Failed to save remote", e)
                        }
                    }
                }
            }
        }

        binding.pull.setOnClickListener {
            showProgressDialog("Pulling changes...") { progressBinding, dialog ->
                val writer = object : Writer() {
                    override fun write(cbuf: CharArray, off: Int, len: Int) {
                        val text = String(cbuf, off, len)
                        progressBinding.root.post {
                            progressBinding.outputText.append(text)
                            progressBinding.scrollView.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                    override fun flush() {}
                    override fun close() {}
                }
                
                try {
                    repository.pull(
                        writer,
                        binding.rebase.isChecked,
                        Credentials(Prefs.gitUsername, Prefs.gitApiKey)
                    )
                    withContext(Dispatchers.Main) {
                        (binding.recyclerview.adapter as GitAdapter).updateCommits(repository.getCommitList())
                        updateStatus()
                        dialog.dismiss()
                        Snackbar.make(binding.root, "Pulled changes from remote", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBinding.progressIndicator.isIndeterminate = false
                        progressBinding.progressIndicator.progress = 0
                        progressBinding.outputText.append("\nError: ${e.message}")
                        progressBinding.progressTitle.text = "Pull Failed"
                    }
                }
            }
        }

        binding.commit.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val status = repository.git.status()
                    if (status.hasUncommittedChanges().not()) {
                        withContext(Dispatchers.Main) {
                            Snackbar.make(binding.root, "Nothing to commit", Snackbar.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    repository.commit(
                        getAuthor(), binding.commitMessage.text.toString()
                    )
                    withContext(Dispatchers.Main) {
                        (binding.recyclerview.adapter as GitAdapter).updateCommits(repository.getCommitList())
                        updateStatus()
                        binding.commitMessage.text?.clear()
                        Snackbar.make(binding.root, "Committed", Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("GitFragment", "Commit failed", e)
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Commit failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.push.setOnClickListener {
            showProgressDialog("Pushing changes...") { progressBinding, dialog ->
                val writer = object : Writer() {
                    override fun write(cbuf: CharArray, off: Int, len: Int) {
                        val text = String(cbuf, off, len)
                        progressBinding.root.post {
                            progressBinding.outputText.append(text)
                            progressBinding.scrollView.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                    override fun flush() {}
                    override fun close() {}
                }

                try {
                    repository.push(
                        writer, Credentials(Prefs.gitUsername, Prefs.gitApiKey)
                    )
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Snackbar.make(binding.root, "Pushed", Snackbar.LENGTH_SHORT).show()
                    }
                    Analytics.logEvent(
                        "git_push", mapOf(
                            "project" to ProjectHandler.getProject()!!.name,
                            "remote" to binding.remote.text.toString(),
                            "rebase" to binding.rebase.isChecked,
                            "time" to System.currentTimeMillis().toString()
                        )
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBinding.progressIndicator.isIndeterminate = false
                        progressBinding.progressIndicator.progress = 0
                        progressBinding.outputText.append("\nError: ${e.message}")
                        progressBinding.progressTitle.text = "Push Failed"
                    }
                }
            }
        }

        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.custom_command) {
                val commandBinding = GitCommandBinding.inflate(layoutInflater)
                BottomSheetDialog(requireContext()).apply {
                    setContentView(commandBinding.root)
                    commandBinding.execute.setOnClickListener {
                        commandBinding.gitOutput.visibility = View.VISIBLE
                        commandBinding.gitOutput.text = ""
                        lifecycleScope.launch(Dispatchers.IO) {
                            ProjectHandler.getProject()!!.root.execGit(
                                commandBinding.gitCommand.editText?.text.toString().split(" ").toMutableList(),
                                PrintStream(object : OutputStream() {
                                    override fun write(b: Int) {
                                        lifecycleScope.launch(Dispatchers.Main) {
                                            commandBinding.gitOutput.append(b.toChar().toString())
                                        }
                                    }
                                })
                            )
                        }
                    }
                    show()
                }
            }
            true
        }
    }

    private fun showProgressDialog(title: String, action: suspend (DialogGitProgressBinding, androidx.appcompat.app.AlertDialog) -> Unit) {
        val progressBinding = DialogGitProgressBinding.inflate(layoutInflater)
        progressBinding.progressTitle.text = title
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(progressBinding.root)
            .setCancelable(false)
            .setPositiveButton("Close", null)
            .create()
        
        dialog.show()
        val closeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
        closeButton.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            action(progressBinding, dialog)
            withContext(Dispatchers.Main) {
                closeButton.visibility = View.VISIBLE
                progressBinding.progressIndicator.isIndeterminate = false
                progressBinding.progressIndicator.progress = 100
            }
        }
    }

    private fun updateStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val status = try { repository.git.status() } catch (e: Exception) { null }
            if (status != null) {
                val unstaged = mutableListOf<StagingAdapter.File>()
                val staged = mutableListOf<StagingAdapter.File>()

                status.added.forEach { staged.add(StagingAdapter.File(it, StagingAdapter.FileStatus.ADDED)) }
                status.changed.forEach { staged.add(StagingAdapter.File(it, StagingAdapter.FileStatus.STAGED_MODIFIED)) }
                status.removed.forEach { staged.add(StagingAdapter.File(it, StagingAdapter.FileStatus.STAGED_REMOVED)) }

                status.modified.forEach { unstaged.add(StagingAdapter.File(it, StagingAdapter.FileStatus.MODIFIED)) }
                status.missing.forEach { unstaged.add(StagingAdapter.File(it, StagingAdapter.FileStatus.MISSING)) }
                status.untracked.forEach { unstaged.add(StagingAdapter.File(it, StagingAdapter.FileStatus.UNTRACKED)) }
                status.conflicting.forEach { unstaged.add(StagingAdapter.File(it, StagingAdapter.FileStatus.CONFLICTING)) }

                withContext(Dispatchers.Main) {
                    (binding.staging.adapter as? StagingAdapter)?.updateFiles(unstaged)
                    (binding.stagedList.adapter as? StagingAdapter)?.updateFiles(staged)
                    binding.commit.isEnabled = staged.isNotEmpty()
                }
            }
        }
    }

    private fun showDiff(file: StagingAdapter.File) {
        lifecycleScope.launch(Dispatchers.IO) {
            val diff = repository.getDiff(file.path)
            withContext(Dispatchers.Main) {
                val diffBinding = DialogGitDiffBinding.inflate(layoutInflater)
                
                if (diff.isEmpty()) {
                    diffBinding.diffText.text = "No changes or file is untracked"
                } else {
                    diffBinding.diffText.text = formatDiffText(diff)
                }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Diff: ${file.path}")
                    .setView(diffBinding.root)
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }

    private fun formatDiffText(diff: String): CharSequence {
        val spannable = SpannableStringBuilder()
        val lines = diff.lines()
        for (line in lines) {
            val start = spannable.length
            spannable.append(line).append("\n")
            val end = spannable.length
            
            when {
                line.startsWith("+") && !line.startsWith("+++") -> {
                    // Green for additions
                    spannable.setSpan(BackgroundColorSpan(0x3300FF00.toInt()), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(ForegroundColorSpan(Color.parseColor("#2E7D32")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.startsWith("-") && !line.startsWith("---") -> {
                    // Red for deletions
                    spannable.setSpan(BackgroundColorSpan(0x33FF0000.toInt()), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(ForegroundColorSpan(Color.parseColor("#C62828")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.startsWith("@@") -> {
                    // Blue for headers
                    spannable.setSpan(ForegroundColorSpan(Color.parseColor("#0277BD")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                line.startsWith("diff") || line.startsWith("index") || line.startsWith("---") || line.startsWith("+++") -> {
                    // Bold for file info
                    spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (this::repository.isInitialized) repository.git.close()
    }

    fun catchException(code: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                code()
            } catch (e: Exception) {
                Log.e("GitFragment", e.message ?: "Unknown error", e)
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, e.message ?: "An error occurred", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getAuthor() = Author(Prefs.gitUsername, Prefs.gitEmail)
}
