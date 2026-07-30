package no.nav.tiltaksarrangor.api

import jakarta.servlet.http.HttpServletRequest
import no.nav.tiltaksarrangor.api.request.ForslagRequest
import no.nav.tiltaksarrangor.melding.MeldingProducer
import no.nav.tiltaksarrangor.melding.forslag.ForslagService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/internal/api")
class InternalApi(
    private val forslagService: ForslagService,
    private val meldingProducer: MeldingProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/publiser-forslag")
    fun republiserForslag(
        request: HttpServletRequest,
        @RequestBody body: ForslagRequest,
    ) {
        if (!isInternal(request)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
        log.info("Re-publiserer forslag ${body.forslagIder} dryrun: ${body.dryRun}")
        body.forslagIder.forEach { forslagId ->
            val forslag = forslagService.get(forslagId).getOrThrow()
            log.info("${if (body.dryRun) "dryrun: " else ""}Republiserer Forslag med id: $forslagId, status ${forslag.status}")
            if (body.status != null && forslag.status == body.status) {
                log.info("Republiserer ikke Forslag med id: $forslagId, status ${forslag.status}")
                return@forEach
            } else if (!body.dryRun) {
                meldingProducer.produce(forslag)
                log.info("Re-publiserte forslag $forslagId")
            }
        }
    }

    companion object {
        private fun isInternal(request: HttpServletRequest): Boolean = request.remoteAddr == "127.0.0.1"
    }
}
