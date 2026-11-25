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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import com.blazify.music.constants.NavBarStyle
import com.blazify.music.constants.GlassThemeKey
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
    val glassTheme by com.blazify.music.utils.rememberPreference(GlassThemeKey, true)
    val bitChordTheme by com.blazify.music.utils.rememberPreference(com.blazify.music.constants.BitChordThemeKey, false)
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val selectedIndex = navigationItems.indexOfFirst { screen ->
        isRouteSelected(currentRoute, screen.route, navigationItems)
    }.coerceAtLeast(0)
    val itemCount = navigationItems.size.coerceAtLeast(1)
    val pillShape = if (bitChordTheme) RoundedCornerShape(28.dp) else RoundedCornerShape(percent = 50)
    val indicatorOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "strix-liquid-glass-indicator",
    )
    val surfaceTop = if (pureBlack) Color(0xE91A1A20) else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    val surfaceBottom = if (pureBlack) Color(0xD9101015) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.68f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (bitChordTheme) 14.dp else 18.dp, vertical = 7.dp)
            .shadow(if (bitChordTheme) 24.dp else 18.dp, pillShape)
            .clip(pillShape)
            .background(
                if (bitChordTheme) Brush.verticalGradient(listOf(Color(0xEE25232D), Color(0xD916151D)))
                else Brush.verticalGradient(listOf(surfaceTop, surfaceBottom)),
            )
            .border(1.dp, Color.White.copy(alpha = if (glassTheme) 0.20f else 0.10f), pillShape)
            .padding(5.dp),
    ) {
        val slotWidth = maxWidth / itemCount
        val indicatorWidth = slotWidth - 2.dp
        Box(
            modifier = Modifier
                .offset(x = (slotWidth * indicatorOffset) + 1.dp)
                .width(indicatorWidth)
                .height(if (bitChordTheme) 56.dp else 53.dp)
                .clip(if (bitChordTheme) RoundedCornerShape(22.dp) else RoundedCornerShape(percent = 50))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.075f)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.19f), RoundedCornerShape(percent = 50)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationItems.forEach { screen ->
                val isSelected = isRouteSelected(currentRoute, screen.route, navigationItems)
                val currentIsSelected by rememberUpdatedState(isSelected)
                val iconRes = if (isSelected) screen.iconIdActive else screen.iconIdInactive
                val label = stringResource(screen.titleId)
                val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                val interactionSource = remember(screen.route) { MutableInteractionSource() }

                if (isSearchItem) {
                    LaunchedEffect(interactionSource) {
                        var longPressed = false
                        interactionSource.interactions.collectLatest { interaction ->
                            when (interaction) {
                                is PressInteraction.Press -> {
                                    longPressed = false
                                    delay(viewConfiguration.longPressTimeoutMillis)
                                    longPressed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSearchLongClick.invoke()
                                }
                                is PressInteraction.Release -> if (!longPressed) onItemClick(screen, currentIsSelected)
                                is PressInteraction.Cancel -> longPressed = false
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (bitChordTheme) 56.dp else 53.dp)
                        .clip(if (bitChordTheme) RoundedCornerShape(20.dp) else RoundedCornerShape(22.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { if (!isSearchItem) onItemClick(screen, currentIsSelected) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = label,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer {
                                val scale = if (isSelected) 1.07f else 1f
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                    if (!slimNav) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
