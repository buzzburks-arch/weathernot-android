package com.weathernot.app

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.weathernot.app.create.NewEventScreen
import com.weathernot.app.event.EventDetailScreen
import com.weathernot.app.events.EventsListScreen
import com.weathernot.app.profile.ProfileScreen
import com.weathernot.app.series.SeriesScreen

object Routes {
    const val EVENTS = "events"
    const val NEW_EVENT = "new"
    const val PROFILE = "profile"
    fun event(eventId: String, criterionId: String? = null) = "event/$eventId" + (criterionId?.let { "?criterion=$it" } ?: "")
    fun series(seriesId: String) = "series/$seriesId"
}

@Composable
fun AppNavHost(session: SessionViewModel, newIntent: Intent?, onIntentHandled: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(newIntent) {
        if (newIntent != null) {
            nav.handleDeepLink(newIntent)
            onIntentHandled()
        }
    }

    NavHost(navController = nav, startDestination = Routes.EVENTS) {
        composable(Routes.EVENTS) {
            EventsListScreen(
                session = session,
                onOpenEvent = { nav.navigate(Routes.event(it)) },
                onOpenSeries = { nav.navigate(Routes.series(it)) },
                onNewEvent = { nav.navigate(Routes.NEW_EVENT) },
                onProfile = { nav.navigate(Routes.PROFILE) },
            )
        }
        composable(
            route = "event/{eventId}?criterion={criterionId}",
            arguments = listOf(
                navArgument("eventId") { type = NavType.StringType },
                navArgument("criterionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            // The links the backend puts in alerts (notify/message.py).
            deepLinks = listOf(
                navDeepLink { uriPattern = "weathernot://event/{eventId}" },
                navDeepLink { uriPattern = "weathernot://event/{eventId}/criterion/{criterionId}" },
            ),
        ) { entry ->
            val eventId = entry.arguments?.getString("eventId").orEmpty()
            EventDetailScreen(
                session = session,
                eventId = eventId,
                linkedCriterionId = entry.arguments?.getString("criterionId"),
                onBack = { if (!nav.popBackStack()) nav.navigate(Routes.EVENTS) },
                onOpenEvent = { id -> nav.navigate(Routes.event(id)) { popUpTo(Routes.EVENTS) } },
                onOpenSeries = { nav.navigate(Routes.series(it)) },
                onDeleted = { nav.popBackStack(Routes.EVENTS, inclusive = false) },
            )
        }
        composable(Routes.NEW_EVENT) {
            NewEventScreen(
                session = session,
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate(Routes.event(id)) { popUpTo(Routes.EVENTS) } },
            )
        }
        composable(
            route = "series/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
        ) { entry ->
            SeriesScreen(
                seriesId = entry.arguments?.getString("seriesId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenEvent = { nav.navigate(Routes.event(it)) },
                onDeleted = { nav.popBackStack(Routes.EVENTS, inclusive = false) },
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(session = session, onBack = { nav.popBackStack() })
        }
    }
}
