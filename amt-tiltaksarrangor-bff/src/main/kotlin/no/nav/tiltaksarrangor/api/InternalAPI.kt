package no.nav.tiltaksarrangor.api

import jakarta.servlet.http.HttpServletRequest
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.security.token.support.core.api.Unprotected
import no.nav.tiltaksarrangor.melding.MeldingProducer
import no.nav.tiltaksarrangor.melding.forslag.ForslagService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Unprotected
@RestController
@RequestMapping("/internal/api")
class InternalAPI(
    private val forslagService: ForslagService,
    private val meldingProducer: MeldingProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class ForslagRequest(
        val forslagIder: List<UUID>,
        val dryRun: Boolean = true,
        val status: Forslag.Status? = null,
    )

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
