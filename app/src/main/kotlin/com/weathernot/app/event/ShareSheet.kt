package com.weathernot.app.event

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weathernot.app.AppEnvironment
import com.weathernot.app.ui.ErrorBanner
import com.weathernot.app.ui.FieldError
import com.weathernot.app.ui.MutedText
import com.weathernot.kit.format.formatDay
import com.weathernot.kit.format.parseInstant
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Sharing the event's read-only status page: copy the link or hand it to
 * the share sheet; see and revoke links already out there; and leave a
 * note for whoever opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(eventName: String, vm: EventDetailViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var selected by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadSharing() }
    val links = vm.links
    // The newest link, until one's picked (or the picked one is revoked).
    LaunchedEffect(links) {
        if (links != null && links.none { it.token == selected }) selected = links.lastOrNull()?.token
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Share $eventName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            MutedText("Anyone with the link sees the event's current weather status — read-only, no sign-in.")
            vm.error?.let { ErrorBanner(it, onDismiss = { vm.error = null }) }

            val token = selected
            if (token != null) {
                val url = AppEnvironment.statusUrl(token)
                OutlinedTextField(value = url, onValueChange = {}, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(url))
                        copied = true
                    }) {
                        Icon(Icons.Outlined.ContentCopy, null)
                        Text(if (copied) "  Copied" else "  Copy link")
                    }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, eventName)
                            putExtra(Intent.EXTRA_TEXT, "Weather status for $eventName: $url")
                        }
                        context.startActivity(Intent.createChooser(send, "Share $eventName"))
                    }) {
                        Icon(Icons.Outlined.Share, null)
                        Text("  Share…")
                    }
                }
            } else if (links != null) {
                Button(onClick = vm::newLink) { Text("Make a share link") }
            } else {
                MutedText("Loading your links…")
            }

            StatusMessageEditor(vm)

            if (!links.isNullOrEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::newLink) {
                        Icon(Icons.Filled.Add, null)
                        Text(" New link")
                    }
                }
                links.forEachIndexed { index, link ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = link.token == selected, role = Role.RadioButton, onClick = {
                            selected = link.token
                            copied = false
                        }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = link.token == selected, onClick = null)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("Link ${index + 1}", fontWeight = if (link.token == selected) FontWeight.SemiBold else FontWeight.Normal)
                            MutedText(
                                "…${link.token.takeLast(6)}" + (link.createdAt?.let {
                                    " · made " + formatDay(parseInstant(it).atZone(ZoneId.systemDefault()).toLocalDate().toString(), withYear = true)
                                } ?: ""),
                            )
                        }
                        TextButton(onClick = { vm.revokeLink(link.token) }) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
                    }
                }
                MutedText(
                    "Revoking stops a link working straight away. Use separate links for different groups so you can revoke one without the others.",
                )
            }
        }
    }
}

/** The organizer's note on the shared page -- the plan people should know about. */
@Composable
private fun StatusMessageEditor(vm: EventDetailViewModel) {
    val saved = vm.statusMessage ?: return
    var draft by remember(saved) { mutableStateOf(saved) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 500) draft = it },
            label = { Text("Message on the shared page (optional)") },
            placeholder = { Text("e.g. Ceremony moves to the barn if it rains.") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(enabled = !saving && draft.trim() != saved, onClick = {
            saving = true
            error = null
            scope.launch {
                error = vm.saveStatusMessage(draft)
                saving = false
            }
        }) { Text(if (saving) "Saving…" else "Save message") }
        FieldError(error)
    }
}
