/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.databinding.InstallResourcesFragmentBinding
import andihasan7.kartikaide.common.BaseBindingFragment
import andihasan7.kartikaide.rewrite.util.FileUtil
import com.andihasan7.kartikaide.util.Download
import com.andihasan7.kartikaide.util.ResourceUtil

class InstallResourcesFragment : BaseBindingFragment<InstallResourcesFragmentBinding>() {

    val rawUrl = "https://github.com/Cosmic-Ide/binaries/raw/main/"
    override fun getViewBinding() = InstallResourcesFragmentBinding.inflate(layoutInflater)

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.installResourcesButton.setOnClickListener {
            binding.installResourcesButton.isEnabled = false
            binding.installResourcesProgress.visibility = View.VISIBLE
            binding.installResourcesProgressText.visibility = View.VISIBLE

            lifecycleScope.launch(Dispatchers.IO) {
                for (res in ResourceUtil.missingResources()) {
                    withContext(Dispatchers.Main) {
                        binding.installResourcesText.text = "Preparing to download resource $res"
                        binding.installResourcesProgress.isIndeterminate = true
                        binding.installResourcesProgressText.text = "Connecting..."
                    }
                    if (installResource(res).not()) {
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        binding.installResourcesText.text = "Downloaded resource $res"
                    }
                }
                withContext(Dispatchers.Main) {
                    parentFragmentManager.commit {
                        replace(R.id.fragment_container, ProjectFragment())
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    suspend fun installResource(res: String): Boolean {
        return try {
            val url = rawUrl + res.substringAfterLast('/')
            val file = FileUtil.dataDir.resolve(res)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
            
            Download(url) { percent ->
                lifecycleScope.launch(Dispatchers.Main) {
                    if (percent == -1) {
                        if (!binding.installResourcesProgress.isIndeterminate) {
                            binding.installResourcesProgress.isIndeterminate = true
                        }
                        binding.installResourcesProgressText.text = "Downloading..."
                    } else {
                        binding.installResourcesProgress.isIndeterminate = false
                        binding.installResourcesProgress.progress = percent
                        binding.installResourcesProgressText.text = "$percent%"
                    }
                }
            }.start(file)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                binding.installResourcesText.text =
                    "Failed to download resource $res: ${e.message}"
                binding.installResourcesButton.isEnabled = true
                binding.installResourcesProgress.visibility = View.GONE
                binding.installResourcesProgressText.visibility = View.GONE
            }
            false
        }
    }
}
