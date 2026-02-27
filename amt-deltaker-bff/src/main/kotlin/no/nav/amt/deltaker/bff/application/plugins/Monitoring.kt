package no.nav.amt.deltaker.bff.application.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry

fun Application.configureMonitoring() {
    install(CallLogging) {
        disableDefaultColors()
        filter { call -> call.request.path().startsWith("/") && !call.request.path().startsWith("/internal") }
        callIdMdc("call-id")
    }
    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }

    val appMicrometerRegistry = PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        PrometheusRegistry.defaultRegistry,
        Clock.SYSTEM,
    )

    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
        // ...
    }
    routing {
        get("/internal/prometheus") {
            call.respond(appMicrometerRegistry.scrape())
        }
    }
}
