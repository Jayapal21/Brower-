package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DiagnosticCheckResult
import com.example.data.ObscuraSettings
import com.example.data.SearchEngineOption
import com.example.engine.BrowserProfiles
import com.example.motion.MotionModePreference
import com.example.motion.MotionTelemetry
import com.example.motion.PerformanceProfile
import com.example.ui.ObscuraViewModel
import com.example.ui.components.ObscuraSectionCard
import com.example.ui.components.ObscuraSubScreenScaffold
import com.example.ui.components.StatusPill
import com.example.ui.components.TactileButton
import com.example.ui.components.TactileFilterChip
import com.example.ui.components.TactileOutlinedButton
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalMotionPolicy
import com.example.ui.theme.LocalObscuraColors
import com.example.ui.theme.ObscuraMotionTokens
import com.example.ui.theme.ObscuraSizing
import com.example.ui.theme.ObscuraSpacing
import com.example.ui.theme.pressScale
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StealthScreen(
    viewModel: ObscuraViewModel,
    settings: ObscuraSettings,
    diagnosticResult: DiagnosticCheckResult?,
    motionTelemetry: MotionTelemetry = MotionTelemetry(),
    onBack: () -> Unit,
) {
    var diagnosticInput by remember { mutableStateOf("169.254.169.254") }
    var proxyDraft by remember(settings.proxyUrl) { mutableStateOf(settings.proxyUrl) }
    var uaDraft by remember(settings.customUserAgent) { mutableStateOf(settings.customUserAgent) }
    val extColors = LocalObscuraColors.current

    ObscuraSubScreenScaffold(
        title = "Settings & Stealth",
        subtitle = "${viewModel.trackerBlocklist.domainCount()} tracker rules · ${viewModel.robotsCache.size()} robots cached",
        onBack = onBack,
    ) { mod ->
        LazyColumn(
            modifier = mod.widthIn(max = ObscuraSizing.maxContentWidthExpanded),
            contentPadding = PaddingValues(ObscuraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.lg),
        ) {
            // Appearance & Search Engine
            item {
                ObscuraSectionCard(
                    title = "Appearance & Search",
                    subtitle = "Day & Night depth theme and omnibox search provider",
                    leadingIcon = Icons.Outlined.DarkMode,
                ) {
                    SettingsToggleRow(
                        title = "Night Theme",
                        subtitle = if (settings.darkTheme) {
                            "Active: Deep obsidian glass surfaces with emerald highlights"
                        } else {
                            "Active: Soft cloud porcelain surfaces with cobalt accents"
                        },
                        checked = settings.darkTheme,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(darkTheme = checked) }
                        },
                        testTag = "toggle_dark_theme",
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "Search Engine",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(ObscuraSpacing.sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        SearchEngineOption.entries.forEach { engine ->
                            TactileFilterChip(
                                selected = settings.searchEngine == engine,
                                onClick = {
                                    viewModel.updateSettings { it.copy(searchEngine = engine) }
                                },
                                label = { Text(engine.label) },
                                modifier = Modifier.testTag("search_engine_${engine.name.lowercase()}"),
                            )
                        }
                    }
                }
            }

            // Adaptive Motion Engine Telemetry & Controls
            item {
                ObscuraSectionCard(
                    title = "Adaptive Motion Engine",
                    subtitle = "Display refresh profiling, live FrameMetrics & thermal adaptation",
                    leadingIcon = Icons.Outlined.Speed,
                    trailing = {
                        val (bg, fg) = when (motionTelemetry.activeProfile) {
                            PerformanceProfile.HIGH -> extColors.successContainer to extColors.onSuccessContainer
                            PerformanceProfile.BALANCED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                            PerformanceProfile.LOW -> extColors.warningContainer to extColors.onWarningContainer
                        }
                        StatusPill(
                            text = "${motionTelemetry.activeProfile.name} · ${motionTelemetry.activeRefreshRateHz.toInt()} Hz",
                            containerColor = bg,
                            contentColor = fg,
                            icon = Icons.Outlined.Speed,
                        )
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        MotionModePreference.entries.forEach { mode ->
                            TactileFilterChip(
                                selected = settings.motionMode == mode,
                                onClick = {
                                    viewModel.updateSettings { it.copy(motionMode = mode) }
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("motion_mode_${mode.name.lowercase()}"),
                            )
                        }
                    }

                    Spacer(Modifier.height(ObscuraSpacing.md))

                    val activePolicy = LocalMotionPolicy.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Spring Stiffness",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "${settings.springStiffness.roundToInt()} (effective ${activePolicy.toolbarSpring.stiffness.roundToInt()})",
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.springStiffness,
                        onValueChange = { next ->
                            viewModel.updateSettings { it.copy(springStiffness = next) }
                        },
                        valueRange = 200f..3500f,
                        modifier = Modifier.testTag("spring_stiffness_slider"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Spring Damping Ratio",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.2f (effective %.2f)",
                                settings.springDamping,
                                activePolicy.tabCardSpring.dampingRatio,
                            ),
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = settings.springDamping,
                        onValueChange = { next ->
                            viewModel.updateSettings { it.copy(springDamping = next) }
                        },
                        valueRange = 0.45f..1.0f,
                        modifier = Modifier.testTag("spring_damping_slider"),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        val presets = listOf(
                            Triple("Soft", 450f, 0.88f),
                            Triple("Balanced", 1100f, 0.85f),
                            Triple("Bouncy", 1300f, 0.65f),
                            Triple("Snappy", 2600f, 0.94f),
                        )
                        presets.forEach { (label, stiff, damp) ->
                            val selected = kotlin.math.abs(settings.springStiffness - stiff) < 80f &&
                                kotlin.math.abs(settings.springDamping - damp) < 0.04f
                            TactileFilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.updateSettings {
                                        it.copy(springStiffness = stiff, springDamping = damp)
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("spring_preset_${label.lowercase()}"),
                            )
                        }
                    }

                    Spacer(Modifier.height(ObscuraSpacing.md))

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(ObscuraSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Supported display rates: ${motionTelemetry.supportedRefreshRatesHz.joinToString(" / ") { "${it}Hz" }} (Tier: ${motionTelemetry.refreshTier.label})",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Reason: ${motionTelemetry.adaptationReason}",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Thermal: ${motionTelemetry.thermalLevel.label} · Battery Saver: ${if (motionTelemetry.batterySaverActive) "ON" else "OFF"}",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val health = motionTelemetry.frameHealth
                            val frameLine = if (health.sampleCount > 0) {
                                String.format(
                                    Locale.US,
                                    "Observed FrameMetrics (%d frames): %.1f%% jank · P95 %.1fms (budget %.1fms)",
                                    health.sampleCount,
                                    health.jankPercent,
                                    health.p95FrameMs,
                                    health.frameBudgetMs,
                                )
                            } else {
                                String.format(
                                    Locale.US,
                                    "FrameMetrics: awaiting rendered frames (target budget %.1fms)",
                                    health.frameBudgetMs,
                                )
                            }
                            Text(
                                text = frameLine,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Core Privacy & Security Policies
            item {
                ObscuraSectionCard(
                    title = "Privacy & Security Engine",
                    subtitle = "Built-in protection powered by Obscura core",
                    leadingIcon = Icons.Outlined.VerifiedUser,
                    trailing = {
                        StatusPill(
                            text = "${viewModel.trackerBlocklist.domainCount()} rules",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = Icons.Outlined.VerifiedUser,
                        )
                    },
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = ObscuraSpacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Desktop Site User-Agent",
                        subtitle = "Use full desktop Chrome fingerprint profile in browser tabs",
                        checked = settings.desktopMode,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(desktopMode = checked) }
                        },
                        testTag = "toggle_desktop_mode",
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = ObscuraSpacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Allow Private Network (--allow-private-network)",
                        subtitle = "Permit loopback, RFC1918 & link-local targets (disables SSRF guard)",
                        checked = settings.allowPrivateNetwork,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(allowPrivateNetwork = checked) }
                        },
                        testTag = "toggle_allow_private",
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = ObscuraSpacing.sm), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsToggleRow(
                        title = "Obey robots.txt (--obey-robots)",
                        subtitle = "Fetch and enforce RFC 9309 crawl rules before navigating",
                        checked = settings.obeyRobots,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings { it.copy(obeyRobots = checked) }
                        },
                        testTag = "toggle_obey_robots",
                    )
                }
            }

            // Fingerprint Profiles
            item {
                ObscuraSectionCard(
                    title = "Fingerprint Profiles",
                    subtitle = "Consistent Chrome identity used for User-Agent & navigator.userAgentData",
                    leadingIcon = Icons.Outlined.Fingerprint,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.xxs)) {
                        BrowserProfiles.PROFILES.forEach { profile ->
                            val selected = settings.selectedProfileIndex == profile.id
                            val rowInteraction = remember { MutableInteractionSource() }
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressScale(rowInteraction, ObscuraMotionTokens.PRESSED_SCALE_CARD)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(
                                        interactionSource = rowInteraction,
                                        indication = ripple(),
                                        role = Role.RadioButton,
                                    ) {
                                        viewModel.updateSettings { it.copy(selectedProfileIndex = profile.id) }
                                    }
                                    .testTag("profile_option_${profile.id}"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = ObscuraSpacing.xs, horizontal = ObscuraSpacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            viewModel.updateSettings { it.copy(selectedProfileIndex = profile.id) }
                                        },
                                    )
                                    Spacer(Modifier.width(ObscuraSpacing.xs))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = profile.label, style = MaterialTheme.typography.bodyMedium)
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
            }

            // Proxy, Transport & Browsing Data
            item {
                ObscuraSectionCard(
                    title = "Transport & Browsing Data",
                    subtitle = "Proxy settings for batch scraping and local data controls",
                    leadingIcon = Icons.Outlined.Public,
                ) {
                    OutlinedTextField(
                        value = proxyDraft,
                        onValueChange = { proxyDraft = it },
                        label = { Text("Proxy URL (e.g. socks5://127.0.0.1:1080)") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
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
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_ua_input"),
                    )
                    Spacer(Modifier.height(ObscuraSpacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TactileOutlinedButton(
                            onClick = viewModel::clearAllBrowsingData,
                            modifier = Modifier.testTag("clear_browsing_data_button"),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(ObscuraSpacing.xs))
                            Text("Clear Data")
                        }
                        TactileButton(
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        proxyUrl = proxyDraft.trim(),
                                        customUserAgent = uaDraft.trim(),
                                    )
                                }
                            },
                            modifier = Modifier.testTag("save_transport_button"),
                        ) {
                            Text("Apply Overrides")
                        }
                    }
                }
            }

            // Live SSRF & Blocklist Diagnostic Tester
            item {
                ObscuraSectionCard(
                    title = "SSRF & Blocklist Tester",
                    subtitle = "Verify any IP, host, or URL against Obscura policy rules",
                    leadingIcon = Icons.Outlined.BugReport,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    ) {
                        OutlinedTextField(
                            value = diagnosticInput,
                            onValueChange = { diagnosticInput = it },
                            label = { Text("Host, IP, or URL") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("diagnostic_input"),
                        )
                        TactileButton(
                            onClick = { viewModel.runDiagnosticCheck(diagnosticInput) },
                            modifier = Modifier.testTag("diagnostic_run_button"),
                        ) {
                            Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(ObscuraSpacing.xxs))
                            Text("Check")
                        }
                    }

                    if (diagnosticResult != null) {
                        Spacer(Modifier.height(ObscuraSpacing.md))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(ObscuraSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.xs),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("SSRF Policy", style = MaterialTheme.typography.labelLarge)
                                    StatusPill(
                                        text = if (diagnosticResult.ssrfForbidden) "BLOCKED" else "ALLOWED",
                                        containerColor = if (diagnosticResult.ssrfForbidden) {
                                            MaterialTheme.colorScheme.errorContainer
                                        } else {
                                            extColors.successContainer
                                        },
                                        contentColor = if (diagnosticResult.ssrfForbidden) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            extColors.onSuccessContainer
                                        },
                                    )
                                }
                                Text(
                                    text = diagnosticResult.ssrfReason,
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = ObscuraSpacing.xxs), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Tracker Blocklist", style = MaterialTheme.typography.labelLarge)
                                    StatusPill(
                                        text = if (diagnosticResult.trackerBlocked) "BLOCKED" else "CLEAN",
                                        containerColor = if (diagnosticResult.trackerBlocked) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            extColors.successContainer
                                        },
                                        contentColor = if (diagnosticResult.trackerBlocked) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            extColors.onSuccessContainer
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
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    val rowInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(rowInteraction, ObscuraMotionTokens.PRESSED_SCALE_CARD)
            .clip(MaterialTheme.shapes.small)
            .clickable(
                interactionSource = rowInteraction,
                indication = ripple(),
                role = Role.Switch,
            ) { onCheckedChange(!checked) }
            .padding(vertical = ObscuraSpacing.xs, horizontal = ObscuraSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = ObscuraSpacing.md)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}
