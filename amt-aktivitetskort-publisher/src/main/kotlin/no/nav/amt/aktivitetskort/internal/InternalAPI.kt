package no.nav.amt.aktivitetskort.internal

import jakarta.servlet.http.HttpServletRequest
import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.service.AktivitetskortService
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.security.token.support.core.api.Unprotected
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Suppress("SpringMvcPathVariableDeclarationInspection")
@RestController
@RequestMapping("/internal")
class InternalAPI(
    private val aktivitetskortService: AktivitetskortService,
    private val aktivitetskortProducer: AktivitetskortProducer,
    private val deltakerRepository: DeltakerRepository,
) {
    // Regenererer aktivitetskort på samme deltaker
    @Unprotected
    @GetMapping("/publiser/{deltakerId}")
    fun publiserAktivitetskortForDeltaker(
        servlet: HttpServletRequest,
        @PathVariable("deltakerId") deltakerId: UUID,
    ) = if (isInternal(servlet)) {
        val aktivitetskort = aktivitetskortService.lagAktivitetskort(deltakerId)

        aktivitetskortProducer.send(aktivitetskort)
        log.info("Publiserte aktivitetskort for deltaker med id $deltakerId")
    } else {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    @Unprotected
    @GetMapping("/opprett-nye-kort")
    fun opprettAktivitetskortForDeltaker(
        servlet: HttpServletRequest,
        @RequestBody body: DeltakereBody,
    ) = if (isInternal(servlet)) {
        // Skal kun brukes i spesielle tilfeller hvor vi vet at det gamle kortet hører til en tidligere oppfølgingsperiode
        // og det ikke er opprettet nytt kort fordi vi tidligere ikke sjekket oppfølgingsperiode

        body.deltakere.forEach { deltakerId ->
            val deltaker = deltakerRepository.get(deltakerId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
            val sisteMelding = aktivitetskortService.getSisteMeldingForDeltaker(deltakerId)
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Denne deltakelsen har ingen tidligere meldinger og skal opprettes",
                )
            if (sisteMelding.oppfolgingperiode != null) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Siste melding for deltaker $deltakerId har oppfølgingsperiode ${sisteMelding.oppfolgingperiode}." +
                        "Endepunktet skal kun brukes for meldinger uten info om oppfølgingsperiode",
                )
            }
            var nyAktivitetskortId = UUID.randomUUID()
            if (deltaker.kilde == Kilde.ARENA) {
                val aktivitetskortIdFraDab = aktivitetskortService.hentAktivitetskortIdForArenaDeltaker(deltakerId)
                if (aktivitetskortIdFraDab != sisteMelding.id) {
                    // Hvis vi får ny id fra dab så har de allerede laget et kort i en ny periode
                    nyAktivitetskortId = aktivitetskortIdFraDab
                }
            }

            log.info(
                "Siste melding for deltaker med id $deltakerId," +
                    " er ${sisteMelding.id}. Oppretter ny melding med id $nyAktivitetskortId Kilde=${deltaker.kilde}",
            )

            val melding = aktivitetskortService.opprettMelding(deltaker = deltaker, nyAktivitetskortId)
            aktivitetskortProducer.send(melding.aktivitetskort)

            log.info("Publiserte nytt aktivitetskort ${melding.id} for deltaker med id $deltakerId")
        }
    } else {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    @Unprotected
    @GetMapping("/resend/{deltakerId}")
    fun resendSistSendteMelding(
        servlet: HttpServletRequest,
        @PathVariable("deltakerId") deltakerId: UUID,
    ) = if (isInternal(servlet)) {
        val aktivitetskort = aktivitetskortService
            .getSisteMeldingForDeltaker(
                deltakerId,
            )?.aktivitetskort
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke melding")

        aktivitetskortProducer.send(aktivitetskort)
        logResendMessage(deltakerId)
    } else {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    @Unprotected
    @GetMapping("/resend/")
    fun resendSistMeldinger(
        servlet: HttpServletRequest,
        @RequestBody body: DeltakereBody,
    ) = if (isInternal(servlet)) {
        body.deltakere.forEach { deltakerId ->
            val aktivitetskort = aktivitetskortService
                .getSisteMeldingForDeltaker(deltakerId)
                ?.aktivitetskort
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke melding")
            aktivitetskortProducer.send(aktivitetskort)
            logResendMessage(deltakerId)
        }
    } else {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    @Unprotected
    @PostMapping("/slett/")
    fun slettAktivitetskort(
        servlet: HttpServletRequest,
        @RequestBody body: SlettAktivitetskortBody,
    ) = if (isInternal(servlet)) {
        aktivitetskortProducer.slettAktivitetskort(
            aktivitetskortId = body.aktivitetskortId,
            personIdent = body.personIdent,
            navIdent = body.navIdent,
        )
    } else {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }

    data class DeltakereBody(
        val deltakere: List<UUID>,
    )

    data class SlettAktivitetskortBody(
        val aktivitetskortId: UUID,
        val personIdent: String,
        val navIdent: String,
    )

    companion object {
        private val log = LoggerFactory.getLogger(InternalAPI::class.java)

        private fun logResendMessage(deltakerId: UUID) = log.info("Resendte siste aktivitetskort for deltaker med id $deltakerId")

        private fun isInternal(servlet: HttpServletRequest): Boolean = servlet.remoteAddr == "127.0.0.1"
    }
}
