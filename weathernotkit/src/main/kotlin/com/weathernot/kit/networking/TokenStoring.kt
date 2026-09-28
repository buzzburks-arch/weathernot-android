package com.weathernot.kit.networking

/**
 * Where [ApiClient] keeps the session token between launches. The app's
 * implementation is Keystore-backed (`SecureTokenStore`); tests use
 * [InMemoryTokenStore].
 */
interface TokenStoring {
    fun token(): String?
    fun save(token: String)
    fun clear()
}

class InMemoryTokenStore(initial: String? = null) : TokenStoring {
    @Volatile private var stored: String? = initial

    override fun token(): String? = stored
    override fun save(token: String) {
        stored = token
    }
    override fun clear() {
        stored = null
    }
}
