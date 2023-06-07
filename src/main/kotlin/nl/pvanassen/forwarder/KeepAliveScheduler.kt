package nl.pvanassen.forwarder

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors.newScheduledThreadPool
import java.util.concurrent.TimeUnit

@Configuration
internal open class KeepAliveScheduler(private val forwardService: ForwardService) {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("Initialising keep alive schedule")
        newScheduledThreadPool(1).scheduleAtFixedRate(forwardService::keepAlive, 0, 1, TimeUnit.MINUTES)
    }

}