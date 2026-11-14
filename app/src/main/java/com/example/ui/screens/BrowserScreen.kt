package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.ObscuraSettings
import com.example.ui.BrowserUiState
import com.example.ui.ObscuraViewModel
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import java.io.ByteArrayInputStream

private val QUICK_URLS = listOf(
    "example.com" to "https://example.com",
    "Hacker News" to "https://news.ycombinator.com",
    "Quotes" to "https://quotes.toscrape.com",
    "Wikipedia" to "https://en.wikipedia.org/wiki/Web_scraping",
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: ObscuraViewModel,
    state: BrowserUiState,
    settings: ObscuraSettings,
    isVisible: Boolean,
    obtainWebView: (android.content.Context) -> WebView,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Omnibox & Controls
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = state.urlInput,
                        onValueChange = viewModel::updateUrlInput,
                        singleLine = true,
                        placeholder = {
                            Text(
                                "https://example.com",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (settings.stealthEnabled) Icons.Filled.Shield else Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (settings.stealthEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                viewModel.navigateTo(state.urlInput)
                            },
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("browser_url_input"),
                    )

                    FilledIconButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.navigateTo(state.urlInput)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("browser_go_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(R.string.cd_go),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Action bar + status pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = viewModel::goBack,
                            enabled = state.canGoBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("browser_back_button"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = viewModel::goForward,
                            enabled = state.canGoForward,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("browser_forward_button"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = stringResource(R.string.cd_forward),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = viewModel::reload,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("browser_reload_button"),
                        ) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.cd_reload),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = viewModel::captureScreenshot,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("browser_screenshot_button"),
                        ) {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = stringResource(R.string.cd_screenshot),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.exportPdf(context) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("browser_pdf_button"),
                        ) {
                            Icon(
                                Icons.Outlined.PictureAsPdf,
                                contentDescription = stringResource(R.string.cd_pdf),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.lastLoadMs > 0) {
                            StatusPill(
                                text = "${state.lastLoadMs}ms",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        StatusPill(
                            text = "${state.pageBlockedTrackers} blocked",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = Icons.Filled.Shield,
                        )
                    }
                }

                // Quick target chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUICK_URLS.forEach { (label, url) ->
                        AssistChip(
                            onClick = { viewModel.navigateTo(url) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            modifier = Modifier.testTag("quick_url_${label.lowercase().replace(' ', '_')}"),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = state.isLoading) {
            LinearProgressIndicator(
                progress = { (state.progress / 100f).coerceIn(0.05f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Security block banner (SSRF / Tracker / Robots)
        if (state.blockedMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Navigation Blocked by Obscura Policy",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = state.blockedMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.navigateTo("https://example.com") },
                            modifier = Modifier.testTag("blocked_fallback_button"),
                        ) {
                            Text("Load example.com")
                        }
                    }
                }
            }
        } else {
            // Keep WebView mounted so Extract/MCP always access the live DOM
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        obtainWebView(ctx).also { webView ->
                            viewModel.attachWebView(webView)
                            viewModel.applyWebViewSettings(webView, settings)
                        }
                    },
                    update = { webView ->
                        viewModel.applyWebViewSettings(webView, settings)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("obscura_webview"),
                )
            }
        }

        // Bottom active title bar
        if (isVisible && state.pageTitle.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = state.pageTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (settings.stealthEnabled) "STEALTH ON" else "STANDARD",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (settings.stealthEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Screenshot Preview Dialog
    state.screenshotBitmap?.let { bmp ->
        Dialog(onDismissRequest = viewModel::dismissScreenshot) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Captured Page Screenshot",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = viewModel::dismissScreenshot) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close screenshot")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page screenshot preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = viewModel::dismissScreenshot) {
                            Text("Dismiss")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.shareScreenshot(bmp) },
                            modifier = Modifier.testTag("share_screenshot_button"),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share PNG")
                        }
                    }
                }
            }
        }
    }

    // Auto-sync initial settings change
    LaunchedEffect(settings.selectedProfileIndex, settings.customUserAgent) {
        // Trigger recomposition update block on WebView
    }
}
