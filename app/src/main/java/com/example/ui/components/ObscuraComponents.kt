package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalMotionPolicy
import com.example.ui.theme.LocalObscuraColors
import com.example.ui.theme.ObscuraElevation
import com.example.ui.theme.ObscuraMotionTokens
import com.example.ui.theme.ObscuraRadii
import com.example.ui.theme.ObscuraSizing
import com.example.ui.theme.ObscuraSpacing
import com.example.ui.theme.pressScale

/**
 * Policy-aware soft M3 shadow with Day/Night tinted ambient and spot colors.
 * Scales smoothly with [LocalMotionPolicy.current.cardElevationDp] and drops to 0.dp on LOW/Reduced Motion.
 */
fun Modifier.obscuraDepthShadow(
    baseElevation: Dp = ObscuraElevation.card,
    shape: Shape,
): Modifier = composed {
    val policy = LocalMotionPolicy.current
    val ext = LocalObscuraColors.current
    val multiplier = (policy.cardElevationDp / 2.5f).coerceAtLeast(0f)
    val effective = (baseElevation.value * multiplier).dp
    if (effective <= 0.dp) {
        this
    } else {
        val ambientAlpha = if (ext.isDark) 0.55f else 0.14f
        val spotAlpha = if (ext.isDark) 0.72f else 0.18f
        this.shadow(
            elevation = effective,
            shape = shape,
            clip = false,
            ambientColor = ext.shadowTint.copy(alpha = ambientAlpha),
            spotColor = ext.shadowTint.copy(alpha = spotAlpha),
        )
    }
}

@Composable
fun obscuraRimBorder(
    active: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    width: Dp = if (active) ObscuraSizing.borderActive else ObscuraSizing.borderHairline,
): BorderStroke {
    if (active) {
        return BorderStroke(width, activeColor)
    }
    val ext = LocalObscuraColors.current
    return BorderStroke(
        width = width,
        brush = Brush.verticalGradient(
            colors = listOf(
                ext.rimHighlightTop,
                MaterialTheme.colorScheme.outlineVariant,
                ext.rimHighlightBottom,
            ),
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TactileCardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    baseElevation: Dp = ObscuraElevation.card,
    active: Boolean = false,
    activeBorderColor: Color = MaterialTheme.colorScheme.primary,
    pressedScale: Float = ObscuraMotionTokens.PRESSED_SCALE_CARD,
    accessibilityLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = obscuraRimBorder(active = active, activeColor = activeBorderColor),
        modifier = modifier
            .obscuraDepthShadow(baseElevation = baseElevation, shape = shape)
            .pressScale(interaction, pressedScale = pressedScale)
            .clip(shape)
            .let { mod ->
                if (accessibilityLabel != null) {
                    mod.semantics { contentDescription = accessibilityLabel }
                } else {
                    mod
                }
            }
            .let { mod ->
                if (onLongClick != null) {
                    mod.combinedClickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    mod.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        role = Role.Button,
                        onClick = onClick,
                    )
                }
            },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObscuraSubScreenScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    pinnedHeader: (@Composable () -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val policy = LocalMotionPolicy.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = policy.translucentChromeAlpha),
                shadowElevation = if (scrollBehavior.state.contentOffset < -4f) ObscuraElevation.subtle else ObscuraElevation.flat,
            ) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            TactileIconButton(
                                onClick = onBack,
                                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        },
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    if (pinnedHeader != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = ObscuraSpacing.lg,
                                    end = ObscuraSpacing.lg,
                                    bottom = ObscuraSpacing.md,
                                ),
                        ) {
                            pinnedHeader()
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
fun ObscuraSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ObscuraRadii.capsule)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ObscuraSizing.minTouchTarget),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ObscuraSpacing.lg, end = ObscuraSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(ObscuraSizing.iconStandard),
            )
            Spacer(Modifier.width(ObscuraSpacing.sm))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                TactileIconButton(
                    onClick = { onQueryChange("") },
                    icon = Icons.Outlined.Close,
                    contentDescription = "Clear filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DomainMonogramBadge(
    label: String? = null,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = ObscuraSizing.badgeSize,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(ObscuraRadii.sm),
        color = containerColor,
        border = BorderStroke(ObscuraSizing.borderHairline, contentColor.copy(alpha = 0.16f)),
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(ObscuraSizing.iconStandard),
                )
            } else {
                Text(
                    text = label.orEmpty().take(2).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
fun TactileIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .size(ObscuraSizing.minTouchTarget)
            .pressScale(
                interactionSource = interactionSource,
                pressedScale = ObscuraMotionTokens.PRESSED_SCALE_ICON,
                enabled = enabled,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(ObscuraSizing.iconToolbar),
        )
    }
}

@Composable
fun TactileFilledTonalIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier
            .size(ObscuraSizing.minTouchTarget)
            .pressScale(
                interactionSource = interactionSource,
                pressedScale = ObscuraMotionTokens.PRESSED_SCALE_ICON,
                enabled = enabled,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(ObscuraSizing.iconToolbar),
        )
    }
}

@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ObscuraRadii.sm),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = ObscuraSizing.buttonHeight)
            .pressScale(
                interactionSource = interactionSource,
                pressedScale = ObscuraMotionTokens.PRESSED_SCALE_BUTTON,
                enabled = enabled,
            ),
        content = content,
    )
}

@Composable
fun TactileOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ObscuraRadii.sm),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = BorderStroke(ObscuraSizing.borderThin, MaterialTheme.colorScheme.outline),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = ObscuraSizing.buttonHeight)
            .pressScale(
                interactionSource = interactionSource,
                pressedScale = ObscuraMotionTokens.PRESSED_SCALE_BUTTON,
                enabled = enabled,
            ),
        content = content,
    )
}

@Composable
fun TactileFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ObscuraRadii.sm),
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = ObscuraSizing.buttonHeight)
            .pressScale(
                interactionSource = interactionSource,
                pressedScale = ObscuraMotionTokens.PRESSED_SCALE_BUTTON,
                enabled = enabled,
            ),
        content = content,
    )
}

@Composable
fun TactileTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(ObscuraRadii.sm),
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = ObscuraSizing.minTouchTarget)
            .pressScale(
                interactionSource = interactionSource,
                pressedScale = ObscuraMotionTokens.PRESSED_SCALE_BUTTON,
                enabled = enabled,
            ),
        content = content,
    )
}

@Composable
fun TactileFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(ObscuraRadii.sm),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        ),
        interactionSource = interactionSource,
        modifier = modifier.pressScale(
            interactionSource = interactionSource,
            pressedScale = ObscuraMotionTokens.PRESSED_SCALE_BUTTON,
            enabled = enabled,
        ),
    )
}

@Composable
fun TactileAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        label = label,
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(ObscuraRadii.sm),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = enabled,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        interactionSource = interactionSource,
        modifier = modifier.pressScale(
            interactionSource = interactionSource,
            pressedScale = ObscuraMotionTokens.PRESSED_SCALE_BUTTON,
            enabled = enabled,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObscuraSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .obscuraDepthShadow(baseElevation = ObscuraElevation.card, shape = shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = obscuraRimBorder(),
    ) {
        Column(modifier = Modifier.padding(ObscuraSpacing.lg)) {
            if (title != null || trailing != null) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(ObscuraSpacing.sm),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.md),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        if (leadingIcon != null) {
                            DomainMonogramBadge(
                                icon = leadingIcon,
                                size = 36.dp,
                            )
                        }
                        Column {
                            if (title != null) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (subtitle != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (trailing != null) {
                        Box(modifier = Modifier.padding(start = ObscuraSpacing.xs)) {
                            trailing()
                        }
                    }
                }
                Spacer(Modifier.height(ObscuraSpacing.md))
            }
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ObscuraRadii.pill),
        color = containerColor,
        border = BorderStroke(ObscuraSizing.borderHairline, contentColor.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ObscuraSpacing.xxs),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun MonospaceOutputBox(
    title: String,
    text: String,
    onShare: (() -> Unit)? = null,
    onCopied: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(ObscuraSizing.borderHairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = ObscuraSpacing.md, end = ObscuraSpacing.xxs, top = ObscuraSpacing.xxs, bottom = ObscuraSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TactileIconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(text))
                            onCopied?.invoke()
                        },
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_output),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onShare != null) {
                        TactileIconButton(
                            onClick = onShare,
                            icon = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.cd_share_output),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ObscuraSpacing.md)
                    .horizontalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = text,
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun ObscuraEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .padding(horizontal = ObscuraSpacing.xl, vertical = ObscuraSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(ObscuraRadii.md),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(
                ObscuraSizing.borderHairline,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
            ),
            modifier = Modifier
                .size(64.dp)
                .obscuraDepthShadow(ObscuraElevation.subtle, RoundedCornerShape(ObscuraRadii.md)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ObscuraSizing.iconEmpty),
                )
            }
        }
        Spacer(Modifier.height(ObscuraSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ObscuraSpacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
