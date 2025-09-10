package com.example.cutx

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.cutx.data.Project
import com.example.cutx.ui.screens.HomeScreen
import com.example.cutx.ui.screens.VideoPickerScreen
import com.example.cutx.ui.screens.VideoEditScreen
import com.example.cutx.ui.theme.CutXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CutXTheme {
                VideoEditorApp()
            }
        }
    }
}

@Composable
fun VideoEditorApp() {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

    when (currentScreen) {
        "home" -> {
            HomeScreen(
                onNewProjectClick = {
                    currentScreen = "picker"
                },
                onProjectClick = { project ->
                    // Handle project click - could open project details or editor
                },
                onSeeAllProjectsClick = {
                    // Handle see all projects click
                }
            )
        }
        "picker" -> {
            VideoPickerScreen(
                onBackClick = {
                    currentScreen = "home"
                },
                onVideoSelected = { uri ->
                    selectedVideoUri = uri
                    currentScreen = "editor"
                }
            )
        }
        "editor" -> {
            val uri = selectedVideoUri
            if (uri != null) {
                VideoEditScreen(
                    videoUri = uri,
                    onBack = { currentScreen = "home" },
                    onShare = { /* TODO: implement share/export */ }
                )
            } else {
                currentScreen = "home"
            }
        }
    }
}
