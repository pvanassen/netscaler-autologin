package nl.pvanassen

import jakarta.servlet.http.HttpServletRequest
import nl.pvanassen.forwarder.ForwardService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
internal class RepositoryController(private val forwardService: ForwardService) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @GetMapping("/**")
    fun getResource(httpServletRequest: HttpServletRequest): ResponseEntity<ByteArray> {
        log.info("Handling ${httpServletRequest.requestURI}")
        val auth = try {
            getAuth(httpServletRequest)
        }
        catch (unauthorised: UnauthorisedException) {
            log.warn("UnauthorisedException: ${unauthorised.message}")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header("WWW-Authenticate", "Basic realm=\"Netscaler\"").build()
        }
        return forwardService.get(auth.first, auth.second, httpServletRequest.requestURI)
    }

    private fun getAuth(httpServletRequest: HttpServletRequest): Pair<String, String> {
        val authorization: String = httpServletRequest.getHeader("Authorization") ?: throw UnauthorisedException("Auth header missing")
        if (!authorization.lowercase(Locale.getDefault()).startsWith("basic")) {
            throw UnauthorisedException("Auth not basic")
        }

        val base64Credentials = authorization.substring("Basic".length).trim { it <= ' ' }
        val credentials = String(Base64.getDecoder().decode(base64Credentials)).split(':')
        return Pair(credentials[0], credentials[1])
    }

    internal class UnauthorisedException(message: String): RuntimeException(message)
}