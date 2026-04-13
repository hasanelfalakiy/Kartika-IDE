/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import andihasan7.kartikaide.common.BaseBindingFragment
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import com.andihasan7.kartikaide.databinding.FragmentAboutBinding
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.BuildConfig
import java.time.LocalDate

class AboutFragment : BaseBindingFragment<FragmentAboutBinding>() {
    
    override var isBackHandled = true

    override fun getViewBinding() = FragmentAboutBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupAuthorImage()
        setupAppInfo()

        setupSocial()

    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupAuthorImage() {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val baseUrl = "https://avatars.githubusercontent.com/u/96150715?v=4"
        val fullUrl = "$baseUrl&t=$dateStr"

        Glide.with(this)
            .load(fullUrl)
            .centerCrop()
            .circleCrop()
            .placeholder(R.drawable.img_placeholder)
            .into(binding.imgAuthor)
    }

    private fun setupAppInfo() {
        val _nowYear = LocalDate.now().year.toString()
        val copyright = getString(R.string.copyright, _nowYear)
        binding.tvCopyright.setText(copyright)
        binding.version.text = "Kartika IDE v${BuildConfig.VERSION_NAME}"
    }

    private fun setupSocial() {
        binding.socialLayout.btnTelegram.setOnClickListener {
            val uri = Uri.parse("https://t.me/moonlight_studio01")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        binding.socialLayout.btnEmail.setOnClickListener {
            val recipient = "moonlight.official001@gmail.com"
            val intent = Intent(Intent.ACTION_SEND).apply {
                // The intent does not have a URI, so declare the "text/plain" MIME type
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient)) // recipients
                putExtra(Intent.EXTRA_SUBJECT, "")
                putExtra(Intent.EXTRA_TEXT, "")
                // You can also attach multiple items by passing an ArrayList of Uris
            }
            startActivity(intent)
        }

        binding.socialLayout.btnInstagram.setOnClickListener {
            val uri = Uri.parse("https://www.instagram.com/andihasan_ashari?igsh=MmtwNmk2aXV1Mmx2")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        binding.socialLayout.btnYoutube.setOnClickListener {
            val uri = Uri.parse("https://youtube.com/@moonlightstudio")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }

        binding.btnShare.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.share_description))
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)

        }
    }
}
