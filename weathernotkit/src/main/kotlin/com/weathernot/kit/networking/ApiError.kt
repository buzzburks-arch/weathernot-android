package com.weathernot.kit.networking

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

sealed class ApiError(message: String) : Exception(message) {
    /** No session token, or the server turned it down (it's been cleared). */
    data object NotAuthenticated : ApiError("Not signed in")

    /** A non-2xx response; [detail] is the API's readable reason, when it gave one. */
    data class Server(val statusCode: Int, val detail: String?) : ApiError(detail ?: "HTTP $statusCode")

    data class Decoding(val reason: String) : ApiError("Unexpected response: $reason")

    data class Transport(val reason: String) : ApiError(reason)
}

/**
 * FastAPI's error body. `detail` is a string for an `HTTPException` and a
 * list of field errors for a validation failure -- only the string is
 * readable enough to show (same rule as the web app's `client.ts`).
 */
@Serializable
internal data class ServerErrorBody(val detail: JsonElement? = null) {
    val readable: String?
        get() = (detail as? JsonPrimitive)?.takeIf { it.isString }?.content
}
