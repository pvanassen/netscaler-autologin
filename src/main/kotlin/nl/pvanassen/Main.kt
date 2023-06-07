package nl.pvanassen

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@SpringBootApplication
open class Main {

    @Bean
    open fun getProps(environment: Environment) = NetscalerAutologinProperties(
        environment.getProperty("NETSCALER_AUTOLOGIN_URL") ?: throw RuntimeException("NETSCALER_AUTOLOGIN_URL not set")
    )
}

fun main(vararg args: String) {
    runApplication<Main>(*args)
}
