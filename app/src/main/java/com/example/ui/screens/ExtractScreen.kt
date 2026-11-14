package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DumpFormat
import com.example.ui.ExtractUiState
import com.example.ui.ObscuraViewModel
import com.example.ui.components.MonospaceOutputBox
import com.example.ui.components.ObscuraEmptyState
import com.example.ui.components.ObscuraSectionCard
import com.example.ui.components.ObscuraSubScreenScaffold
import com.example.ui.components.StatusPill
import com.example.ui.components.TactileAssistChip
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileFilterChip
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObscuraSizing
import com.example.ui.theme.ObscuraSpacing

private val EVAL_PRESETS = listOf(
    "Title" to "document.title",
    "WebDriver" to "({ webdriver: navigator.webdriver, platform: navigator.platform })",
    "Headings" to "Array.from(document.querySelectorAll('h1,h2')).map(e => e.textContent.trim())",
    "Links JSON" to "Array.from(document.querySelectorAll('a')).slice(0,20).map(a => ({ text: a.textContent.trim(), href: a.href }))",
    "Meta" to "Array.from(document.querySelectorAll('meta')).map(m => ({ name: m.name || m.property, content: m.content }))",
)

@Composable
fun ExtractScreen(
    viewModel: ObscuraViewModel,
    state: ExtractUiState,
    activeUrl: String,
    onBack: () -> Unit,
) {
    ObscuraSubScreenScaffold(
        title = "DOM Extract & JS Eval",
        subtitle = activeUrl,
        onBack = onBack,
    ) { mod ->
        LazyColumn(
            modifier = mod.widthIn(max = ObscuraSizing.maxContentWidthExpanded),
            contentPadding = PaddingValues(ObscuraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.lg),
        ) {
            item {
                ObscuraSectionCard(
                    title = "DOM Extraction (--dump)",
                    subtitle = "Extract structured content from the active tab",
                    leadingIcon = Icons.Outlined.Description,
                    trailing = {
                        TactileButton(
                            onClick = { viewModel.runExtraction() },
                            enabled = !state.isExtracting,
                            modifier = Modifier.testTag("extract_run_button"),
                        ) {
                            if (state.isExtracting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(ObscuraSpacing.xs))
                            Text("Extract")
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        DumpFormat.entries.forEach { format ->
                            TactileFilterChip(
                                selected = state.selectedFormat == format,
                                onClick = { viewModel.selectDumpFormat(format) },
                                label = { Text(format.label, style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.testTag("dump_format_${format.cliFlag}"),
                            )
                        }
                    }

                    Spacer(Modifier.height(ObscuraSpacing.md))

                    if (state.selectedFormat == DumpFormat.SNAPSHOT && state.snapshotRefs.isNotEmpty()) {
                        Text(
                            text = "Interactive Element Refs (${state.snapshotRefs.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(ObscuraSpacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.xs)) {
                            state.snapshotRefs.take(25).forEach { ref ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                                    ) {
                                        StatusPill(
                                            text = ref.ref,
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                        Text(
                                            text = "<${ref.tag}>",
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(
                                            text = ref.text.ifEmpty { ref.href.ifEmpty { "(element)" } },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(ObscuraSpacing.md))
                    }

                    if (state.extractedOutput.isBlank()) {
                        ObscuraEmptyState(
                            icon = Icons.Outlined.Description,
                            title = "Ready to Extract",
                            description = "Select Markdown, Text, Links, HTML, Assets, Cookies, Snapshot, or Original and tap Extract.",
                        )
                    } else {
                        MonospaceOutputBox(
                            title = "--dump ${state.selectedFormat.cliFlag} (${state.extractedOutput.length} chars)",
                            text = state.extractedOutput,
                            onShare = {
                                viewModel.shareText("Obscura ${state.selectedFormat.label} Export", state.extractedOutput)
                            },
                            onCopied = { viewModel.showStatusBanner("Extracted output copied") },
                        )
                    }
                }
            }

            item {
                ObscuraSectionCard(
                    title = "JavaScript Console (--eval)",
                    subtitle = "Evaluate JS against the live DOM",
                    leadingIcon = Icons.Outlined.Code,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        EVAL_PRESETS.forEach { (label, code) ->
                            TactileAssistChip(
                                onClick = { viewModel.evaluateJs(code) },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.testTag("eval_preset_${label.lowercase().replace(' ', '_')}"),
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.jsExpression,
                        onValueChange = viewModel::updateJsExpression,
                        label = { Text("JavaScript Expression") },
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eval_expression_input"),
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TactileButton(
                            onClick = { viewModel.evaluateJs() },
                            enabled = !state.isEvaluatingJs,
                            modifier = Modifier.testTag("eval_run_button"),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(ObscuraSpacing.xs))
                            Text("Run --eval")
                        }
                    }

                    Spacer(Modifier.height(ObscuraSpacing.md))

                    if (state.jsResult.isBlank()) {
                        ObscuraEmptyState(
                            icon = Icons.Outlined.Code,
                            title = "Evaluate JS",
                            description = "Tap any preset or enter an expression to inspect DOM values or stealth fingerprint properties.",
                        )
                    } else {
                        MonospaceOutputBox(
                            title = "Eval Result (JSON)",
                            text = state.jsResult,
                            onShare = { viewModel.shareText("Obscura JS Eval", state.jsResult) },
                            onCopied = { viewModel.showStatusBanner("JS eval result copied") },
                        )
                    }
                }
            }
        }
    }
}
