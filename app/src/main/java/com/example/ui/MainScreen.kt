package com.example.ui

import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.explorer.ExplorerScreen
import com.example.ui.explorer.ExplorerViewModel
import com.example.ui.favorites.FavoritesScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.reader.ComicReaderScreen
import com.example.ui.reader.ReaderViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

fun encodeNavUri(rawUri: String): String {
    return Base64.encodeToString(
        rawUri.toByteArray(StandardCharsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )
}

fun decodeNavUri(encodedUri: String): String {
    return try {
        String(
            Base64.decode(encodedUri, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            StandardCharsets.UTF_8
        )
    } catch (_: Exception) {
        URLDecoder.decode(encodedUri, StandardCharsets.UTF_8.name())
    }
}

sealed class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Library : BottomNavItem("library", "Library", Icons.Default.Book)
    object Explorer : BottomNavItem("explorer", "Explorer", Icons.Default.Folder)
    object Favorites : BottomNavItem("favorites", "Favorites", Icons.Default.Star)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val libraryViewModel: LibraryViewModel = viewModel()
    val explorerViewModel: ExplorerViewModel = viewModel()

    val bottomNavItems = listOf(
        BottomNavItem.Library,
        BottomNavItem.Explorer,
        BottomNavItem.Favorites
    )

    val isReaderActive = currentRoute?.startsWith("reader/") == true

    Scaffold(
        bottomBar = {
            if (!isReaderActive) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            modifier = Modifier.testTag("nav_item_${item.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Library.route) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onComicClick = { rawUri ->
                        navController.navigate("reader/${encodeNavUri(rawUri)}")
                    },
                    onNavigateToExplorer = {
                        navController.navigate(BottomNavItem.Explorer.route)
                    }
                )
            }

            composable(BottomNavItem.Explorer.route) {
                ExplorerScreen(
                    viewModel = explorerViewModel,
                    onComicSelected = { rawUri ->
                        navController.navigate("reader/${encodeNavUri(rawUri)}")
                    }
                )
            }

            composable(BottomNavItem.Favorites.route) {
                FavoritesScreen(
                    viewModel = libraryViewModel,
                    onComicClick = { rawUri ->
                        navController.navigate("reader/${encodeNavUri(rawUri)}")
                    }
                )
            }

            composable(
                route = "reader/{encodedUri}",
                arguments = listOf(navArgument("encodedUri") { type = NavType.StringType })
            ) { backStackEntry ->
                val encodedUri = backStackEntry.arguments?.getString("encodedUri") ?: ""
                val comicUri = decodeNavUri(encodedUri)

                val context = LocalContext.current
                val readerViewModel = remember(comicUri) {
                    ReaderViewModel(context.applicationContext as android.app.Application, comicUri)
                }

                ComicReaderScreen(
                    viewModel = readerViewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
