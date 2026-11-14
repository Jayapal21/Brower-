package com.example.ui.screens

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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ConsoleLogEntry
import com.example.data.NetworkLogEntry
import com.example.data.NetworkOutcome
import com.example.data.ServerBridgeState
import com.example.ui.ObscuraViewModel
import com.example.ui.components.ObscuraEmptyState
import com.example.ui.components.ObscuraSectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily

private enum class NetworkFilter(val label: String) {
    ALL("All"),
    BLOCKED("Blocked"),
    ALLOWED("Allowed"),
    CONSOLE("Console"),
}

@Composable
fun NetworkScreen(
    viewModel: ObscuraViewModel,
    networkLogs: List<NetworkLogEntry>,
    consoleLogs: List<ConsoleLogEntry>,
    bridgeState: ServerBridgeState,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(NetworkFilter.ALL) }

    val filteredNetwork = remember(networkLogs, filter) {
        when (filter) {
            NetworkFilter.ALL -> networkLogs
            NetworkFilter.BLOCKED -> networkLogs.filter {
                it.outcome != NetworkOutcome.ALLOWED
            }
            NetworkFilter.ALLOWED -> networkLogs.filter {
                it.outcome == NetworkOutcome.ALLOWED
            }
            NetworkFilter.CONSOLE -> emptyList()
        }
    }

    val blockedCount = remember(networkLogs) {
        networkLogs.count { it.outcome != NetworkOutcome.ALLOWED }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Local MCP / CDP Bridge Server (obscura serve / obscura mcp --http)
        item {
            ObscuraSectionCard(
                title = "Local MCP & CDP Bridge",
                subtitle = "Loopback JSON-RPC 2.0 (/mcp) & CDP discovery (/json/version) on 127.0.0.1",
                trailing = {
                    StatusPill(
                        text = if (bridgeState.running) "ACTIVE :${bridgeState.port}" else "STOPPED",
                        containerColor = if (bridgeState.running) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (bridgeState.running) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        icon = Icons.Outlined.Hub,
                    )
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (bridgeState.running) {
                                "http://127.0.0.1:${bridgeState.port}/mcp"
                            } else {
                                "Exposes browser_navigate, browser_snapshot, browser_evaluate"
                            },
                            fontFamily = if (bridgeState.running) JetBrainsMonoFamily else null,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (bridgeState.lastCall != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Requests: ${bridgeState.requestCount} · Last: ${bridgeState.lastCall}",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (bridgeState.error != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = bridgeState.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = viewModel::toggleBridgeServer,
                        colors = if (bridgeState.running) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("bridge_toggle_button"),
                    ) {
                        Icon(
                            imageVector = if (bridgeState.running) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = stringResource(R.string.cd_toggle_server),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (bridgeState.running) "Stop" else "Start")
                    }
                }
            }
        }

        // Network & Console Filter Bar
        item {
            ObscuraSectionCard(
                title = "Traffic & Blocklist Telemetry",
                subtitle = "${networkLogs.size} total · $blockedCount blocked by stealth/SSRF · ${consoleLogs.size} console",
                trailing = {
                    IconButton(
                        onClick = {
                            if (filter == NetworkFilter.CONSOLE) {
                                viewModel.clearConsoleLogs()
                            } else {
                                viewModel.clearNetworkLogs()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("network_clear_button"),
                    ) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = stringResource(R.string.cd_clear_logs),
                        )
                    }
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NetworkFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag("network_filter_${item.name.lowercase()}"),
                        )
                    }
                }
            }
        }

        if (filter == NetworkFilter.CONSOLE) {
            if (consoleLogs.isEmpty()) {
                item {
                    ObscuraEmptyState(
                        icon = Icons.Outlined.Lan,
                        title = "No Console Messages",
                        description = "JavaScript console.log, warn, and error messages from loaded pages appear here.",
                    )
                }
            } else {
                items(consoleLogs, key = { it.id }) { entry ->
                    ConsoleLogRow(entry)
                }
            }
        } else {
            if (filteredNetwork.isEmpty()) {
                item {
                    ObscuraEmptyState(
                        icon = Icons.Outlined.Shield,
                        title = "No Intercepted Requests",
                        description = "Navigate in the Browser tab or run a Batch Scrape to inspect live HTTP traffic and tracker blocks.",
                    )
                }
            } else {
                items(filteredNetwork, key = { it.id }) { entry ->
                    NetworkLogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun NetworkLogRow(entry: NetworkLogEntry) {
    val (bg, fg, label) = when (entry.outcome) {
        NetworkOutcome.ALLOWED -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            entry.statusCode?.toString() ?: "ALLOWED",
        )
        NetworkOutcome.BLOCKED_TRACKER -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "TRACKER BLOCKED",
        )
        NetworkOutcome.BLOCKED_SSRF -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "SSRF BLOCKED",
        )
        NetworkOutcome.BLOCKED_ROBOTS -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "ROBOTS BLOCKED",
        )
        NetworkOutcome.ERROR -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "ERROR",
        )
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${entry.timestamp} · ${entry.method} · ${entry.resourceType}",
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(text = label, containerColor = bg, contentColor = fg)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.url,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.detail,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                )
            }
        }
    }
}

@Composable
private fun ConsoleLogRow(entry: ConsoleLogEntry) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${entry.timestamp} · ${entry.level}",
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = entry.source,
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.message,
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
