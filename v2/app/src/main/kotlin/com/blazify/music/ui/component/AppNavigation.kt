/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.component

import androidx.compose.ui.Alignment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.blazify.music.constants.GlassThemeKey
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
import androidx.compose.ui.graphics.graphicsLayer
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
    val selectedScale by animateFloatAsState(
        targetValue = if (selected) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "strix-nav-selection-scale",
    )

    when (style) {
        NavBarStyle.PILL -> {
            val shape = RoundedCornerShape(16.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = selectedScale
                        scaleY = selectedScale
                    }
                    .clip(shape)
                    .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = if (selected) accent else idle,
                    modifier = Modifier.size(22.dp),
                )
                if (!slimNav) {
                    Text(
                        text = label,
                        color = if (selected) accent else idle,
                        fontSize = 11.sp,
                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        NavBarStyle.GRADIENT -> {
            val shape = RoundedCornerShape(14.dp)
            // Label sits under the icon, and the highlight wraps both.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = selectedScale
                        scaleY = selectedScale
                    }
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(accent.copy(alpha = 0.85f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)),
                                    ),
                                )
                                .border(1.dp, accent.copy(alpha = 0.55f), shape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = if (selected) onAccent else idle,
                    modifier = Modifier.size(20.dp),
                )
                if (!slimNav) {
                    Text(
                        text = label,
                        color = if (selected) onAccent else idle,
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
                    .graphicsLayer {
                        scaleX = selectedScale
                        scaleY = selectedScale
                    }
                    .clip(shape)
                    .then(
                        if (selected) {
                            Modifier
                                .background(accent.copy(alpha = 0.14f))
                                .border(1.dp, accent.copy(alpha = 0.7f), shape)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = if (selected) accent else idle,
                    modifier = Modifier.size(20.dp),
                )
                if (!slimNav) {
                    Text(
                        text = label,
                        color = if (selected) accent else idle,
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
                    tint = if (selected) accent else idle,
                    modifier = Modifier.size(22.dp),
                )
                if (!slimNav) {
                    Text(
                        text = label,
                        color = if (selected) accent else idle,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(if (selected) 18.dp else 0.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) accent else Color.Transparent),
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
    val containerColor = Color.Transparent
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val glassShape = RoundedCornerShape(30.dp)
    // iOS-style glass is opt-in. When disabled, keep a crisp Material surface with
    // no translucent overlay, which also prevents the selected tab from looking blurred.
    val glassTheme by com.blazify.music.utils.rememberPreference(GlassThemeKey, true)
    val glassSurface = if (glassTheme) {
        if (pureBlack) Color(0xCC101012) else MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    } else {
        if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    }
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val navBarStyle by rememberEnumPreference(NavBarStyleKey, NavBarStyle.PILL)

    NavigationBar(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .shadow(if (glassTheme) 16.dp else 6.dp, glassShape)
            .clip(glassShape)
            .background(
                if (glassTheme) {
                    Brush.verticalGradient(
                        listOf(glassSurface.copy(alpha = 0.96f), glassSurface.copy(alpha = 0.76f)),
                    )
                } else {
                    Brush.verticalGradient(listOf(glassSurface, glassSurface))
                },
            )
            .border(
                1.dp,
                if (glassTheme) Color.White.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                glassShape,
            ),
        containerColor = containerColor,
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
                label = null
            )
        }
    }
}
