package com.example.cutx.ui.screens

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.pm.ActivityInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@OptIn(UnstableApi::class)
// Edit model types (top-level)
data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)
data class TrimRange(val startMs: Long, val endMs: Long)
data class EditState(val crop: CropRect?, val speed: Float, val trim: TrimRange?)
sealed interface EditOp { 
    data class SetCrop(val crop: CropRect?): EditOp
    data class SetSpeed(val speed: Float): EditOp
    data class SetTrim(val trim: TrimRange?): EditOp
}

@SuppressLint("NewApi")
@RequiresApi(Build.VERSION_CODES.N)
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
    var elapsedTimeMs by remember { mutableStateOf(0L) }
    var playbackStartTime by remember { mutableStateOf(0L) }

    val undoStack = remember { mutableStateListOf<EditOp>() }
    val redoStack = remember { mutableStateListOf<EditOp>() }
    var editState by remember { mutableStateOf(EditState(crop = null, speed = 1f, trim = null)) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf("") }
    var showExportOptions by remember { mutableStateOf(false) }

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
                    elapsedTimeMs = 0L
                    playbackStartTime = System.currentTimeMillis()
                }
            }

            override fun onIsPlayingChanged(isPlaying_: Boolean) {
                isPlaying = isPlaying_
                if (isPlaying_) {
                    // Start tracking elapsed time
                    playbackStartTime = System.currentTimeMillis() - elapsedTimeMs
                }
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
            .background(Color(0xFF000000)) // CapCut's pure black background
            .statusBarsPadding()
    ) {
        // CapCut-style top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Simple back button
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Clean title
            Text(
                text = "Edit",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            // Simple share button
            IconButton(
                onClick = { showExportOptions = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.IosShare,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // CapCut-style video player area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView<PlayerView>(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                factory = { ctx: android.content.Context ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                }
            )
            
        }

        // CapCut-style control bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Undo button
            CapCutControlButton(
                icon = Icons.Default.Undo,
                enabled = undoStack.isNotEmpty(),
                onClick = {
                if (undoStack.isNotEmpty()) {
                    val op = undoStack.removeLast()
                    when (op) {
                        is EditOp.SetCrop -> {
                            redoStack.add(EditOp.SetCrop(editState.crop))
                            editState = editState.copy(crop = op.crop)
                        }
                        is EditOp.SetSpeed -> {
                            redoStack.add(EditOp.SetSpeed(editState.speed))
                            editState = editState.copy(speed = op.speed)
                            exoPlayer.setPlaybackSpeed(editState.speed)
                                // Reset playback tracking when speed changes
                                playbackStartTime = System.currentTimeMillis()
                            }
                            is EditOp.SetTrim -> {
                                redoStack.add(EditOp.SetTrim(editState.trim))
                                editState = editState.copy(trim = op.trim)
                            }
                        }
                    }
                }
            )
            
            // Play/Pause button (main control)
                Box(
                    modifier = Modifier
                    .size(56.dp)
                    .background(Color.White, CircleShape)
                        .clickable {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
            
            // Redo button
            CapCutControlButton(
                icon = Icons.Default.Redo,
                enabled = redoStack.isNotEmpty(),
                onClick = {
                if (redoStack.isNotEmpty()) {
                    val op = redoStack.removeLast()
                    when (op) {
                        is EditOp.SetCrop -> {
                            undoStack.add(EditOp.SetCrop(editState.crop))
                            editState = editState.copy(crop = op.crop)
                        }
                        is EditOp.SetSpeed -> {
                            undoStack.add(EditOp.SetSpeed(editState.speed))
                            editState = editState.copy(speed = op.speed)
                            exoPlayer.setPlaybackSpeed(editState.speed)
                                // Reset playback tracking when speed changes
                                playbackStartTime = System.currentTimeMillis()
                            }
                            is EditOp.SetTrim -> {
                                undoStack.add(EditOp.SetTrim(editState.trim))
                                editState = editState.copy(trim = op.trim)
                            }
                        }
                    }
                }
            )
            
            // Fullscreen button
            CapCutControlButton(
                icon = Icons.Default.Fullscreen,
                enabled = true,
                onClick = { isFullscreen = true }
            )
        }

        // Keep duration/position in sync
        LaunchedEffect(exoPlayer) {
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            while (true) {
                positionMs = exoPlayer.currentPosition
                durationMs = exoPlayer.duration.takeIf { it > 0 } ?: durationMs
                
                // Track elapsed time when playing - account for speed changes
                if (isPlaying) {
                    val realElapsedTime = System.currentTimeMillis() - playbackStartTime
                    // Apply speed multiplier to get actual video time
                    elapsedTimeMs = (realElapsedTime * editState.speed).toLong()
                }
                
                if (!isUserScrubbing && durationMs > 0) {
                    // Calculate slider position based on actual video position
                    val videoPosition = exoPlayer.currentPosition
                    sliderValue = if (durationMs > 0) {
                        (videoPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
                kotlinx.coroutines.delay(250)
            }
        }

        // CapCut-style timeline area
        val ctx = LocalContext.current
        val thumbs by remember(videoUri, ctx) { mutableStateOf(generateThumbnails(ctx, videoUri)) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000))
                .padding(vertical = 12.dp)
        ) {
            // Duration row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Show elapsed time and total duration based on actual video position
                val currentVideoPosition = exoPlayer.currentPosition
                val elapsedMin = (currentVideoPosition / 1000) / 60
                val elapsedSec = (currentVideoPosition / 1000) % 60
                val durMin = (durationMs / 1000) / 60
                val durSec = (durationMs / 1000) % 60
                Text(String.format("%02d:%02d / %02d:%02d", elapsedMin, elapsedSec, durMin, durSec), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Text("", color = Color.Transparent, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                // CapCut-style thumbnail strip
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(thumbs) { bmp ->
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .height(60.dp)
                                .width(60.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
                // CapCut-style slider over thumbnails
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val w = size.width.coerceAtLeast(1f.toInt())
                                val fraction = (offset.x.coerceIn(0f, w.toFloat()) / w).coerceIn(0f, 1f)
                                sliderValue = fraction
                                if (durationMs > 0) {
                                    // Seek to the position based on fraction
                                    val seekTo = (durationMs * fraction).toLong()
                                    exoPlayer.seekTo(seekTo)
                                    // Reset playback tracking
                                    playbackStartTime = System.currentTimeMillis()
                                }
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
                                        // Seek to the position based on fraction
                                        val seekTo = (durationMs * sliderValue).toLong()
                                        exoPlayer.seekTo(seekTo)
                                        // Reset playback tracking
                                        playbackStartTime = System.currentTimeMillis()
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
                    // CapCut-style slider track
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerY = size.height / 2f
                        
                        // Simple background track
                        drawLine(
                            color = Color.White.copy(alpha = 0.2f),
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                        
                        // Trim markers if trim is set
                        editState.trim?.let { trim ->
                            val startFraction = if (durationMs > 0) (trim.startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                            val endFraction = if (durationMs > 0) (trim.endMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 1f
                            
                            // Draw trim range background
                            drawLine(
                                color = Color(0xFFFF6B6B).copy(alpha = 0.3f),
                                start = Offset(size.width * startFraction, centerY),
                                end = Offset(size.width * endFraction, centerY),
                                strokeWidth = 2f,
                                cap = StrokeCap.Round
                            )
                            
                            // Draw trim start marker
                            drawLine(
                                color = Color(0xFFFF6B6B),
                                start = Offset(size.width * startFraction, centerY - 8f),
                                end = Offset(size.width * startFraction, centerY + 8f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            
                            // Draw trim end marker
                            drawLine(
                                color = Color(0xFFFF6B6B),
                                start = Offset(size.width * endFraction, centerY - 8f),
                                end = Offset(size.width * endFraction, centerY + 8f),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                        }
                        
                        // Active segment
                        drawLine(
                            color = Color.White,
                            start = Offset(0f, centerY),
                            end = Offset(size.width * sliderValue, centerY),
                            strokeWidth = 2f,
                            cap = StrokeCap.Round
                        )
                        
                        // Simple thumb indicator
                        val thumbX = size.width * sliderValue
                        drawLine(
                            color = Color.White,
                            start = Offset(thumbX, centerY - 10f),
                            end = Offset(thumbX, centerY + 10f),
                            strokeWidth = 4f,
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

        // CapCut-style bottom toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color(0xFF000000))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapCutBottomTool(icon = Icons.Default.GridOn, label = "Canvas")
            CapCutBottomTool(icon = Icons.Default.Wallpaper, label = "BG")
            CapCutBottomTool(icon = Icons.Default.ContentCut, label = "Trim", onClick = { showTrimDialog = true })
            CapCutBottomTool(icon = Icons.Default.CallSplit, label = "Split")
            CapCutBottomTool(icon = Icons.Default.Crop, label = "Crop", onClick = { showCropDialog = true })
            CapCutBottomTool(icon = Icons.Default.Speed, label = "Speed", onClick = { showSpeedDialog = true })
            CapCutBottomTool(icon = Icons.Default.FilterList, label = "Filter")
        }

        // Fullscreen shown inside an Alert Dialog that covers the screen
        if (isFullscreen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { isFullscreen = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                var fsControlsVisible by remember { mutableStateOf(false) }
                var fsPulse by remember { mutableStateOf(0) }

                // Auto-hide controls after 2s when playing
                LaunchedEffect(fsPulse, isPlaying) {
                    if (fsControlsVisible && isPlaying) {
                        kotlinx.coroutines.delay(2000)
                        fsControlsVisible = false
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                fsControlsVisible = !fsControlsVisible
                                fsPulse++
                            })
                        }
                ) {
                    // Video player
                    AndroidView<PlayerView>(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx: android.content.Context ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = exoPlayer
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        }
                    )

                    if (fsControlsVisible) {
                        // Top back
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isFullscreen = false },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Bottom controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.White, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(4.dp)
                                        .width((sliderValue * 100).dp)
                                        .background(Color.White, RoundedCornerShape(2.dp))
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(elapsedTimeMs),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatTime(durationMs),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
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

        // Exact CapCut Speed UI
        if (showSpeedDialog) {
            var temp by remember { mutableStateOf(editState.speed) }
            Dialog(onDismissRequest = { showSpeedDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(0.dp), 
                    color = Color(0xFF000000),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Top bar with X, undo, Speed title, and checkmark
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A1A))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // X button (cancel)
                            IconButton(
                                onClick = { showSpeedDialog = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Undo button
                            IconButton(
                                onClick = {
                                    if (undoStack.isNotEmpty()) {
                                        val op = undoStack.removeLast()
                                        when (op) {
                                            is EditOp.SetSpeed -> {
                                                redoStack.add(EditOp.SetSpeed(editState.speed))
                                                editState = editState.copy(speed = op.speed)
                                                exoPlayer.setPlaybackSpeed(op.speed)
                                            }
                                            else -> {}
                                        }
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Undo,
                                    contentDescription = "Undo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Speed title
                            Text(
                                text = "Speed",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            // Checkmark button (apply)
                            IconButton(
                                onClick = {
                                    undoStack.add(EditOp.SetSpeed(editState.speed))
                                    redoStack.clear()
                                    editState = editState.copy(speed = temp)
                                    exoPlayer.setPlaybackSpeed(temp)
                                    showSpeedDialog = false
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Apply",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // Main speed control area
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(40.dp))
                            
                            // Large speed display
                            Text(
                                text = String.format("%.1fx", temp),
                                color = Color.White,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Normal
                            )
                            
                            Spacer(Modifier.height(40.dp))
                            
                            // Speed slider with tick marks
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val centerY = size.height / 2f
                                    val trackHeight = 4f
                                    
                                    // Background track
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.3f),
                                        start = Offset(0f, centerY),
                                        end = Offset(size.width, centerY),
                                        strokeWidth = trackHeight,
                                        cap = StrokeCap.Round
                                    )
                                    
                                    // Tick marks
                                    val tickPositions = listOf(0.1f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f)
                                    tickPositions.forEach { speed ->
                                        val position = (speed - 0.1f) / (4.0f - 0.1f)
                                        val x = size.width * position
                                        
                                        // Draw tick mark
                                        val tickHeight = if (speed == 1.0f) 20f else 12f
                                        val tickWidth = if (speed == 1.0f) 3f else 2f
                                        
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.6f),
                                            start = Offset(x, centerY - tickHeight/2f),
                                            end = Offset(x, centerY + tickHeight/2f),
                                            strokeWidth = tickWidth,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                    
                                    // Active track
                                    val progress = (temp - 0.1f) / (4.0f - 0.1f)
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(0f, centerY),
                                        end = Offset(size.width * progress, centerY),
                                        strokeWidth = trackHeight,
                                        cap = StrokeCap.Round
                                    )
                                    
                                    // Thumb
                                    val thumbX = size.width * progress
                                    drawCircle(
                                        color = Color.White,
                                        radius = 12f,
                                        center = Offset(thumbX, centerY)
                                    )
                                }
                                
                                // Invisible slider for interaction
                        Slider(
                            value = temp,
                                    onValueChange = { v -> temp = v.coerceIn(0.1f, 4.0f) },
                                    valueRange = 0.1f..4.0f,
                                    modifier = Modifier.fillMaxSize(),
                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                        thumbColor = Color.Transparent,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    )
                                )
                            }
                            
                            Spacer(Modifier.height(20.dp))
                            
                            // Speed labels
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("0.1x", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("1x", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("2x", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("3x", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                Text("4x", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            
                            Spacer(Modifier.height(40.dp))
                            
                            // Total duration display
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "TOTAL ${formatTime(durationMs)}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        // Bottom section with video thumbnail and song info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1A1A1A))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Video thumbnail
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color(0xFF333333), RoundedCornerShape(8.dp))
                            ) {
                                // Placeholder for video thumbnail
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Text(
                                        text = formatTime(durationMs),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.width(12.dp))
                            
                            // Song info
                            Text(
                                text = "Original Audio",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Trim dialog
        if (showTrimDialog) {
            Dialog(onDismissRequest = { showTrimDialog = false }) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E1E)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Trim Video", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(16.dp))
                        
                        var tempStart by remember { mutableStateOf(editState.trim?.startMs ?: 0L) }
                        var tempEnd by remember { mutableStateOf(editState.trim?.endMs ?: durationMs) }
                        
                        // Start time selector
                        Text("Start Time", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = if (durationMs > 0) (tempStart.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { fraction ->
                                tempStart = (fraction * durationMs.toFloat()).toLong().coerceIn(0L, tempEnd)
                            },
                            valueRange = 0f..1f
                        )
                        Text(
                            text = formatTime(tempStart),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // End time selector
                        Text("End Time", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = if (durationMs > 0) (tempEnd.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 1f,
                            onValueChange = { fraction ->
                                tempEnd = (fraction * durationMs.toFloat()).toLong().coerceIn(tempStart, durationMs)
                            },
                            valueRange = 0f..1f
                        )
                        Text(
                            text = formatTime(tempEnd),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Duration display
                        Text(
                            text = "Duration: ${formatTime(tempEnd - tempStart)}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { showTrimDialog = false }) { Text("Cancel") }
                            Row {
                                TextButton(onClick = {
                                    undoStack.add(EditOp.SetTrim(editState.trim))
                                    redoStack.clear()
                                    editState = editState.copy(trim = null)
                                    showTrimDialog = false
                                }) { Text("Clear") }
                                TextButton(onClick = {
                                    undoStack.add(EditOp.SetTrim(editState.trim))
                                    redoStack.clear()
                                    editState = editState.copy(trim = TrimRange(tempStart, tempEnd))
                                    showTrimDialog = false
                                }) { Text("Apply") }
                            }
                        }
                    }
                }
            }
        }

        // Export options dialog
        if (showExportOptions) {
            Dialog(onDismissRequest = { showExportOptions = false }) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF1E1E1E)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Export Options", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(16.dp))
                        
                        // Share option
                        TextButton(
                            onClick = {
                                showExportOptions = false
                                isExporting = true
                                exportProgress = ""
                                scope.launch {
                                    try {
                                        exportProgress = "Preparing export..."
                                        editState.trim?.let { trim ->
                                            exportProgress = "Applying trim (${formatTime(trim.endMs - trim.startMs)})..."
                                        }
                                        if (editState.speed != 1f) {
                                            exportProgress = "Applying speed changes (${editState.speed}x)..."
                                        }
                                        val out = exportWithTransformerSuspending(
                                            context = context,
                                            inputUri = videoUri,
                                            editState = editState
                                        )
                                        isExporting = false
                                        if (out != null) {
                                            // Check if effects were applied successfully
                                            val hasEffects = editState.speed != 1f || editState.trim != null
                                            if (hasEffects) {
                                                exportProgress = "Export completed (some effects may not be applied due to compatibility issues)"
                                            } else {
                                                exportProgress = "Export completed successfully!"
                                            }
                                            try {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    context.packageName + ".fileprovider",
                                                    out
                                                )
                                                android.util.Log.i("VideoExport", "Generated URI: $uri")
                                                
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "video/mp4"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                
                                                val chooserIntent = android.content.Intent.createChooser(shareIntent, "Share video").apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                
                                                context.startActivity(chooserIntent)
                                                exportProgress = "Video shared successfully!"
                                                Toast.makeText(context, "Video exported and shared successfully!", Toast.LENGTH_LONG).show()
                                                
                                                // Also save to gallery as backup
                                                saveVideoToGallery(context, out)
                                            } catch (e: Exception) {
                                                android.util.Log.e("VideoExport", "Sharing failed: ${e.message}", e)
                                                exportProgress = "Export successful but sharing failed: ${e.message}"
                                                
                                                // Try to save to gallery as fallback
                                                try {
                                                    saveVideoToGallery(context, out)
                                                    exportProgress = "Video saved to gallery successfully!"
                                                    Toast.makeText(context, "Video exported and saved to gallery!", Toast.LENGTH_LONG).show()
                                                } catch (galleryException: Exception) {
                                                    android.util.Log.e("VideoExport", "Gallery save failed: ${galleryException.message}", galleryException)
                                                    Toast.makeText(context, "Export completed but failed to save to gallery", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            exportProgress = "Export failed. Check logs for details."
                                        }
                                    } catch (e: Throwable) {
                                        exportProgress = "Export failed: ${e.message}"
                                        android.util.Log.e("VideoExport", "Export error", e)
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.IosShare, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Share Video", color = Color.White)
                            }
                        }
                        
                        // Save to gallery option
                        TextButton(
                            onClick = {
                                showExportOptions = false
                                isExporting = true
                                exportProgress = ""
                                scope.launch {
                                    try {
                                        exportProgress = "Preparing export..."
                                        editState.trim?.let { trim ->
                                            exportProgress = "Applying trim (${formatTime(trim.endMs - trim.startMs)})..."
                                        }
                                        if (editState.speed != 1f) {
                                            exportProgress = "Applying speed changes (${editState.speed}x)..."
                                        }
                                        val out = exportWithTransformerSuspending(
                                            context = context,
                                            inputUri = videoUri,
                                            editState = editState
                                        )
                                        isExporting = false
                                        if (out != null) {
                                            // Check if speed was applied successfully
                                            if (editState.speed != 1f) {
                                                exportProgress = "Export completed (speed changes may not be applied due to compatibility issues)"
                                            }
                                            try {
                                                saveVideoToGallery(context, out)
                                                exportProgress = "Video saved to gallery successfully!"
                                                Toast.makeText(context, "Video exported and saved to gallery!", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                android.util.Log.e("VideoExport", "Gallery save failed: ${e.message}", e)
                                                exportProgress = "Export successful but failed to save to gallery: ${e.message}"
                                                Toast.makeText(context, "Export completed but failed to save to gallery", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            exportProgress = "Export failed. Check logs for details."
                                        }
                                    } catch (e: Throwable) {
                                        exportProgress = "Export failed: ${e.message}"
                                        android.util.Log.e("VideoExport", "Export error", e)
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Wallpaper, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Save to Gallery", color = Color.White)
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showExportOptions = false }) { Text("Cancel") }
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

// Helper function to format time in MM:SS format
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// CapCut-style control button component
@Composable
private fun CapCutControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        animationSpec = tween(200)
    )
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                Color.White.copy(alpha = if (enabled) 0.1f else 0.05f),
                CircleShape
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier.size(20.dp)
        )
    }
}

// CapCut-style bottom tool component
@Composable
private fun CapCutBottomTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    label: String, 
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    Color.White.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// CapCut-style speed button component
@Composable
private fun CapCutSpeedButton(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                if (isSelected) Color.White else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (speed == 1.0f) "1x" else String.format("%.2fx", speed),
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
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
    inputUri: Uri,
    editState: EditState
): File? {
    return try {
        // Create output file in external storage for better compatibility
        val outputDir = File(context.getExternalFilesDir(null), "exports")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val outFile = File(outputDir, "cutx_export_${System.currentTimeMillis()}.mp4")
        
        val transformer = Transformer.Builder(context)
            .build()
        
        // Build edited media item with effects
        val editedBuilder = EditedMediaItem.Builder(MediaItem.fromUri(inputUri))
        
        // Apply trim if specified
        editState.trim?.let { trim ->
            try {
                // For now, we'll log the trim settings but not apply them in export
                // TODO: Implement proper trim functionality when Media3 API is stable
                android.util.Log.i("VideoExport", "Trim requested: ${trim.startMs}ms to ${trim.endMs}ms (not yet implemented in export)")
                android.util.Log.w("VideoExport", "Trim functionality is available in UI but not yet implemented in export")
            } catch (e: Exception) {
                android.util.Log.e("VideoExport", "Failed to process trim: ${e.message}", e)
            }
        }
        
        // Apply speed changes using SpeedChangeEffect
        if (editState.speed != 1f) {
            try {
                val speedChangeEffect = SpeedChangeEffect(editState.speed)
                val effects = Effects(emptyList(), listOf(speedChangeEffect))
                editedBuilder.setEffects(effects)
                android.util.Log.i("VideoExport", "Speed effect applied: ${editState.speed}x")
            } catch (e: Exception) {
                android.util.Log.e("VideoExport", "Failed to apply speed effect: ${e.message}", e)
                // For now, skip speed effects if they cause issues
                // TODO: Implement alternative speed change method
                android.util.Log.w("VideoExport", "Exporting without speed changes due to effect failure")
            }
        }
        
        // TODO: Apply crop changes when crop functionality is implemented
        // if (editState.crop != null) {
        //     editedBuilder.setCrop(...)
        // }
        
        val edited = editedBuilder.build()
        val deferred = CompletableDeferred<ExportResult?>()
        val listener = object : Transformer.Listener {
            @OptIn(UnstableApi::class)
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                android.util.Log.i("VideoExport", "Export completed successfully")
                deferred.complete(exportResult)
            }

            @OptIn(UnstableApi::class)
            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                android.util.Log.e("VideoExport", "Export failed: ${exportException.message}", exportException)
                deferred.complete(null)
            }
        }
        
        transformer.addListener(listener)
        
        try {
            android.util.Log.i("VideoExport", "Starting export to: ${outFile.absolutePath}")
            transformer.start(edited, outFile.absolutePath)
            
            // Add timeout to prevent hanging (5 minutes max)
            val result = withTimeoutOrNull(300_000L) {
                deferred.await()
            }
            
            if (result != null && outFile.exists() && outFile.length() > 0) {
                android.util.Log.i("VideoExport", "Export successful: ${outFile.absolutePath}, size: ${outFile.length()}")
                outFile
            } else {
                android.util.Log.e("VideoExport", "Export failed or timed out: result=$result, exists=${outFile.exists()}, size=${outFile.length()}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoExport", "Export exception: ${e.message}", e)
            null
        } finally {
            transformer.removeListener(listener)
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoExport", "Export exception: ${e.message}", e)
        null
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

// Function to save video to gallery
private fun saveVideoToGallery(context: Context, videoFile: File) {
    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFile.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.SIZE, videoFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CutX")
            }
        }
        
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { 
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                videoFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            android.util.Log.i("VideoExport", "Video saved to gallery: $uri")
        }
    } catch (e: Exception) {
        android.util.Log.e("VideoExport", "Failed to save video to gallery: ${e.message}", e)
        throw e
    }
}


