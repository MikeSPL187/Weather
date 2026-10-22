package app.aura.core.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Minimal HTTP GET seam so forecast clients can be tested with recorded responses. */
fun interface HttpFetcher {
    suspend fun get(url: String): String
}

class UrlConnectionFetcher(private val timeoutMs: Int = 10_000) : HttpFetcher {
    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", "Aura/0.2 (Android)")
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
