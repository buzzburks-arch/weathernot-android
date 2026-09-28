package com.weathernot.kit

import com.weathernot.kit.models.CriterionInput
import com.weathernot.kit.models.GridVariable
import com.weathernot.kit.models.Operator
import com.weathernot.kit.networking.ApiClient
import com.weathernot.kit.networking.ApiError
import com.weathernot.kit.networking.InMemoryTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApiClientTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        token: String? = "session",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<ApiClient, InMemoryTokenStore> {
        val store = InMemoryTokenStore(token)
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return ApiClient("http://api", store, HttpClient(engine)) to store
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun bodyOf(request: HttpRequestData) = Json.parseToJsonElement((request.body as TextContent).text).jsonObject

    @Test
    fun signInRequestsALinkThenVerifiesItAndKeepsTheSession() = runBlocking {
        val (api, store) = client(token = null) { request ->
            when (request.url.encodedPath) {
                "/auth/request-link" -> json("""{"token":"magic"}""")
                "/auth/verify" -> json("""{"session_token":"sess-1"}""")
                else -> error("unexpected ${request.url}")
            }
        }
        api.signIn("jamie@example.com")
        assertEquals("sess-1", store.token())
        assertEquals("jamie@example.com", bodyOf(requests[0])["email"]!!.jsonPrimitive.content)
        assertEquals("magic", bodyOf(requests[1])["token"]!!.jsonPrimitive.content)
        assertNull(requests[0].headers[HttpHeaders.Authorization])
    }

    @Test
    fun sendsTheBearerTokenAndEncodesQueries() = runBlocking {
        val (api, _) = client { json("[]") }
        api.searchPlaces("Liberty Park & more")
        val request = requests.single()
        assertEquals("Bearer session", request.headers[HttpHeaders.Authorization])
        assertEquals("Liberty Park & more", request.url.parameters["q"])
    }

    @Test
    fun aCriterionEditSendsExplicitNullsToClearFields() = runBlocking {
        val (api, _) = client { json("{}") }
        api.updateCriterion("e1", "c1", CriterionInput(GridVariable.WIND_GUST, Operator.GTE, 8.9))
        val body = bodyOf(requests.single())
        assertEquals(HttpMethod.Patch, requests.single().method)
        assertEquals(JsonNull, body["watch_threshold"])
        assertEquals(JsonNull, body["min_duration_hours"])
        assertEquals(JsonNull, body["also"])
        assertEquals(JsonNull, body["segment_id"])
        assertEquals("gte", body["operator"]!!.jsonPrimitive.content)
    }

    @Test
    fun aNewCriterionIsSnakeCaseAndOmitsWhatsUnset() = runBlocking {
        val (api, _) = client {
            json("""{"criterion_id":"c","variable":"wind_gust","operator":"gte","threshold":8.9,"watch_threshold":4.5}""")
        }
        val created = api.addCriterion("e1", CriterionInput(GridVariable.WIND_GUST, Operator.GTE, 8.9, watchThreshold = 4.5, suggested = true))
        val body = bodyOf(requests.single())
        assertEquals("wind_gust", body["variable"]!!.jsonPrimitive.content)
        assertEquals("4.5", body["watch_threshold"]!!.jsonPrimitive.content)
        assertEquals("true", body["suggested"]!!.jsonPrimitive.content)
        assertTrue("also" !in body)
        assertEquals(4.5, created.watchThreshold)
    }

    @Test
    fun clearingTheDecisionDeadlineSendsNull() = runBlocking {
        val (api, _) = client { json("""{"decide_by":null,"sent_at":null}""") }
        api.setDecisionDeadline("e1", null)
        assertEquals(JsonNull, bodyOf(requests.single())["decide_by"])
    }

    @Test
    fun a401ForgetsTheSession() = runBlocking {
        val (api, store) = client { json("""{"detail":"invalid session"}""", HttpStatusCode.Unauthorized) }
        assertThrows<ApiError.NotAuthenticated> { runBlocking { api.listEvents() } }
        assertNull(store.token())
    }

    @Test
    fun serverErrorsCarryTheReadableDetailOnly() = runBlocking {
        val (api, _) = client { request ->
            if (request.url.encodedPath == "/events") {
                json("""{"detail":[{"loc":["body","name"],"msg":"field required"}]}""", HttpStatusCode.UnprocessableEntity)
            } else {
                json("""{"detail":"address lookup isn't set up"}""", HttpStatusCode.ServiceUnavailable)
            }
        }
        val validation = assertThrows<ApiError.Server> { runBlocking { api.listEvents() } }
        assertEquals(422, validation.statusCode)
        assertNull(validation.detail)
        val unavailable = assertThrows<ApiError.Server> { runBlocking { api.searchPlaces("park") } }
        assertEquals("address lookup isn't set up", unavailable.detail)
    }

    @Test
    fun reverseGeocodeCanBeNull() = runBlocking {
        val (api, _) = client { json("null") }
        assertNull(api.placeAt(1.0, 2.0))
    }
}
