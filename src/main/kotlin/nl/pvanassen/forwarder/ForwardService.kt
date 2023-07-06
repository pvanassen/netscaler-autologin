package nl.pvanassen.forwarder

import nl.pvanassen.NetscalerAutologinProperties
import nl.pvanassen.TokenCache
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpClient.Redirect.NEVER
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers

@Service
class ForwardService(private val properties: NetscalerAutologinProperties, private val tokenCache: TokenCache) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(NEVER)
        .build()

    fun get(username: String, password: String, path: String): ResponseEntity<ByteArray> {
        val result = forwardGet(tokenCache.getCookie(username) ?: "", path)
        if (result.statusCode() != 302) {
            return ResponseEntity
                .status(result.statusCode())
                .headers(copyHeaders(result))
                .body(result.body())
        }
        tokenCache.refresh(username, password)
        return get(username, password, path)
    }

    private fun copyHeaders(result: HttpResponse<*>): HttpHeaders {
        val headers = HttpHeaders()
        result.headers().map().forEach {
            headers[it.key] = it.value
        }
        return headers
    }

    private fun forwardGet(cookie: String, path: String): HttpResponse<ByteArray> {
        return httpClient.send(
            HttpRequest
                .newBuilder(URI(properties.url + path))
                .GET()
                .header("Cookie", cookie)
                .build(), BodyHandlers.ofByteArray()
        )
    }

    internal fun keepAlive() {
        log.info("Keep alive")
        tokenCache.keys().forEach {
            log.info("Keep alive $it")
            try {
                val cookie = tokenCache.getCookieByToken(it)!!
                val status = forwardGet(cookie, properties.keepAlivePath).statusCode()
                if (status == 302) {
                    log.info("Keep alive $it is expired")
                    tokenCache.remove(it)
                } else {
                    log.info("Keep alive $it is still valid")
                }
            }
            catch (e: Exception) {
                log.warn("Error in keep-aline", e)
            }
        }
    }
}