/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.util

import andihasan7.kartikaide.common.PreferenceKeys as CommonKeys

/**
 * Alias object to maintain compatibility with existing code in :app module
 * while using the source of truth from :common module.
 */
object PreferenceKeys {

    // Appearance
    const val APP_THEME = CommonKeys.APP_THEME

    // Compiler
    const val COMPILER_USE_FJFS = CommonKeys.COMPILER_USE_FJFS
    const val COMPILER_USE_K2 = CommonKeys.COMPILER_USE_K2
    const val COMPILER_USE_SSVM = CommonKeys.COMPILER_USE_SSVM
    const val COMPILER_JAVA_VERSIONS = CommonKeys.COMPILER_JAVA_VERSIONS
    const val COMPILER_JAVAC_FLAGS = CommonKeys.COMPILER_JAVAC_FLAGS
    const val COMPILER_KOTLIN_VERSION = CommonKeys.COMPILER_KOTLIN_VERSION

    // Editor
    const val EDITOR_FONT_SIZE = CommonKeys.EDITOR_FONT_SIZE
    const val EDITOR_TAB_SIZE = CommonKeys.EDITOR_TAB_SIZE
    const val EDITOR_USE_SPACES = CommonKeys.EDITOR_USE_SPACES
    const val EDITOR_LIGATURES_ENABLE = CommonKeys.EDITOR_LIGATURES_ENABLE
    const val EDITOR_WORDWRAP_ENABLE = CommonKeys.EDITOR_WORDWRAP_ENABLE
    const val EDITOR_SCROLLBAR_SHOW = CommonKeys.EDITOR_SCROLLBAR_SHOW
    const val EDITOR_HW_ENABLE = CommonKeys.EDITOR_HW_ENABLE
    const val EDITOR_NON_PRINTABLE_SYMBOLS_SHOW = CommonKeys.EDITOR_NON_PRINTABLE_SYMBOLS_SHOW
    const val EDITOR_LINE_NUMBERS_SHOW = CommonKeys.EDITOR_LINE_NUMBERS_SHOW
    const val EDITOR_DOUBLE_CLICK_CLOSE = CommonKeys.EDITOR_DOUBLE_CLICK_CLOSE
    const val EDITOR_EXP_JAVA_COMPLETION = CommonKeys.EDITOR_EXP_JAVA_COMPLETION
    const val KOTLIN_REALTIME_ERRORS = CommonKeys.KOTLIN_REALTIME_ERRORS
    const val EDITOR_FONT = CommonKeys.EDITOR_FONT
    const val EDITOR_COLOR_SCHEME = CommonKeys.EDITOR_COLOR_SCHEME
    const val BRACKET_PAIR_AUTOCOMPLETE = CommonKeys.BRACKET_PAIR_AUTOCOMPLETE
    const val QUICK_DELETE = CommonKeys.QUICK_DELETE
    const val STICKY_SCROLL = CommonKeys.STICKY_SCROLL
    const val DISABLE_SYMBOLS_VIEW = CommonKeys.DISABLE_SYMBOLS_VIEW

    // Formatter
    const val FORMATTER_KTFMT_STYLE = CommonKeys.FORMATTER_KTFMT_STYLE
    const val FORMATTER_GJF_STYLE = CommonKeys.FORMATTER_GJF_STYLE
    const val FORMATTER_GJF_OPTIONS = CommonKeys.FORMATTER_GJF_OPTIONS
    const val KTFMT_MAX_WIDTH = CommonKeys.KTFMT_MAX_WIDTH
    const val KTFMT_BLOCK_INDENT = CommonKeys.KTFMT_BLOCK_INDENT
    const val KTFMT_CONTINUATION_INDENT = CommonKeys.KTFMT_CONTINUATION_INDENT
    const val KTFMT_REMOVE_UNUSED_IMPORTS = CommonKeys.KTFMT_REMOVE_UNUSED_IMPORTS
    const val KTFMT_MANAGE_TRAILING_COMMAS = CommonKeys.KTFMT_MANAGE_TRAILING_COMMAS

    // Git
    const val GIT_USERNAME = CommonKeys.GIT_USERNAME
    const val GIT_EMAIL = CommonKeys.GIT_EMAIL
    const val GIT_API_KEY = CommonKeys.GIT_API_KEY

    // Plugins
    const val AVAILABLE_PLUGINS = CommonKeys.AVAILABLE_PLUGINS
    const val INSTALLED_PLUGINS = CommonKeys.INSTALLED_PLUGINS
    const val PLUGIN_REPOSITORY = CommonKeys.PLUGIN_REPOSITORY
    const val PLUGIN_SETTINGS = CommonKeys.PLUGIN_SETTINGS

    // Gemini Pro
    const val GEMINI_API_KEY = CommonKeys.GEMINI_API_KEY
    const val GEMINI_MODEL = CommonKeys.GEMINI_MODEL
    const val TEMPERATURE = CommonKeys.TEMPERATURE
    const val TOP_P = CommonKeys.TOP_P
    const val TOP_K = CommonKeys.TOP_K
    const val CANDIDATE_COUNT = CommonKeys.CANDIDATE_COUNT
    const val MAX_TOKENS = CommonKeys.MAX_TOKENS
}
