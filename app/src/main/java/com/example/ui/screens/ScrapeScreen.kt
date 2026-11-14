package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ScrapeJobResult
import com.example.data.ScrapeMode
import com.example.data.ScrapeStatus
import com.example.ui.ObscuraViewModel
import com.example.ui.ScrapeUiState
import com.example.ui.components.MonospaceOutputBox
import com.example.ui.components.ObscuraEmptyState
import com.example.ui.components.ObscuraSectionCard
import com.example.ui.components.ObscuraSubScreenScaffold
import com.example.ui.components.StatusPill
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileFilterChip
import com.example.ui.components.TactileOutlinedButton
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalObscuraColors
import com.example.ui.theme.ObscuraSizing
import com.example.ui.theme.ObscuraSpacing
import kotlin.math.roundToInt

@Composable
fun ScrapeScreen(
    viewModel: ObscuraViewModel,
    state: ScrapeUiState,
    onBack: () -> Unit,
) {
    ObscuraSubScreenScaffold(
        title = "Batch Scraper",
        subtitle = "Parallel extraction with SSRF & tracker gates",
        onBack = onBack,
    ) { mod ->
        LazyColumn(
            modifier = mod.widthIn(max = ObscuraSizing.maxContentWidthExpanded),
            contentPadding = PaddingValues(ObscuraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.lg),
        ) {
            item {
                ObscuraSectionCard(
                    title = "Target Queue (obscura scrape)",
                    subtitle = "Fetch multiple URLs concurrently",
                    leadingIcon = Icons.Outlined.Bolt,
                ) {
                    OutlinedTextField(
                        value = state.urlsText,
                        onValueChange = viewModel::updateScrapeUrls,
                        label = { Text("Target URLs (one per line)") },
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scrape_urls_input"),
                    )

                    Spacer(Modifier.height(ObscuraSpacing.md))

                    Text(
                        text = "Extraction Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(ObscuraSpacing.xs))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        ScrapeMode.entries.forEach { mode ->
                            TactileFilterChip(
                                selected = state.mode == mode,
                                onClick = { viewModel.updateScrapeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("scrape_mode_${mode.name.lowercase()}"),
                            )
                        }
                    }

                    Spacer(Modifier.height(ObscuraSpacing.md))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Worker Concurrency", style = MaterialTheme.typography.labelLarge)
                        StatusPill(
                            text = "${state.concurrency} workers",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Slider(
                        value = state.concurrency.toFloat(),
                        onValueChange = { viewModel.updateScrapeConcurrency(it.roundToInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.testTag("scrape_concurrency_slider"),
                    )

                    Spacer(Modifier.height(ObscuraSpacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        if (state.isRunning) {
                            TactileButton(
                                onClick = viewModel::stopBatchScrape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scrape_stop_button"),
                            ) {
                                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(ObscuraSpacing.xs))
                                Text("Stop (${state.completedCount}/${state.totalCount})")
                            }
                        } else {
                            TactileButton(
                                onClick = viewModel::startBatchScrape,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scrape_start_button"),
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(ObscuraSpacing.xs))
                                Text("Run Batch Scrape")
                            }
                        }

                        if (state.results.isNotEmpty() && !state.isRunning) {
                            TactileOutlinedButton(
                                onClick = {
                                    viewModel.shareText("Obscura Scrape JSON", viewModel.formatScrapeExport(asJson = true))
                                },
                                modifier = Modifier.testTag("scrape_export_json_button"),
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(ObscuraSpacing.xs))
                                Text("JSON")
                            }
                            TactileOutlinedButton(
                                onClick = {
                                    viewModel.shareText("Obscura Scrape TSV", viewModel.formatScrapeExport(asJson = false))
                                },
                                modifier = Modifier.testTag("scrape_export_tsv_button"),
                            ) {
                                Text("TSV")
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.isRunning && state.totalCount > 0) {
                        Column {
                            Spacer(Modifier.height(ObscuraSpacing.md))
                            LinearProgressIndicator(
                                progress = {
                                    (state.completedCount.toFloat() / state.totalCount.coerceAtLeast(1)).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (state.results.isEmpty()) {
                item {
                    ObscuraEmptyState(
                        icon = Icons.Outlined.Bolt,
                        title = "No Scrape Jobs Yet",
                        description = "Enter one URL per line above and tap Run Batch Scrape to extract pages in parallel.",
                    )
                }
            } else {
                items(state.results, key = { it.url }) { job ->
                    ScrapeResultCard(
                        job = job,
                        onShare = { viewModel.shareText(job.title.ifEmpty { job.url }, job.output) },
                        onCopied = { viewModel.showStatusBanner("Scrape output copied") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrapeResultCard(
    job: ScrapeJobResult,
    onShare: () -> Unit,
    onCopied: () -> Unit,
) {
    var expanded by remember(job.url) { mutableStateOf(false) }
    val extColors = LocalObscuraColors.current

    val (pillBg, pillFg, pillText) = when (job.status) {
        ScrapeStatus.QUEUED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "QUEUED",
        )
        ScrapeStatus.RUNNING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "RUNNING",
        )
        ScrapeStatus.SUCCESS -> Triple(
            extColors.successContainer,
            extColors.onSuccessContainer,
            "HTTP ${job.httpStatus ?: 200} · ${job.elapsedMs}ms",
        )
        ScrapeStatus.BLOCKED -> Triple(
            extColors.warningContainer,
            extColors.onWarningContainer,
            "POLICY BLOCKED",
        )
        ScrapeStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "FAILED",
        )
    }

    ObscuraSectionCard(
        title = job.title.ifEmpty { job.url },
        subtitle = job.url,
        trailing = {
            StatusPill(
                text = pillText,
                containerColor = pillBg,
                contentColor = pillFg,
                icon = when (job.status) {
                    ScrapeStatus.SUCCESS -> Icons.Outlined.CheckCircle
                    ScrapeStatus.BLOCKED -> Icons.Outlined.Shield
                    ScrapeStatus.FAILED -> Icons.Outlined.ErrorOutline
                    else -> null
                },
            )
        },
    ) {
        if (job.error != null) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = job.error,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(ObscuraSpacing.md),
                )
            }
        } else if (job.output.isNotEmpty()) {
            val preview = if (job.output.length <= 280) job.output else job.output.take(280) + "…"
            AnimatedContent(targetState = expanded, label = "scrape_output_expand") { isExpanded ->
                if (isExpanded) {
                    MonospaceOutputBox(
                        title = "Extracted Output (${job.output.length} chars)",
                        text = job.output,
                        onShare = onShare,
                        onCopied = onCopied,
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = preview,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(ObscuraSpacing.md),
                        )
                    }
                }
            }
            Spacer(Modifier.height(ObscuraSpacing.sm))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TactileOutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Inspect Full Output")
                }
            }
        }
    }
}
