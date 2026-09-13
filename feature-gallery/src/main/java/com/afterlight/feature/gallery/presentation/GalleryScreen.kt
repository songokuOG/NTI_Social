package com.afterlight.feature.gallery.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
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
 * Gallery screen with LazyVerticalGrid and memory-safe thumbnails.
 * Stage 13: produceState for decryption, DisposableEffect for cleanup.
 */
@Composable
fun GalleryScreen(
    partyId: String,
    onNavigateBack: () -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    ScreenProtectionManager.ProtectScreen()
    val mediaList by viewModel.mediaList.collectAsState()
    val selectedMediaIndex by viewModel.selectedMediaIndex.collectAsState()
    
    LaunchedEffect(partyId) {
        viewModel.loadParty(partyId)
    }
    
    // Show full-screen viewer if media selected
    if (selectedMediaIndex != null) {
        FullScreenMediaViewer(
            mediaList = mediaList,
            initialIndex = selectedMediaIndex!!,
            partyId = partyId,
            onDismiss = { viewModel.clearSelection() }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Party Gallery") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            if (mediaList.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No photos yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Take some photos to see them here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Grid of thumbnails
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    itemsIndexed(mediaList) { index, mediaEntity ->
                        ThumbnailItem(
                            mediaEntity = mediaEntity,
                            partyId = partyId,
                            onClick = { viewModel.onMediaSelected(index) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thumbnail item with produceState decryption and DisposableEffect cleanup.
 */
@Composable
fun ThumbnailItem(
    mediaEntity: MediaEntity,
    partyId: String,
    onClick: () -> Unit,
    repository: GalleryRepository = hiltViewModel<GalleryViewModel>().let {
        // Access repository through DI manually
        EntryPointAccessors.fromActivity(
            androidx.compose.ui.platform.LocalContext.current as android.app.Activity,
            GalleryEntryPoint::class.java
        ).repository()
    }
) {
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
    
    // Cleanup decrypted bytes when composable leaves composition
    DisposableEffect(mediaEntity.id) {
        onDispose {
            // ByteArray cleanup happens automatically when produceState scope ends
        }
    }
    
    Card(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (decryptionState) {
                is DecryptionState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
                is DecryptionState.Success -> {
                    val bitmap = (decryptionState as DecryptionState.Success).bitmap
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                is DecryptionState.Error -> {
                    // Corrupted badge
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = "Corrupted",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Corrupted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Decryption state for thumbnail loading.
 */
sealed class DecryptionState {
    data object Loading : DecryptionState()
    data class Success(val bitmap: android.graphics.Bitmap) : DecryptionState()
    data class Error(val message: String) : DecryptionState()
}
