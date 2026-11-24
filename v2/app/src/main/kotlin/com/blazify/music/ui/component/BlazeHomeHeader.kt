/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.component

import com.blazify.music.utils.loadedBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.blazify.music.ui.theme.PlayerColorExtractor
import com.blazify.music.LocalPlayerConnection
import com.blazify.music.LocalDatabase
import coil3.toBitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.imageLoader
import androidx.palette.graphics.Palette
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.AsyncImage
import com.blazify.music.R
import com.blazify.music.constants.DarkModeKey
import com.blazify.music.constants.ShowHomeGreetingKey
import com.blazify.music.constants.ShowHomeSearchBarKey
import com.blazify.music.ui.screens.settings.DarkMode
import com.blazify.music.ui.theme.BlazeGradientEnd
import com.blazify.music.ui.theme.BlazeThemeColor
import com.blazify.music.utils.rememberEnumPreference
import com.blazify.music.utils.rememberPreference
import java.util.Calendar
import java.time.LocalDateTime

private val SearchRowHeight = 52.dp

/**
 * Blazify home header, ported from the original Flutter app:
 * top row (account | logo + wordmark | settings), greeting card with the
 * hero image overflowing above the card, and a rounded search bar.
 */
@Composable
fun BlazeHomeHeader(
    userName: String = stringResource(R.string.blaze_greeting_default_name),
    onAccountClick: () -> Unit = {},
    onSettingsClick: (() -> Unit)? = {},
    onSearchClick: () -> Unit = {},
    onMicClick: () -> Unit = {},
    // Two ways to start music from the card itself, each showing the cover of the
    // song it starts with. Null hides the button; with neither, the card keeps its
    // old "Enjoy the music" line instead.
    onForYouClick: (() -> Unit)? = null,
    forYouArt: String? = null,
    onSpeedDialClick: (() -> Unit)? = null,
    speedDialArt: String? = null,
) {
    val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isDark = if (darkMode == DarkMode.AUTO) isSystemInDarkTheme() else darkMode == DarkMode.ON
    // Both blocks are optional — some people want a compact home (Look & Feel → Home).
    val showGreeting by rememberPreference(ShowHomeGreetingKey, defaultValue = true)
    val showSearchBar by rememberPreference(ShowHomeSearchBarKey, defaultValue = true)
    // The two icons in the theme's own colour — the album's, when the theme
    // follows the album — as the iOS header draws them. White on black read as
    // furniture; the colour says they belong to the app that is playing.
    val iconTint = MaterialTheme.colorScheme.primary
    // The logo and name are smaller where the full size would run into the two icons: a narrow
    // screen, or an ordinary one with very large text.
    val compactHeader =
        LocalConfiguration.current.screenWidthDp / LocalDensity.current.fontScale < 330f

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row: account | logo + "Strix Music" | settings
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            IconButton(onClick = onAccountClick) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = stringResource(R.string.account),
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // The launch logo lands here: this one reports where it is, keeps out of sight
                // while that one is on its way, and fades in as it arrives.
                val logoShown by com.blazify.music.utils.LaunchSignal.headerLogoShown.collectAsState()
                val logoAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (logoShown) 1f else 0f,
                    animationSpec = androidx.compose.animation.core.tween(160),
                    label = "headerLogo",
                )
                val logoSize = if (compactHeader) 30.dp else 42.dp
                val logoModifier = Modifier
                    .size(logoSize)
                    .graphicsLayer { alpha = logoAlpha }
                // On a festival day here, the logo dresses up and plays a celebration when tapped.
                val festival = com.blazify.music.festival.rememberActiveFestival()
                if (festival != null) {
                    com.blazify.music.festival.FestivalLogo(festival, isDark, logoSize, logoModifier)
                } else {
                    Image(
                        painter = painterResource(
                            if (isDark) R.drawable.blaze_logo_white else R.drawable.blaze_logo,
                        ),
                        contentDescription = null,
                        modifier = logoModifier,
                    )
                }
                Spacer(Modifier.width(if (compactHeader) 6.dp else 10.dp))
                Text(
                    text = "Strix Music",
                    fontSize = if (compactHeader) 18.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (compactHeader) 1.sp else 3.sp,
                    maxLines = 1,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = if (isDark) {
                            Brush.linearGradient(listOf(Color.White, Color.White))
                        } else {
                            Brush.linearGradient(listOf(BlazeThemeColor, BlazeGradientEnd))
                        },
                    ),
                )
            }
            onSettingsClick?.let { openSettings ->
                IconButton(onClick = openSettings) {
                    Icon(
                        painter = painterResource(R.drawable.settings),
                        contentDescription = stringResource(R.string.settings),
                        tint = iconTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Compact listening dashboard replaces the decorative hero artwork.
        // The duration comes from the app's real listening-history events.
        val cardStart = MaterialTheme.colorScheme.primary
        val cardEnd = MaterialTheme.colorScheme.tertiary
        val onCard = Color.White
        if (showGreeting) {
            ListeningProgressCard(userName = userName)
        }

        // Search, and beside it its own round button for finding the song that's playing near
        // you. The pill stays plain so it always reads the same; the button takes the colour of
        // the song, as the card above does. The pill takes whatever width is left and its hint
        // shortens, so no screen is too narrow for the two, and it grows with a large text size.
        if (showSearchBar) Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val pillShape = RoundedCornerShape(50)
            val hintColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color(0x8A000000)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SearchRowHeight)
                    // Solid, because a shadow shows through a see-through fill.
                    .then(if (isDark) Modifier else Modifier.shadow(4.dp, pillShape))
                    .clip(pillShape)
                    .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                    .clickable(onClick = onSearchClick)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = hintColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_search_hint),
                    color = hintColor,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            val songStart by animateColorAsState(cardStart, tween(500), label = "recognizeStart")
            val songEnd by animateColorAsState(cardEnd, tween(500), label = "recognizeEnd")
            val songInk by animateColorAsState(onCard, tween(500), label = "recognizeInk")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(SearchRowHeight)
                    .shadow(6.dp, CircleShape, ambientColor = songStart, spotColor = songStart)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(songStart, songEnd)))
                    .clickable(onClick = onMicClick),
            ) {
                Icon(
                    painter = painterResource(R.drawable.sound_wave),
                    contentDescription = stringResource(R.string.recognize_music),
                    tint = songInk,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}


@Composable
private fun ListeningProgressCard(userName: String) {
    val database = LocalDatabase.current
    val now = remember { LocalDateTime.now() }
    val startOfDay = remember { now.toLocalDate().atStartOfDay() }
    val weekStart = remember { now.toLocalDate().minusDays(6).atStartOfDay() }
    val todayMs by database.getTotalPlayTimeInRange(startOfDay, now).collectAsState(initial = 0L)
    val weekMs by database.getTotalPlayTimeInRange(weekStart, now).collectAsState(initial = 0L)
    val todayMinutes = (todayMs ?: 0L).coerceAtLeast(0L) / 60_000L
    val weekMinutes = (weekMs ?: 0L).coerceAtLeast(0L) / 60_000L
    val targetMinutes = 180L
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (todayMinutes.toFloat() / targetMinutes).coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(850),
        label = "dailyListeningProgress",
    )
    fun durationLabel(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}j ${mins}m" else "${mins}m"
    }
    val greeting = when (java.time.LocalTime.now().hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..17 -> "Selamat sore"
        else -> "Selamat malam"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF28232A), Color(0xFF151416), Color(0xFF21191F))))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(greeting, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    userName.takeIf { it.isNotBlank() } ?: "Pencinta Musik",
                    color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFDAA1B7).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(R.drawable.sound_wave), contentDescription = null, tint = Color(0xFFE8AFC4), modifier = Modifier.size(23.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text("WAKTU MENDENGARKAN HARI INI", color = Color(0xFFBBAEB5), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(durationLabel(todayMinutes), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Text("Target 3 jam", color = Color(0xFFE8AFC4), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = Color(0xFFE5A9BF),
            trackColor = Color.White.copy(alpha = 0.12f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (todayMinutes >= targetMinutes) "Target harian tercapai!" else "${durationLabel((targetMinutes - todayMinutes).coerceAtLeast(0))} lagi menuju target",
                color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, modifier = Modifier.weight(1f),
            )
            Text("7 hari: ${durationLabel(weekMinutes)}", color = Color.White.copy(alpha = 0.82f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The greeting card's column: greeting, name, then the buttons or the "Enjoy the music"
 * line. Every line stays on one line in any language and at any length.
 *
 * Drawn twice. With [showWords] the words show and the buttons' row is left empty; without
 * it the words are invisible, kept only so the buttons land where they belong, and
 * silent so a screen reader does not read them twice.
 */
@Composable
private fun CardWords(
    greeting: String,
    userName: String,
    onCard: Color,
    showWords: Boolean,
    hasButtons: Boolean,
    photoLeft: Dp,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit = {},
) {
    // A slight drop shadow, so white words stay readable on a pale card.
    val shadow = with(LocalDensity.current) {
        Shadow(
            color = (if (onCard.luminance() > 0.5f) Color.Black else Color.White).copy(alpha = 0.25f),
            offset = Offset(0f, 1.dp.toPx()),
            blurRadius = 3.dp.toPx(),
        )
    }
    val words = if (showWords) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}
    // Where each line fades, measured from the photo's left edge (the lines start 20dp
    // into the card). Her hand is in the first ~27dp at the name's height, with a gap
    // before her sweater, so the name is gone by then; the greeting sits beside her
    // hair and fades later, which keeps the sun or moon after it whole.
    val fromLine = photoLeft - 20.dp
    val greetingFade = Modifier.fadeOutBetween(fromLine + 16.dp, fromLine + 56.dp)
    val lowerFade = Modifier.fadeOutBetween(fromLine - 4.dp, fromLine + 24.dp)
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp),
    ) {
        // Each line gets its own room below it, inside its fade, so the fade does
        // not clip the shadow under the letters.
        Box(greetingFade.padding(bottom = 6.dp)) {
            Text(
                text = greeting,
                color = onCard,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                lineHeight = 27.sp,
                softWrap = false,
                style = TextStyle(shadow = shadow),
                modifier = words,
            )
        }
        Box(lowerFade.padding(bottom = if (hasButtons) 8.dp else 6.dp)) {
            Text(
                text = userName,
                color = onCard.copy(alpha = 0.95f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(shadow = shadow),
                modifier = words,
            )
        }
        if (!hasButtons) {
            Box(lowerFade) {
                Text(
                    text = stringResource(R.string.home_enjoy_music),
                    color = onCard.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                    softWrap = false,
                    style = TextStyle(shadow = shadow),
                    modifier = words,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showWords) Spacer(Modifier.height(32.dp)) else buttons()
            }
        }
    }
}

/** Fades whatever is drawn out to nothing between [start] and [end], left to right. */
private fun Modifier.fadeOutBetween(start: Dp, end: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startX = start.toPx(),
                    endX = end.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/**
 * A small button on the greeting card that starts music: the cover of the song it
 * plays first, with a play mark on it, then the label, or the bare cover alone. A cover says "this plays"
 * where a text-only pill read as a label nobody would think to tap. A drop shadow
 * lifts it off the card.
 */
@Composable
private fun CardButton(
    art: String?,
    icon: Int,
    label: String,
    onCard: Color,
    fill: Color,
    onClick: () -> Unit,
    showLabel: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            // A drop shadow lifts the buttons off the card, so they read as things to press.
            .shadow(elevation = 6.dp, shape = shape)
            .clip(shape)
            .background(fill)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            // A cover alone has no words, so a screen reader is given the label instead.
            .then(if (showLabel) Modifier else Modifier.semantics { contentDescription = label }),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .background(onCard.copy(alpha = 0.12f)),
        ) {
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                // The play mark belongs to the labelled button; a cover on its own
                // stays a clean picture.
                if (showLabel) Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            } else {
                // No cover yet (still loading, or nothing to show): the button's own icon.
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = onCard,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (showLabel) {
            Text(
                text = label,
                color = onCard,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
            )
        }
    }
}

private fun greetingRes(): Int {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11 -> R.string.home_greeting_morning
        hour in 12..16 -> R.string.home_greeting_afternoon
        hour in 17..20 -> R.string.home_greeting_evening
        else -> R.string.home_greeting_night
    }
}
