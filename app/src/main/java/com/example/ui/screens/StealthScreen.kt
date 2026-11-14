package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DiagnosticCheckResult
import com.example.data.ObscuraSettings
import com.example.engine.BrowserProfiles
import com.example.ui.ObscuraViewModel
import com.example.ui.components.ObscuraSectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun StealthScreen(
    viewModel: ObscuraViewModel,
    settings: ObscuraSettings,
    diagnosticResult: DiagnosticCheckResult?,
    modifier: Modifier = Modifier,
) {
    var diagnosticInput by remember { mutableStateOf("169.254.169.254") }
    var proxyDraft by remember(settings.proxyUrl) { mutableStateOf(settings.proxyUrl) }
    var uaDraft by remember(settings.customUserAgent) { mutableStateOf(settings.customUserAgent) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Banner Card
        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_obscura_hero),
                            contentDescription = stringResource(R.string.cd_hero_banner),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.large),
                        )
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Obscura Stealth Engine",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            StatusPill(
                                text = "${viewModel.trackerBlocklist.domainCount()} trackers",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                icon = Icons.Outlined.VerifiedUser,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Anti-fingerprinting profiles, 3,520+ domain tracker blocklist, RFC 9309 robots.txt cache (${viewModel.robotsCache.size()} origins), and SSRF IP/DNS guard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Core Policy Toggles
        item {
            ObscuraSectionCard(
                title = "Engine & Privacy Controls",
                subtitle = "Flags mirrored from Obscura CLI (--stealth, --allow-private-network, --obey-robots)",
            ) {
                SettingsToggleRow(
                    title = "Stealth Mode (--stealth)",
                    subtitle = "Block 3,520+ trackers and mask navigator.webdriver & userAgentData",
                    checked = settings.stealthEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(stealthEnabled = checked) }
                    },
                    testTag = "toggle_stealth_mode",
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsToggleRow(
                    title = "Allow Private Network (--allow-private-network)",
                    subtitle = "Permit loopback, RFC1918 & link-local targets (disables SSRF guard)",
                    checked = settings.allowPrivateNetwork,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(allowPrivateNetwork = checked) }
                    },
                    testTag = "toggle_allow_private",
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsToggleRow(
                    title = "Obey robots.txt (--obey-robots)",
                    subtitle = "Fetch and enforce RFC 9309 crawl rules before navigating",
                    checked = settings.obeyRobots,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(obeyRobots = checked) }
                    },
                    testTag = "toggle_obey_robots",
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsToggleRow(
                    title = "Obsidian Dark Theme",
                    subtitle = "Switch between Obsidian dark mode and crisp light theme",
                    checked = settings.darkTheme,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(darkTheme = checked) }
                    },
                    testTag = "toggle_dark_theme",
                )
            }
        }

        // Browser Fingerprint Profiles
        item {
            ObscuraSectionCard(
                title = "Fingerprint Profiles",
                subtitle = "Stable desktop Chrome identity injected into User-Agent & navigator.userAgentData",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BrowserProfiles.PROFILES.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable {
                                    viewModel.updateSettings { it.copy(selectedProfileIndex = profile.id) }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .testTag("profile_option_${profile.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.selectedProfileIndex == profile.id,
                                onClick = {
                                    viewModel.updateSettings { it.copy(selectedProfileIndex = profile.id) }
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${profile.platform} · Chrome/${profile.chromeMajor}",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Network Transport & Storage Overrides
        item {
            ObscuraSectionCard(
                title = "Proxy & Transport Overrides",
                subtitle = "Used by raw fetch and parallel batch scraper (--proxy, --user-agent)",
            ) {
                OutlinedTextField(
                    value = proxyDraft,
                    onValueChange = { proxyDraft = it },
                    label = { Text("Proxy URL (e.g. socks5://127.0.0.1:1080)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_url_input"),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = uaDraft,
                    onValueChange = { uaDraft = it },
                    label = { Text("Custom User-Agent Override (optional)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_ua_input"),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = viewModel::clearCookies,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("clear_cookies_button"),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Cookies")
                    }
                    Button(
                        onClick = {
                            viewModel.updateSettings {
                                it.copy(
                                    proxyUrl = proxyDraft.trim(),
                                    customUserAgent = uaDraft.trim(),
                                )
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("save_transport_button"),
                    ) {
                        Text("Apply Overrides")
                    }
                }
            }
        }

        // Live SSRF & Blocklist Diagnostic Tester
        item {
            ObscuraSectionCard(
                title = "SSRF & Blocklist Diagnostic Tester",
                subtitle = "Test any IP, domain, or URL against Obscura's deny-set and 3,520+ tracker rules",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = diagnosticInput,
                        onValueChange = { diagnosticInput = it },
                        label = { Text("Host, IP, or URL") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("diagnostic_input"),
                    )
                    Button(
                        onClick = { viewModel.runDiagnosticCheck(diagnosticInput) },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("diagnostic_run_button"),
                    ) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Check")
                    }
                }

                if (diagnosticResult != null) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("SSRF Policy", style = MaterialTheme.typography.labelLarge)
                                StatusPill(
                                    text = if (diagnosticResult.ssrfForbidden) "BLOCKED" else "ALLOWED",
                                    containerColor = if (diagnosticResult.ssrfForbidden) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    contentColor = if (diagnosticResult.ssrfForbidden) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
                                )
                            }
                            Text(
                                text = diagnosticResult.ssrfReason,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Tracker Blocklist", style = MaterialTheme.typography.labelLarge)
                                StatusPill(
                                    text = if (diagnosticResult.trackerBlocked) "BLOCKED" else "CLEAN",
                                    containerColor = if (diagnosticResult.trackerBlocked) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (diagnosticResult.trackerBlocked) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Text(
                                text = "Matched rule: ${diagnosticResult.trackerMatch}",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
