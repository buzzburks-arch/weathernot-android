package com.weathernot.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.weathernot.app.AppEnvironment
import com.weathernot.kit.models.PlaceMatch
import com.weathernot.kit.networking.ApiError
import kotlinx.coroutines.delay

/** A place as the event keeps it. */
data class PickedPlace(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String,
)

/**
 * Type-ahead place search (the API's geocoder), with coordinates to type
 * instead -- for somewhere search can't find, or a server with address
 * lookup turned off (it answers 503).
 */
@Composable
fun PlaceSearch(picked: PickedPlace?, onPick: (PickedPlace) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceMatch>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var typingCoordinates by remember { mutableStateOf(false) }

    // Debounced: the query changes a character at a time.
    LaunchedEffect(query) {
        results = emptyList()
        message = null
        val trimmed = query.trim()
        if (trimmed.length < 3) return@LaunchedEffect
        delay(350)
        searching = true
        try {
            results = AppEnvironment.api.searchPlaces(trimmed)
            if (results.isEmpty()) message = "No places match. Try a town or street name, or enter coordinates."
        } catch (e: ApiError.Server) {
            if (e.statusCode == 503) {
                message = "Address search isn't available — enter the coordinates instead."
                typingCoordinates = true
            } else {
                message = e.userMessage("Search failed. Try again.")
            }
        } catch (e: Exception) {
            message = e.userMessage("Search failed. Try again.")
        } finally {
            searching = false
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search for the place") },
            placeholder = { Text("e.g. Liberty Park, Salt Lake City") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (searching) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { MutedText(it) }
        results.forEachIndexed { index, match ->
            if (index > 0) HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPick(PickedPlace(match.latitude, match.longitude, match.name.ifBlank { match.displayName }, match.address))
                        query = ""
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(match.name.ifBlank { match.displayName }, fontWeight = FontWeight.Medium)
                    if (match.context.isNotBlank()) MutedText(match.context)
                }
            }
        }
        if (typingCoordinates) {
            CoordinateFields(picked, onPick)
        } else {
            TextButton(onClick = { typingCoordinates = true }) { Text("Enter coordinates instead") }
        }
        picked?.let { place ->
            EventMap(place.latitude, place.longitude, place.name.ifBlank { "The event" }, height = 160.dp)
            if (place.address.isNotBlank()) MutedText(place.address)
        }
    }
}

@Composable
private fun CoordinateFields(picked: PickedPlace?, onPick: (PickedPlace) -> Unit) {
    var lat by remember { mutableStateOf(picked?.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(picked?.longitude?.toString() ?: "") }
    val latValue = lat.trim().toDoubleOrNull()
    val lonValue = lon.trim().toDoubleOrNull()
    val valid = latValue != null && lonValue != null && latValue in -90.0..90.0 && lonValue in -180.0..180.0
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            lat, { lat = it }, label = { Text("Latitude") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            lon, { lon = it }, label = { Text("Longitude") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
        )
    }
    TextButton(enabled = valid, onClick = { onPick(PickedPlace(latValue!!, lonValue!!, picked?.name.orEmpty(), "")) }) {
        Text("Use these coordinates")
    }
}
