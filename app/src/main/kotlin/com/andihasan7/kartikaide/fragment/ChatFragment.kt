/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.genai.errors.ClientException
import com.google.genai.errors.ServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.andihasan7.kartikaide.R
import com.andihasan7.kartikaide.adapter.ConversationAdapter
import com.andihasan7.kartikaide.chat.ChatProvider
import andihasan7.kartikaide.common.BaseBindingFragment
import com.andihasan7.kartikaide.databinding.FragmentChatBinding
import java.util.concurrent.CompletionException

class ChatFragment : BaseBindingFragment<FragmentChatBinding>() {

    private val conversationAdapter = ConversationAdapter()

    override fun getViewBinding() = FragmentChatBinding.inflate(layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view.context)
        setOnClickListeners()
        setupRecyclerView()
        binding.messageText.requestFocus()
    }

    private fun setupUI(context: Context) {
        initToolbar()
        binding.toolbar.title = "Gemini Pro"
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.clear) {
                conversationAdapter.clear()
                return@setOnMenuItemClickListener true
            }
            false
        }
    }

    private fun setOnClickListeners() {
        binding.sendMessageButtonIcon.setOnClickListener {
            val message = binding.messageText.text.toString().trim()
            if (message.isEmpty()) {
                return@setOnClickListener
            }
            val conversation = ConversationAdapter.Conversation(message, "user")
            conversationAdapter.add(conversation)
            binding.messageText.setText("")
            
            // Scroll to show user message
            binding.recyclerview.post {
                binding.recyclerview.smoothScrollToPosition(conversationAdapter.itemCount - 1)
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val replyFuture = ChatProvider.generate(
                        conversationAdapter.getConversations()
                    )

                    withContext(Dispatchers.Main) {
                        val response = ConversationAdapter.Conversation(stream = replyFuture)
                        conversationAdapter.add(response)
                        binding.recyclerview.post {
                            binding.recyclerview.smoothScrollToPosition(conversationAdapter.itemCount - 1)
                        }
                    }
                } catch (e: Exception) {
                    val error = if (e is CompletionException) e.cause ?: e else e
                    val errorMessage = when (error) {
                        is ClientException -> if (error.message?.contains("429") == true) 
                            "Quota exceeded. Please wait a few seconds or check your API plan." 
                            else "Client Error: ${error.message}"
                        is ServerException -> "Server Error: ${error.message}"
                        else -> "Error: ${error.localizedMessage}"
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                        // Add error message to chat so user knows what happened
                        conversationAdapter.add(ConversationAdapter.Conversation("Error: $errorMessage", "bot"))
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerview.apply {
            adapter = conversationAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            // Automatically scroll to bottom when keyboard opens due to adjustResize
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom && conversationAdapter.itemCount > 0) {
                    postDelayed({
                        smoothScrollToPosition(conversationAdapter.itemCount - 1)
                    }, 100)
                }
            }
        }
    }
}

private val Int.dp: Int
    get() = (Resources.getSystem().displayMetrics.density * this + 0.5f).toInt()
