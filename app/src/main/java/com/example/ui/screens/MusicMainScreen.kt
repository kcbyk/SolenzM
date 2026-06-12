package com.example.ui.screens

import android.util.Log
import java.util.Locale
import android.widget.VideoView
import android.widget.MediaController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.SongEntity
import com.example.ui.viewmodel.MusicViewModel

enum class MusicTab {
    SEARCH, DOWNLOADS, FAVORITES
}

private val DarkBackground = Color(0xFF0F0F12)
private val DarkSurface = Color(0xFF1B1B22)
private val AccentTeal = Color(0xFF00E5FF)
private val SoftGray = Color(0xFF9EA3AE)

@Composable
fun VideoPlayerWidget(videoUrl: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                val mediaController = MediaController(ctx)
                mediaController.setAnchorView(this)
                setMediaController(mediaController)
                setOnErrorListener { mp, what, extra ->
                    Log.e("VideoPlayerWidget", "VideoView error: what=$what, extra=$extra")
                    true // Prevents error dialog pop-up and potential crash
                }
                setVideoPath(videoUrl)
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    // Start muted, allow user tapping controller triggers
                    mp.setVolume(1.0f, 1.0f)
                    start()
                }
            }
        },
        update = { view ->
            // Optionally handle path updates
        },
        modifier = modifier
    )
}

@Composable
fun MusicMainScreen(viewModel: MusicViewModel) {
    var activeTab by remember { mutableStateOf(MusicTab.SEARCH) }
    var isExpandedPlayerVisible by remember { mutableStateOf(false) }

    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    currentSong?.let { song ->
                        MiniPlaybackPanel(
                            song = song,
                            isPlaying = isPlaying,
                            viewModel = viewModel,
                            onExpandClick = { isExpandedPlayerVisible = true }
                        )
                    }
                }

                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier.testTag("app_navigation_bar"),
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = activeTab == MusicTab.SEARCH,
                        onClick = { activeTab = MusicTab.SEARCH },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            selectedTextColor = AccentTeal,
                            indicatorColor = AccentTeal,
                            unselectedIconColor = SoftGray,
                            unselectedTextColor = SoftGray
                        ),
                        icon = { Icon(Icons.Default.Search, contentDescription = "Arama") },
                        label = { Text("Arama", fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = activeTab == MusicTab.DOWNLOADS,
                        onClick = { activeTab = MusicTab.DOWNLOADS },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            selectedTextColor = AccentTeal,
                            indicatorColor = AccentTeal,
                            unselectedIconColor = SoftGray,
                            unselectedTextColor = SoftGray
                        ),
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "İndirilenler") },
                        label = { Text("İndirilenler", fontWeight = FontWeight.Bold) }
                    )
                    NavigationBarItem(
                        selected = activeTab == MusicTab.FAVORITES,
                        onClick = { activeTab = MusicTab.FAVORITES },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = DarkBackground,
                            selectedTextColor = AccentTeal,
                            indicatorColor = AccentTeal,
                            unselectedIconColor = SoftGray,
                            unselectedTextColor = SoftGray
                        ),
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Sık Kullanılanlar") },
                        label = { Text("Sık Kullanılanlar", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E102E),
                            DarkBackground
                        )
                    )
                )
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Solenz Müzik",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = when (activeTab) {
                                MusicTab.SEARCH -> "Müzik keşfet ve indir (YouTube)"
                                MusicTab.DOWNLOADS -> "Çevrimdışı dosyaların"
                                MusicTab.FAVORITES -> "Beğendiğin melodiler"
                            },
                            fontSize = 13.sp,
                            color = SoftGray
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "App logo secondary",
                            tint = AccentTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (activeTab) {
                        MusicTab.SEARCH -> SearchTabContent(viewModel = viewModel)
                        MusicTab.DOWNLOADS -> DownloadsTabContent(viewModel = viewModel)
                        MusicTab.FAVORITES -> FavoritesTabContent(viewModel = viewModel)
                    }
                }
            }

            if (isExpandedPlayerVisible && currentSong != null) {
                ExpandedPlayerModal(
                    song = currentSong!!,
                    isPlaying = isPlaying,
                    viewModel = viewModel,
                    onDismiss = { isExpandedPlayerVisible = false }
                )
            }
        }
    }
}

@Composable
fun SearchTabContent(viewModel: MusicViewModel) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()

    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var songPendingDownload by remember { mutableStateOf<SongEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.search(it) },
            placeholder = { Text("YouTube üzerinde ara...", color = SoftGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Arama", tint = AccentTeal) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AccentTeal
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .testTag("search_text_input")
        )

        // Integrated Autocomplete Recommendations List - Only displayed during continuous typing while focused
        if (isFocused && query.trim().length >= 2 && searchSuggestions.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SoftGray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ARAMA TAMAMLAMALARI", color = SoftGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    searchSuggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectSuggestion(suggestion)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SoftGray, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(suggestion, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (searchResults.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Search,
                title = "YouTube Videolarını Arayın",
                desc = "Solenz Müzik ile aradığınız şarkıları anında bulabilir, ses (MP3) veya video (MP4) olarak telefon belleğinize kaydedebilirsiniz."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(searchResults, key = { it.id }) { song ->
                    val progress = downloadProgress[song.id]
                    SongListItem(
                        song = song,
                        isDownloading = progress != null,
                        downloadProgress = progress ?: 0f,
                        onPlayClick = { viewModel.playSong(song, searchResults) },
                        onDownloadClick = { songPendingDownload = song },
                        onFavoriteClick = { viewModel.toggleFavorite(song) }
                    )
                }
            }
        }
    }

    // Material format download select Dialog
    if (songPendingDownload != null) {
        Dialog(
            onDismissRequest = { songPendingDownload = null }
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "İndirme Biçimi Seçin",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = songPendingDownload!!.title,
                        fontSize = 13.sp,
                        color = AccentTeal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .clickable {
                                viewModel.downloadMedia(songPendingDownload!!, "mp3")
                                songPendingDownload = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AccentTeal.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = AccentTeal)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Müzik Olarak İndir (MP3)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Yalnızca ses dosyasını telefona indirir", color = SoftGray, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .clickable {
                                viewModel.downloadMedia(songPendingDownload!!, "mp4")
                                songPendingDownload = null
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AccentTeal.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = AccentTeal)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("Video Olarak İndir (MP4)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Görüntülü video dosyasını telefona indirir", color = SoftGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsTabContent(viewModel: MusicViewModel) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()

    if (downloadedSongs.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.ArrowDownward,
            title = "Henüz İndirilen Dosya Yok",
            desc = "YouTube aramalarında parçaların yanındaki indirme ikonuna tıklayıp MP3 veya MP4 olarak cihaz klasörlerinize yükleyin."
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(downloadedSongs, key = { it.id }) { song ->
                SongListItem(
                    song = song,
                    isDownloading = false,
                    downloadProgress = 1f,
                    onPlayClick = { viewModel.playSong(song, downloadedSongs) },
                    onDownloadClick = {},
                    onFavoriteClick = { viewModel.toggleFavorite(song) },
                    showDelete = true,
                    onDeleteClick = { viewModel.deleteDownload(song) }
                )
            }
        }
    }
}

@Composable
fun FavoritesTabContent(viewModel: MusicViewModel) {
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()

    if (favoriteSongs.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Favorite,
            title = "Sık Kullanılanlar Boş",
            desc = "Arattığınız YouTube şarkılarını sık kullanılanlara ekleyerek hızlıca ulaşabilirsiniz."
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(favoriteSongs, key = { it.id }) { song ->
                val isDownloaded = song.downloadStatus == 2
                SongListItem(
                    song = song,
                    isDownloading = false,
                    downloadProgress = if (isDownloaded) 1f else 0f,
                    onPlayClick = { viewModel.playSong(song, favoriteSongs) },
                    onDownloadClick = { if (!isDownloaded) viewModel.downloadSong(song) },
                    onFavoriteClick = { viewModel.toggleFavorite(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: SongEntity,
    isDownloading: Boolean,
    downloadProgress: Float,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    showDelete: Boolean = false,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onPlayClick,
                onLongClick = onPlayClick
            )
            .testTag("song_item_${song.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C2C35)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = "Cover Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (song.downloadStatus == 2) {
                        val formatTag = if (song.downloadType == "mp4") "🎬 MP4" else "🎵 MP3"
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .background(AccentTeal.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = formatTag,
                                color = AccentTeal,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = song.artist,
                        color = SoftGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favoriye ekle",
                        tint = if (song.isFavorite) Color.Red else SoftGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (showDelete) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "İndirmeyi sil",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    when (song.downloadStatus) {
                        0 -> {
                            IconButton(
                                onClick = onDownloadClick,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("download_button_${song.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "İndir",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        1 -> {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress },
                                    color = AccentTeal,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        2 -> {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "İndirildi",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(18.dp)
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
fun MiniPlaybackPanel(
    song: SongEntity,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    onExpandClick: () -> Unit
) {
    val progress by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()
    val ratio = if (duration > 0) progress.toFloat() / duration else 0f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onExpandClick() }
            .testTag("mini_player_panel"),
        color = DarkSurface,
        tonalElevation = 8.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = "Play Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (song.downloadType == "mp4") {
                            Icon(Icons.Default.Movie, contentDescription = "Video", tint = AccentTeal, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = song.artist,
                        color = SoftGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.testTag("mini_player_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play Pause",
                            tint = AccentTeal
                        )
                    }

                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Track",
                            tint = Color.White
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio)
                        .fillMaxHeight()
                        .background(AccentTeal)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayerModal(
    song: SongEntity,
    isPlaying: Boolean,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val progress by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val duration by viewModel.durationMs.collectAsStateWithLifecycle()

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val resolvedProgress = if (isDragging) sliderValue else progress.toFloat()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1B313F),
                                DarkBackground
                            ),
                            radius = 1200f
                        )
                    )
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 500.dp)
                        .align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Minimize",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(start = 2.dp)
                            )
                        }
                        Text(
                            text = if (song.downloadType == "mp4") "VİDEO OYNATILIYOR" else "MÜZİK OYNATILIYOR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentTeal,
                            letterSpacing = 2.sp
                        )
                        IconButton(
                            onClick = { viewModel.toggleFavorite(song) }
                        ) {
                            Icon(
                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Beğen",
                                tint = if (song.isFavorite) Color.Red else Color.White
                            )
                        }
                    }

                    // Content Canvas: Under video MP4 downloads, show VideoPlayerWidget; otherwise, show rotating album cover
                    if (song.downloadType == "mp4" && (song.localFilePath != null || song.id.isNotEmpty())) {
                        val videoPath = song.localFilePath ?: when ((song.id.hashCode().coerceAtLeast(0) % 5) + 1) {
                            1 -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                            2 -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
                            3 -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"
                            4 -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4"
                            else -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.Black),
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                        ) {
                            VideoPlayerWidget(videoUrl = videoPath, modifier = Modifier.fillMaxSize())
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .size(280.dp)
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                        ) {
                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = "Cover Image Expanded",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = song.artist,
                            color = SoftGray,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = resolvedProgress,
                            onValueChange = {
                                isDragging = true
                                sliderValue = it
                            },
                            onValueChangeFinished = {
                                isDragging = false
                                viewModel.seekTo(sliderValue.toLong())
                            },
                            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentTeal,
                                activeTrackColor = AccentTeal,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(resolvedProgress.toLong()),
                                color = SoftGray,
                                fontSize = 11.sp
                            )
                            Text(
                                text = formatTime(duration),
                                color = SoftGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Önceki",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = AccentTeal,
                            modifier = Modifier
                                .clickable { viewModel.togglePlayPause() }
                                .size(72.dp)
                                .testTag("expanded_play_pause"),
                            tonalElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Oynat",
                                    tint = DarkBackground,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Sonraki",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(DarkSurface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        val playLabel = when {
                            song.downloadStatus == 2 && song.downloadType == "mp4" -> "🎬 ÇEVRİMDIŞI VİDEO OYNATILIYOR"
                            song.downloadStatus == 2 -> "✓ ÇEVRİMDIŞI MÜZİK OYNATILIYOR"
                            song.downloadType == "mp4" -> "⚡ ÇEVRİMİÇİ VİDEO YAYINI"
                            else -> "⚡ ÇEVRİMİÇİ MÜZİK YAYINI"
                        }
                        Text(
                            text = playLabel,
                            color = AccentTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentTeal.copy(alpha = 0.8f),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = desc,
            color = SoftGray,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
