/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.fragment

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.os.BundleCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.subScreen
import com.andihasan7.kartikaide.MainActivity
import com.andihasan7.kartikaide.chat.ChatProvider
import andihasan7.kartikaide.common.BaseBindingFragment
import com.andihasan7.kartikaide.databinding.FragmentSettingsBinding
import com.andihasan7.kartikaide.fragment.settings.AboutSettings
import com.andihasan7.kartikaide.fragment.settings.AppearanceSettings
import com.andihasan7.kartikaide.fragment.settings.CompilerSettings
import com.andihasan7.kartikaide.fragment.settings.EditorSettings
import com.andihasan7.kartikaide.fragment.settings.FormatterSettings
import com.andihasan7.kartikaide.fragment.settings.GeminiSettings
import com.andihasan7.kartikaide.fragment.settings.GitSettings
import com.andihasan7.kartikaide.fragment.settings.PluginSettingsProvider

/**
 * Fragment for displaying settings screen.
 */
class SettingsFragment : BaseBindingFragment<FragmentSettingsBinding>() {
    private lateinit var preferencesAdapter: PreferencesAdapter
    override var isBackHandled = true

    override fun getViewBinding() = FragmentSettingsBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Preference.Config.dialogBuilderFactory = { context -> MaterialAlertDialogBuilder(context) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appearanceSettings = AppearanceSettings(requireActivity() as MainActivity)
        val editorSettings = EditorSettings(requireActivity())
        val formatterSettings = FormatterSettings(requireActivity())
        val compilerSettings = CompilerSettings(requireActivity())
        val pluginsSettings = PluginSettingsProvider(requireActivity())
        val gitSettings = GitSettings(requireActivity())
        val aboutSettings = AboutSettings(requireActivity())
        val geminiSettings = GeminiSettings(requireActivity())

        var geminiScreen: PreferenceScreen? = null

        val screen = screen(requireContext()) {
            subScreen {
                collapseIcon = true
                title = "Appearance"
                summary = "Customize the appearance as you see fit"
                appearanceSettings.provideSettings(this)
            }
            subScreen {
                collapseIcon = true
                title = "Code editor"
                summary = "Customize editor settings"
                editorSettings.provideSettings(this)
            }
            subScreen {
                collapseIcon = true
                title = "Compiler"
                summary = "Configure compiler options and build process"
                compilerSettings.provideSettings(this)
            }
            subScreen {
                collapseIcon = true
                title = "Formatter"
                summary = "Adjust code formatting preferences"
                formatterSettings.provideSettings(this)
            }
            subScreen {
                collapseIcon = true
                title = "Plugins"
                summary = "Explore and install plugins to extend the functionality of the IDE"
                pluginsSettings.provideSettings(this)
            }
            subScreen {
                collapseIcon = true
                title = "Git"
                summary = "Configure Git integration"
                gitSettings.provideSettings(this)
            }
            geminiScreen = subScreen {
                collapseIcon = true
                title = "Gemini"
                summary = "Configure Gemini integration"
                geminiSettings.provideSettings(this)
            }
            subScreen {
                collapseIcon = true
                title = "About"
                summary = "Learn more about Kartika IDE"
                aboutSettings.provideSettings(this)
            }
        }

        preferencesAdapter = PreferencesAdapter(screen)
        savedInstanceState?.let {
            val savedState = BundleCompat.getParcelable(
                    it,
                    "adapter",
                    PreferencesAdapter.SavedState::class.java
                )
            if (savedState != null) {
                preferencesAdapter.loadSavedState(savedState)
            }
        }

        binding.preferencesView.adapter = preferencesAdapter
        binding.toolbar.setNavigationOnClickListener {
            if (preferencesAdapter.currentScreen == geminiScreen) {
                ChatProvider.regenerateModel()
            }
            if (!preferencesAdapter.goBack()) {
                parentFragmentManager.setFragmentResult("settings_changed", Bundle())
                parentFragmentManager.popBackStack()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isResumed.not()) {
                        return
                    }
                    if (preferencesAdapter.currentScreen == geminiScreen) {
                        ChatProvider.regenerateModel()
                    }
                    if (!preferencesAdapter.goBack()) {
                        parentFragmentManager.setFragmentResult("settings_changed", Bundle())
                        isEnabled = false
                        parentFragmentManager.popBackStack()
                    }
                }
            })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::preferencesAdapter.isInitialized.not()) {
            return
        }
        outState.putParcelable("adapter", preferencesAdapter.getSavedState())
    }
}
