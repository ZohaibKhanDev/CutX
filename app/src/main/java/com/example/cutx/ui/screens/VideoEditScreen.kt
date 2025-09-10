package com.example.cutx.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import kotlinx.coroutines.CompletableDeferred

@OptIn(UnstableApi::class)
// Edit model types (top-level)
data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)
data class EditState(val crop: CropRect?, val speed: Float)
sealed interface EditOp { data class SetCrop(val crop: CropRect?): EditOp; data class SetSpeed(val speed: Float): EditOp }

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(UnstableApi::class)
@Composable
fun VideoEditScreen(
    videoUri: Uri,
    onBack: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableStateOf(0L) }
    var positionMs by remember { mutableStateOf(0L) }
    var sliderValue by remember { mutableStateOf(0f) }
    var isUserScrubbing by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    val undoStack = remember { mutableStateListOf<EditOp>() }
    val redoStack = remember { mutableStateListOf<EditOp>() }
    var editState by remember { mutableStateOf(EditState(crop = null, speed = 1f)) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }

    // Player instance
    val exoPlayer = remember(context, videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.takeIf { it > 0 } ?: durationMs
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    exoPlayer.seekTo(0)
                }
            }

            override fun onIsPlayingChanged(isPlaying_: Boolean) {
                isPlaying = isPlaying_
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x1A000000), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Video Edit",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = {
                    isExporting = true
                    exportProgress = ""
                    scope.launch {
                        try {
                            if (editState.crop != null || editState.speed != 1f) {
                                exportProgress = "Crop/Speed not applied in export yet"
                            }
                            val out = exportWithTransformerSuspending(
                                context = context,
                                inputUri = videoUri
                            )
                            isExporting = false
                            if (out != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".fileprovider",
                                    out
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share video"))
                            }
                        } catch (_: Throwable) {
                            isExporting = false
                        }
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0x1A000000), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.IosShare,
                    contentDescription = "Share",
                    tint = Color.White
                )
            }
        }

        // Player area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AndroidView<PlayerView>(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx: android.content.Context ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                }
            )
        }

        // Play/Pause control below the preview (hidden while playing per design)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (undoStack.isNotEmpty()) {
                    val op = undoStack.removeLast()
                    // Revert current state and push to redo
                    when (op) {
                        is EditOp.SetCrop -> {
                            redoStack.add(EditOp.SetCrop(editState.crop))
                            editState = editState.copy(crop = op.crop)
                        }
                        is EditOp.SetSpeed -> {
                            redoStack.add(EditOp.SetSpeed(editState.speed))
                            editState = editState.copy(speed = op.speed)
                            exoPlayer.setPlaybackSpeed(editState.speed)
                        }
                    }
                }
            }) { Icon(Icons.Default.Undo, contentDescription = "Undo", tint = Color.White) }
            Surface(shape = CircleShape, color = Color(0xFF1C1C1C)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clickable {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            IconButton(onClick = {
                if (redoStack.isNotEmpty()) {
                    val op = redoStack.removeLast()
                    // Apply op and push inverse to undo
                    when (op) {
                        is EditOp.SetCrop -> {
                            undoStack.add(EditOp.SetCrop(editState.crop))
                            editState = editState.copy(crop = op.crop)
                        }
                        is EditOp.SetSpeed -> {
                            undoStack.add(EditOp.SetSpeed(editState.speed))
                            editState = editState.copy(speed = op.speed)
                            exoPlayer.setPlaybackSpeed(editState.speed)
                        }
                    }
                }
            }) { Icon(Icons.Default.Redo, contentDescription = "Redo", tint = Color.White) }
            IconButton(onClick = { isFullscreen = true }) { Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White) }
        }

        // Keep duration/position in sync
        LaunchedEffect(exoPlayer) {
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            while (true) {
                positionMs = exoPlayer.currentPosition
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: durationMs
                if (!isUserScrubbing && durationMs > 0) {
                    sliderValue = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
                kotlinx.coroutines.delay(250)
            }
        }

        // Timeline area
        val ctx = LocalContext.current
        val thumbs by remember(videoUri, ctx) { mutableStateOf(generateThumbnails(ctx, videoUri)) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151515))
                .padding(vertical = 8.dp)
        ) {
            // Duration row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val curMin = (positionMs / 1000) / 60
                val curSec = (positionMs / 1000) % 60
                val durMin = (durationMs / 1000) / 60
                val durSec = (durationMs / 1000) % 60
                Text(String.format("%02d:%02d / %02d:%02d", curMin, curSec, durMin, durSec), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Text("", color = Color.Transparent, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(thumbs) { bmp ->
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(64.dp)
                                .width(64.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
                // Custom slider over thumbnails with tap + drag
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val w = size.width.coerceAtLeast(1f.toInt())
                                val fraction = (offset.x.coerceIn(0f, w.toFloat()) / w).coerceIn(0f, 1f)
                                sliderValue = fraction
                                if (durationMs > 0) exoPlayer.seekTo((durationMs * sliderValue).toLong())
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val w = size.width.coerceAtLeast(1f.toInt())
                                    val fraction = (startOffset.x.coerceIn(0f, w.toFloat()) / w).coerceIn(0f, 1f)
                                    sliderValue = fraction
                                    isUserScrubbing = true
                                },
                                onDragEnd = {
                                    if (durationMs > 0) {
                                        val seekTo = (durationMs * sliderValue).toLong()
                                        exoPlayer.seekTo(seekTo)
                                    }
                                    isUserScrubbing = false
                                }
                            ) { change, _ ->
                                val w = size.width.coerceAtLeast(1f.toInt())
                                val x = change.position.x.coerceIn(0f, w.toFloat())
                                val fraction = (x / w).coerceIn(0f, 1f)
                                isUserScrubbing = true
                                sliderValue = fraction
                            }
                        }
                ) {
                    // Track
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerY = size.height / 2f
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                        // Active segment
                        drawLine(
                            color = Color(0xFF4A90E2),
                            start = Offset(0f, centerY),
                            end = Offset(size.width * sliderValue, centerY),
                            strokeWidth = 6f,
                            cap = StrokeCap.Round
                        )
                        // Thumb indicator
                        val thumbX = size.width * sliderValue
                        drawLine(
                            color = Color(0xFF4A90E2),
                            start = Offset(thumbX, 0f),
                            end = Offset(thumbX, size.height),
                            strokeWidth = 8f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // Add music row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f)
                )
                Spacer(Modifier.width(8.dp))
                Text("Add music", color = Color.White, fontSize = 14.sp)
            }
        }

        // Bottom tool bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color(0xFF0F0F0F))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTool(icon = Icons.Default.GridOn, label = "Canvas")
            BottomTool(icon = Icons.Default.Wallpaper, label = "BG")
            BottomTool(icon = Icons.Default.ContentCut, label = "Trim")
            BottomTool(icon = Icons.Default.CallSplit, label = "Split")
            BottomTool(icon = Icons.Default.Crop, label = "Crop", onClick = { showCropDialog = true })
            BottomTool(icon = Icons.Default.Speed, label = "Speed", onClick = { showSpeedDialog = true })
            BottomTool(icon = Icons.Default.FilterList, label = "Filter")
        }

        if (isFullscreen) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { isFullscreen = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView<PlayerView>(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx: android.content.Context ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = exoPlayer
                            }
                        }
                    )
                }
            }
        }

        // Crop dialog
        if (showCropDialog) {
            Dialog(onDismissRequest = { showCropDialog = false }) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E1E)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Crop", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fun select(c: CropRect?) {
                                undoStack.add(EditOp.SetCrop(editState.crop))
                                redoStack.clear()
                                editState = editState.copy(crop = c)
                            }
                            AssistChip(onClick = { select(null) }, label = { Text("None") })
                            AssistChip(onClick = { select(CropRect(0.0f, 0.0f, 1.0f, 1.0f)) }, label = { Text("1:1") })
                            AssistChip(onClick = {
                                select(CropRect(0f, 0.125f, 1f, 0.75f))
                            }, label = { Text("16:9") })
                            AssistChip(onClick = {
                                select(CropRect(0.3125f, 0f, 0.375f, 1f))
                            }, label = { Text("9:16") })
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showCropDialog = false }) { Text("Close") }
                        }
                    }
                }
            }
        }

        // Speed dialog
        if (showSpeedDialog) {
            Dialog(onDismissRequest = { showSpeedDialog = false }) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E1E)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Playback speed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(12.dp))
                        var temp by remember { mutableStateOf(editState.speed) }
                        Slider(
                            value = temp,
                            onValueChange = { v -> temp = v.coerceIn(0.25f, 3f) },
                            valueRange = 0.25f..3f
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(String.format("%.2fx", temp), color = Color.White)
                            Row {
                                TextButton(onClick = { showSpeedDialog = false }) { Text("Cancel") }
                                TextButton(onClick = {
                                    undoStack.add(EditOp.SetSpeed(editState.speed))
                                    redoStack.clear()
                                    editState = editState.copy(speed = temp)
                                    exoPlayer.setPlaybackSpeed(temp)
                                    showSpeedDialog = false
                                }) { Text("Apply") }
                            }
                        }
                    }
                }
            }
        }

        // Export overlay
        if (isExporting) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.75f)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(if (exportProgress.isEmpty()) "Exporting..." else exportProgress, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF1C1C1C),
            modifier = Modifier.size(40.dp)
        ) {
            Box(Modifier.fillMaxSize().clickable(enabled = onClick != null) { onClick?.invoke() }, contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

private fun generateThumbnails(context: Context, uri: Uri): List<Bitmap> {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val durationUs = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
        val step = if (durationUs > 0) durationUs / 12 else 500_000L
        val frames = mutableListOf<Bitmap>()
        var t = 0L
        while (t < durationUs && frames.size < 12) {
            retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frames.add(it) }
            t += step
        }
        retriever.release()
        frames
    } catch (t: Throwable) {
        emptyList()
    }
}

// Export utilities using Media3 Transformer (basic copy without effects for now)
@OptIn(androidx.media3.common.util.UnstableApi::class)
private suspend fun exportWithTransformerSuspending(
    context: Context,
    inputUri: Uri
): File? {
    val outFile = File(context.cacheDir, "cutx_export_${System.currentTimeMillis()}.mp4")
    val transformer = Transformer.Builder(context).build()
    val edited = EditedMediaItem.Builder(MediaItem.fromUri(inputUri)).build()
    val deferred = CompletableDeferred<Boolean>()
    val listener = object : Transformer.Listener {
        @OptIn(UnstableApi::class)
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            deferred.complete(true)
        }

        @OptIn(UnstableApi::class)
        override fun onError(
            composition: Composition,
            exportResult: ExportResult,
            exportException: ExportException
        ) {
            deferred.complete(false)
        }
    }
    transformer.addListener(listener)
    return try {
        transformer.start(edited, outFile.absolutePath)
        val ok = deferred.await()
        if (ok && outFile.exists()) outFile else null
    } catch (_: Throwable) {
        null
    } finally {
        transformer.removeListener(listener)
    }
}

// Helpers to resolve file path and probe video size
private object FileUtils {
    fun getPathFromUri(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> uri.path
            "content" -> try {
                val cursor = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                val name = cursor?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: (System.currentTimeMillis().toString())
                val out = File(context.cacheDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                } ?: return null
                out.absolutePath
            } catch (_: Throwable) { null }
            else -> null
        }
    }
}

private data class VideoSize(val width: Int, val height: Int)
private object VideoProbe {
    fun probe(context: Context, uri: Uri): VideoSize? = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(context, uri)
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
        mmr.release()
        VideoSize(w, h)
    } catch (_: Throwable) { null }
}

// Compatibility wrapper for normalized crop rect storage separate from composable scope
private data class VideoEditScreen_CropRectCompat(val left: Float, val top: Float, val width: Float, val height: Float)


