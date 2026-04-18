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

package com.andihasan7.kartikaide.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andihasan7.kartikaide.fragment.PluginsFragment
import andihasan7.kartikaide.rewrite.plugin.api.Plugin
import andihasan7.kartikaide.rewrite.util.FileUtil
import andihasan7.kartikaide.common.Prefs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledPluginsScreen(onBackClick: () -> Unit) {
    val plugins = remember { mutableStateListOf<Plugin>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        plugins.addAll(PluginsFragment.getPlugins())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Installed Plugins") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (plugins.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No plugins installed")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(plugins) { plugin ->
                    PluginItem(
                        plugin = plugin,
                        trailing = {
                            IconButton(onClick = {
                                FileUtil.pluginDir.resolve(plugin.name).deleteRecursively()
                                plugins.remove(plugin)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailablePluginsScreen(onBackClick: () -> Unit) {
    var plugins by remember { mutableStateOf<List<Plugin>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        isLoading = true
        plugins = withContext(Dispatchers.IO) {
            try {
                val pluginsJson = URL(Prefs.pluginRepository).readText()
                val pluginsType = object : TypeToken<List<Plugin>>() {}.type
                Gson().fromJson(pluginsJson, pluginsType) as List<Plugin>
            } catch (e: Exception) {
                emptyList()
            }
        }
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Available Plugins") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (plugins.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Could not load plugins from repository")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(plugins) { plugin ->
                    PluginItem(
                        plugin = plugin,
                        trailing = {
                            IconButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val pluginFolder = FileUtil.pluginDir.resolve(plugin.name)
                                        pluginFolder.mkdirs()
                                        pluginFolder.resolve("config.json").writeText(Gson().toJson(plugin))
                                        
                                        val dexUrl = "${plugin.source}/releases/download/${plugin.version}/classes.dex"
                                        pluginFolder.resolve("classes.dex").writeBytes(URL(dexUrl).readBytes())
                                        
                                        snackbarHostState.showSnackbar("Installed ${plugin.name}")
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Failed to install ${plugin.name}")
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Download, contentDescription = "Install")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PluginItem(
    plugin: Plugin,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Extension,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v${plugin.version} by ${plugin.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plugin.description.isNotEmpty()) {
                    Text(
                        text = plugin.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}
