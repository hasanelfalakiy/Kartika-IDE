/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.andihasan7.kartikaide.fragment.InstallResourcesViewModel
import com.andihasan7.kartikaide.model.ProjectViewModel
import com.andihasan7.kartikaide.ui.screens.*
import com.andihasan7.kartikaide.ui.theme.KartikaTheme
import com.andihasan7.kartikaide.util.ThemeUtils
import com.andihasan7.kartikaide.util.ResourceUtil

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        ThemeUtils.init(this)

        setContent {
            val themeMode by ThemeUtils.themeState.collectAsState()
            
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            KartikaTheme(darkTheme = darkTheme) {
                KartikaApp()
            }
        }
    }
}

@Composable
fun KartikaApp() {
    val navController = rememberNavController()
    
    val startDestination = if (ResourceUtil.missingResources().isNotEmpty()) {
        "install_resources"
    } else {
        "project_list"
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("install_resources") {
            val viewModel: InstallResourcesViewModel = viewModel()
            InstallResourcesScreen(
                viewModel = viewModel,
                onFinished = {
                    navController.navigate("project_list") {
                        popUpTo("install_resources") { inclusive = true }
                    }
                }
            )
        }
        
        composable("project_list") {
            val viewModel: ProjectViewModel = viewModel()
            ProjectListScreen(
                viewModel = viewModel,
                onProjectClick = { project ->
                    // Handle navigation to Editor
                },
                onSettingsClick = {
                    navController.navigate("settings_main")
                },
                onAboutClick = {
                    navController.navigate("about")
                },
                onNewProjectClick = {
                    navController.navigate("new_project")
                },
                /*onGitCloneClick = {
                    // Git Clone logic
                }*/
            )
        }

        composable("settings_main") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onAboutClick = { navController.navigate("about") },
                onNavigate = { screen -> navController.navigate("settings_$screen") }
            )
        }

        composable("settings_appearance") {
            AppearanceSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        composable("settings_editor") {
            EditorSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        composable("settings_compiler") {
            CompilerSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        composable("settings_formatter") {
            FormatterSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        composable("settings_git") {
            GitSettingsScreen(onBackClick = { navController.popBackStack() })
        }

        composable("settings_gemini") {
            GeminiSettingsScreen(onBackClick = { navController.popBackStack() })
        }
        
        composable("settings_plugins") {
            PluginsSettingsScreen(
                onBackClick = { navController.popBackStack() },
                onAvailablePluginsClick = { navController.navigate("available_plugins") },
                onInstalledPluginsClick = { navController.navigate("installed_plugins") }
            )
        }

        composable("about") {
            AboutScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("new_project") {
            val viewModel: ProjectViewModel = viewModel()
            NewProjectScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onProjectCreated = { project ->
                    navController.popBackStack()
                    // Handle navigation to Editor
                }
            )
        }

        composable("available_plugins") {
            AvailablePluginsScreen(onBackClick = { navController.popBackStack() })
        }

        composable("installed_plugins") {
            InstalledPluginsScreen(onBackClick = { navController.popBackStack() })
        }
    }
}
