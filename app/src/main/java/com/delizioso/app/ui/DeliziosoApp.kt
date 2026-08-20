package com.delizioso.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.ui.navigation.ClayDock
import com.delizioso.app.ui.navigation.Routes
import com.delizioso.app.ui.screens.create.CreateScreen
import com.delizioso.app.ui.screens.cook.CookCompleteScreen
import com.delizioso.app.ui.screens.cook.CookModeScreen
import com.delizioso.app.ui.screens.detail.RecipeDetailScreen
import com.delizioso.app.ui.screens.edit.EditRecipeScreen
import com.delizioso.app.ui.screens.grocery.GroceryScreen
import com.delizioso.app.ui.screens.import.ImportPreviewScreen
import com.delizioso.app.ui.screens.import.ImportScreen
import com.delizioso.app.ui.screens.import.ImportViewModel
import com.delizioso.app.ui.screens.library.LibraryScreen
import com.delizioso.app.ui.screens.planner.PlannerScreen
import com.delizioso.app.ui.screens.profile.ProfileScreen
import com.delizioso.app.ui.screens.profile.RecipeSourcesScreen
import com.delizioso.app.ui.screens.search.OnlineSearchScreen
import kotlinx.coroutines.launch

@Composable
fun DeliziosoApp(
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    // A link shared into the app always lands on the Import tab.
    LaunchedEffect(sharedLink) {
        if (sharedLink != null && currentRoute != Routes.IMPORT) {
            navController.navigate(Routes.IMPORT) {
                popUpTo(Routes.LIBRARY) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentRoute in Routes.tabRoutes) {
                ClayDock(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.LIBRARY) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onRecipeClick = { id -> navController.navigate(Routes.recipeDetail(id)) },
                    onCreateClick = { navController.navigate(Routes.CREATE) },
                    onProfileClick = { navController.navigate(Routes.PROFILE) },
                    onSearchOnline = { navController.navigate(Routes.IMPORT_SEARCH) },
                    onImportClick = { navController.navigate(Routes.IMPORT) },
                )
            }
            composable(Routes.PLANNER) {
                PlannerScreen(
                    onRecipeClick = { id -> navController.navigate(Routes.recipeDetail(id)) },
                    onOpenGrocery = { navController.navigate(Routes.GROCERY) },
                    onProfileClick = { navController.navigate(Routes.PROFILE) },
                )
            }
            composable(Routes.GROCERY) {
                GroceryScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.COOK,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: return@composable
                CookModeScreen(
                    recipeId = recipeId,
                    onBack = { navController.popBackStack() },
                    onFinished = {
                        navController.navigate(Routes.cookComplete(recipeId)) {
                            popUpTo(Routes.COOK) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.COOK_COMPLETE,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: return@composable
                CookCompleteScreen(
                    recipeId = recipeId,
                    onBackToLibrary = {
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(Routes.LIBRARY) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.IMPORT) {
                ImportScreen(
                    onPreview = { navController.navigate(Routes.IMPORT_PREVIEW) },
                    onRecipeClick = { id -> navController.navigate(Routes.recipeDetail(id)) },
                    onProfileClick = { navController.navigate(Routes.PROFILE) },
                    onSearchOnline = { navController.navigate(Routes.IMPORT_SEARCH) },
                    sharedLink = sharedLink,
                    onSharedLinkHandled = onSharedLinkHandled,
                )
            }
            composable(Routes.IMPORT_PREVIEW) {
                val parentEntry = navController.getBackStackEntry(Routes.IMPORT)
                val importViewModel: ImportViewModel =
                    viewModel(viewModelStoreOwner = parentEntry, factory = ImportViewModel.Factory)
                ImportPreviewScreen(
                    viewModel = importViewModel,
                    // Ready is also the Import screen's "open the preview" trigger,
                    // so leaving it set would bounce straight back in here.
                    onBack = {
                        importViewModel.discard()
                        navController.popBackStack()
                    },
                    onSaved = { id ->
                        navController.navigate(Routes.recipeDetail(id)) {
                            popUpTo(Routes.IMPORT)
                        }
                    },
                )
            }
            composable(Routes.IMPORT_SEARCH) {
                // Same view model instance the preview reads, or the preview
                // would open on an empty import.
                val parentEntry = navController.getBackStackEntry(Routes.IMPORT)
                OnlineSearchScreen(
                    importViewModel = viewModel(viewModelStoreOwner = parentEntry, factory = ImportViewModel.Factory),
                    onBack = { navController.popBackStack() },
                    onPreview = { navController.navigate(Routes.IMPORT_PREVIEW) },
                )
            }
            composable(
                route = Routes.RECIPE_EDIT,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: return@composable
                EditRecipeScreen(
                    recipeId = recipeId,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenRecipeSources = { navController.navigate(Routes.RECIPE_SOURCES) })
            }
            composable(Routes.RECIPE_SOURCES) {
                RecipeSourcesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CREATE) {
                CreateScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.navigate(Routes.recipeDetail(id)) {
                            popUpTo(Routes.CREATE) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.RECIPE_DETAIL,
                arguments = listOf(navArgument("recipeId") { type = NavType.LongType }),
            ) { entry ->
                val recipeId = entry.arguments?.getLong("recipeId") ?: return@composable
                RecipeDetailScreen(
                    recipeId = recipeId,
                    onBack = { navController.popBackStack() },
                    onStartCooking = { navController.navigate(Routes.cook(recipeId)) },
                    onEdit = { navController.navigate(Routes.recipeEdit(recipeId)) },
                )
            }
        }
    }
}
