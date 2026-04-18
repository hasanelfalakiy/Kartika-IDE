/*
 * This file is part of Cosmic IDE.
 * Cosmic IDE is a free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * Cosmic IDE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with Cosmic IDE. If not, see <https://www.gnu.org/licenses/>.
 */

package com.andihasan7.kartikaide.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import com.andihasan7.kartikaide.BuildConfig
import com.andihasan7.kartikaide.R
import andihasan7.kartikaide.common.Analytics
import andihasan7.kartikaide.common.Prefs
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val year = LocalDate.now().year.toString()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    var showDonateDialog by remember { mutableStateOf(false) }

    if (showDonateDialog) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            title = { Text("Donate") },
            text = { Text("Support the development of Kartika IDE via Trakteer or Saweria.") },
            confirmButton = {
                TextButton(onClick = { 
                    openUrl(context, "https://trakteer.id/andi_hasan2/tip")
                    showDonateDialog = false
                }) { Text("Trakteer") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    openUrl(context, "https://saweria.co/andiHasan")
                    showDonateDialog = false
                }) { Text("Saweria") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Icon
            Box(
                modifier = Modifier
                    .size(113.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = R.mipmap.ic_launcher,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 25.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.sub_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Social Links Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SocialIconItem(
                    icon = painterResource(R.drawable.ic_telegram),
                    onClick = { openUrl(context, "https://t.me/moonlight_studio01") }
                )
                SocialIconItem(
                    icon = painterResource(R.drawable.ic_mail_24px),
                    onClick = { openUrl(context, "mailto:moonlight.official001@gmail.com") }
                )
                SocialIconItem(
                    icon = painterResource(R.drawable.ic_instagram_filled),
                    onClick = { openUrl(context, "https://www.instagram.com/andihasan_ashari") }
                )
                SocialIconItem(
                    icon = painterResource(R.drawable.ic_youtube),
                    onClick = { openUrl(context, "https://youtube.com/@moonlightstudio") }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Maintainer Section
            Text(
                text = stringResource(R.string.dev),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = "https://avatars.githubusercontent.com/u/96150715?v=4",
                        contentDescription = stringResource(R.string.img_dev),
                        modifier = Modifier
                            .size(73.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(R.drawable.img_placeholder)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.dev_maintener),
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 19.sp
                        )
                        Text(
                            text = stringResource(R.string.sub_author),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Items Group
            Text(
                text = "Actions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                textAlign = TextAlign.Start
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    AboutActionItem(
                        icon = Icons.Default.Favorite,
                        title = "Donate",
                        summary = "Support the development of Kartika IDE",
                        onClick = { showDonateDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    AboutActionItem(
                        icon = Icons.Default.Terminal,
                        title = "Rish (Shizuku)",
                        summary = "Connect to Shizuku for advanced features",
                        onClick = { 
                            try {
                                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (intent != null) {
                                    context.startActivity(intent)
                                } else {
                                    openUrl(context, "https://shizuku.rikka.app/")
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Shizuku app not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)

                    AboutActionItem(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear Cache",
                        summary = "Free up some space by deleting temporary files",
                        onClick = { 
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)

                    AboutActionItem(
                        icon = Icons.Default.BugReport,
                        title = "Force Crash",
                        summary = "Test crash reporting (Caution: app will exit)",
                        onClick = { throw RuntimeException("Manual Crash Triggered for testing") }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)

                    var analyticsEnabled by remember { mutableStateOf(Prefs.analyticsEnabled) }
                    SwitchActionItem(
                        icon = Icons.Default.Analytics,
                        title = "Analytics",
                        summary = "Help us improve by sending anonymous usage data",
                        checked = analyticsEnabled,
                        onCheckedChange = { 
                            analyticsEnabled = it
                            prefs.edit { putBoolean("analytics_preference", it) }
                            Analytics.setAnalyticsCollectionEnabled(it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Share Section
            Button(
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_description))
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.share_app))
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.share_note),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT})",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.copyright, year),
                style = MaterialTheme.typography.bodySmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
fun SocialIconItem(icon: Painter, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AboutActionItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SwitchActionItem(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
    }
}
