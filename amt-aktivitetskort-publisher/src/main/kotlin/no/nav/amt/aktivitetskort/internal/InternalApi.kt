package no.nav.amt.aktivitetskort.internal

import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.repositories.DeltakerRepository
import no.nav.amt.aktivitetskort.service.AktivitetskortService
import no.nav.amt.lib.models.deltaker.Kilde
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.transaction.support.TransactionTemplate
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
class InternalApi(
    private val aktivitetskortService: AktivitetskortService,
    private val aktivitetskortProducer: AktivitetskortProducer,
    private val transactionTemplate: TransactionTemplate,
    private val deltakerRepository: DeltakerRepository,
) {
    // Regenererer aktivitetskort på samme deltaker
    @GetMapping("/publiser/{deltakerId}")
    fun publiserAktivitetskortForDeltaker(
        @PathVariable("deltakerId") deltakerId: UUID,
    ) {
        aktivitetskortService.lagAktivitetskort(deltakerId)
            ?: throw RuntimeException("Kunne ikke opprette aktivitetskort for $deltakerId")
        log.info("La aktivitetskort i Kafka-outbox for deltaker med id $deltakerId")
    }

    @GetMapping("/opprett-nye-kort")
    fun opprettAktivitetskortForDeltaker(
        @RequestBody body: DeltakereBody,
    ) {
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

            val melding = aktivitetskortService.opprettMelding(
                deltaker = deltaker,
                meldingId = nyAktivitetskortId,
            )

            log.info("La nytt aktivitetskort ${melding.id} i Kafka-outbox for deltaker med id $deltakerId")
        }
    }

    @GetMapping("/resend/{deltakerId}")
    fun resendSistSendteMelding(
        @PathVariable("deltakerId") deltakerId: UUID,
    ) {
        val aktivitetskort = aktivitetskortService
            .getSisteMeldingForDeltaker(deltakerId)
            ?.aktivitetskort
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Fant ikke melding",
            )

        transactionTemplate.executeWithoutResult {
            aktivitetskortProducer.send(aktivitetskort)
        }
        logResendMessage(deltakerId)
    }

    @GetMapping("/resend/")
    fun resendSistMeldinger(
        @RequestBody body: DeltakereBody,
    ) {
        body.deltakere.forEach { deltakerId ->
            val aktivitetskort = aktivitetskortService
                .getSisteMeldingForDeltaker(deltakerId)
                ?.aktivitetskort
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke melding")

            transactionTemplate.executeWithoutResult {
                aktivitetskortProducer.send(aktivitetskort)
            }
            logResendMessage(deltakerId)
        }
    }

    @PostMapping("/slett/")
    fun slettAktivitetskort(
        @RequestBody body: SlettAktivitetskortBody,
    ) {
        transactionTemplate.executeWithoutResult {
            aktivitetskortProducer.slettAktivitetskort(
                aktivitetskortId = body.aktivitetskortId,
                personIdent = body.personIdent,
                navIdent = body.navIdent,
            )
        }
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
        private val log = LoggerFactory.getLogger(InternalApi::class.java)

        private fun logResendMessage(deltakerId: UUID) = log.info("La siste aktivitetskort i Kafka-outbox for deltaker med id $deltakerId")
    }
}
