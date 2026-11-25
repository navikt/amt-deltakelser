package no.nav.amt.aktivitetskort.internal

import no.nav.amt.aktivitetskort.kafka.producer.AktivitetskortProducer
import no.nav.amt.aktivitetskort.service.AktivitetskortService
import org.slf4j.LoggerFactory
import org.springframework.dao.ConcurrencyFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Suppress("SpringMvcPathVariableDeclarationInspection")
@RestController
@RequestMapping("/internal")
class InternalApi(
    private val aktivitetskortService: AktivitetskortService,
    private val aktivitetskortProducer: AktivitetskortProducer,
    private val transactionTemplate: TransactionTemplate,
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
            val melding = aktivitetskortService.opprettNyttKort(deltakerId)
            log.info("La nytt aktivitetskort ${melding.id} i Kafka-outbox for deltaker med id $deltakerId")
        }
    }

    @GetMapping("/resend/{deltakerId}")
    fun resendSistSendteMelding(
        @PathVariable("deltakerId") deltakerId: UUID,
    ) {
        aktivitetskortService.resendSisteMelding(deltakerId)
        logResendMessage(deltakerId)
    }

    @GetMapping("/resend/")
    fun resendSistMeldinger(
        @RequestBody body: DeltakereBody,
    ) {
        body.deltakere.forEach { deltakerId ->
            aktivitetskortService.resendSisteMelding(deltakerId)
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

    @ExceptionHandler(ConcurrencyFailureException::class)
    fun handterSamtidigEndring(e: ConcurrencyFailureException): ResponseEntity<String> {
        log.warn("Kortene ble endret under behandlingen. Forespørselen må forsøkes på nytt.", e)
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Kortene er endret. Forsøk forespørselen på nytt.")
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
