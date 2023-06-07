package nl.pvanassen.netscaler

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.contains
import nl.pvanassen.NetscalerAutologinProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@Service
class NetscalerService(private val properties: NetscalerAutologinProperties) {
    companion object {
        private const val ACCEPT_VALUE =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"

        private const val USER_AGENT_VALUE =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"

        private const val ACCEPT_NAME = "Accept"

        private const val USER_AGENT_NAME = "User-Agent"
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val cookieManager = CookieManager()

    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .cookieHandler(cookieManager)
        .build()

    private val objectMapper = XmlMapper()

    init {
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    }

    fun login(username: String, password: String): String {
        log.info("Performing a login")

        log.info("Getting initial cookies")
        val authUrl = initialPageLoadForCookiesAndGetAuthUrl()

        log.info("Fetching configuration")
        val requirementsURI = fetchConfigurationAndExtractRequirementsURI(authUrl)

        log.info("Fetching postback data")
        val postbackData = fetchPostbackDataForLogin(requirementsURI, authUrl)

        log.info("Logging in, waiting for phone verification")
        val redirectURI = loginAndGetRedirectURI(username, password, postbackData)

        log.info("Loading final cookies")
        followRedirectToSetCookies(redirectURI)

        log.info("Done, you are now logged in!")
        return cookieManager.cookieStore.cookies.joinToString(separator = "; ") { it.toString() }
    }

    private fun followRedirectToSetCookies(redirectURI: String) {
        val redirect = HttpRequest.newBuilder(URI(redirectURI))
            .GET()
            .header(ACCEPT_NAME, ACCEPT_VALUE)
            .header(USER_AGENT_NAME, USER_AGENT_VALUE)
            .build()

        val redirectResponse = httpClient.send(redirect, HttpResponse.BodyHandlers.ofByteArray())
        if (redirectResponse.statusCode() != 200) {
            throw RuntimeException("Status ended up not 200")
        }
    }

    private fun loginAndGetRedirectURI(username: String, password: String, postbackData: PostbackData): String {
        val encodedUser = URLEncoder.encode(username, Charsets.UTF_8)
        val encodedPass = URLEncoder.encode(password, Charsets.UTF_8)
        val login = HttpRequest.newBuilder(URI(postbackData.uri))
            .POST(HttpRequest.BodyPublishers.ofString("login=$encodedUser&passwd=$encodedPass&savecredentials=false&nsg-x1-logon-button=Log+On&StateContext=${postbackData.stateContext}"))
            .header(ACCEPT_NAME, ACCEPT_VALUE)
            .header(USER_AGENT_NAME, USER_AGENT_VALUE)
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()

        val loginResponse = httpClient.send(login, HttpResponse.BodyHandlers.ofByteArray())
        val loginTree = objectMapper.readTree(loginResponse.body())
        if (!loginTree.contains("RedirectURL")) {
            throw RuntimeException("Error while logging in")
        }
        return loginTree["RedirectURL"].asText()
    }

    private fun fetchPostbackDataForLogin(requirementsURI: String, authUrl: String): PostbackData {
        val requirements = HttpRequest.newBuilder(URI(requirementsURI))
            .POST(HttpRequest.BodyPublishers.noBody())
            .header(ACCEPT_NAME, ACCEPT_VALUE)
            .header(USER_AGENT_NAME, USER_AGENT_VALUE)
            .build()

        val requirementsResponse = httpClient.send(requirements, HttpResponse.BodyHandlers.ofByteArray())
        val requirementsTree = objectMapper.readTree(requirementsResponse.body())
        return PostbackData(
            requirementsTree["StateContext"].asText(),
            authUrl + requirementsTree["AuthenticationRequirements"]["PostBack"].asText()
        )
    }

    private fun fetchConfigurationAndExtractRequirementsURI(authUrl: String): String {
        val configuration = HttpRequest.newBuilder(URI("$authUrl/cgi/GetAuthMethods"))
            .POST(HttpRequest.BodyPublishers.noBody())
            .header(ACCEPT_NAME, ACCEPT_VALUE)
            .header(USER_AGENT_NAME, USER_AGENT_VALUE)
            .build()

        val configurationResponse = httpClient.send(configuration, HttpResponse.BodyHandlers.ofByteArray())
        val configurationTree = objectMapper.readTree(configurationResponse.body())
        return authUrl + configurationTree["method"]["url"].asText()
    }

    private fun initialPageLoadForCookiesAndGetAuthUrl(): String {
        val initial = HttpRequest.newBuilder(URI(properties.url))
            .GET()
            .header(ACCEPT_NAME, ACCEPT_VALUE)
            .header(USER_AGENT_NAME, USER_AGENT_VALUE)
            .build()

        val initialResponse = httpClient.send(initial, HttpResponse.BodyHandlers.ofString())
        if (!initialResponse.uri().toString().endsWith("/logon/LogonPoint/tmindex.html", ignoreCase = true)) {
            throw RuntimeException("Not on login page")
        }
        return initialResponse.uri().toString().replace(initialResponse.uri().path, "")
    }

}