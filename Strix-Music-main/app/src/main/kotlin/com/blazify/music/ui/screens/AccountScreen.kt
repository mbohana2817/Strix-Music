/**
 * Blazify Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.blazify.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.blazify.music.LocalPlayerAwareWindowInsets
import com.blazify.music.R
import com.blazify.music.constants.GridItemSize
import com.blazify.music.constants.GridItemsSizeKey
import com.blazify.music.constants.GridThumbnailHeight
import com.blazify.music.db.entities.PodcastEntity
import com.blazify.music.ui.component.ChipsRow
import com.blazify.music.ui.component.IconButton
import com.blazify.music.ui.component.LocalMenuState
import com.blazify.music.ui.component.YouTubeGridItem
import com.blazify.music.ui.component.shimmer.GridItemPlaceHolder
import com.blazify.music.ui.component.shimmer.ListItemPlaceHolder
import com.blazify.music.ui.component.shimmer.ShimmerHost
import com.blazify.music.ui.menu.YouTubeAlbumMenu
import com.blazify.music.ui.menu.YouTubeArtistMenu
import com.blazify.music.ui.menu.YouTubePlaylistMenu
import com.blazify.music.ui.utils.backToMain
import com.blazify.music.utils.rememberEnumPreference
import com.blazify.music.utils.StreakManager
import com.blazify.music.viewmodels.AccountContentType
import com.blazify.music.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    navController: NavController,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val sePlaylist by viewModel.sePlaylist.collectAsStateWithLifecycle()
    val rdpnPlaylist by viewModel.rdpnPlaylist.collectAsStateWithLifecycle()
    val podcastPlaylists by viewModel.podcastPlaylists.collectAsStateWithLifecycle()
    val podcastChannels by viewModel.podcastChannels.collectAsStateWithLifecycle()
    val selectedContentType by viewModel.selectedContentType.collectAsStateWithLifecycle()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            StreakFireCard(
                state = StreakManager.read(androidx.compose.ui.platform.LocalContext.current),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            ChipsRow(
                chips =
                    listOf(
                        AccountContentType.PLAYLISTS to stringResource(R.string.filter_playlists),
                        AccountContentType.ALBUMS to stringResource(R.string.filter_albums),
                        AccountContentType.ARTISTS to stringResource(R.string.filter_artists),
                        AccountContentType.PODCASTS to stringResource(R.string.filter_podcasts),
                    ),
                currentValue = selectedContentType,
                onValueUpdate = { viewModel.setSelectedContentType(it) },
            )
        }

        when (selectedContentType) {
            AccountContentType.PLAYLISTS -> {
                items(
                    items = playlists.orEmpty().distinctBy { it.id },
                    key = { "account_playlist_${it.id}" },
                ) { item ->
                    YouTubeGridItem(
                        item = item,
                        fillMaxWidth = true,
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("online_playlist/${item.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ),
                    )
                }

                if (playlists == null) {
                    items(8) {
                        ShimmerHost {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }

            AccountContentType.ALBUMS -> {
                items(
                    items = albums.orEmpty().distinctBy { it.id },
                    key = { "account_album_${it.id}" },
                ) { item ->
                    YouTubeGridItem(
                        item = item,
                        fillMaxWidth = true,
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("album/${item.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ),
                    )
                }

                if (albums == null) {
                    items(8) {
                        ShimmerHost {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }

            AccountContentType.ARTISTS -> {
                items(
                    items = artists.orEmpty().distinctBy { it.id },
                    key = { "account_artist_${it.id}" },
                ) { item ->
                    YouTubeGridItem(
                        item = item,
                        fillMaxWidth = true,
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        navController.navigate("artist/${item.id}")
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ),
                    )
                }

                if (artists == null) {
                    items(8) {
                        ShimmerHost {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }

            AccountContentType.PODCASTS -> {
                // Show RDPN "New Episodes" playlist if available
                rdpnPlaylist?.let { rdpn ->
                    item(
                        key = "rdpn_playlist",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        SePlaylistAccountItem(
                            thumbnailUrl = rdpn.thumbnail,
                            title = stringResource(R.string.new_episodes),
                            subtitle = stringResource(R.string.auto_playlist),
                            onClick = { navController.navigate("online_playlist/RDPN") },
                        )
                    }
                }

                // Show SE "Episodes for Later" playlist if available
                sePlaylist?.let { se ->
                    item(
                        key = "se_playlist",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        SePlaylistAccountItem(
                            thumbnailUrl = se.thumbnail,
                            title = stringResource(R.string.episodes_for_later),
                            subtitle = stringResource(R.string.auto_playlist),
                            onClick = { navController.navigate("online_playlist/SE") },
                        )
                    }
                }

                // Subscribed podcast shows
                if (podcastPlaylists.isNotEmpty()) {
                    item(
                        key = "podcasts_header",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = stringResource(R.string.filter_podcasts),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    itemsIndexed(
                        items = podcastPlaylists,
                        key = { _, item -> "podcast_${item.id}" },
                        span = { _, _ -> GridItemSpan(maxLineSpan) },
                    ) { _, podcast ->
                        PodcastAccountItem(
                            thumbnailUrl = podcast.thumbnailUrl,
                            title = podcast.title,
                            subtitle = podcast.author,
                            onClick = { navController.navigate("online_podcast/${podcast.id}") },
                        )
                    }
                }

                // Podcast channels
                if (podcastChannels.isNotEmpty()) {
                    item(
                        key = "channels_header",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = stringResource(R.string.filter_channels),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    itemsIndexed(
                        items = podcastChannels,
                        key = { _, item -> "channel_${item.id}" },
                        span = { _, _ -> GridItemSpan(maxLineSpan) },
                    ) { _, channel ->
                        PodcastChannelAccountItem(
                            thumbnailUrl = channel.thumbnail,
                            name = channel.title,
                            onClick = { navController.navigate("artist/${channel.id}") },
                        )
                    }
                }

                if (rdpnPlaylist == null && sePlaylist == null && podcastPlaylists.isEmpty() && podcastChannels.isEmpty()) {
                    items(4, span = { GridItemSpan(maxLineSpan) }) {
                        ShimmerHost {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.account)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun SePlaylistAccountItem(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
        )
    }
}

@Composable
private fun PodcastAccountItem(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PodcastChannelAccountItem(
    thumbnailUrl: String?,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}


@Composable
private fun StreakFireCard(state: StreakManager.State, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "streak-fire")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "streak-pulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "streak-glow",
    )
    val level = state.current.coerceIn(0, 99)
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF160A2A), Color(0xFF32105F), Color(0xFF120A20)),
                ),
            )
            .border(1.dp, Color(0xFFB56CFF).copy(alpha = 0.34f), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse; shadowElevation = 18f; alpha = 0.98f }
                    .clip(CircleShape)
                    .background(Color(0xFF7B2CFF).copy(alpha = 0.22f))
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (level >= 1) "🔥" else "♨", fontSize = 34.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Streak Api", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (level > 0) "${level} hari berturut-turut • Level ${level.coerceAtMost(99)}" else "Mulai streak-mu hari ini",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.10f))) {
                    Box(Modifier.fillMaxWidth(if (level == 0) 0.03f else level / 99f).height(6.dp).clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF9D5CFF), Color(0xFFE2B8FF)))))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (level >= 1) "✦ PURPLE FLAME AKTIF • Rekor ${state.best.coerceAtMost(99)} hari • Maks. Level 99"
                    else "Mulai dari Level 1 untuk membuka efek Purple Flame • Maks. Level 99",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFDDB8FF).copy(alpha = 0.72f),
                )
            }
            Text("Lv.${level}", fontWeight = FontWeight.Black, color = Color(0xFFE0C2FF), modifier = Modifier.graphicsLayer { alpha = 0.75f + glow * 0.45f })
        }
    }
}
