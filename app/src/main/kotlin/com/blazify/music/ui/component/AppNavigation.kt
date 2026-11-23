/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import com.blazify.music.constants.NavBarStyle
import com.blazify.music.constants.NavBarStyleKey
import com.blazify.music.utils.rememberEnumPreference
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.blazify.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int
)

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current

    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        Spacer(modifier = Modifier.weight(1f))

        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchLongClick.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // For search item, click is handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Draws one navigation item for the chosen highlight style. PILL leaves the icon
 * bare (Material draws its own pill); the others wrap icon + label in their own
 * highlight so the selected tab reads clearly.
 */
@Composable
private fun NavItemContent(
    style: NavBarStyle,
    iconRes: Int,
    label: String,
    selected: Boolean,
    slimNav: Boolean,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "strixNavigationSelection",
    )
    val animatedTint by animateColorAsState(
        targetValue = if (selected) accent else idle,
        animationSpec = tween(durationMillis = 220),
        label = "strixNavigationTint",
    )

    when (style) {
        NavBarStyle.PILL -> {
            Icon(painter = painterResource(id = iconRes), contentDescription = label)
        }

        NavBarStyle.GRADIENT -> {
            val shape = RoundedCornerShape(14.dp)
            // Label sits under the icon, and the highlight wraps both.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clip(shape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                accent.copy(alpha = 0.85f * selectedProgress),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f * selectedProgress),
                            ),
                        ),
                    )
                    .border(1.dp, accent.copy(alpha = 0.55f * selectedProgress), shape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = if (selected) onAccent else animatedTint,
                    modifier = Modifier.size(20.dp),
                )
                if (!slimNav) {
                    Text(
                        text = label,
                        color = if (selected) onAccent else animatedTint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        NavBarStyle.OUTLINED -> {
            val shape = RoundedCornerShape(12.dp)
            // Label sits under the icon, and the outlined box wraps both.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clip(shape)
                    .background(accent.copy(alpha = 0.14f * selectedProgress))
                    .border(1.dp, accent.copy(alpha = 0.7f * selectedProgress), shape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = animatedTint,
                    modifier = Modifier.size(20.dp),
                )
                if (!slimNav) {
                    Text(
                        text = label,
                        color = animatedTint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        NavBarStyle.UNDERLINE -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = animatedTint,
                    modifier = Modifier.size(22.dp),
                )
                if (!slimNav) {
                    Text(
                        text = label,
                        color = animatedTint,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(18.dp * selectedProgress)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.copy(alpha = selectedProgress)),
                )
            }
        }
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null
) {
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val navBarStyle by rememberEnumPreference(NavBarStyleKey, NavBarStyle.PILL)
    // Strix glass dock: translucent burgundy-tinted surface with a fine glass edge.
    val glassShape = RoundedCornerShape(30.dp)
    val glassTop = if (pureBlack) Color(0xFF171015).copy(alpha = 0.92f)
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
    val glassBottom = if (pureBlack) Color(0xFF080608).copy(alpha = 0.86f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)

    NavigationBar(
        modifier = modifier
            .clip(glassShape)
            .background(Brush.verticalGradient(listOf(glassTop, glassBottom)))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), glassShape),
        containerColor = Color.Transparent,
        contentColor = contentColor
    ) {
        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSearchLongClick.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            val label = stringResource(screen.titleId)
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (!isSearchItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // For search item, click is handled via InteractionSource
                },
                interactionSource = interactionSource,
                // Custom styles draw their own highlight, so hide the Material pill.
                colors = if (navBarStyle == NavBarStyle.PILL) {
                    NavigationBarItemDefaults.colors()
                } else {
                    NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                },
                icon = {
                    NavItemContent(
                        style = navBarStyle,
                        iconRes = iconRes,
                        label = label,
                        selected = isSelected,
                        slimNav = slimNav,
                    )
                },
                // Only the Material style uses the separate label slot; the others
                // draw the label inside their own highlight.
                label = if (navBarStyle == NavBarStyle.PILL && !slimNav) {
                    {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else null
            )
        }
    }
}
