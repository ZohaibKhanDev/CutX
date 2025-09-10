package com.example.cutx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cutx.data.HomeData
import com.example.cutx.data.Project
import com.example.cutx.ui.components.BannerSlider
import com.example.cutx.ui.components.MyProjects
import com.example.cutx.ui.components.NewProjectButton

@Composable
fun HomeScreen(
    onNewProjectClick: () -> Unit = {},
    onProjectClick: (Project) -> Unit = {},
    onSeeAllProjectsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)) // Dark background
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Banner Slider with status bar
            item {
                BannerSlider(
                    banners = HomeData.bannerItems,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // My Projects Section
            item {
                MyProjects(
                    projects = HomeData.myProjects,
                    onProjectClick = onProjectClick,
                    onSeeAllClick = onSeeAllProjectsClick,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            
            // New Project Button
            item {
                NewProjectButton(
                    onClick = onNewProjectClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                )
            }
        }
    }
}
