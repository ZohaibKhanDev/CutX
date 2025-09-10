package com.example.cutx.data

import androidx.compose.ui.graphics.vector.ImageVector

data class BannerItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val isActive: Boolean = false
)

data class Project(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val duration: String,
    val date: String,
    val isActive: Boolean = false
)

// Sample data matching the exact UI from the image
object HomeData {
    val bannerItems = listOf(
        BannerItem(
            id = "1",
            title = "Pro Editor",
            imageUrl = "https://plus.unsplash.com/premium_photo-1682093312368-62186980fd6a?q=80&w=1171&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D",
            isActive = false
        ),
        BannerItem(
            id = "2",
            title = "Pro Editor",
            imageUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=400&h=300&fit=crop",
            isActive = false
        ),
        BannerItem(
            id = "3",
            title = "Pro Editor",
            imageUrl = "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=400&h=300&fit=crop",
            isActive = false
        ),
        BannerItem(
            id = "4",
            title = "Pro Editor",
            imageUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=400&h=300&fit=crop",
            isActive = false
        ),
        BannerItem(
            id = "5",
            title = "Pro Editor",
            imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&h=300&fit=crop",
            isActive = false
        ),
        BannerItem(
            id = "6",
            title = "Pro Editor",
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&h=300&fit=crop",
            isActive = false
        ),
        BannerItem(
            id = "7",
            title = "Pro Editor",
            imageUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&h=300&fit=crop",
            isActive = false
        ),
        BannerItem(
            id = "8",
            title = "Pro Editor",
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400&h=300&fit=crop",
            isActive = true
        )
    )

    val myProjects = listOf(
        Project(
            id = "1",
            title = "24004 May 08, 2026",
            thumbnailUrl = "https://images.unsplash.com/photo-1494790108755-2616b612b786?w=200&h=200&fit=crop",
            duration = "00:26",
            date = "24004 May 08, 2026",
            isActive = false
        ),
        Project(
            id = "2",
            title = "24003 April 27, 2026",
            thumbnailUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=200&h=200&fit=crop",
            duration = "00:33",
            date = "24003 April 27, 2026",
            isActive = false
        ),
        Project(
            id = "3",
            title = "23088 March 19, 2026",
            thumbnailUrl = "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=200&h=200&fit=crop",
            duration = "01:41",
            date = "23088 March 19, 2026",
            isActive = false
        ),
        Project(
            id = "4",
            title = "23087 March 15, 2026",
            thumbnailUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=200&h=200&fit=crop",
            duration = "02:15",
            date = "23087 March 15, 2026",
            isActive = false
        ),
        Project(
            id = "5",
            title = "23086 March 10, 2026",
            thumbnailUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&h=200&fit=crop",
            duration = "01:58",
            date = "23086 March 10, 2026",
            isActive = false
        )
    )
}
