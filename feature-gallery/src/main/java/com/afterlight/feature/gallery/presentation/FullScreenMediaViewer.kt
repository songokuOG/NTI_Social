package com.afterlight.feature.gallery.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.afterlight.core.security.ScreenProtectionManager
import com.afterlight.data.local.model.MediaEntity
import com.afterlight.feature.gallery.di.GalleryEntryPoint
import com.afterlight.feature.gallery.domain.GalleryRepository
import com.afterlight.feature.gallery.domain.JpegBitmapDecoder
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen media viewer with HorizontalPager and pinch-to-zoom.
 * Stage 13: Per-page decryption only, memory-efficient.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenMediaViewer(
    mediaList: List<MediaEntity>,
    initialIndex: Int,
    partyId: String,
    onDismiss: () -> Unit
) {
    ScreenProtectionManager.ProtectScreen()
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { mediaList.size }
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} / ${mediaList.size}") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { pageIndex ->
            // Decrypt only current page (memory-efficient)
            FullScreenMediaPage(
                mediaEntity = mediaList[pageIndex],
                partyId = partyId
            )
        }
    }
}

/**
 * Single full-screen media page with pinch-to-zoom.
 * Decrypts only when visible, cleans up when swiped away.
 */
@Composable
fun FullScreenMediaPage(
    mediaEntity: MediaEntity,
    partyId: String,
    repository: GalleryRepository = EntryPointAccessors.fromActivity(
        androidx.compose.ui.platform.LocalContext.current as android.app.Activity,
        GalleryEntryPoint::class.java
    ).repository()
) {
    // Zoom state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        
        // Only allow panning when zoomed in
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }
    
    var decryptionState by remember(mediaEntity.id) {
        mutableStateOf<DecryptionState>(DecryptionState.Loading)
    }
    LaunchedEffect(mediaEntity.id) {
        decryptionState = withContext(Dispatchers.IO) {
            try {
                val bytes = repository.decryptMedia(mediaEntity.id, partyId).getOrThrow()
                val bitmap = JpegBitmapDecoder.decode(bytes)
                bytes.fill(0)
                if (bitmap == null) {
                    DecryptionState.Error("Unable to decode photo")
                } else {
                    DecryptionState.Success(bitmap)
                }
            } catch (e: Exception) {
                DecryptionState.Error(e.message ?: "Decryption failed")
            }
        }
    }
    
    // Cleanup when page leaves composition
    DisposableEffect(mediaEntity.id) {
        onDispose {
            // Reset zoom state
            scale = 1f
            offset = Offset.Zero
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (decryptionState) {
            is DecryptionState.Loading -> {
                CircularProgressIndicator(color = Color.White)
            }
            is DecryptionState.Success -> {
                val bitmap = (decryptionState as DecryptionState.Success).bitmap
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full-screen photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = transformableState),
                    contentScale = ContentScale.Fit
                )
            }
            is DecryptionState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Failed to load media",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        (decryptionState as DecryptionState.Error).message,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
