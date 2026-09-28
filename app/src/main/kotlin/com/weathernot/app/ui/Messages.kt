package com.weathernot.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/** A snackbar a view model asks for -- the web app's toasts, Undo included. */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    /** Runs when it goes away without the action (e.g. to carry out a removal Undo could still stop). */
    val onDismiss: (() -> Unit)? = null,
)

@Composable
fun ShowMessages(messages: Flow<UiMessage>, host: SnackbarHostState) {
    LaunchedEffect(messages, host) {
        coroutineScope {
            messages.collect { message ->
                // A newer message replaces the one showing, which counts as dismissing it.
                host.currentSnackbarData?.dismiss()
                launch {
                    val result = host.showSnackbar(
                        message = message.text,
                        actionLabel = message.actionLabel,
                        withDismissAction = message.actionLabel == null,
                        duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke() else message.onDismiss?.invoke()
                }
            }
        }
    }
}
