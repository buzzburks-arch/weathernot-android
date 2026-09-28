// Pure Kotlin/JVM module: API models, the API client, and the web app's
// formatting rules (units, criterion wording, event times). Builds and
// tests with just the JVM toolchain, no Android SDK. The ktor engine is
// left to the consumer: okhttp in :app, MockEngine in the tests here.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}
