package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import kotlin.math.roundToInt

@Composable
fun ScrapeScreen(
    viewModel: ObscuraViewModel,
    state: ScrapeUiState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ObscuraSectionCard(
                title = "Parallel Batch Scraper (obscura scrape)",
                subtitle = "Fetch multiple URLs concurrently with SSRF, Tracker Blocklist & robots.txt gates",
            ) {
                OutlinedTextField(
                    value = state.urlsText,
                    onValueChange = viewModel::updateScrapeUrls,
                    label = { Text("Target URLs (one per line)") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scrape_urls_input"),
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Extraction Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScrapeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.mode == mode,
                            onClick = { viewModel.updateScrapeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("scrape_mode_${mode.name.lowercase()}"),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Worker Concurrency",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    StatusPill(
                        text = "${state.concurrency} workers",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Slider(
                    value = state.concurrency.toFloat(),
                    onValueChange = { viewModel.updateScrapeConcurrency(it.roundToInt()) },
                    valueRange = 1f..10f,
                    steps = 8,
                    modifier = Modifier.testTag("scrape_concurrency_slider"),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.isRunning) {
                        Button(
                            onClick = viewModel::stopBatchScrape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scrape_stop_button"),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop (${state.completedCount}/${state.totalCount})")
                        }
                    } else {
                        Button(
                            onClick = viewModel::startBatchScrape,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("scrape_start_button"),
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Batch Scrape")
                        }
                    }

                    if (state.results.isNotEmpty() && !state.isRunning) {
                        OutlinedButton(
                            onClick = {
                                viewModel.shareText("Obscura Scrape JSON", viewModel.formatScrapeExport(asJson = true))
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("scrape_export_json_button"),
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.shareText("Obscura Scrape TSV", viewModel.formatScrapeExport(asJson = false))
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("scrape_export_tsv_button"),
                        ) {
                            Text("TSV")
                        }
                    }
                }

                AnimatedVisibility(visible = state.isRunning && state.totalCount > 0) {
                    Column {
                        Spacer(Modifier.height(12.dp))
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
                )
            }
        }
    }
}

@Composable
private fun ScrapeResultCard(
    job: ScrapeJobResult,
    onShare: () -> Unit,
) {
    var expanded by remember(job.url) { mutableStateOf(false) }

    val (pillBg, pillFg, pillText) = when (job.status) {
        ScrapeStatus.QUEUED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "QUEUED",
        )
        ScrapeStatus.RUNNING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "RUNNING",
        )
        ScrapeStatus.SUCCESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "HTTP ${job.httpStatus ?: 200} · ${job.elapsedMs}ms",
        )
        ScrapeStatus.BLOCKED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
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
            Text(
                text = job.error,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (job.output.isNotEmpty()) {
            val preview = if (expanded || job.output.length <= 280) {
                job.output
            } else {
                job.output.take(280) + "…"
            }
            if (expanded) {
                MonospaceOutputBox(
                    title = "Extracted Output (${job.output.length} chars)",
                    text = job.output,
                    onShare = onShare,
                )
            } else {
                Text(
                    text = preview,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(if (expanded) "Collapse" else "Inspect Full Output")
                }
            }
        }
    }
}
