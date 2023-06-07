package nl.pvanassen

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import nl.pvanassen.netscaler.NetscalerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.Duration.ofHours


@Service
class TokenCache(private val netscalerService: NetscalerService) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val config = RateLimiterConfig.custom()
        .limitRefreshPeriod(Duration.ofSeconds(30))
        .limitForPeriod(1)
        .timeoutDuration(Duration.ofSeconds(30))
        .build()

    private val rateLimiterRegistry = RateLimiterRegistry.of(config)

    private val rateLimiter = rateLimiterRegistry
        .rateLimiter("login", config)

    private val restrictedCall = RateLimiter
        .decorateFunction<Pair<String, String>, String>(rateLimiter) {
            netscalerService.login(it.first, it.second)
        }

    private val cache = Caffeine.newBuilder()
        .expireAfterAccess(ofHours(12))
        .build<String, String>()

    fun getCookie(username: String): String? {
        return cache.getIfPresent(username.sha256())
    }

    fun getCookieByToken(token: String): String? {
        return cache.getIfPresent(token)
    }

    fun refresh(username: String, password: String) {
        log.info("Refreshing cookie")
        cache.put(username.sha256(), restrictedCall.apply(Pair(username, password)))
    }

    fun keys() = cache.asMap().keys.toList()

    private fun String.sha256(): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(this.toByteArray())
            .fold("") { str, it -> str + "%02x".format(it) }
    }

    fun remove(key: String) {
        cache.invalidate(key)
    }
}